package moe.chenxy.huaweipods.update

import android.content.Context
import moe.chenxy.huaweipods.BuildConfig

internal data class PendingUpdateSnapshot(
    val tag: String,
    val versionCode: Long,
    val versionName: String,
    val releaseUrl: String,
    val changelog: String,
    val isPreview: Boolean = false,
)

internal fun restorePendingUpdate(
    snapshot: PendingUpdateSnapshot?,
    currentVersionCode: Long,
    allowPreview: Boolean = false,
    allowPrerelease: Boolean = false,
): GitHubRelease? {
    snapshot ?: return null
    if (snapshot.isPreview && !allowPreview) return null
    if (!snapshot.isPreview && !allowPrerelease && GitHubReleaseChecker.isPrereleaseVersion(snapshot.versionName)) return null
    if (snapshot.tag != snapshot.tag.trim()) return null
    if (snapshot.versionCode <= currentVersionCode) return null

    val parsedTag = GitHubReleaseChecker.parseTag(snapshot.tag) ?: return null
    if (parsedTag.versionCode != snapshot.versionCode) return null
    if (parsedTag.versionName != snapshot.versionName) return null
    if (!GitHubReleaseChecker.isTrustedReleaseUrl(snapshot.releaseUrl)) return null

    return GitHubRelease(
        tag = snapshot.tag,
        versionCode = snapshot.versionCode,
        versionName = snapshot.versionName,
        releaseUrl = snapshot.releaseUrl,
        changelog = snapshot.changelog.take(GitHubReleaseChecker.MAX_CHANGELOG_CHARS),
    )
}

/** Persists an available release until the user explicitly handles it or installs it. */
class PendingUpdateStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    fun restore(
        currentVersionCode: Long,
        allowPreview: Boolean = false,
    ): GitHubRelease? {
        val snapshot = if (prefs.contains(KEY_VERSION_CODE)) {
            PendingUpdateSnapshot(
                tag = prefs.getString(KEY_TAG, "").orEmpty(),
                versionCode = prefs.getLong(KEY_VERSION_CODE, 0L),
                versionName = prefs.getString(KEY_VERSION_NAME, "").orEmpty(),
                releaseUrl = prefs.getString(KEY_RELEASE_URL, "").orEmpty(),
                changelog = prefs.getString(KEY_CHANGELOG, "").orEmpty(),
                isPreview = prefs.getBoolean(KEY_IS_PREVIEW, false),
            )
        } else {
            null
        }
        return restorePendingUpdate(
            snapshot, currentVersionCode, allowPreview,
            allowPrerelease = GitHubReleaseChecker.isPrereleaseVersion(BuildConfig.VERSION_NAME),
        ).also { restored ->
            if (snapshot != null && restored == null) clear()
        }
    }

    fun save(
        release: GitHubRelease,
        isPreview: Boolean = false,
    ): Boolean {
        val validated = restorePendingUpdate(
            snapshot = release.toSnapshot(isPreview = isPreview),
            currentVersionCode = 0L,
            allowPreview = isPreview,
            allowPrerelease = true,
        ) ?: return false
        return prefs.edit()
            .putString(KEY_TAG, validated.tag)
            .putLong(KEY_VERSION_CODE, validated.versionCode)
            .putString(KEY_VERSION_NAME, validated.versionName)
            .putString(KEY_RELEASE_URL, validated.releaseUrl)
            .putString(KEY_CHANGELOG, validated.changelog)
            .putBoolean(KEY_IS_PREVIEW, isPreview)
            .commit()
    }

    fun clear(): Boolean = prefs.edit().clear().commit()

    private fun GitHubRelease.toSnapshot(isPreview: Boolean) = PendingUpdateSnapshot(
        tag = tag,
        versionCode = versionCode,
        versionName = versionName,
        releaseUrl = releaseUrl,
        changelog = changelog,
        isPreview = isPreview,
    )

    private companion object {
        const val PREFS_NAME = "huaweipods_pending_update"
        const val KEY_TAG = "tag"
        const val KEY_VERSION_CODE = "version_code"
        const val KEY_VERSION_NAME = "version_name"
        const val KEY_RELEASE_URL = "release_url"
        const val KEY_CHANGELOG = "changelog"
        const val KEY_IS_PREVIEW = "is_preview"
    }
}
