package moe.chenxy.huaweipods.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PendingUpdateStoreTest {
    @Test
    fun `stable installs discard a previously cached RC update`() {
        val pending = PendingUpdateSnapshot(
            tag = "30-1.9.2-coloros.1-rc1",
            versionCode = 30L,
            versionName = "1.9.2-coloros.1-rc1",
            releaseUrl = "https://github.com/Nshpiter/HuaweiPods/releases/tag/30-1.9.2-coloros.1-rc1",
            changelog = "RC",
        )
        assertNull(restorePendingUpdate(pending, currentVersionCode = 22L))
        assertEquals(pending.tag, restorePendingUpdate(pending, currentVersionCode = 22L, allowPrerelease = true)?.tag)
    }

    private val valid = PendingUpdateSnapshot(
        tag = "10-1.4.3-coloros.1",
        versionCode = 10L,
        versionName = "1.4.3-coloros.1",
        releaseUrl = "https://github.com/Nshpiter/HuaweiPods/releases/tag/10-1.4.3-coloros.1",
        changelog = "Update notes",
    )

    @Test
    fun `newer trusted release is restored`() {
        val restored = restorePendingUpdate(valid, currentVersionCode = 9L)

        assertEquals("10-1.4.3-coloros.1", restored?.tag)
        assertEquals("Update notes", restored?.changelog)
    }

    @Test
    fun `installed or older release is discarded`() {
        assertNull(restorePendingUpdate(valid, currentVersionCode = 10L))
        assertNull(restorePendingUpdate(valid, currentVersionCode = 11L))
    }

    @Test
    fun `mismatched tag metadata is discarded`() {
        assertNull(
            restorePendingUpdate(
                valid.copy(versionName = "1.4.4-coloros.1"),
                currentVersionCode = 9L,
            ),
        )
    }

    @Test
    fun `untrusted release url is discarded`() {
        assertNull(
            restorePendingUpdate(
                valid.copy(releaseUrl = "https://example.com/releases/tag/10-1.4.3-coloros.1"),
                currentVersionCode = 9L,
            ),
        )
    }

    @Test
    fun `preview release only restores in debug mode`() {
        val preview = valid.copy(
            tag = valid.tag + "-preview",
            versionName = valid.versionName + "-preview",
            isPreview = true,
        )

        assertNull(restorePendingUpdate(preview, currentVersionCode = 9L))
        assertEquals(
            preview.tag,
            restorePendingUpdate(
                snapshot = preview,
                currentVersionCode = 9L,
                allowPreview = true,
            )?.tag,
        )
    }
}
