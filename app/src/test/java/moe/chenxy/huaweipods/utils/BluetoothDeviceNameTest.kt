package moe.chenxy.huaweipods.utils

import moe.chenxy.huaweipods.pods.HuaweiDeviceRoute
import moe.chenxy.huaweipods.pods.detectHuaweiDeviceRoute
import org.junit.Assert.*
import org.junit.Test

class BluetoothDeviceNameTest {
    @Test fun customNamePreservesChineseEmojiAndInternalSpaces() {
        assertEquals("我的 🎧  耳机", resolveBluetoothDisplayName(" 我的 🎧  耳机 ", "HUAWEI FreeClip"))
    }
    @Test fun blankAliasFallsBackToOriginalName() {
        listOf(null, "", " \t\n").forEach {
            assertEquals("HUAWEI FreeClip", resolveBluetoothDisplayName(it, " HUAWEI FreeClip "))
        }
    }
    @Test fun missingNamesHaveANonemptyDefault() {
        assertEquals("HuaweiPods", resolveBluetoothDisplayName(null, null))
        assertEquals("HuaweiPods", resolveBluetoothDisplayName(" ", "\t"))
    }
    @Test fun olderBatteryEventsCanUseModelOrLocalizedFallbackTitle() {
        assertEquals("HUAWEI FreeClip", resolveBluetoothDisplayName(null, "HUAWEI FreeClip", "耳机"))
        assertEquals("耳机", resolveBluetoothDisplayName(null, null, "耳机"))
    }
    @Test fun displayAliasDoesNotChangeOriginalModelIdentity() {
        val modelName = "HUAWEI FreeClip"
        assertEquals("我的耳机", resolveBluetoothDisplayName("我的耳机", modelName))
        assertEquals(HuaweiDeviceRoute.HUAWEI_FREECLIP, detectHuaweiDeviceRoute(modelName))
    }
    @Test fun currentDeviceRenameRefreshesKnownBatteryWithoutChangingAddressCase() {
        assertTrue(shouldRefreshBluetoothDisplayName("AA:BB", "aa:bb", true))
    }
    @Test fun otherOrMissingDeviceCannotRefreshCurrentNotification() {
        assertFalse(shouldRefreshBluetoothDisplayName("AA:BB", "CC:DD", true))
        assertFalse(shouldRefreshBluetoothDisplayName(null, "AA:BB", true))
        assertFalse(shouldRefreshBluetoothDisplayName("AA:BB", " ", true))
    }
    @Test fun renameDoesNotCreateBatteryStateForANewOrClosedSession() {
        assertFalse(shouldRefreshBluetoothDisplayName("AA:BB", "AA:BB", false))
    }
}
