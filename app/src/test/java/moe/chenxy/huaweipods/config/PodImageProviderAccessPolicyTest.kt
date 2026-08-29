package moe.chenxy.huaweipods.config

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PodImageProviderAccessPolicyTest {
    @Test
    fun `only module and actual image scopes may open files`() {
        listOf(
            "moe.chenxy.huaweipods",
            "com.android.bluetooth",
            "com.android.settings",
            "com.milink.service",
            "com.xiaomi.bluetooth",
        ).forEach { assertTrue(it, PodImageProviderAccessPolicy.mayOpenImage(it)) }

        assertFalse(PodImageProviderAccessPolicy.mayOpenImage("com.huawei.smartaudio"))
        assertFalse(PodImageProviderAccessPolicy.mayOpenImage("com.example.thirdparty"))
        assertFalse(PodImageProviderAccessPolicy.mayOpenImage(null))
    }

    @Test
    fun `shared bluetooth uid may open images through an allowed package`() {
        assertTrue(
            PodImageProviderAccessPolicy.mayOpenImage(
                listOf("com.android.bluetooth", "com.xiaomi.bluetooth"),
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
        assertTrue(PodImageProviderAccessPolicy.maySubmitOfficialImageIdentity("moe.chenxy.huaweipods"))
        assertTrue(PodImageProviderAccessPolicy.maySubmitOfficialImageIdentity("com.android.bluetooth"))
        assertFalse(PodImageProviderAccessPolicy.maySubmitOfficialImageIdentity("com.huawei.smartaudio"))
        assertFalse(PodImageProviderAccessPolicy.maySubmitOfficialImageIdentity("com.android.settings"))
        assertFalse(PodImageProviderAccessPolicy.maySubmitOfficialImageIdentity("com.example.thirdparty"))
    }
}
