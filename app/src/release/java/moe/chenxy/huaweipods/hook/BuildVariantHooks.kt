package moe.chenxy.huaweipods.hook

internal object BuildVariantHooks {
    fun supportsPackage(packageName: String): Boolean = packageName == "com.huawei.smartaudio"

    fun installForPackage(entry: HookEntry, packageName: String, classLoader: ClassLoader) {
        if (packageName != "com.huawei.smartaudio") return
        entry.loadHook(
            "smart-audio-freeclip2-bridge",
            SmartAudioFreeClip2BridgeHook,
            classLoader,
            packageName,
        )
    }
}
