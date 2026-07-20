package moe.chenxy.huaweipods.debugcapture

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

/** 一次性查询当前已连接的蓝牙耳机，不执行扫描，也不返回仅配对但未连接的设备。 */
object ConnectedHeadsetDetector {
    private const val DETECTION_TIMEOUT_MS = 2_500L

    suspend fun detect(context: Context): DetectionResult = withContext(Dispatchers.Main.immediate) {
        val appContext = context.applicationContext
        if (
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.BLUETOOTH_CONNECT,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return@withContext DetectionResult.PermissionRequired
        }

        val adapter = appContext.getSystemService(BluetoothManager::class.java)?.adapter
            ?: return@withContext DetectionResult.BluetoothUnavailable
        val enabled = try {
            adapter.isEnabled
        } catch (_: SecurityException) {
            return@withContext DetectionResult.PermissionRequired
        }
        if (!enabled) {
            return@withContext DetectionResult.BluetoothDisabled
        }

        suspendCancellableCoroutine { continuation ->
            val operation = DetectionOperation(
                context = appContext,
                adapter = adapter,
                continuation = continuation,
            )
            operation.start()
            continuation.invokeOnCancellation {
                operation.cancel()
            }
        }
    }

    private class DetectionOperation(
        private val context: Context,
        private val adapter: BluetoothAdapter,
        private val continuation: CancellableContinuation<DetectionResult>,
    ) : BluetoothProfile.ServiceListener {
        private val handler = Handler(Looper.getMainLooper())
        private val pendingProfiles = PROFILE_IDS.toMutableSet()
        private val proxies = mutableMapOf<Int, BluetoothProfile>()
        private val devices = linkedMapOf<String, MutableConnectedHeadset>()
        private var completed = false

        private val timeoutRunnable = Runnable {
            finish(DetectionResult.Success(snapshotDevices(), timedOut = true))
        }

        fun start() {
            check(Looper.myLooper() == Looper.getMainLooper())
            handler.postDelayed(timeoutRunnable, DETECTION_TIMEOUT_MS)

            for (profileId in PROFILE_IDS) {
                val accepted = try {
                    adapter.getProfileProxy(context, this, profileId)
                } catch (_: SecurityException) {
                    finish(DetectionResult.PermissionRequired)
                    return
                } catch (error: RuntimeException) {
                    finish(DetectionResult.Failed(error.message ?: error.javaClass.simpleName))
                    return
                }
                if (!accepted) {
                    pendingProfiles.remove(profileId)
                }
            }
            finishIfComplete()
        }

        fun cancel() {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                closeWithoutResuming()
            } else {
                handler.post(::closeWithoutResuming)
            }
        }

        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (completed) {
                closeProxy(profile, proxy)
                return
            }
            if (profile !in PROFILE_IDS) {
                closeProxy(profile, proxy)
                return
            }

            proxies.put(profile, proxy)?.let { previous ->
                if (previous !== proxy) closeProxy(profile, previous)
            }
            pendingProfiles.remove(profile)

            try {
                proxy.connectedDevices.forEach { device ->
                    val address = device.address
                    val key = address.uppercase(Locale.ROOT)
                    val displayName = device.alias?.takeIf(String::isNotBlank)
                        ?: device.name?.takeIf(String::isNotBlank)
                        ?: UNKNOWN_HEADSET_NAME
                    val current = devices.getOrPut(key) {
                        MutableConnectedHeadset(
                            address = address,
                            displayName = displayName,
                        )
                    }
                    if (current.displayName == UNKNOWN_HEADSET_NAME && displayName != UNKNOWN_HEADSET_NAME) {
                        current.displayName = displayName
                    }
                    current.profiles += HeadsetProfile.fromProfileId(profile)
                }
            } catch (_: SecurityException) {
                finish(DetectionResult.PermissionRequired)
                return
            } catch (error: RuntimeException) {
                finish(DetectionResult.Failed(error.message ?: error.javaClass.simpleName))
                return
            }

            finishIfComplete()
        }

        override fun onServiceDisconnected(profile: Int) {
            proxies.remove(profile)?.let { closeProxy(profile, it) }
            pendingProfiles.remove(profile)
            finishIfComplete()
        }

        private fun finishIfComplete() {
            if (!completed && pendingProfiles.isEmpty()) {
                finish(DetectionResult.Success(snapshotDevices(), timedOut = false))
            }
        }

        private fun finish(result: DetectionResult) {
            if (completed) return
            completed = true
            handler.removeCallbacks(timeoutRunnable)
            closeAllProxies()
            if (!continuation.isActive) {
                return
            }
            continuation.resume(result)
        }

        private fun closeWithoutResuming() {
            if (completed) return
            completed = true
            handler.removeCallbacks(timeoutRunnable)
            closeAllProxies()
        }

        private fun closeAllProxies() {
            val openProxies = proxies.toMap()
            proxies.clear()
            openProxies.forEach { (profile, proxy) -> closeProxy(profile, proxy) }
        }

        private fun closeProxy(profile: Int, proxy: BluetoothProfile) {
            try {
                adapter.closeProfileProxy(profile, proxy)
            } catch (_: SecurityException) {
                // 权限可在检测过程中被撤销，此时已无法继续操作蓝牙服务。
            } catch (_: RuntimeException) {
                // 蓝牙服务可能正在重启；proxy 已从本地集合移除，避免重复关闭。
            }
        }

        private fun snapshotDevices(): List<ConnectedHeadset> = devices.values
            .map { device ->
                ConnectedHeadset(
                    address = device.address,
                    displayName = device.displayName,
                    profiles = device.profiles.toSet(),
                )
            }
            .sortedWith(
                compareBy<ConnectedHeadset> { it.displayName.lowercase(Locale.ROOT) }
                    .thenBy { it.address },
            )
    }

    private data class MutableConnectedHeadset(
        val address: String,
        var displayName: String,
        val profiles: MutableSet<HeadsetProfile> = linkedSetOf(),
    )

    private const val UNKNOWN_HEADSET_NAME = "未知蓝牙耳机"

    private val PROFILE_IDS = intArrayOf(
        BluetoothProfile.A2DP,
        BluetoothProfile.HEADSET,
        BluetoothProfile.LE_AUDIO,
    )
}

enum class HeadsetProfile {
    A2DP,
    HEADSET,
    LE_AUDIO;

    internal companion object {
        fun fromProfileId(profileId: Int): HeadsetProfile = when (profileId) {
            BluetoothProfile.A2DP -> A2DP
            BluetoothProfile.HEADSET -> HEADSET
            BluetoothProfile.LE_AUDIO -> LE_AUDIO
            else -> error("不支持的蓝牙 profile: $profileId")
        }
    }
}

data class ConnectedHeadset(
    val address: String,
    val displayName: String,
    val profiles: Set<HeadsetProfile>,
)

sealed interface DetectionResult {
    data class Success(
        val devices: List<ConnectedHeadset>,
        val timedOut: Boolean,
    ) : DetectionResult

    data object PermissionRequired : DetectionResult
    data object BluetoothUnavailable : DetectionResult
    data object BluetoothDisabled : DetectionResult
    data class Failed(val reason: String) : DetectionResult
}
