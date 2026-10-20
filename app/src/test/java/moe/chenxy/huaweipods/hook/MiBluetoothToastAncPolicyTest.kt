package moe.chenxy.huaweipods.hook

import moe.chenxy.huaweipods.coloros.colorOsLiveAlertChipText
import moe.chenxy.huaweipods.coloros.shouldShowColorOsLiveAlert
import moe.chenxy.huaweipods.coloros.shouldAcceptColorOsLiveAlertBatteryUpdate
import moe.chenxy.huaweipods.pods.HuaweiDeviceRoute
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MiBluetoothToastAncPolicyTest {

    @Test
    fun colorOsAutoPopupRequiresAnUnlockedInteractiveFirstConnection() {
        assertTrue(shouldAttemptColorOsAutoPopup(true, false, true, false))
        assertFalse(shouldAttemptColorOsAutoPopup(false, false, true, false))
        assertFalse(shouldAttemptColorOsAutoPopup(true, true, true, false))
        assertFalse(shouldAttemptColorOsAutoPopup(true, false, false, false))
        assertFalse(shouldAttemptColorOsAutoPopup(true, false, true, true))
    }

    @Test
    fun colorOsNotificationMovesToAppOnlyWhenPermissionIsGranted() {
        assertTrue(shouldDelegateColorOsNotificationToApp(true, true))
        assertFalse(shouldDelegateColorOsNotificationToApp(true, false))
        assertFalse(shouldDelegateColorOsNotificationToApp(false, true))
    }

    @Test
    fun colorOsLiveAlertUsesLowestConnectedEarbudForCompactChip() {
        assertTrue(colorOsLiveAlertChipText(100, 80, 60) == "80%")
        assertTrue(colorOsLiveAlertChipText(null, null, 60) == "60%")
        assertTrue(colorOsLiveAlertChipText(null, null, null) == null)
        assertTrue(shouldShowColorOsLiveAlert(true, true))
        assertFalse(shouldShowColorOsLiveAlert(false, true))
        assertFalse(shouldShowColorOsLiveAlert(true, false))
    }

    @Test
    fun colorOsLiveAlertRejectsBatteryEventsOlderThanDisconnect() {
        assertTrue(shouldAcceptColorOsLiveAlertBatteryUpdate(0L, 0L))
        assertFalse(shouldAcceptColorOsLiveAlertBatteryUpdate(200L, 0L))
        assertFalse(shouldAcceptColorOsLiveAlertBatteryUpdate(200L, 199L))
        assertFalse(shouldAcceptColorOsLiveAlertBatteryUpdate(200L, 200L))
        assertTrue(shouldAcceptColorOsLiveAlertBatteryUpdate(200L, 201L))
    }

    @Test
    fun colorOsCloseGuardAllowsUserClickAndOnlyBlocksFirstAutomaticClose() {
        assertTrue(shouldSuppressColorOsPopupClose(true, false, false))
        assertFalse(shouldSuppressColorOsPopupClose(true, true, false))
        assertFalse(shouldSuppressColorOsPopupClose(true, false, true))
        assertFalse(shouldSuppressColorOsPopupClose(false, false, false))
    }

    @Test
    fun colorOsPopupMarkerRejectsNestedOrBlankImageNames() {
        val marker = ColorOsAccessoryPopupBridge.popupMarkerPackageName("freeclip_box.png")
        assertTrue(marker.startsWith("${moe.chenxy.huaweipods.BuildConfig.APPLICATION_ID}|"))
        assertTrue(
            ColorOsAccessoryPopupBridge.popupImageFileNameFromMarker(marker) ==
                "freeclip_box.png",
        )
        assertTrue(
            ColorOsAccessoryPopupBridge.popupImageFileNameFromMarker(
                ColorOsAccessoryPopupBridge.popupMarkerPackageName(null),
            ) == null,
        )
        assertTrue(
            ColorOsAccessoryPopupBridge.popupImageFileNameFromMarker(
                ColorOsAccessoryPopupBridge.popupMarkerPackageName("../image.png"),
            ) == null,
        )
    }

    @Test
    fun notificationUpdateIsRejectedAfterDisconnectOrVerifiedOffline() {
        assertFalse(shouldAcceptPodsNotificationUpdate(true, true))
        assertFalse(shouldAcceptPodsNotificationUpdate(false, false))
        assertTrue(shouldAcceptPodsNotificationUpdate(false, true))
        assertTrue(shouldAcceptPodsNotificationUpdate(false, null))
    }
    @Test
    fun `notification click target is isolated by bluetooth address`() {
        val first = headsetNotificationIntentIdentity("AA:BB:CC:DD:EE:01")
        val second = headsetNotificationIntentIdentity("AA:BB:CC:DD:EE:02")

        assertTrue(first != second)
    }

    @Test
    fun `notification exposes ANC only for verified ANC earbuds`() {
        listOf(
            HuaweiDeviceRoute.HUAWEI_FREEBUDS3,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS5,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS5I,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO4,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO5,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS7I,
        ).forEach { route ->
            assertTrue(route.name, shouldOfferNotificationAncAction(route))
        }
    }

    @Test
    fun `notification never exposes ANC for clips eyewear or unknown devices`() {
        listOf(
            HuaweiDeviceRoute.HUAWEI_FREECLIP,
            HuaweiDeviceRoute.HUAWEI_FREECLIP2,
            HuaweiDeviceRoute.HUAWEI_FREEARC,
            HuaweiDeviceRoute.HUAWEI_EYEWEAR,
            HuaweiDeviceRoute.HUAWEI_EYEWEAR2,
            HuaweiDeviceRoute.UNSUPPORTED,
        ).forEach { route ->
            assertFalse(route.name, shouldOfferNotificationAncAction(route))
        }
    }

    @Test
    fun `case opening is detected only after the case stopped reporting`() {
        assertFalse(isColorOsCaseOpened(previousCaseReported = null, caseReported = true))
        assertTrue(isColorOsCaseOpened(previousCaseReported = false, caseReported = true))
        assertFalse(isColorOsCaseOpened(previousCaseReported = true, caseReported = true))
        assertFalse(isColorOsCaseOpened(previousCaseReported = false, caseReported = false))
    }

    @Test
    fun `case open popup is rate limited per device`() {
        assertTrue(shouldShowColorOsCaseOpenPopup(lastCaseOpenPopupAt = null, now = 1_000L))
        assertFalse(shouldShowColorOsCaseOpenPopup(lastCaseOpenPopupAt = 1_000L, now = 10_000L))
        assertTrue(shouldShowColorOsCaseOpenPopup(lastCaseOpenPopupAt = 1_000L, now = 16_000L))
    }
}
