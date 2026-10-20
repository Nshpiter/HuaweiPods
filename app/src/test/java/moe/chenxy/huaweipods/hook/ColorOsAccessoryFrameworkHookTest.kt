package moe.chenxy.huaweipods.hook

import moe.chenxy.huaweipods.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorOsAccessoryFrameworkHookTest {
    @Suppress("unused", "UNUSED_PARAMETER")
    class FakeDialogActivity {
        fun h(other: FakeDialogActivity) = Unit

        companion object {
            @JvmStatic
            fun g(activity: FakeDialogActivity) = Unit

            @JvmStatic
            fun check(activity: FakeDialogActivity): Boolean = true
        }
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    class AmbiguousDialogActivity {
        companion object {
            @JvmStatic
            fun g(activity: AmbiguousDialogActivity) = Unit

            @JvmStatic
            fun h(activity: AmbiguousDialogActivity) = Unit
        }
    }

    @Test
    fun `finds the renamed static finish method by signature`() {
        assertEquals(
            "g",
            ColorOsAccessoryFrameworkHook.findPopupFinishMethod(FakeDialogActivity::class.java).name,
        )
    }

    @Test(expected = NoSuchMethodException::class)
    fun `refuses to guess between multiple finish candidates`() {
        ColorOsAccessoryFrameworkHook.findPopupFinishMethod(AmbiguousDialogActivity::class.java)
    }

    @Test
    fun `popup marker carries cached image and model fallback drawable`() {
        val marker = ColorOsAccessoryPopupBridge.popupMarkerPackageName(
            "freebuds5_box.png",
            "img_freebuds5_box",
        )

        assertEquals("${BuildConfig.APPLICATION_ID}|freebuds5_box.png|img_freebuds5_box", marker)
        assertTrue(ColorOsAccessoryPopupBridge.isHuaweiPodsMarker(marker))
        assertEquals("freebuds5_box.png", ColorOsAccessoryPopupBridge.popupImageFileNameFromMarker(marker))
        assertEquals("img_freebuds5_box", ColorOsAccessoryPopupBridge.popupFallbackDrawableFromMarker(marker))
    }

    @Test
    fun `popup marker keeps the fallback drawable without a cached image`() {
        val marker = ColorOsAccessoryPopupBridge.popupMarkerPackageName(null, "img_box")

        assertNull(ColorOsAccessoryPopupBridge.popupImageFileNameFromMarker(marker))
        assertEquals("img_box", ColorOsAccessoryPopupBridge.popupFallbackDrawableFromMarker(marker))
    }

    @Test
    fun `popup marker rejects unsafe drawable names and foreign packages`() {
        val marker = ColorOsAccessoryPopupBridge.popupMarkerPackageName("box.png", "../raw/secret")

        assertNull(ColorOsAccessoryPopupBridge.popupFallbackDrawableFromMarker(marker))
        assertFalse(ColorOsAccessoryPopupBridge.isHuaweiPodsMarker("com.oplus.melody|box.png"))
        assertFalse(ColorOsAccessoryPopupBridge.isHuaweiPodsMarker(null))
    }
}
