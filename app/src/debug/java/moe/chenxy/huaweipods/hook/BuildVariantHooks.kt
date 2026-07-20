package moe.chenxy.huaweipods.hook

import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import moe.chenxy.huaweipods.debugcapture.AiLifeCaptureHook

internal object BuildVariantHooks {
    private val capturePackages = setOf(
        "com.huawei.smarthome",
        "com.huawei.smartaudio",
    )

    fun onPackageLoaded(entry: HookEntry, param: PackageLoadedParam) {
        if (param.packageName !in capturePackages) return
        entry.loadHook(AiLifeCaptureHook, param.defaultClassLoader, param.packageName)
    }
}
