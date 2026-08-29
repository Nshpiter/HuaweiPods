package moe.chenxy.huaweipods.utils

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.Resources
import moe.chenxy.huaweipods.BuildConfig

/** 检测 APK 覆盖安装后，宿主进程是否仍在运行旧版 Hook dex。 */
object ModuleResourceResolver {
    @Volatile
    private var hotReloadApplicationInfo: ApplicationInfo? = null

    /** API 102 的新代 Hook 使用框架交付的 ApplicationInfo 校验版本，避开宿主 PM 缓存。 */
    fun initialize(moduleApplicationInfo: ApplicationInfo, hotReloadEnabled: Boolean) {
        hotReloadApplicationInfo = moduleApplicationInfo.takeIf { hotReloadEnabled }
    }

    /** 资源也固定到 API 102 当前代 APK，避免长期宿主进程复用旧资源缓存。 */
    fun resources(hostContext: Context): Resources? = runCatching {
        hotReloadApplicationInfo?.let { applicationInfo ->
            hostContext.packageManager.getResourcesForApplication(applicationInfo)
        } ?: hostContext.createPackageContext(
                BuildConfig.APPLICATION_ID,
                Context.CONTEXT_IGNORE_SECURITY,
            ).resources
    }.getOrNull()

    fun isCurrentModuleBuild(hostContext: Context): Boolean {
        hotReloadApplicationInfo?.let { applicationInfo ->
            if (applicationInfo.packageName != BuildConfig.APPLICATION_ID) return false
            if (applicationInfo.sourceDir.isNullOrBlank()) return false
            val frameworkBuildId = applicationInfo.metaData?.getString(MODULE_BUILD_ID_META_DATA)
            return frameworkBuildId == null || moduleBuildMatches(
                frameworkBuildId,
                BuildConfig.MODULE_BUILD_ID,
            )
        }
        val installedBuildId = runCatching {
                hostContext.packageManager.getApplicationInfo(
                    BuildConfig.APPLICATION_ID,
                    PackageManager.GET_META_DATA,
                ).metaData?.getString(MODULE_BUILD_ID_META_DATA)
            }.getOrNull()
        return moduleBuildMatches(installedBuildId, BuildConfig.MODULE_BUILD_ID)
    }

    internal fun moduleBuildMatches(installedBuildId: String?, hookBuildId: String): Boolean =
        !installedBuildId.isNullOrBlank() && installedBuildId == hookBuildId

    private const val MODULE_BUILD_ID_META_DATA =
        "moe.chenxy.huaweipods.MODULE_BUILD_ID"
}
