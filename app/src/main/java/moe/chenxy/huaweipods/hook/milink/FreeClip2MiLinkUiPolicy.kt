package moe.chenxy.huaweipods.hook.milink

internal enum class FreeClip2MiLinkLabel {
    AUDIO_SETTINGS,
    OFF,
    FIXED,
    HEAD_TRACKING,
}

internal object FreeClip2MiLinkUiPolicy {
    private val audioSettingsTitles = setOf("噪声控制", "Noise control")
    private val offTitles = setOf("关闭", "Off")
    private val fixedTitles = setOf("沉浸声", "沉浸音", "Immersive sound", "Immersive audio", "固定", "Fixed")
    private val headTrackingTitles = setOf("头部追踪", "头部跟踪", "Head tracking")
    private val spatialAudioTitles = setOf("空间音频", "Spatial audio")
    private val volumeHeadingPattern = Regex(
        pattern = "^(?:音量|volume)(?:\\s*(?:[|｜·:]\\s*.*|\\d{1,3}%))?$",
        option = RegexOption.IGNORE_CASE,
    )

    fun classify(text: CharSequence?): FreeClip2MiLinkLabel? {
        val normalized = text?.toString()?.trim()?.takeIf(String::isNotEmpty) ?: return null
        return when {
            audioSettingsTitles.any { it.equals(normalized, ignoreCase = true) } ->
                FreeClip2MiLinkLabel.AUDIO_SETTINGS
            offTitles.any { it.equals(normalized, ignoreCase = true) } ->
                FreeClip2MiLinkLabel.OFF
            fixedTitles.any { it.equals(normalized, ignoreCase = true) } ->
                FreeClip2MiLinkLabel.FIXED
            headTrackingTitles.any { it.equals(normalized, ignoreCase = true) } ->
                FreeClip2MiLinkLabel.HEAD_TRACKING
            else -> null
        }
    }

    /** 仅匹配分区标题，避免把“调节音量”等操作项误作字体参考。 */
    fun isVolumeHeading(text: CharSequence?): Boolean {
        val normalized = text?.toString()?.trim()?.takeIf(String::isNotEmpty) ?: return false
        return volumeHeadingPattern.matches(normalized)
    }

    fun isSpatialAudioHeading(text: CharSequence?): Boolean {
        val normalized = text?.toString()?.trim()?.takeIf(String::isNotEmpty) ?: return false
        return spatialAudioTitles.any { it.equals(normalized, ignoreCase = true) }
    }

    /** 三个选项必须正好占据同一容器的连续位置，才允许移动宿主 View。 */
    fun isSafeConsecutiveOrder(indices: Collection<Int>): Boolean {
        val sorted = indices.distinct().sorted()
        return sorted.size == 3 && sorted.last() - sorted.first() == 2
    }

    /** 宿主按该标志计算详情弹窗高度；无 ANC 型号必须同时关闭，不能只隐藏子 View。 */
    fun shouldCollapseHostAncSection(isSupported: Boolean, supportsAnc: Boolean): Boolean =
        isSupported && !supportsAnc
}
