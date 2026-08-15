package moe.chenxy.huaweipods.hook.milink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FreeClip2MiLinkUiPolicyTest {

    @Test
    fun `section title translation aligns its visible start without accumulating`() {
        assertEquals(
            -19f,
            FreeClip2MiLinkUiPolicy.alignedTitleTranslation(
                originalTranslation = 3f,
                referenceStart = 100,
                targetStart = 122,
            ),
        )
    }
    @Test
    fun `native labels map to official FreeClip2 semantics`() {
        assertEquals(FreeClip2MiLinkLabel.AUDIO_SETTINGS, FreeClip2MiLinkUiPolicy.classify("噪声控制"))
        assertEquals(FreeClip2MiLinkLabel.FIXED, FreeClip2MiLinkUiPolicy.classify("沉浸声"))
        assertEquals(FreeClip2MiLinkLabel.FIXED, FreeClip2MiLinkUiPolicy.classify("沉浸音"))
        assertEquals(FreeClip2MiLinkLabel.HEAD_TRACKING, FreeClip2MiLinkUiPolicy.classify("头部追踪"))
        assertEquals(FreeClip2MiLinkLabel.OFF, FreeClip2MiLinkUiPolicy.classify("关闭"))
        assertNull(FreeClip2MiLinkUiPolicy.classify("噪声控制说明"))
        // 模块注入的真实音效标题不能被再次当成宿主旧“噪声控制”标题隐藏。
        assertNull(FreeClip2MiLinkUiPolicy.classify("音效"))
    }

    @Test
    fun `only three consecutive sibling slots may be reordered`() {
        assertTrue(FreeClip2MiLinkUiPolicy.isSafeConsecutiveOrder(listOf(5, 3, 4)))
        assertFalse(FreeClip2MiLinkUiPolicy.isSafeConsecutiveOrder(listOf(1, 2, 4)))
        assertFalse(FreeClip2MiLinkUiPolicy.isSafeConsecutiveOrder(listOf(1, 1, 2)))
    }

    @Test
    fun `section typography reference only accepts exact volume heading`() {
        assertTrue(FreeClip2MiLinkUiPolicy.isVolumeHeading("音量 | 42%"))
        assertTrue(FreeClip2MiLinkUiPolicy.isVolumeHeading("Volume: 42%"))
        assertTrue(FreeClip2MiLinkUiPolicy.isSpatialAudioHeading("空间音频"))
        assertTrue(FreeClip2MiLinkUiPolicy.isSpatialAudioHeading("Spatial audio"))
        assertFalse(FreeClip2MiLinkUiPolicy.isVolumeHeading("调节音量"))
        assertFalse(FreeClip2MiLinkUiPolicy.isVolumeHeading("Volume control"))
        assertFalse(FreeClip2MiLinkUiPolicy.isSpatialAudioHeading("空间音频说明"))
    }

    @Test
    fun `host ANC section collapses only for supported devices without ANC`() {
        assertTrue(FreeClip2MiLinkUiPolicy.shouldCollapseHostAncSection(true, false))
        assertFalse(FreeClip2MiLinkUiPolicy.shouldCollapseHostAncSection(true, true))
        assertFalse(FreeClip2MiLinkUiPolicy.shouldCollapseHostAncSection(false, false))
    }
}
