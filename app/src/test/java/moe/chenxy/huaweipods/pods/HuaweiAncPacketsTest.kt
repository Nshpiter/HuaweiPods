package moe.chenxy.huaweipods.pods

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HuaweiAncPacketsTest {
    @Test
    fun `FreeBuds 3 mode packets remain unchanged`() {
        assertArrayEquals(
            hex("5A0006002B040101006821"),
            HuaweiAncPackets.enabled(HuaweiDeviceRoute.HUAWEI_FREEBUDS3, false),
        )
        assertArrayEquals(
            hex("5A0006002B040101017800"),
            HuaweiAncPackets.enabled(HuaweiDeviceRoute.HUAWEI_FREEBUDS3, true),
        )
    }

    @Test
    fun `FreeBuds 5 mode packets match verified capture`() {
        assertArrayEquals(
            hex("5A0007002B0401020000D22D"),
            HuaweiAncPackets.enabled(HuaweiDeviceRoute.HUAWEI_FREEBUDS5, false),
        )
        assertArrayEquals(
            hex("5A0007002B04010201FFFFEC"),
            HuaweiAncPackets.enabled(HuaweiDeviceRoute.HUAWEI_FREEBUDS5, true),
        )
        assertArrayEquals(
            hex("5A0007002B0401020103D17F"),
            HuaweiAncPackets.mode(
                HuaweiDeviceRoute.HUAWEI_FREEBUDS5,
                NoiseControlMode.NOISE_CANCELLATION,
                0x03,
            ),
        )
        assertArrayEquals(
            hex("5A0007002B0401020103D17F"),
            HuaweiAncPackets.mode(
                HuaweiDeviceRoute.HUAWEI_FREEBUDS5,
                NoiseControlMode.NOISE_CANCELLATION,
            ),
        )
        assertArrayEquals(
            hex("5A0007002B0401020101F13D"),
            HuaweiAncPackets.mode(
                HuaweiDeviceRoute.HUAWEI_FREEBUDS5,
                NoiseControlMode.NOISE_CANCELLATION,
                0x01,
            ),
        )
        assertArrayEquals(
            hex("5A0007002B0401020100E11C"),
            HuaweiAncPackets.mode(
                HuaweiDeviceRoute.HUAWEI_FREEBUDS5,
                NoiseControlMode.NOISE_CANCELLATION,
                0x00,
            ),
        )
        assertNull(
            HuaweiAncPackets.mode(
                HuaweiDeviceRoute.HUAWEI_FREEBUDS5,
                NoiseControlMode.NOISE_CANCELLATION,
                0x02,
            ),
        )
        assertNull(
            HuaweiAncPackets.mode(
                HuaweiDeviceRoute.HUAWEI_FREEBUDS5,
                NoiseControlMode.TRANSPARENCY,
            ),
        )
    }

    @Test
    fun `FreeBuds 6i basic mode packets match verified capture`() {
        assertArrayEquals(
            hex("5A0007002B0401020000D22D"),
            HuaweiAncPackets.enabled(HuaweiDeviceRoute.HUAWEI_FREEBUDS6I, false),
        )
        assertArrayEquals(
            hex("5A0007002B04010201FFFFEC"),
            HuaweiAncPackets.enabled(HuaweiDeviceRoute.HUAWEI_FREEBUDS6I, true),
        )
    }

    @Test
    fun `FreeBuds Pro 3 uses captured ANC on and protocol family off packets`() {
        assertArrayEquals(
            hex("5A0007002B0401020000D22D"),
            HuaweiAncPackets.enabled(HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3, false),
        )
        assertArrayEquals(
            hex("5A0007002B04010201FFFFEC"),
            HuaweiAncPackets.enabled(HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3, true),
        )
    }

    @Test
    fun `FreeBuds Pro 4 basic mode packets match verified capture`() {
        assertArrayEquals(
            hex("5A0007002B0401020000D22D"),
            HuaweiAncPackets.enabled(HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO4, false),
        )
        assertArrayEquals(
            hex("5A0007002B04010201FFFFEC"),
            HuaweiAncPackets.enabled(HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO4, true),
        )
    }

    @Test
    fun `FreeBuds Pro 5 three mode packets match verified capture`() {
        assertArrayEquals(
            hex("5A0007002B0401020000D22D"),
            HuaweiAncPackets.mode(HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO5, NoiseControlMode.OFF),
        )
        assertArrayEquals(
            hex("5A0007002B0401020103D17F"),
            HuaweiAncPackets.mode(
                HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO5,
                NoiseControlMode.NOISE_CANCELLATION,
            ),
        )
        assertArrayEquals(
            hex("5A0007002B0401020202940D"),
            HuaweiAncPackets.mode(HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO5, NoiseControlMode.TRANSPARENCY),
        )
        assertArrayEquals(
            hex("5A0007002B0401020204F4CB"),
            HuaweiAncPackets.mode(
                HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO5,
                NoiseControlMode.TRANSPARENCY,
                0x04,
            ),
        )
    }

    @Test
    fun `FreeBuds 7i basic mode packets match verified capture`() {
        assertArrayEquals(
            hex("5A0007002B0401020000D22D"),
            HuaweiAncPackets.enabled(HuaweiDeviceRoute.HUAWEI_FREEBUDS7I, false),
        )
        assertArrayEquals(
            hex("5A0007002B04010201FFFFEC"),
            HuaweiAncPackets.enabled(HuaweiDeviceRoute.HUAWEI_FREEBUDS7I, true),
        )
    }

    @Test
    fun `modern models do not expose the FreeBuds 3 direction dial command`() {
        assertNull(HuaweiAncPackets.level(HuaweiDeviceRoute.HUAWEI_FREEBUDS5, 0))
        assertNull(HuaweiAncPackets.level(HuaweiDeviceRoute.HUAWEI_FREEBUDS6I, 0))
        assertNull(HuaweiAncPackets.level(HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3, 0))
        assertNull(HuaweiAncPackets.level(HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO4, 0))
        assertNull(HuaweiAncPackets.level(HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO5, 0))
        assertNull(HuaweiAncPackets.level(HuaweiDeviceRoute.HUAWEI_FREEBUDS7I, 0))
        assertNull(HuaweiAncPackets.level(HuaweiDeviceRoute.HUAWEI_EYEWEAR, 0))
    }

    @Test
    fun `newer Huawei models use the verified battery query`() {
        val query = hex("5A0009000108010002000300FBB9")

        assertArrayEquals(query, HuaweiAncPackets.batteryQuery(HuaweiDeviceRoute.HUAWEI_FREEBUDS5))
        assertArrayEquals(query, HuaweiAncPackets.batteryQuery(HuaweiDeviceRoute.HUAWEI_FREEBUDS6I))
        assertArrayEquals(query, HuaweiAncPackets.batteryQuery(HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3))
        assertArrayEquals(query, HuaweiAncPackets.batteryQuery(HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO4))
        assertArrayEquals(query, HuaweiAncPackets.batteryQuery(HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO5))
        assertArrayEquals(query, HuaweiAncPackets.batteryQuery(HuaweiDeviceRoute.HUAWEI_FREEBUDS7I))
        assertArrayEquals(query, HuaweiAncPackets.batteryQuery(HuaweiDeviceRoute.HUAWEI_FREECLIP))
        assertArrayEquals(query, HuaweiAncPackets.batteryQuery(HuaweiDeviceRoute.HUAWEI_FREECLIP2))
        assertArrayEquals(query, HuaweiAncPackets.batteryQuery(HuaweiDeviceRoute.HUAWEI_EYEWEAR))
        assertArrayEquals(query, HuaweiAncPackets.batteryQuery(HuaweiDeviceRoute.HUAWEI_EYEWEAR2))
        assertNull(HuaweiAncPackets.batteryQuery(HuaweiDeviceRoute.HUAWEI_FREEBUDS3))
    }

    @Test
    fun `FreeBuds 5i mode and level packets match verified capture`() {
        val route = HuaweiDeviceRoute.HUAWEI_FREEBUDS5I
        val packets = listOf(
            NoiseControlMode.OFF to "5A0007002B0401020000D22D",
            NoiseControlMode.TRANSPARENCY to "5A0007002B04010202FFAABF",
        )
        packets.forEach { (mode, packet) ->
            assertArrayEquals(packet, hex(packet), HuaweiAncPackets.mode(route, mode))
        }
        mapOf(
            0x03 to "5A0007002B0401020103D17F",
            0x01 to "5A0007002B0401020101F13D",
            0x00 to "5A0007002B0401020100E11C",
            0x02 to "5A0007002B0401020102C15E",
        ).forEach { (level, packet) ->
            assertArrayEquals(
                packet,
                hex(packet),
                HuaweiAncPackets.mode(route, NoiseControlMode.NOISE_CANCELLATION, level),
            )
        }
    }

    @Test
    fun `Pro 3 Pro 5 and 6i entering modes from off replay captured FF transition packets`() {
        val off = HuaweiAncState(NoiseControlMode.OFF)
        listOf(
            HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO5,
        ).forEach { route ->
            assertArrayEquals(
                route.name,
                hex("5A0007002B04010201FFFFEC"),
                HuaweiAncPackets.mode(
                    route,
                    NoiseControlMode.NOISE_CANCELLATION,
                    huaweiAncCommandSubMode(route, NoiseControlMode.NOISE_CANCELLATION, 0x03, off),
                ),
            )
            assertArrayEquals(
                route.name,
                hex("5A0007002B04010202FFAABF"),
                HuaweiAncPackets.mode(
                    route,
                    NoiseControlMode.TRANSPARENCY,
                    huaweiAncCommandSubMode(route, NoiseControlMode.TRANSPARENCY, 0x01, off),
                ),
            )
        }
    }

    @Test
    fun `modern RFCOMM models use the verified DeviceInfo query`() {
        val query = hex(
            "5A00210001070100020003000400050006000700080009000A000B000C000F0018001900DFF3",
        )

        enabledHuaweiDeviceRoutes()
            .filter { it.supportsRfcommBattery }
            .forEach { route ->
                assertArrayEquals(route.name, query, HuaweiAncPackets.deviceInfoQuery(route))
            }
        assertNull(HuaweiAncPackets.deviceInfoQuery(HuaweiDeviceRoute.HUAWEI_FREEBUDS3))
        assertNull(HuaweiAncPackets.deviceInfoQuery(HuaweiDeviceRoute.UNSUPPORTED))
        assertArrayEquals(query, HuaweiAncPackets.routeFreeDeviceInfoQuery())
    }

    @Test
    fun `modern models with captured readback expose the ANC state query`() {
        val query = hex("5A0005002B2A0100427E")

        assertArrayEquals(query, HuaweiAncPackets.currentStateQuery(HuaweiDeviceRoute.HUAWEI_FREEBUDS6I))
        assertArrayEquals(query, HuaweiAncPackets.currentStateQuery(HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3))
        assertArrayEquals(query, HuaweiAncPackets.currentStateQuery(HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO5))
        assertArrayEquals(query, HuaweiAncPackets.currentStateQuery(HuaweiDeviceRoute.HUAWEI_FREEBUDS5))
        assertArrayEquals(query, HuaweiAncPackets.currentStateQuery(HuaweiDeviceRoute.HUAWEI_FREEBUDS7I))
        assertNull(HuaweiAncPackets.currentStateQuery(HuaweiDeviceRoute.HUAWEI_FREEBUDS3))
        assertNull(HuaweiAncPackets.currentStateQuery(HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO4))
        assertNull(HuaweiAncPackets.currentStateQuery(HuaweiDeviceRoute.HUAWEI_FREECLIP2))
    }

    @Test
    fun `FreeBuds 6i packets follow the captured semantic level mapping`() {
        val expected = mapOf(
            HuaweiAncLevel.ADAPTIVE to "5A0007002B0401020103D17F",
            HuaweiAncLevel.LIGHT to "5A0007002B0401020101F13D",
            HuaweiAncLevel.BALANCED to "5A0007002B0401020100E11C",
            HuaweiAncLevel.DEEP to "5A0007002B0401020102C15E",
        )

        HuaweiDeviceRoute.HUAWEI_FREEBUDS6I.ancLevelOptions.forEach { option ->
            assertArrayEquals(
                hex(expected.getValue(option.level)),
                HuaweiAncPackets.mode(
                    HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
                    NoiseControlMode.NOISE_CANCELLATION,
                    option.protocolValue,
                ),
            )
        }
    }

    @Test
    fun `Pro 3 and 7i retain four verified ANC packets`() {
        val expected = mapOf(
            HuaweiAncLevel.ADAPTIVE to "5A0007002B0401020101F13D",
            HuaweiAncLevel.LIGHT to "5A0007002B0401020100E11C",
            HuaweiAncLevel.BALANCED to "5A0007002B0401020102C15E",
            HuaweiAncLevel.DEEP to "5A0007002B0401020103D17F",
        )
        listOf(
            HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3,
            HuaweiDeviceRoute.HUAWEI_FREEBUDS7I,
        ).forEach { route ->
            route.ancLevelOptions.forEach { option ->
                assertArrayEquals(
                    hex(expected.getValue(option.level)),
                    HuaweiAncPackets.mode(route, NoiseControlMode.NOISE_CANCELLATION, option.protocolValue),
                )
            }
        }
    }

    @Test
    fun `Pro 5 packets follow the Smart Audio labels confirmed on device`() {
        val expected = mapOf(
            HuaweiAncLevel.ADAPTIVE to "5A0007002B0401020103D17F",
            HuaweiAncLevel.LIGHT to "5A0007002B0401020101F13D",
            HuaweiAncLevel.BALANCED to "5A0007002B0401020100E11C",
            HuaweiAncLevel.DEEP to "5A0007002B0401020102C15E",
        )

        HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO5.ancLevelOptions.forEach { option ->
            assertArrayEquals(
                hex(expected.getValue(option.level)),
                HuaweiAncPackets.mode(
                    HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO5,
                    NoiseControlMode.NOISE_CANCELLATION,
                    option.protocolValue,
                ),
            )
        }
    }

    @Test
    fun `FreeBuds 6i exposes both verified transparency submodes`() {
        assertArrayEquals(
            hex("5A0007002B04010202FFAABF"),
            HuaweiAncPackets.mode(
                HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
                NoiseControlMode.TRANSPARENCY,
                0xFF,
            ),
        )
        assertArrayEquals(
            hex("5A0007002B0401020201A46E"),
            HuaweiAncPackets.mode(
                HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
                NoiseControlMode.TRANSPARENCY,
                0x01,
            ),
        )
        assertArrayEquals(
            hex("5A0007002B0401020202940D"),
            HuaweiAncPackets.mode(
                HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
                NoiseControlMode.TRANSPARENCY,
                0x02,
            ),
        )
        assertNull(
            HuaweiAncPackets.mode(
                HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
                NoiseControlMode.TRANSPARENCY,
                0x7F,
            ),
        )
    }

    @Test
    fun `FreeBuds 3 direction dial exposes all nine verified forward packets`() {
        val packets = listOf(
            "5A0006002B080101002713",
            "5A0006002B080101013732",
            "5A0006002B080101020751",
            "5A0006002B080101031770",
            "5A0006002B080101046797",
            "5A0006002B0801010577B6",
            "5A0006002B0801010647D5",
            "5A0006002B0801010757F4",
            "5A0006002B08010108A61B",
        )

        packets.forEachIndexed { level, packet ->
            assertArrayEquals(
                "level=$level",
                hex(packet),
                HuaweiAncPackets.level(HuaweiDeviceRoute.HUAWEI_FREEBUDS3, level),
            )
        }
    }

    @Test
    fun `transparency defaults use each models captured standard mode`() {
        assertArrayEquals(
            hex("5A0007002B0401020202940D"),
            HuaweiAncPackets.mode(HuaweiDeviceRoute.HUAWEI_FREEBUDS6I, NoiseControlMode.TRANSPARENCY),
        )
        assertArrayEquals(
            hex("5A0007002B04010202FFAABF"),
            HuaweiAncPackets.mode(HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3, NoiseControlMode.TRANSPARENCY),
        )
        assertArrayEquals(
            hex("5A0007002B0401020202940D"),
            HuaweiAncPackets.mode(HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO5, NoiseControlMode.TRANSPARENCY),
        )
    }

    private fun hex(value: String): ByteArray = value.chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()
}
