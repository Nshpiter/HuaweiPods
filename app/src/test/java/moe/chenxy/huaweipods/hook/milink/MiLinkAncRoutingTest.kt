package moe.chenxy.huaweipods.hook.milink

import android.view.ViewGroup
import java.util.concurrent.atomic.AtomicInteger
import moe.chenxy.huaweipods.pods.FreeClip2SpatialAudioMode
import moe.chenxy.huaweipods.pods.HuaweiDeviceRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MiLinkAncRoutingTest {
    @Test
    fun `two-state ANC buttons route by their own labels instead of compressed host indexes`() {
        assertEquals(2, miLinkTwoStateAncStatusForLabel("降噪"))
        assertEquals(2, miLinkTwoStateAncStatusForLabel(" Noise cancellation "))
        assertEquals(2, miLinkTwoStateAncStatusForLabel("noise reduction"))
        assertEquals(1, miLinkTwoStateAncStatusForLabel("关闭"))
        assertEquals(1, miLinkTwoStateAncStatusForLabel("OFF"))
        assertNull(miLinkTwoStateAncStatusForLabel("通透"))
        assertNull(miLinkTwoStateAncStatusForLabel("噪声控制"))
        assertNull(miLinkTwoStateAncStatusForLabel(null))
    }

    @Test
    fun `bound two-state buttons remain usable if host reuses them for a three-state route`() {
        assertEquals(
            2,
            miLinkBoundAncStatusForRoute(HuaweiDeviceRoute.HUAWEI_FREEBUDS3, "降噪"),
        )
        assertEquals(
            2,
            miLinkBoundAncStatusForRoute(HuaweiDeviceRoute.HUAWEI_FREEBUDS6I, "降噪"),
        )
        assertEquals(
            1,
            miLinkBoundAncStatusForRoute(HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO5, "关闭"),
        )
        assertNull(miLinkBoundAncStatusForRoute(HuaweiDeviceRoute.HUAWEI_FREECLIP2, "降噪"))
        assertNull(miLinkBoundAncStatusForRoute(HuaweiDeviceRoute.UNSUPPORTED, "关闭"))
    }

    @Test
    fun `native ANC card rendering is guarded as UI-only and always restores depth`() {
        val depth = AtomicInteger(0)

        assertEquals(
            "rendered",
            withMiLinkAncUiSync(depth) {
                assertEquals(1, depth.get())
                "rendered"
            },
        )
        assertEquals(0, depth.get())

        runCatching {
            withMiLinkAncUiSync(depth) {
                assertEquals(1, depth.get())
                error("render failed")
            }
        }
        assertEquals(0, depth.get())
    }

    @Test
    fun `visible MiLink detail polls ANC only for routes with verified readback`() {
        assertTrue(
            shouldPollVisibleMiLinkAnc(
                route = HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
                visibleDetailCount = 1,
            ),
        )
        assertFalse(
            shouldPollVisibleMiLinkAnc(
                route = HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
                visibleDetailCount = 0,
            ),
        )
        assertFalse(
            shouldPollVisibleMiLinkAnc(
                route = HuaweiDeviceRoute.HUAWEI_FREECLIP2,
                visibleDetailCount = 1,
            ),
        )
        assertFalse(
            shouldPollVisibleMiLinkAnc(
                route = HuaweiDeviceRoute.HUAWEI_FREEBUDS3,
                visibleDetailCount = 1,
            ),
        )
    }

    @Test
    fun `HyperOS 4 ANC card primes cached selection during its first layout`() {
        val hyperOs4 = miLinkAncHostSpecs.first { it.adapterName == "hyperos4-v18" }
        val legacy = miLinkAncHostSpecs.first { it.adapterName == "legacy" }

        assertTrue(
            shouldPrimeMiLinkAncCard(
                HuaweiDeviceRoute.HUAWEI_FREEBUDS3,
                hyperOs4,
                "constructor",
            ),
        )
        assertTrue(
            shouldPrimeMiLinkAncCard(
                HuaweiDeviceRoute.HUAWEI_FREEBUDS3,
                hyperOs4,
                "constructor-post",
            ),
        )
        assertFalse(
            shouldPrimeMiLinkAncCard(
                HuaweiDeviceRoute.HUAWEI_FREEBUDS3,
                hyperOs4,
                "M",
            ),
        )
        assertFalse(
            shouldPrimeMiLinkAncCard(
                HuaweiDeviceRoute.HUAWEI_FREEBUDS3,
                legacy,
                "constructor",
            ),
        )
        assertFalse(
            shouldPrimeMiLinkAncCard(
                HuaweiDeviceRoute.HUAWEI_FREECLIP2,
                hyperOs4,
                "constructor",
            ),
        )
    }

    @Test
    fun `late HyperOS 4 ANC refresh uses current FreeBuds 3 state as UI only`() {
        val hyperOs4 = miLinkAncHostSpecs.first { it.adapterName == "hyperos4-v18" }

        assertEquals(
            MiLinkAncHostRefreshDecision(hostState = 0, guardAsUiOnly = true),
            miLinkAncHostRefreshDecision(
                cardRoute = HuaweiDeviceRoute.HUAWEI_FREEBUDS3,
                activeRoute = HuaweiDeviceRoute.HUAWEI_FREEBUDS3,
                currentHuaweiStatus = 2,
                hostSpec = hyperOs4,
                incomingHostState = 2,
            ),
        )
        assertEquals(
            MiLinkAncHostRefreshDecision(hostState = 2, guardAsUiOnly = true),
            miLinkAncHostRefreshDecision(
                cardRoute = HuaweiDeviceRoute.HUAWEI_FREEBUDS3,
                activeRoute = HuaweiDeviceRoute.HUAWEI_FREEBUDS3,
                currentHuaweiStatus = 1,
                hostSpec = hyperOs4,
                incomingHostState = 0,
            ),
        )
    }

    @Test
    fun `late HyperOS 4 ANC refresh cannot roll FreeBuds 6i back to an older mode`() {
        val hyperOs4 = miLinkAncHostSpecs.first { it.adapterName == "hyperos4-v18" }

        assertEquals(
            MiLinkAncHostRefreshDecision(hostState = 0, guardAsUiOnly = true),
            miLinkAncHostRefreshDecision(
                cardRoute = HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
                activeRoute = HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
                currentHuaweiStatus = 2,
                hostSpec = hyperOs4,
                incomingHostState = 2,
            ),
        )
        assertEquals(
            MiLinkAncHostRefreshDecision(hostState = 1, guardAsUiOnly = true),
            miLinkAncHostRefreshDecision(
                cardRoute = HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
                activeRoute = HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
                currentHuaweiStatus = 3,
                hostSpec = hyperOs4,
                incomingHostState = 2,
            ),
        )
        assertEquals(
            MiLinkAncHostRefreshDecision(hostState = 2, guardAsUiOnly = true),
            miLinkAncHostRefreshDecision(
                cardRoute = HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
                activeRoute = HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
                currentHuaweiStatus = 1,
                hostSpec = hyperOs4,
                incomingHostState = 1,
            ),
        )
    }

    @Test
    fun `ANC host refresh never rewrites another route or a no-ANC card`() {
        val hyperOs4 = miLinkAncHostSpecs.first { it.adapterName == "hyperos4-v18" }

        assertEquals(
            MiLinkAncHostRefreshDecision(hostState = 2, guardAsUiOnly = false),
            miLinkAncHostRefreshDecision(
                cardRoute = HuaweiDeviceRoute.HUAWEI_FREEBUDS3,
                activeRoute = HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
                currentHuaweiStatus = 2,
                hostSpec = hyperOs4,
                incomingHostState = 2,
            ),
        )
        assertEquals(
            MiLinkAncHostRefreshDecision(hostState = 1, guardAsUiOnly = false),
            miLinkAncHostRefreshDecision(
                cardRoute = HuaweiDeviceRoute.HUAWEI_FREECLIP2,
                activeRoute = HuaweiDeviceRoute.HUAWEI_FREECLIP2,
                currentHuaweiStatus = 2,
                hostSpec = hyperOs4,
                incomingHostState = 1,
            ),
        )
    }

    @Test
    fun `late HyperOS 4 ANC refresh is UI only for every active ANC route`() {
        val hyperOs4 = miLinkAncHostSpecs.first { it.adapterName == "hyperos4-v18" }

        assertEquals(
            MiLinkAncHostRefreshDecision(hostState = 0, guardAsUiOnly = true),
            miLinkAncHostRefreshDecision(
                cardRoute = HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO5,
                activeRoute = HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO5,
                currentHuaweiStatus = 2,
                hostSpec = hyperOs4,
                incomingHostState = 1,
            ),
        )
    }

    @Test
    fun `only Eyewear routes use the MiLink audio glasses presentation`() {
        val glassesRoutes = setOf(
            HuaweiDeviceRoute.HUAWEI_EYEWEAR,
            HuaweiDeviceRoute.HUAWEI_EYEWEAR2,
        )
        HuaweiDeviceRoute.entries.forEach { route ->
            assertEquals(route in glassesRoutes, shouldPresentAsMiLinkAudioGlasses(route))
        }
    }

    @Test
    fun `MiLink ANC host hooks every compatible implementation`() {
        assertEquals(
            listOf("legacy"),
            compatibleMiLinkAncHostSpecs { className -> className.endsWith(".j") }
                .map(MiLinkAncHostSpec::adapterName),
        )
        assertEquals(
            listOf("hyperos4-v18"),
            compatibleMiLinkAncHostSpecs { className -> className.endsWith(".r") }
                .map(MiLinkAncHostSpec::adapterName),
        )
        assertEquals(
            listOf("legacy", "hyperos4-v18"),
            compatibleMiLinkAncHostSpecs { true }.map(MiLinkAncHostSpec::adapterName),
        )
        assertEquals(
            "anc_card_text",
            miLinkAncHostSpecs.first { it.adapterName == "hyperos4-v18" }.titleIdNames.first(),
        )
        assertEquals(
            "W",
            miLinkAncHostSpecs.first { it.adapterName == "hyperos4-v18" }.heightMethodName,
        )
        assertFalse(
            miLinkAncHostSpecs.first { it.adapterName == "hyperos4-v18" }
                .recomputeHeightWhenHidden,
        )
        assertEquals(
            setOf("M"),
            miLinkAncHostSpecs.first { it.adapterName == "hyperos4-v18" }
                .refreshMethodNames,
        )
        assertEquals(
            MiLinkAncValueOrder.NOISE_TRANSPARENCY_OFF,
            miLinkAncHostSpecs.first { it.adapterName == "hyperos4-v18" }.displayValueOrder,
        )
        assertEquals(
            MiLinkAncValueOrder.OFF_NOISE_TRANSPARENCY,
            miLinkAncHostSpecs.first { it.adapterName == "legacy" }.displayValueOrder,
        )
        assertNull(
            miLinkAncHostSpecs.first { it.adapterName == "legacy" }
                .refreshMethodNames,
        )
        assertTrue(
            miLinkAncHostSpecs.first { it.adapterName == "legacy" }
                .recomputeHeightWhenHidden,
        )
        assertTrue(compatibleMiLinkAncHostSpecs { false }.isEmpty())
    }

    @Test
    fun `MiLink audio effect host supports legacy and HyperOS 4 cards`() {
        assertEquals(
            "o",
            selectMiLinkAudioEffectHostSpec { className -> className.endsWith(".w0") }
                ?.renderMethodName,
        )
        assertEquals(
            "w",
            selectMiLinkAudioEffectHostSpec { className -> className.endsWith(".h1") }
                ?.renderMethodName,
        )
        val hyperOs4 = miLinkAudioEffectHostSpecs.first { it.adapterName == "hyperos4-v18" }
        assertEquals("mi_audio_effect_card_text", hyperOs4.titleIdName)
        assertEquals("mi_audio_effect_select_card", hyperOs4.selectCardIdName)
        assertEquals(MiLinkSpatialAudioValueOrder.FIXED_FIRST, hyperOs4.valueOrder)
        assertEquals(
            MiLinkSpatialAudioValueOrder.HEAD_TRACKING_FIRST,
            miLinkAudioEffectHostSpecs.first { it.adapterName == "legacy" }.valueOrder,
        )
        assertNull(selectMiLinkAudioEffectHostSpec { false })
    }

    @Test
    fun `HyperOS 4 FreeClip2 sound effect replaces the reserved ANC card slot`() {
        val hyperOs4 = miLinkAudioEffectHostSpecs.first { it.adapterName == "hyperos4-v18" }
        assertEquals("anc_select_card", hyperOs4.soundEffectSlotIdName)
        assertNull(
            miLinkAudioEffectHostSpecs.first { it.adapterName == "legacy" }
                .soundEffectSlotIdName,
        )
    }

    @Test
    fun `replacing the HyperOS 4 ANC slot preserves its measured height`() {
        assertEquals(216, miLinkSoundEffectCardHeight(216, replacesHostSlot = true))
        assertEquals(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            miLinkSoundEffectCardHeight(216, replacesHostSlot = false),
        )
    }

    @Test
    fun `FreeClip2 spatial effect accepts native and offset MiLink states`() {
        assertEquals(
            FreeClip2SpatialAudioMode.OFF,
            freeClip2SpatialModeForMiLinkAudioEffect(0),
        )
        assertEquals(
            FreeClip2SpatialAudioMode.FIXED,
            freeClip2SpatialModeForMiLinkAudioEffect(21),
        )
        assertEquals(
            FreeClip2SpatialAudioMode.HEAD_TRACKING,
            freeClip2SpatialModeForMiLinkAudioEffect(32),
        )
        assertNull(freeClip2SpatialModeForMiLinkAudioEffect(3))
    }

    @Test
    fun `FreeClip2 spatial effect round trips MiLink order without swapping fixed and tracking`() {
        FreeClip2SpatialAudioMode.entries.forEach { mode ->
            val hostValue = miLinkAudioEffectForFreeClip2SpatialMode(mode)
            assertEquals(mode, freeClip2SpatialModeForMiLinkAudioEffect(hostValue))
        }
        assertEquals(1, miLinkAudioEffectForFreeClip2SpatialMode(FreeClip2SpatialAudioMode.FIXED))
        assertEquals(2, miLinkAudioEffectForFreeClip2SpatialMode(FreeClip2SpatialAudioMode.HEAD_TRACKING))
    }

    @Test
    fun `legacy FreeClip2 spatial effect keeps native head tracking first order`() {
        val hostSpec = miLinkAudioEffectHostSpecs.first { it.adapterName == "legacy" }
        assertEquals(
            FreeClip2SpatialAudioMode.HEAD_TRACKING,
            freeClip2SpatialModeForMiLinkAudioEffect(1, hostSpec),
        )
        assertEquals(
            FreeClip2SpatialAudioMode.FIXED,
            freeClip2SpatialModeForMiLinkAudioEffect(2, hostSpec),
        )
        FreeClip2SpatialAudioMode.entries.forEach { mode ->
            val hostValue = miLinkAudioEffectForFreeClip2SpatialMode(mode, hostSpec)
            assertEquals(mode, freeClip2SpatialModeForMiLinkAudioEffect(hostValue, hostSpec))
        }
    }

    @Test
    fun `controller API follows the selected host value order`() {
        val legacy = miLinkAudioEffectHostSpecs.first { it.adapterName == "legacy" }
        val hyperOs4 = miLinkAudioEffectHostSpecs.first { it.adapterName == "hyperos4-v18" }

        assertEquals(
            FreeClip2SpatialAudioMode.HEAD_TRACKING,
            freeClip2SpatialModeForMiLinkAudioEffect(1, legacy),
        )
        assertEquals(
            FreeClip2SpatialAudioMode.FIXED,
            freeClip2SpatialModeForMiLinkAudioEffect(1, hyperOs4),
        )
        assertEquals(
            1,
            miLinkAudioEffectForFreeClip2SpatialMode(
                FreeClip2SpatialAudioMode.HEAD_TRACKING,
                legacy,
            ),
        )
        assertEquals(
            2,
            miLinkAudioEffectForFreeClip2SpatialMode(
                FreeClip2SpatialAudioMode.HEAD_TRACKING,
                hyperOs4,
            ),
        )
    }

    @Test
    fun `only legacy FreeClip2 reserves hidden ANC height for sound effects`() {
        val legacy = miLinkAncHostSpecs.first { it.adapterName == "legacy" }
        val hyperOs4 = miLinkAncHostSpecs.first { it.adapterName == "hyperos4-v18" }
        assertTrue(shouldReserveLegacyMiLinkAncHeight(HuaweiDeviceRoute.HUAWEI_FREECLIP2, legacy))
        assertFalse(shouldReserveLegacyMiLinkAncHeight(HuaweiDeviceRoute.HUAWEI_FREECLIP2, hyperOs4))
        assertFalse(shouldReserveLegacyMiLinkAncHeight(HuaweiDeviceRoute.HUAWEI_FREEARC, legacy))
    }

    @Test
    fun `open-ear and eyewear routes never expose ANC`() {
        listOf(
            HuaweiDeviceRoute.HUAWEI_FREECLIP,
            HuaweiDeviceRoute.HUAWEI_FREECLIP2,
            HuaweiDeviceRoute.HUAWEI_FREEARC,
            HuaweiDeviceRoute.HUAWEI_EYEWEAR,
            HuaweiDeviceRoute.HUAWEI_EYEWEAR2,
        ).forEach { route ->
            assertNull(miLinkAncModeFor(route, 2))
            assertEquals(0, miLinkHostAncStateFor(route, 2))
            assertNull(huaweiAncStatusForMiLink(route, 0))
            assertNull(huaweiAncStatusForMiLink(route, 1))
            assertNull(huaweiAncStatusForMiLink(route, 2))
        }
    }

    @Test
    fun `three-state routes preserve off ANC and transparency`() {
        listOf(
            HuaweiDeviceRoute.HUAWEI_FREEBUDS5I,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO5,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS7I,
        ).forEach { route ->
            assertEquals(0, miLinkAncModeFor(route, 1))
            assertEquals(1, miLinkAncModeFor(route, 2))
            assertEquals(2, miLinkAncModeFor(route, 3))
            assertEquals(1, huaweiAncStatusForMiLink(route, 0))
            assertEquals(2, huaweiAncStatusForMiLink(route, 1))
            assertEquals(3, huaweiAncStatusForMiLink(route, 2))
        }
    }

    @Test
    fun `HyperOS 4 v18 keeps display and runtime ANC value domains separate`() {
        val hostSpec = miLinkAncHostSpecs.first { it.adapterName == "hyperos4-v18" }
        val route = HuaweiDeviceRoute.HUAWEI_FREEBUDS6I

        // HeadsetInfo/M(int) display domain: 0=ANC, 1=transparency, 2=off.
        assertEquals(0, miLinkAncModeFor(route, 2, hostSpec))
        assertEquals(1, miLinkAncModeFor(route, 3, hostSpec))
        assertEquals(2, miLinkAncModeFor(route, 1, hostSpec))
        assertEquals(2, huaweiAncStatusForMiLink(route, 0, hostSpec))
        assertEquals(3, huaweiAncStatusForMiLink(route, 1, hostSpec))
        assertEquals(1, huaweiAncStatusForMiLink(route, 2, hostSpec))

        // Runtime command domain: 0=off, 1=ANC, 2=transparency.
        assertEquals(1, huaweiAncStatusForMiLink(route, 0))
        assertEquals(2, huaweiAncStatusForMiLink(route, 1))
        assertEquals(3, huaweiAncStatusForMiLink(route, 2))
    }

    @Test
    fun `HyperOS 4 two-state card renders cached FreeBuds 3 ANC and off selections`() {
        val hostSpec = miLinkAncHostSpecs.first { it.adapterName == "hyperos4-v18" }
        val route = HuaweiDeviceRoute.HUAWEI_FREEBUDS3

        assertEquals(0, miLinkHostAncStateFor(route, 2, hostSpec))
        assertEquals(2, miLinkHostAncStateFor(route, 1, hostSpec))
        assertEquals(2, huaweiAncStatusForMiLink(route, 0, hostSpec))
        assertEquals(1, huaweiAncStatusForMiLink(route, 2, hostSpec))
        assertNull(huaweiAncStatusForMiLink(route, 1, hostSpec))
    }

    @Test
    fun `two-state ANC routes reject transparency`() {
        listOf(
            HuaweiDeviceRoute.HUAWEI_FREEBUDS3,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS5,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO4,
        ).forEach { route ->
            assertEquals(0, miLinkAncModeFor(route, 1))
            assertEquals(1, miLinkAncModeFor(route, 2))
            assertNull(huaweiAncStatusForMiLink(route, 2))
        }
    }

    @Test
    fun `only two-state ANC routes detach the native transparency button`() {
        listOf(
            HuaweiDeviceRoute.HUAWEI_FREEBUDS3,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS5,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO4,
        ).forEach { route -> assertTrue(shouldDetachMiLinkTransparency(route)) }

        listOf(
            HuaweiDeviceRoute.HUAWEI_FREEBUDS5I,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO5,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS7I,
            HuaweiDeviceRoute.HUAWEI_FREECLIP2,
            HuaweiDeviceRoute.HUAWEI_FREEARC,
            HuaweiDeviceRoute.HUAWEI_EYEWEAR2,
        ).forEach { route -> assertFalse(shouldDetachMiLinkTransparency(route)) }
    }

    @Test
    fun `detached transparency view is not removed again after its parent is gone`() {
        assertTrue(
            shouldRemoveMiLinkCapabilityView(
                detachWhenHidden = true,
                parentAvailable = true,
                stillInParent = true,
            ),
        )
        assertFalse(
            shouldRemoveMiLinkCapabilityView(
                detachWhenHidden = true,
                parentAvailable = false,
                stillInParent = true,
            ),
        )
        assertFalse(
            shouldRemoveMiLinkCapabilityView(
                detachWhenHidden = true,
                parentAvailable = false,
                stillInParent = false,
            ),
        )
    }

    @Test
    fun `headset image guard only restores a tracked matching cache entry`() {
        assertTrue(
            shouldReapplyMiLinkHeadsetIcon(
                requestedKey = "device|image",
                cachedKey = "device|image",
                alreadyApplied = false,
            ),
        )
        assertFalse(shouldReapplyMiLinkHeadsetIcon(null, "device|image", false))
        assertFalse(shouldReapplyMiLinkHeadsetIcon("other", "device|image", false))
        assertFalse(shouldReapplyMiLinkHeadsetIcon("device|image", "device|image", true))
    }

    @Test
    fun `addressless ANC card fallback requires one live card and active ANC route`() {
        assertTrue(
            shouldUseActiveMiLinkAncCardFallback(
                activeRoute = HuaweiDeviceRoute.HUAWEI_FREEBUDS3,
                activeAddress = "AA:BB:CC:DD:EE:FF",
                sessionConfirmed = true,
                candidateAddressCount = 0,
                liveAncCardCount = 1,
            ),
        )
        assertFalse(
            shouldUseActiveMiLinkAncCardFallback(
                activeRoute = HuaweiDeviceRoute.HUAWEI_FREEBUDS3,
                activeAddress = "AA:BB:CC:DD:EE:FF",
                sessionConfirmed = true,
                candidateAddressCount = 0,
                liveAncCardCount = 2,
            ),
        )
        assertFalse(
            shouldUseActiveMiLinkAncCardFallback(
                activeRoute = HuaweiDeviceRoute.HUAWEI_FREECLIP2,
                activeAddress = "AA:BB:CC:DD:EE:FF",
                sessionConfirmed = true,
                candidateAddressCount = 0,
                liveAncCardCount = 1,
            ),
        )
        assertFalse(
            shouldUseActiveMiLinkAncCardFallback(
                activeRoute = HuaweiDeviceRoute.HUAWEI_FREEBUDS3,
                activeAddress = "AA:BB:CC:DD:EE:FF",
                sessionConfirmed = false,
                candidateAddressCount = 0,
                liveAncCardCount = 1,
            ),
        )
    }

    @Test
    fun `legacy MiLink ANC labels map every host state`() {
        assertEquals(setOf("关闭", "off"), miLinkAncModeLabels(0))
        assertEquals(setOf("降噪", "noise cancellation"), miLinkAncModeLabels(1))
        assertTrue("通透" in miLinkAncModeLabels(2))
        assertTrue("环境声" in miLinkAncModeLabels(2))
        assertTrue(miLinkAncModeLabels(3).isEmpty())
    }

    @Test
    fun `headset icon preflight requires one confirmed active detail`() {
        assertTrue(
            shouldUseActiveMiLinkIconFallback(
                activeRoute = HuaweiDeviceRoute.HUAWEI_FREEBUDS3,
                activeAddress = "AA:BB:CC:DD:EE:FF",
                sessionConfirmed = true,
                liveHeadsetDetailCount = 1,
            ),
        )
        assertFalse(
            shouldUseActiveMiLinkIconFallback(
                activeRoute = HuaweiDeviceRoute.HUAWEI_FREEBUDS3,
                activeAddress = "AA:BB:CC:DD:EE:FF",
                sessionConfirmed = true,
                liveHeadsetDetailCount = 2,
            ),
        )
        assertFalse(
            shouldUseActiveMiLinkIconFallback(
                activeRoute = HuaweiDeviceRoute.HUAWEI_FREEBUDS3,
                activeAddress = null,
                sessionConfirmed = true,
                liveHeadsetDetailCount = 1,
            ),
        )
        assertFalse(
            shouldUseActiveMiLinkIconFallback(
                activeRoute = HuaweiDeviceRoute.HUAWEI_FREEBUDS3,
                activeAddress = "AA:BB:CC:DD:EE:FF",
                sessionConfirmed = false,
                liveHeadsetDetailCount = 1,
            ),
        )
    }

    @Test
    fun `headset icon refresh keeps a strictly identified route before the next frame`() {
        assertEquals(
            HuaweiDeviceRoute.HUAWEI_FREEBUDS3,
            immediateMiLinkHeadsetIconRoute(
                strictRoute = HuaweiDeviceRoute.HUAWEI_FREEBUDS3,
                activeRoute = HuaweiDeviceRoute.UNSUPPORTED,
                activeAddress = null,
                sessionConfirmed = false,
                liveHeadsetDetailCount = 2,
            ),
        )
        assertEquals(
            HuaweiDeviceRoute.HUAWEI_FREECLIP2,
            immediateMiLinkHeadsetIconRoute(
                strictRoute = HuaweiDeviceRoute.UNSUPPORTED,
                activeRoute = HuaweiDeviceRoute.HUAWEI_FREECLIP2,
                activeAddress = "AA:BB:CC:DD:EE:FF",
                sessionConfirmed = true,
                liveHeadsetDetailCount = 1,
            ),
        )
        assertEquals(
            HuaweiDeviceRoute.UNSUPPORTED,
            immediateMiLinkHeadsetIconRoute(
                strictRoute = HuaweiDeviceRoute.UNSUPPORTED,
                activeRoute = HuaweiDeviceRoute.HUAWEI_FREEBUDS3,
                activeAddress = "AA:BB:CC:DD:EE:FF",
                sessionConfirmed = false,
                liveHeadsetDetailCount = 1,
            ),
        )
    }

    @Test
    fun `posted headset icon refresh keeps the confirmed route while host identity is rebinding`() {
        assertEquals(
            HuaweiDeviceRoute.HUAWEI_FREEBUDS3,
            refreshedMiLinkHeadsetIconRoute(
                strictRoute = HuaweiDeviceRoute.UNSUPPORTED,
                labelRoute = HuaweiDeviceRoute.UNSUPPORTED,
                activeRoute = HuaweiDeviceRoute.HUAWEI_FREEBUDS3,
                activeAddress = "AA:BB:CC:DD:EE:FF",
                sessionConfirmed = true,
                liveHeadsetDetailCount = 1,
            ),
        )
        assertEquals(
            HuaweiDeviceRoute.UNSUPPORTED,
            refreshedMiLinkHeadsetIconRoute(
                strictRoute = HuaweiDeviceRoute.UNSUPPORTED,
                labelRoute = HuaweiDeviceRoute.UNSUPPORTED,
                activeRoute = HuaweiDeviceRoute.HUAWEI_FREEBUDS3,
                activeAddress = "AA:BB:CC:DD:EE:FF",
                sessionConfirmed = true,
                liveHeadsetDetailCount = 2,
            ),
        )
    }

    @Test
    fun `title fallback only restores presentation for a unique no ANC model`() {
        assertEquals(
            HuaweiDeviceRoute.HUAWEI_FREECLIP2,
            noAncMiLinkPresentationRoute(listOf("HUAWEI FreeClip 2", "已连接")),
        )
        assertEquals(
            HuaweiDeviceRoute.HUAWEI_EYEWEAR2,
            noAncMiLinkPresentationRoute(listOf("HUAWEI Eyewear 2")),
        )
        assertEquals(
            HuaweiDeviceRoute.HUAWEI_FREEARC,
            noAncMiLinkPresentationRoute(listOf("HUAWEI FreeArc", "已连接")),
        )
        assertEquals(
            HuaweiDeviceRoute.UNSUPPORTED,
            noAncMiLinkPresentationRoute(listOf("HUAWEI FreeBuds Pro 5")),
        )
        assertEquals(
            HuaweiDeviceRoute.UNSUPPORTED,
            noAncMiLinkPresentationRoute(listOf("HUAWEI FreeClip 2", "HUAWEI Eyewear 2")),
        )
        assertEquals(
            HuaweiDeviceRoute.UNSUPPORTED,
            noAncMiLinkPresentationRoute(listOf("Bluetooth headset")),
        )
    }

    @Test
    fun `three-state models use their captured submode defaults`() {
        assertEquals(
            0x03,
            normalizeMiLinkAncSubMode(
                HuaweiDeviceRoute.HUAWEI_FREEBUDS5I,
                huaweiStatus = 2,
                requestedSubMode = null,
                storedSubMode = null,
            ),
        )
        assertEquals(
            0x03,
            normalizeMiLinkAncSubMode(
                HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
                huaweiStatus = 2,
                requestedSubMode = null,
                storedSubMode = null,
            ),
        )
        assertEquals(
            0x02,
            normalizeMiLinkAncSubMode(
                HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
                huaweiStatus = 3,
                requestedSubMode = null,
                storedSubMode = null,
            ),
        )
        assertEquals(
            0xFF,
            normalizeMiLinkAncSubMode(
                HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3,
                huaweiStatus = 3,
                requestedSubMode = null,
                storedSubMode = null,
            ),
        )
        assertEquals(
            0x01,
            normalizeMiLinkAncSubMode(
                HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO5,
                huaweiStatus = 3,
                requestedSubMode = 0x01,
                storedSubMode = null,
            ),
        )
        assertEquals(
            0x02,
            normalizeMiLinkAncSubMode(
                HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO5,
                huaweiStatus = 3,
                requestedSubMode = null,
                storedSubMode = null,
            ),
        )
        assertEquals(
            0x02,
            normalizeMiLinkAncSubMode(
                HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO5,
                huaweiStatus = 3,
                requestedSubMode = 0xFF,
                storedSubMode = null,
            ),
        )
    }

    @Test
    fun `MiLink keeps an optimistic ANC mode until the matching device state arrives`() {
        val gate = MiLinkAncPendingGate(timeoutMs = 5_000L)
        val off = MiLinkAncSelection(status = 1)
        val transparency = MiLinkAncSelection(status = 3, subMode = 0x02)

        assertTrue(gate.tryBegin(transparency, nowMs = 100L))
        assertFalse(gate.shouldAcceptConfirmation(off, nowMs = 300L))
        assertEquals(transparency, gate.current())
        assertTrue(gate.shouldAcceptConfirmation(transparency, nowMs = 500L))
        assertNull(gate.current())
    }

    @Test
    fun `MiLink ANC pending state expires and yields to the verified readback`() {
        val gate = MiLinkAncPendingGate(timeoutMs = 5_000L)
        gate.tryBegin(MiLinkAncSelection(status = 2, subMode = 0x03), nowMs = 100L)

        assertTrue(
            gate.shouldAcceptConfirmation(
                MiLinkAncSelection(status = 1),
                nowMs = 5_100L,
            ),
        )
        assertNull(gate.current())
    }

    @Test
    fun `FreeBuds 5 MiLink routing uses three captured levels and rejects deep`() {
        val route = HuaweiDeviceRoute.HUAWEI_FREEBUDS5

        assertEquals(
            0x03,
            normalizeMiLinkAncSubMode(
                route,
                huaweiStatus = 2,
                requestedSubMode = null,
                storedSubMode = null,
            ),
        )
        listOf(0x03, 0x01, 0x00).forEach { level ->
            assertEquals(
                level,
                normalizeMiLinkAncSubMode(
                    route,
                    huaweiStatus = 2,
                    requestedSubMode = level,
                    storedSubMode = null,
                ),
            )
        }
        assertNull(
            normalizeMiLinkAncSubMode(
                route,
                huaweiStatus = 2,
                requestedSubMode = 0x02,
                storedSubMode = null,
            ),
        )
        assertNull(
            normalizeMiLinkAncSubMode(
                route,
                huaweiStatus = 3,
                requestedSubMode = 0xFF,
                storedSubMode = null,
            ),
        )
    }

    @Test
    fun `dynamic MiLink state requires the same address and route`() {
        val address = "AA:BB:CC:DD:EE:01"
        val route = HuaweiDeviceRoute.HUAWEI_FREEBUDS6I

        assertTrue(matchesMiLinkStateOwner(address, route, address.lowercase(), route))
        assertFalse(matchesMiLinkStateOwner(address, route, "AA:BB:CC:DD:EE:02", route))
        assertFalse(
            matchesMiLinkStateOwner(
                address,
                route,
                address,
                HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3,
            ),
        )
        assertFalse(matchesMiLinkStateOwner(null, route, address, route))
        assertFalse(matchesMiLinkStateOwner(address, route, null, route))
    }
}
