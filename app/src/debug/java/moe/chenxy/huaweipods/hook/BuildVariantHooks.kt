package moe.chenxy.huaweipods.hook

import moe.chenxy.huaweipods.debugcapture.AiLifeCaptureHook
import moe.chenxy.huaweipods.debugcapture.SmartAudioCaptureTarget

internal object BuildVariantHooks {
    fun supportsPackage(packageName: String): Boolean =
        SmartAudioCaptureTarget.isAllowedSender(packageName)

    fun installForPackage(entry: HookEntry, packageName: String, classLoader: ClassLoader) {
        if (!SmartAudioCaptureTarget.isAllowedSender(packageName)) return
        entry.loadHook("ai-life-capture", AiLifeCaptureHook, classLoader, packageName)
        entry.loadHook(
            "smart-audio-freeclip2-bridge",
            SmartAudioFreeClip2BridgeHook,
            classLoader,
            packageName,
        )
    }
}
