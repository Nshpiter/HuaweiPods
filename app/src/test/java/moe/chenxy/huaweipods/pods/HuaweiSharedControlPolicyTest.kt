package moe.chenxy.huaweipods.pods

import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class HuaweiSharedControlPolicyTest {
    private val route = HuaweiDeviceRoute.HUAWEI_FREEBUDS_SE4_ANC

    @Test
    fun `shared control rejects unknown operations and unsupported routes`() {
        assertNull(HuaweiSharedControlPolicy.query(route, "raw"))
        assertNull(HuaweiSharedControlPolicy.query(HuaweiDeviceRoute.HUAWEI_FREEBUDS3, "eq_read"))
        assertNull(HuaweiSharedControlPolicy.query(HuaweiDeviceRoute.HUAWEI_FREEBUDS6I, "wind_read"))
        assertNull(HuaweiSharedControlPolicy.write(route, "eq_preset", 255, "", emptyList(), false))
        assertNull(HuaweiSharedControlPolicy.write(route, "eq_custom", 100, "name", listOf(0), false))
        assertTrue(HuaweiSharedControlPolicy.validNonce(UUID.randomUUID().toString()))
        assertFalse(HuaweiSharedControlPolicy.validNonce("invalid"))
    }

    @Test
    fun `wind write requires confirmed matching state instead of ACK`() {
        val on = hex("5A0009002BB401011D020101CC89")
        val off = hex("5A0009002BB401011D020100DCA8")
        assertTrue(HuaweiSharedControlPolicy.verifiedResponse("wind_set", on, -1, true))
        assertFalse(HuaweiSharedControlPolicy.verifiedResponse("wind_set", off, -1, true))
        assertFalse(HuaweiSharedControlPolicy.verifiedResponse("wind_set", byteArrayOf(), -1, true))
        assertFalse(HuaweiSharedControlPolicy.verifiedResponse("eq_preset", on, 1, true))
    }

    @Test
    fun `preset confirmation rejects write ACK and mismatched preset`() {
        val customState = hex(
            "5A00A9002B4A01010102016403040102030904010105010A060A28140A000A2319000A14" +
                "0718E585A8E9A291E6A0A1E58786000000000000000000000000086C640A28140A000A" +
                "2319000A14E585A8E9A291E6A0A1E58786000000000000000000000000650A2314EC0A" +
                "0AECEC00323CE6B581E8A18CE9A38EE59091000000000000000000000000660A0A0A0A" +
                "0A00F1F1001414E59D87E8A1A1E4BABAE5A3B0000000000000000000000000CA62",
        )
        assertTrue(HuaweiSharedControlPolicy.verifiedResponse("eq_read", customState, -1, false))
        assertFalse(HuaweiSharedControlPolicy.verifiedResponse("eq_preset", customState, 1, false))
        assertTrue(HuaweiSharedControlPolicy.verifiedResponse(
            "eq_custom", customState, -1, false, "全频校准",
            listOf(40, 20, 10, 0, 10, 35, 25, 0, 10, 20),
        ))
        assertFalse(HuaweiSharedControlPolicy.verifiedResponse(
            "eq_custom", customState, -1, false, "全频校准", List(10) { 0 },
        ))
        assertFalse(HuaweiSharedControlPolicy.verifiedResponse(
            "eq_preset", hex("5A0005002B49000057B1"), 1, false,
        ))
    }

    @Test
    fun `AAM transport excludes audio services while retaining SPP and vendor services`() {
        for (id in listOf("1108", "110b", "110e", "111e", "111f")) {
            assertFalse(HuaweiRfcommServicePolicy.isControlCandidate(
                UUID.fromString("0000$id-0000-1000-8000-00805f9b34fb"),
            ))
        }
        assertTrue(HuaweiRfcommServicePolicy.isControlCandidate(
            UUID.fromString("00001101-0000-1000-8000-00805f9b34fb"),
        ))
        assertTrue(HuaweiRfcommServicePolicy.isControlCandidate(
            UUID.fromString("0000fe01-0000-1000-8000-00805f9b34fb"),
        ))
    }

    private fun hex(value: String) = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
