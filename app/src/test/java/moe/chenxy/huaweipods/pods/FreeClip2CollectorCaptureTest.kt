package moe.chenxy.huaweipods.pods

import moe.chenxy.huaweipods.smartaudio.OfficialSmartAudioResource
import moe.chenxy.huaweipods.smartaudio.SmartAudioResourceIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 2026-10-11 典藏版采集回归；DeviceInfo 仅保留产品身份 TLV，移除序列号等字段。 */
class FreeClip2CollectorCaptureTest {
    @Test
    fun `official nod actions match the real collector writes and reports`() {
        assertArrayEquals(hex("5A0009002BB401010B030101C787"), HuaweiFreeClip2Controller.headMotionActionPacket(
            FreeClip2HeadMotionGesture.NOD, FreeClip2HeadMotionAction.ANSWER_CALL,
        ))
        assertArrayEquals(hex("5A0009002BB401010B030102F7E4"), HuaweiFreeClip2Controller.headMotionActionPacket(
            FreeClip2HeadMotionGesture.NOD, FreeClip2HeadMotionAction.REJECT_CALL,
        ))
        assertArrayEquals(hex("5A0006002BB401010B289B"), HuaweiFreeClip2Controller.headMotionQueryPacket())
        val answer = hex("5A000F002BB401010B020101030101040102C55B")
        val reject = hex("5A000F002BB401010B0201010301020401025E87")
        assertEquals(FreeClip2HeadMotionState(true, FreeClip2HeadMotionAction.ANSWER_CALL, FreeClip2HeadMotionAction.REJECT_CALL),
            HuaweiFreeClip2Controller.parseHeadMotionState(answer))
        assertEquals(FreeClip2HeadMotionState(true, FreeClip2HeadMotionAction.REJECT_CALL, FreeClip2HeadMotionAction.REJECT_CALL),
            HuaweiFreeClip2Controller.parseHeadMotionState(answer + reject))
        assertNull(HuaweiFreeClip2Controller.parseHeadMotionState(reject.copyOf(reject.size - 1)))
        val corrupt = reject.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }
        assertNull(HuaweiFreeClip2Controller.parseHeadMotionState(corrupt))
        assertEquals(HuaweiFreeClip2Controller.parseHeadMotionState(reject),
            HuaweiFreeClip2Controller.parseHeadMotionState(corrupt + reject))
    }

    @Test
    fun `noisy volume reads the correct state command rather than quality or capability reports`() {
        assertArrayEquals(hex("5A0005002B8801009182"), HuaweiFreeClip2Controller.noisyVolumeQueryPacket())
        val off = hex("5A0006002B88010100FA2B")
        val on = hex("5A0006002B88010101EA0A")
        assertEquals(false, HuaweiFreeClip2Controller.parseNoisyVolumeState(off))
        assertEquals(true, HuaweiFreeClip2Controller.parseNoisyVolumeState(off + on))
        assertNull(HuaweiFreeClip2Controller.parseNoisyVolumeState(hex("5A0006002B88020101B35A")))
        assertNull(HuaweiFreeClip2Controller.parseNoisyVolumeState(hex("5A0009002B877F04000186A0F75B")))
        assertNull(HuaweiFreeClip2Controller.parseHeadMotionState(on))
        assertNull(HuaweiFreeClip2Controller.parseNoisyVolumeState(on.copyOf(on.size - 1)))
        assertNull(HuaweiFreeClip2Controller.parseNoisyVolumeState(on.copyOf().apply {
            this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte()
        }))
    }

    @Test
    fun `collector identity keeps its exact resource model and color through route selection`() {
        val identity = HuaweiDeviceInfoIdentityParser.parse(
            hex("5A0025000107020201730A0F42544654303032372D3030303137330F084254465430303237190100F930"),
        )
        assertEquals(HuaweiDeviceInfoIdentity("000173", "00"), identity)
        assertEquals(HuaweiDeviceRoute.HUAWEI_FREECLIP2, HuaweiDeviceInfoRoutePolicy.routeForModelId(identity!!.modelId))
        assertTrue(HuaweiDeviceInfoRoutePolicy.isCompatible(HuaweiDeviceRoute.HUAWEI_FREECLIP2, identity.modelId))

        val config = """
            {"modelId":"000173","defaultSubModelId":"00","resourceMap":{
              "00":{"resourceKey":"00","resourceRemark":"000173_00.zip","resourceFileSizeKb":29497},
              "05":{"resourceKey":"05","resourceRemark":"000173_05.zip","resourceFileSizeKb":31696}
            }}
        """.trimIndent().toByteArray()
        for (color in listOf("00", "05")) {
            val resource = OfficialSmartAudioResource.selectResource(
                config, SmartAudioResourceIdentity("AA:BB:CC:DD:EE:FF", identity.modelId, color),
            )
            assertEquals("000173_${color}.zip", resource.archiveFileName)
            assertEquals(color, resource.subModelId)
        }
    }

    @Test
    fun `collector spatial reports use existing off head tracking and fixed mapping`() {
        val captures = mapOf(
            "5A000C002BB4010118020100030100D72A" to FreeClip2SpatialAudioMode.OFF,
            "5A000C002BB4010118020101030100A19E" to FreeClip2SpatialAudioMode.HEAD_TRACKING,
            "5A000C002BB40101180201020301003A42" to FreeClip2SpatialAudioMode.FIXED,
        )
        for ((frame, mode) in captures) {
            val state = HuaweiFreeClip2Controller.parseSpatialAudioState(hex(frame))
            assertEquals(mode, state?.mode)
            assertEquals(FreeClip2SpatialScene.DEFAULT, state?.scene)
        }
        val corrupt = hex(captures.keys.last()).apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }
        assertNull(HuaweiFreeClip2Controller.parseSpatialAudioState(corrupt))
    }

    private fun hex(value: String): ByteArray = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
