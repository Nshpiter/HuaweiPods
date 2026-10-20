package moe.chenxy.huaweipods.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootManagerTest {
    @Test
    fun `bluetooth producer is always restarted after its consumers`() {
        val ordered = RootManager.orderedRestartTargets(
            listOf(
                "com.android.bluetooth",
                "com.heytap.mydevices",
                "com.huawei.smartaudio",
                "com.heytap.accessory",
            ),
        )

        assertEquals(
            listOf(
                "com.heytap.accessory",
                "com.heytap.mydevices",
                "com.huawei.smartaudio",
                "com.android.bluetooth",
            ),
            ordered,
        )
    }

    @Test
    fun `invalid packages are rejected and additional scopes precede bluetooth`() {
        assertEquals(
            listOf("com.example.extra", "com.android.bluetooth"),
            RootManager.orderedRestartTargets(
                listOf("com.android.bluetooth", "com.example.extra", "bad package", "com.example.extra"),
            ),
        )
    }

    @Test
    fun `restart command captures proc once and verifies old pids before every signal`() {
        val command = RootManager.buildRestartCommand(
            listOf("com.heytap.mydevices", "com.android.bluetooth"),
        )

        assertFalse(command.contains("am force-stop"))
        assertFalse(command.contains("tr '\\000'"))
        assertFalse(command.contains("head -n 1"))
        assertEquals(1, Regex.fromLiteral("/proc/[0-9]*").findAll(command).count())
        assertTrue(command.contains("scope_snapshot=\"\$(scope_capture \"\$@\")\""))
        assertTrue(command.contains("for rs_target in \"\$@\""))
        assertTrue(command.contains("scope_pid_matches \"\$rs_pid\" \"\$rs_target\""))
        assertTrue(command.contains("kill -15 \"\$rs_pid\""))
        assertTrue(command.contains("kill -9 \"\$rs_pid\""))
        assertTrue(command.contains("sleep 0.2"))
        assertTrue(command.indexOf("'com.heytap.mydevices'") < command.indexOf("'com.android.bluetooth'"))
    }
}
