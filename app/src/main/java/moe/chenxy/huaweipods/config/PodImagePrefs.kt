package moe.chenxy.huaweipods.config

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import io.github.libxposed.service.XposedService
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

enum class PodImageResource(val fileSuffix: String) {
    BOX("box"),
    LEFT("left"),
    RIGHT("right"),
}

@Serializable
data class EarphonePref(
    val address: String,
    val name: String,
    val boxImagePath: String? = null,
    val leftImagePath: String? = null,
    val rightImagePath: String? = null,
    val cloudModelId: String? = null,
    val cloudSubModelId: String? = null,
    val cloudBoxImagePath: String? = null,
    val cloudLeftImagePath: String? = null,
    val cloudRightImagePath: String? = null,
    val lastConnectedAt: Long = System.currentTimeMillis(),
) {
    fun imagePath(resource: PodImageResource): String? = when (resource) {
        PodImageResource.BOX -> boxImagePath
        PodImageResource.LEFT -> leftImagePath
        PodImageResource.RIGHT -> rightImagePath
    }

    fun cloudImagePath(resource: PodImageResource): String? = when (resource) {
        PodImageResource.BOX -> cloudBoxImagePath
        PodImageResource.LEFT -> cloudLeftImagePath
        PodImageResource.RIGHT -> cloudRightImagePath
    }
}

@Serializable
internal data class CloudImageIdentityPref(
    val address: String,
    val modelId: String,
    val subModelId: String,
)

object PodImagePrefs {
    private const val MAX_CLOUD_IDENTITIES = 16
    const val AUTHORITY = "moe.chenxy.huaweipods.podimages"
    private const val PREF_KEY_EARPHONES = "earphone_prefs_json"
    private const val PREF_KEY_CLOUD_IDENTITIES = "cloud_image_identities_json"
    private const val IMAGE_DIR = "pod_images"

