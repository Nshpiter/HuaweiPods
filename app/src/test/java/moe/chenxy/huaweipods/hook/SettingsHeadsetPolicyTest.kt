package moe.chenxy.huaweipods.hook

import moe.chenxy.huaweipods.pods.HuaweiDeviceRoute
import moe.chenxy.huaweipods.pods.enabledHuaweiDeviceRoutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsHeadsetPolicyTest {
    @Test
    fun `settings ANC renderer is skipped for clip and eyewear routes`() {
        listOf(
            HuaweiDeviceRoute.HUAWEI_FREECLIP,
            HuaweiDeviceRoute.HUAWEI_FREECLIP2,
            HuaweiDeviceRoute.HUAWEI_FREEARC,
            HuaweiDeviceRoute.HUAWEI_EYEWEAR,
            HuaweiDeviceRoute.HUAWEI_EYEWEAR2,
        ).forEach { route ->
            assertFalse(route.name, shouldUpdateSettingsAncUi(route))
        }
    }

    @Test
    fun `settings ANC renderer remains enabled for ANC routes`() {
        listOf(
            HuaweiDeviceRoute.HUAWEI_FREEBUDS3,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3,
        ).forEach { route ->
            assertTrue(route.name, shouldUpdateSettingsAncUi(route))
        }
    }

    @Test
    fun `settings policy exposes only the controls bridged for every route`() {
        fun expectedPolicy(
            anc: Boolean,
            transparency: Boolean,
            gestures: Boolean,
        ) = SettingsHeadsetUiPolicy(
            showAnc = anc,
            showTransparency = transparency,
            showGestureConfiguration = gestures,
            showEarTipFitTest = false,
        )

        val expected = linkedMapOf(
            HuaweiDeviceRoute.HUAWEI_FREEBUDS3 to expectedPolicy(true, false, true),
            HuaweiDeviceRoute.HUAWEI_FREEBUDS4E to expectedPolicy(true, false, true),
            HuaweiDeviceRoute.HUAWEI_FREEBUDS5 to expectedPolicy(true, false, false),
            HuaweiDeviceRoute.HUAWEI_FREEBUDS5I to expectedPolicy(true, true, true),
            HuaweiDeviceRoute.HUAWEI_FREEBUDS6I to expectedPolicy(true, true, true),
            HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3 to expectedPolicy(true, true, true),
            HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO4 to expectedPolicy(true, false, false),
            HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO5 to expectedPolicy(true, true, true),
            HuaweiDeviceRoute.HUAWEI_FREEBUDS7I to expectedPolicy(true, true, true),
            HuaweiDeviceRoute.HUAWEI_FREECLIP to expectedPolicy(false, false, false),
            HuaweiDeviceRoute.HUAWEI_FREECLIP2 to expectedPolicy(false, false, true),
            HuaweiDeviceRoute.HUAWEI_FREEARC to expectedPolicy(false, false, true),
            HuaweiDeviceRoute.HUAWEI_EYEWEAR to expectedPolicy(false, false, false),
            HuaweiDeviceRoute.HUAWEI_EYEWEAR2 to expectedPolicy(false, false, true),
        )

        assertEquals(enabledHuaweiDeviceRoutes(), expected.keys.toList())
        expected.forEach { (route, policy) ->
            assertEquals(route.name, policy, settingsHeadsetUiPolicy(route))
        }
    }

    @Test
    fun `ear tip fit stays hidden for every route until its command is bridged`() {
        enabledHuaweiDeviceRoutes().forEach { route ->
            assertFalse(route.name, settingsHeadsetUiPolicy(route).showEarTipFitTest)
        }
        assertFalse(
            HuaweiDeviceRoute.UNSUPPORTED.name,
            settingsHeadsetUiPolicy(HuaweiDeviceRoute.UNSUPPORTED).showEarTipFitTest,
        )
    }

    @Test
    fun `ear tip fit labels cover exact Chinese and English settings titles`() {
        listOf(
            "耳塞贴合度检测",
            "耳塞贴合度测试",
            "耳塞贴合",
            "Ear tip fit test",
            "Earbud fit test",
        ).forEach { label -> assertTrue(label, isSettingsEarTipFitText(label)) }

        listOf(
            "佩戴检测",
            "通话",
            "检查更新",
            "Find earbuds",
            "Head tracking",
            "打开耳塞贴合度检测可获得更准确的结果",
            "Run the Ear tip fit test after changing ear tips",
        ).forEach { label -> assertFalse(label, isSettingsEarTipFitText(label)) }
    }

    @Test
    fun `fake Xiaomi notification setting uses exact Chinese and English titles`() {
        listOf(
            "通知栏显示",
            "耳机连接后在通知栏显示状态信息",
            "Notification display",
            "Show in notification shade",
        ).forEach { label -> assertTrue(label, isSettingsNotificationDisplayText(label)) }

        listOf(
            "通知与状态栏",
            "锁屏通知",
            "Show notification history",
            "关闭通知栏显示后仍可在模块中管理通知",
        ).forEach { label -> assertFalse(label, isSettingsNotificationDisplayText(label)) }
    }

    @Test
    fun `unsupported route exposes no settings controls`() {
        val policy = settingsHeadsetUiPolicy(HuaweiDeviceRoute.UNSUPPORTED)
        assertFalse(policy.showAnc)
        assertFalse(policy.showTransparency)
        assertFalse(policy.showGestureConfiguration)
        assertFalse(policy.showEarTipFitTest)
    }

    @Test
    fun `FreeBuds 5 replaces the native four-level row with a three-level selector`() {
        assertTrue(usesCustomSettingsAncSelector(HuaweiDeviceRoute.HUAWEI_FREEBUDS5))
        assertFalse(usesCustomSettingsAncSelector(HuaweiDeviceRoute.HUAWEI_FREEBUDS6I))
        assertFalse(usesCustomSettingsAncSelector(HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3))
        assertFalse(usesCustomSettingsAncSelector(HuaweiDeviceRoute.HUAWEI_FREEBUDS3))
    }

    @Test
    fun `only replaced native ANC level rows need a deferred prune`() {
        assertTrue(requiresDeferredSettingsAncLevelPrune(HuaweiDeviceRoute.HUAWEI_FREEBUDS3))
        assertTrue(requiresDeferredSettingsAncLevelPrune(HuaweiDeviceRoute.HUAWEI_FREEBUDS5))
        assertFalse(requiresDeferredSettingsAncLevelPrune(HuaweiDeviceRoute.HUAWEI_FREEBUDS6I))
        assertFalse(requiresDeferredSettingsAncLevelPrune(HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO5))
    }

    @Test
    fun `native short-range ANC level controls are detected without matching ordinary sliders`() {
        assertTrue(
            isNativeSettingsAncLevelControl(
                className = "com.android.settings.bluetooth.AncLevelView",
                resourceEntryName = null,
                progressMax = null,
            ),
        )
        assertTrue(
            isNativeSettingsAncLevelControl(
                className = "miuix.androidbasewidget.widget.SeekBar",
                resourceEntryName = "anc_level_seekbar",
                progressMax = 3,
            ),
        )
        assertTrue(
            isNativeSettingsAncLevelControl(
                className = "miuix.androidbasewidget.widget.SeekBar",
                resourceEntryName = null,
                progressMax = 3,
            ),
        )
        assertFalse(
            isNativeSettingsAncLevelControl(
                className = "android.widget.SeekBar",
                resourceEntryName = "media_volume",
                progressMax = 15,
            ),
        )
        assertFalse(
            isNativeSettingsAncLevelControl(
                className = "android.widget.ProgressBar",
                resourceEntryName = "battery_level",
                progressMax = 100,
            ),
        )
    }

    @Test
    fun `native ANC level siblings are only the branches between mode row and direction dial`() {
        assertEquals(listOf(2, 3), nativeSettingsAncLevelSiblingIndexes(1, 4))
        assertEquals(listOf(1), nativeSettingsAncLevelSiblingIndexes(0, 2))
        assertTrue(nativeSettingsAncLevelSiblingIndexes(2, 3).isEmpty())
        assertTrue(nativeSettingsAncLevelSiblingIndexes(3, 2).isEmpty())
        assertTrue(nativeSettingsAncLevelSiblingIndexes(-1, 3).isEmpty())
    }

    @Test
    fun `only FreeBuds 6i reuses the corrected native transparency selector`() {
        assertTrue(usesNativeSettingsTransparencySelector(HuaweiDeviceRoute.HUAWEI_FREEBUDS6I))
        assertFalse(usesNativeSettingsTransparencySelector(HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO5))
        assertFalse(usesNativeSettingsTransparencySelector(HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3))
    }

    @Test
    fun `RecyclerView whole item collapse rejects grouped adjacent settings`() {
        assertTrue(
            shouldCollapseSettingsRecyclerItem(
                itemInteractive = true,
                topLevelInteractiveDescendantCount = 0,
            ),
        )
        assertTrue(
            shouldCollapseSettingsRecyclerItem(
                itemInteractive = false,
                topLevelInteractiveDescendantCount = 1,
            ),
        )
        assertFalse(
            shouldCollapseSettingsRecyclerItem(
                itemInteractive = false,
                topLevelInteractiveDescendantCount = 0,
            ),
        )
        assertFalse(
            shouldCollapseSettingsRecyclerItem(
                itemInteractive = true,
                topLevelInteractiveDescendantCount = 2,
            ),
        )
    }

    @Test
    fun `RecyclerView collapsed layout is reversible and preserves non vertical fields`() {
        val original = SettingsRowLayoutState(
            height = -2,
            topMargin = 12,
            bottomMargin = 18,
            minimumHeight = 144,
        )

        assertEquals(
            SettingsRowLayoutState(
                height = 0,
                topMargin = 0,
                bottomMargin = 0,
                minimumHeight = 0,
            ),
            original.collapsed(),
        )
        assertEquals(-2, original.height)
        assertEquals(12, original.topMargin)
        assertEquals(18, original.bottomMargin)
        assertEquals(144, original.minimumHeight)

        assertEquals(
            SettingsRowLayoutState(null, null, null, 0),
            SettingsRowLayoutState(null, null, null, 96).collapsed(),
        )
    }
}
