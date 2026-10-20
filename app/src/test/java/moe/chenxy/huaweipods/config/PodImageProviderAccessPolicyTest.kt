package moe.chenxy.huaweipods.config

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PodImageProviderAccessPolicyTest {
    @Test
    fun `only module and actual image scopes may open files`() {
        listOf(
            "moe.chenxy.huaweipods.coloros",
            "com.android.bluetooth",
            "com.heytap.accessory",
        ).forEach { assertTrue(it, PodImageProviderAccessPolicy.mayOpenImage(it)) }

        assertFalse(PodImageProviderAccessPolicy.mayOpenImage("com.milink.service"))
        assertFalse(PodImageProviderAccessPolicy.mayOpenImage("com.xiaomi.bluetooth"))

        assertFalse(PodImageProviderAccessPolicy.mayOpenImage("com.huawei.smartaudio"))
        assertFalse(PodImageProviderAccessPolicy.mayOpenImage("com.example.thirdparty"))
        assertFalse(PodImageProviderAccessPolicy.mayOpenImage(null))
    }

    @Test
    fun `shared bluetooth uid may open images through an allowed package`() {
        assertTrue(
            PodImageProviderAccessPolicy.mayOpenImage(
                listOf("com.example.shared", "com.android.bluetooth"),
            ),
        )
        assertFalse(
            PodImageProviderAccessPolicy.mayOpenImage(
                listOf("com.example.one", "com.example.two"),
            ),
        )
    }

    @Test
    fun `only trusted identity producers may submit an identity`() {
        assertTrue(PodImageProviderAccessPolicy.maySubmitOfficialImageIdentity("moe.chenxy.huaweipods.coloros"))
        assertTrue(PodImageProviderAccessPolicy.maySubmitOfficialImageIdentity("com.android.bluetooth"))
        assertFalse(PodImageProviderAccessPolicy.maySubmitOfficialImageIdentity("com.huawei.smartaudio"))
        assertFalse(PodImageProviderAccessPolicy.maySubmitOfficialImageIdentity("com.android.settings"))
        assertFalse(PodImageProviderAccessPolicy.maySubmitOfficialImageIdentity("com.example.thirdparty"))
    }
}
