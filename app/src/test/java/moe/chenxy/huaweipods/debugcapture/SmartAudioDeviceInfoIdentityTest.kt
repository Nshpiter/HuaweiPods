package moe.chenxy.huaweipods.debugcapture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SmartAudioDeviceInfoIdentityTest {
    @Test
    fun `extracts FreeBuds 5 identity from DeviceInfo TLV`() {
        val identity = SmartAudioDeviceInfoIdentity.parse(
            "5A0076000107020201410309484C31524845494E4D07204861726D6F6E794F5320352E302E302E32303828463030314830303343303129091058564C424232333431323130303439370A0F42544654303031332D3030303134310F0842544654303031331810424231393130323333594130303339361901011B8F",
        )
        assertEquals("000141", identity?.modelId)
        assertEquals("01", identity?.subModelId)
    }

    @Test
    fun `extracts Eyewear 2 identity with BTFLFTG prefix`() {
        val identity = SmartAudioDeviceInfoIdentity.parse(
            "5A007A0001070202014F030B484C314F54454D325F564107204861726D6F6E794F5320342E322E302E313338284630303148303033433030290910325243545132363132353030303139320A104254464C46544730302D3030303134460F094254464C4654473030181054513235353632363136413030303739190100CE46",
        )
        assertEquals("00014F", identity?.modelId)
        assertEquals("00", identity?.subModelId)
    }

    @Test
    fun `extracts FreeBuds 6i submodel 02 from DeviceInfo TLV`() {
        val identity = SmartAudioDeviceInfoIdentity.parse(
            "5A0092000107020201530308484C324F52434D5207204861726D6F6E794F5320362E302E302E323932284630303148303033433030290910324E47545132353131373030383734320A0F42544654303031392D3030303135330F08425446543030313918254C2D545134383730323531373030373430352C522D545134383733323531453031393636361901021B06018D51D8C104637C",
        )
        assertEquals("000153", identity?.modelId)
        assertEquals("02", identity?.subModelId)
    }

    @Test
    fun `extracts FreeBuds 5i submodel 07 from DeviceInfo TLV`() {
        val identity = SmartAudioDeviceInfoIdentity.parse(
            "5A008D00010702020145030B484C314F54454D325F564107204861726D6F6E794F5320322E312E302E323138284630303148303033433030290910355055545132333130353030303239390A0F42544654303031342D3030303134350F08425446543030313418254C2D5451393038343232434C3032333535312C522D545139303838323334553030363135301901077DE0",
        )
        assertEquals("000145", identity?.modelId)
        assertEquals("07", identity?.subModelId)
    }

    @Test
    fun `does not infer identity without both strict TLVs`() {
        assertNull(SmartAudioDeviceInfoIdentity.parse("0A0F42544654303031332D303030313431"))
        assertNull(SmartAudioDeviceInfoIdentity.parse("190101"))
        assertNull(SmartAudioDeviceInfoIdentity.parse("not hex"))
    }

    @Test
    fun `rejects truncated or concatenated DeviceInfo frames`() {
        val frame = "5A0076000107020201410309484C31524845494E4D07204861726D6F6E794F5320352E302E302E32303828463030314830303343303129091058564C424232333431323130303439370A0F42544654303031332D3030303134310F0842544654303031331810424231393130323333594130303339361901011B8F"
        assertNull(SmartAudioDeviceInfoIdentity.parse(frame.dropLast(2)))
        assertNull(SmartAudioDeviceInfoIdentity.parse(frame + frame))
    }

    @Test
    fun `does not scan fake submodel bytes inside another TLV`() {
        val model = "BTFT0013-000141".toByteArray(Charsets.US_ASCII)
        val prefix = "BTFT0013".toByteArray(Charsets.US_ASCII)
        val tlvs = byteArrayOf(0x02, 0x02, 0x01, 0x41) +
            byteArrayOf(0x0A, model.size.toByte()) + model +
            byteArrayOf(0x0F, prefix.size.toByte()) + prefix +
            byteArrayOf(0x30, 0x03, 0x19, 0x01, 0x02)
        assertNull(SmartAudioDeviceInfoIdentity.parse(deviceInfoFrame(tlvs)))
    }

    @Test
    fun `rejects same TLVs on a non DeviceInfo command`() {
        val model = "BTFT0013-000141".toByteArray(Charsets.US_ASCII)
        val prefix = "BTFT0013".toByteArray(Charsets.US_ASCII)
        val tlvs = byteArrayOf(0x02, 0x02, 0x01, 0x41) +
            byteArrayOf(0x0A, model.size.toByte()) + model +
            byteArrayOf(0x0F, prefix.size.toByte()) + prefix +
            byteArrayOf(0x19, 0x01, 0x01)
        assertNull(SmartAudioDeviceInfoIdentity.parse(deviceInfoFrame(tlvs, command = 0x08)))
    }

    @Test
    fun `rejects mismatched model code duplicate submodel and shared resources`() {
        val valid = minimalTlvs("BTFT0013", "000141", 0x01)
        assertNull(
            SmartAudioDeviceInfoIdentity.parse(
                deviceInfoFrame(valid.copyOf().apply { this[2] = 0x42 }),
            ),
        )
        assertNull(
            SmartAudioDeviceInfoIdentity.parse(
                deviceInfoFrame(valid + byteArrayOf(0x19, 0x01, 0x02)),
            ),
        )
        assertNull(
            SmartAudioDeviceInfoIdentity.parse(
                deviceInfoFrame(minimalTlvs("BTFT0000", "00000A", 0x00)),
            ),
        )
    }

    private fun minimalTlvs(prefix: String, modelId: String, subModelId: Int): ByteArray {
        val prefixBytes = prefix.toByteArray(Charsets.US_ASCII)
        val modelBytes = "$prefix-$modelId".toByteArray(Charsets.US_ASCII)
        val modelCode = modelId.takeLast(4).chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        return byteArrayOf(0x02, 0x02) + modelCode +
            byteArrayOf(0x0A, modelBytes.size.toByte()) + modelBytes +
            byteArrayOf(0x0F, prefixBytes.size.toByte()) + prefixBytes +
            byteArrayOf(0x19, 0x01, subModelId.toByte())
    }

    private fun deviceInfoFrame(tlvs: ByteArray, command: Int = 0x07): String {
        val declaredPayloadBytes = 3 + tlvs.size
        val withoutCrc = byteArrayOf(
            0x5A,
            0x00,
            declaredPayloadBytes.toByte(),
            (declaredPayloadBytes shr 8).toByte(),
            0x01,
            command.toByte(),
        ) + tlvs
        val crc = crc16Xmodem(withoutCrc)
        return (withoutCrc + byteArrayOf((crc shr 8).toByte(), crc.toByte()))
            .joinToString("") { "%02X".format(it.toInt() and 0xFF) }
    }

    private fun crc16Xmodem(bytes: ByteArray): Int {
        var crc = 0
        bytes.forEach { byte ->
            crc = crc xor ((byte.toInt() and 0xFF) shl 8)
            repeat(8) {
                crc = if ((crc and 0x8000) != 0) {
                    (crc shl 1) xor 0x1021
                } else {
                    crc shl 1
                }
                crc = crc and 0xFFFF
            }
        }
        return crc
    }
}
