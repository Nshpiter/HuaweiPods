package moe.chenxy.huaweipods.hook

import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

internal object BuildVariantHooks {
    fun onPackageLoaded(entry: HookEntry, param: PackageLoadedParam) = Unit
}
