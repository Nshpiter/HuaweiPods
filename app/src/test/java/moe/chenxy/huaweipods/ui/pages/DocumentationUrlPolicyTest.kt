package moe.chenxy.huaweipods.ui.pages

import org.junit.Assert.assertEquals
import org.junit.Test

class DocumentationUrlPolicyTest {
    @Test
    fun `ColorOS guide uses its own branch documentation`() {
        assertEquals(DocumentationUrlDestination.IN_APP, DocumentationUrlPolicy.destination(DocumentationUrlPolicy.HOME_URL))
        assertEquals(DocumentationUrlDestination.EXTERNAL, DocumentationUrlPolicy.destination(
            "https://github.com/Nshpiter/HuaweiPods/blob/main/docs/guide/getting-started.md",
        ))
        assertEquals(DocumentationUrlDestination.EXTERNAL, DocumentationUrlPolicy.destination(
            "https://github.com/other/HuaweiPods/blob/coloros/docs/guide/getting-started.md",
        ))
    }
    @Test
    fun `official website stays inside app`() {
        listOf(
            "https://huaweipods.npiter.de/",
            "https://huaweipods.npiter.de/guide/getting-started.html",
            "https://HUAWEIPODS.NPITER.DE/support/?route=freebuds3#images",
            "https://huaweipods.npiter.de:443/sponsor/",
        ).forEach { url ->
            assertEquals(
                DocumentationUrlDestination.IN_APP,
                DocumentationUrlPolicy.destination(url),
            )
        }
    }

    @Test
    fun `other trusted web destinations open externally`() {
        listOf(
            "https://github.com/Nshpiter/HuaweiPods",
            "https://smarthome-drcn.dbankcdn.cn/device/guide/AAM001/000027/000027.json",
        ).forEach { url ->
            assertEquals(
                DocumentationUrlDestination.EXTERNAL,
                DocumentationUrlPolicy.destination(url),
            )
        }
    }

    @Test
    fun `unsafe or ambiguous urls are blocked`() {
        listOf(
            "http://huaweipods.npiter.de/",
            "javascript:alert(1)",
            "file:///data/local/tmp/test.html",
            "https://user@huaweipods.npiter.de/",
            "https://huaweipods.npiter.de:444/",
            "https://huaweipods.npiter.de.evil.example/",
            "not a url",
        ).forEach { url ->
            assertEquals(
                DocumentationUrlDestination.BLOCKED,
                DocumentationUrlPolicy.destination(url),
            )
        }
    }
}