    private val mutationLock = Any()
    private val remoteSyncLock = Any()
    private var mutationRevision = 0L

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun load(prefs: SharedPreferences): List<EarphonePref> {
        val raw = prefs.getString(PREF_KEY_EARPHONES, null) ?: return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(EarphonePref.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    fun find(prefs: SharedPreferences, address: String): EarphonePref? {
        if (address.isBlank()) return null
        return load(prefs).firstOrNull { it.address.equals(address, ignoreCase = true) }
    }

    fun findOrLatest(prefs: SharedPreferences, address: String): EarphonePref? {
        return find(prefs, address) ?: load(prefs).maxByOrNull { it.lastConnectedAt }
    }

    fun imageDir(context: Context): File = File(context.filesDir, IMAGE_DIR).apply { mkdirs() }

    fun upsertConnected(
        prefs: SharedPreferences,
        service: XposedService?,
        address: String,
        name: String,
    ): List<EarphonePref> {
        if (address.isBlank()) return load(prefs)
        val mutation = mutate(prefs) { current ->
            val existing = current.firstOrNull { it.address.equals(address, ignoreCase = true) }
            val updated = (existing ?: EarphonePref(address = address, name = name)).copy(
                name = name.ifBlank { existing?.name.orEmpty() },
                lastConnectedAt = System.currentTimeMillis(),
            )
            listOf(updated) + current.filterNot { it.address.equals(address, ignoreCase = true) }
        }
        syncRemote(service, mutation)
        return mutation.earphones
    }

    fun saveImages(
        context: Context,
        prefs: SharedPreferences,
        service: XposedService?,
        address: String,
        name: String,
        selectedImages: Map<PodImageResource, Uri?>,
        clearedImages: Set<PodImageResource> = emptySet(),
    ): List<EarphonePref> {
        if (address.isBlank()) return load(prefs)
        // ContentResolver I/O 不占用偏好锁；锁只覆盖原子 read-modify-write。
        val selectedPaths = selectedImages.mapNotNull { (resource, uri) ->
            uri?.let { resource to copyImage(context, address, resource, it) }
        }.toMap()
        val mutation = mutate(prefs) { current ->
            val existing = current.firstOrNull { it.address.equals(address, ignoreCase = true) }
            var updated = existing ?: EarphonePref(address = address, name = name)
            clearedImages.forEach { resource -> updated = updated.withImagePath(resource, null) }
            selectedPaths.forEach { (resource, path) -> updated = updated.withImagePath(resource, path) }
            updated = updated.copy(
                name = name.ifBlank { updated.name },
                lastConnectedAt = System.currentTimeMillis(),
            )
            listOf(updated) + current.filterNot { it.address.equals(address, ignoreCase = true) }
        }
        syncRemote(service, mutation)
        return mutation.earphones
    }

    /** 保存官方 CDN 图片到独立槽位，不覆盖用户手动选择的图片。 */
    fun saveCloudImages(
        prefs: SharedPreferences,
        service: XposedService? = null,
        address: String,
        modelId: String,
        subModelId: String,
        imagePaths: Map<PodImageResource, String>,
    ): List<EarphonePref> {
        if (address.isBlank() || imagePaths[PodImageResource.BOX].isNullOrBlank()) return load(prefs)
        val mutation = mutate(prefs) { current ->
            val existing = current.firstOrNull { it.address.equals(address, ignoreCase = true) }
            val updated = (existing ?: EarphonePref(address = address, name = "")).copy(
                cloudModelId = modelId,
                cloudSubModelId = subModelId,
                cloudBoxImagePath = imagePaths[PodImageResource.BOX],
                cloudLeftImagePath = imagePaths[PodImageResource.LEFT],
                cloudRightImagePath = imagePaths[PodImageResource.RIGHT],
                lastConnectedAt = System.currentTimeMillis(),
            )
            listOf(updated) + current.filterNot { it.address.equals(address, ignoreCase = true) }
        }
        syncRemote(service, mutation)
        return mutation.earphones
    }

    /**
     * 记录智慧音频刚确认的最新资源身份。必须在调度下载前持久化，供跨进程重启后的 Job 校验。
     */
    fun recordLatestCloudIdentity(
        prefs: SharedPreferences,
        address: String,
        modelId: String,
        subModelId: String,
    ): Boolean {
        if (address.isBlank() || modelId.isBlank() || subModelId.isBlank()) return false
        return synchronized(mutationLock) {
            val updated = CloudImageIdentityPref(address, modelId, subModelId)
            val identities = (
                listOf(updated) + loadCloudIdentities(prefs).filterNot {
                    it.address.equals(address, ignoreCase = true)
                }
            ).take(MAX_CLOUD_IDENTITIES)
            prefs.edit()
                .putString(
                    PREF_KEY_CLOUD_IDENTITIES,
                    json.encodeToString(ListSerializer(CloudImageIdentityPref.serializer()), identities),
                )
                .commit()
        }
    }

    internal fun isLatestCloudIdentity(
        prefs: SharedPreferences,
        address: String,
        modelId: String,
        subModelId: String,
    ): Boolean = synchronized(mutationLock) {
        loadCloudIdentities(prefs).firstOrNull {
            it.address.equals(address, ignoreCase = true)
        }?.let { it.modelId == modelId && it.subModelId == subModelId } == true
    }

    internal fun latestCloudIdentities(prefs: SharedPreferences): List<CloudImageIdentityPref> =
        synchronized(mutationLock) { loadCloudIdentities(prefs).take(MAX_CLOUD_IDENTITIES) }

    /**
     * 仅当下载身份仍是该地址的最新身份时写入云图。身份比较与偏好写入共享同一把锁，
     * 因此旧 Job 即使晚于新 Job 完成，也不能覆盖新型号/配色。
     */
    fun saveCloudImagesIfLatest(
        prefs: SharedPreferences,
        service: XposedService? = null,
        address: String,
        modelId: String,
        subModelId: String,
        imagePaths: Map<PodImageResource, String>,
    ): Boolean {
        if (address.isBlank() || imagePaths[PodImageResource.BOX].isNullOrBlank()) return false
        val mutation = synchronized(mutationLock) {
            val latest = loadCloudIdentities(prefs).firstOrNull {
                it.address.equals(address, ignoreCase = true)
            }
            if (latest?.modelId != modelId || latest.subModelId != subModelId) {
                null
            } else {
                mutateLocked(prefs) { current ->
                    val existing = current.firstOrNull { it.address.equals(address, ignoreCase = true) }
                    val updated = (existing ?: EarphonePref(address = address, name = "")).copy(
                        cloudModelId = modelId,
                        cloudSubModelId = subModelId,
                        cloudBoxImagePath = imagePaths[PodImageResource.BOX],
                        cloudLeftImagePath = imagePaths[PodImageResource.LEFT],
                        cloudRightImagePath = imagePaths[PodImageResource.RIGHT],
                        lastConnectedAt = System.currentTimeMillis(),
                    )
                    listOf(updated) + current.filterNot { it.address.equals(address, ignoreCase = true) }
                }
            }
        } ?: return false
        syncRemote(service, mutation)
        return true
    }

    /** 冷启动绑定 LSPosed 服务后，把最新本地快照完整补写到远程偏好。 */
    fun syncSnapshotToRemote(
        prefs: SharedPreferences,
        service: XposedService,
    ): List<EarphonePref> {
        val remotePrefs = runCatching {
            service.getRemotePreferences(ConfigManager.PREFS_NAME)
        }.getOrNull() ?: return load(prefs)
        return syncSnapshotToRemote(prefs, remotePrefs)
    }

    /** 测试入口；远程 IPC 全程不持有 mutationLock。 */
    internal fun syncSnapshotToRemote(
        prefs: SharedPreferences,
        remotePrefs: SharedPreferences,
    ): List<EarphonePref> = synchronized(remoteSyncLock) {
        while (true) {
            val snapshot = synchronized(mutationLock) {
                PreferenceMutation(load(prefs), mutationRevision)
            }
            if (!tryWriteSnapshot(remotePrefs, snapshot.earphones)) {
                return@synchronized snapshot.earphones
            }
            val stillLatest = synchronized(mutationLock) { snapshot.revision == mutationRevision }
            if (stillLatest) return@synchronized snapshot.earphones
        }
        @Suppress("UNREACHABLE_CODE")
        emptyList()
    }

    private fun mutate(
        prefs: SharedPreferences,
        transform: (List<EarphonePref>) -> List<EarphonePref>,
    ): PreferenceMutation = synchronized(mutationLock) {
        mutateLocked(prefs, transform)
    }

    private fun mutateLocked(
        prefs: SharedPreferences,
        transform: (List<EarphonePref>) -> List<EarphonePref>,
    ): PreferenceMutation {
        val normalized = writeSnapshot(prefs, transform(load(prefs)))
        mutationRevision++
        return PreferenceMutation(normalized, mutationRevision)
    }

    /** 远程偏好 IPC 不持有 mutationLock；较旧快照会在发送前被丢弃。 */
    private fun syncRemote(service: XposedService?, mutation: PreferenceMutation) {
        val remotePrefs = runCatching {
            service?.getRemotePreferences(ConfigManager.PREFS_NAME)
        }.getOrNull() ?: return
        synchronized(remoteSyncLock) {
            val stillLatest = synchronized(mutationLock) { mutation.revision == mutationRevision }
            if (stillLatest) tryWriteSnapshot(remotePrefs, mutation.earphones)
        }
    }

    /** RemotePreferences 可能是 LSPosed 的只读代理，写入失败时不能影响宿主进程。 */
    private fun tryWriteSnapshot(
        prefs: SharedPreferences,
        earphones: List<EarphonePref>,
    ): Boolean = runCatching {
        writeSnapshot(prefs, earphones)
        true
    }.getOrDefault(false)

    private fun writeSnapshot(
        prefs: SharedPreferences,
        earphones: List<EarphonePref>,
    ): List<EarphonePref> {
        val normalized = earphones.distinctBy { it.address.uppercase() }
        prefs.edit()
            .putString(PREF_KEY_EARPHONES, json.encodeToString(ListSerializer(EarphonePref.serializer()), normalized))
            .apply()
        return normalized
    }

    private fun loadCloudIdentities(prefs: SharedPreferences): List<CloudImageIdentityPref> {
        val raw = prefs.getString(PREF_KEY_CLOUD_IDENTITIES, null) ?: return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(CloudImageIdentityPref.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    private data class PreferenceMutation(
        val earphones: List<EarphonePref>,
        val revision: Long,
    )

    private fun copyImage(
        context: Context,
        address: String,
        resource: PodImageResource,
        uri: Uri,
    ): String {
        val dir = imageDir(context)
        val file = File(dir, "${address.safeFileName()}_${resource.fileSuffix}.img")
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Unable to open image uri: $uri" }
            file.outputStream().use { output -> input.copyTo(output) }
        }
        return file.absolutePath
    }

    private fun EarphonePref.withImagePath(resource: PodImageResource, path: String?): EarphonePref = when (resource) {
        PodImageResource.BOX -> copy(boxImagePath = path)
        PodImageResource.LEFT -> copy(leftImagePath = path)
        PodImageResource.RIGHT -> copy(rightImagePath = path)
    }

    private fun String.safeFileName(): String = replace(Regex("[^A-Za-z0-9._-]"), "_")
}

fun EarphonePref.imageUri(resource: PodImageResource): Uri? {
    val path = imagePath(resource) ?: return null
    val fileName = File(path).name.takeIf { it.isNotBlank() } ?: return null
    return Uri.Builder()
        .scheme("content")
        .authority(PodImagePrefs.AUTHORITY)
        .appendPath(fileName)
        .build()
}

fun EarphonePref.cloudImageUri(resource: PodImageResource): Uri? {
    val path = cloudImagePath(resource) ?: return null
    val fileName = File(path).name.takeIf { it.isNotBlank() } ?: return null
    return Uri.Builder()
        .scheme("content")
        .authority(PodImagePrefs.AUTHORITY)
        .appendPath(fileName)
        .build()
}

/** 模块界面与 Hook 保持相同优先级：用户手动图优先，官方云图其次。 */
fun EarphonePref.preferredImagePath(resource: PodImageResource): String? =
    imagePath(resource) ?: cloudImagePath(resource)
