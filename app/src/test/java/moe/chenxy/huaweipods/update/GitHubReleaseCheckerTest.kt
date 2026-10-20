package moe.chenxy.huaweipods.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubReleaseCheckerTest {
    private fun releaseJson(
        code: Int,
        version: String,
        prerelease: Boolean = false,
        draft: Boolean = false,
    ): String = """{"tag_name":"$code-$version","html_url":"https://github.com/Nshpiter/HuaweiPods/releases/tag/$code-$version","prerelease":$prerelease,"draft":$draft}"""

    @Test
    fun `ColorOS channel ignores newer HyperOS releases`() {
        val payload = "[${releaseJson(100, "1.10.0")},${releaseJson(22, "1.9.1-coloros.1")}]"
        val release = GitHubReleaseChecker.parseReleaseList(payload, allowPrerelease = false).getOrThrow()
        assertEquals(22L, release?.versionCode)
        assertEquals(null, GitHubReleaseChecker.parseTag("100-1.10.0"))
        assertEquals(null, GitHubReleaseChecker.parseTag("26-1.9.0.2-rc3"))
    }

    @Test
    fun `stable builds skip RC versions and draft releases`() {
        val payload = "[${releaseJson(24, "1.9.1-coloros.2", draft = true)}," +
            "${releaseJson(23, "1.9.1-coloros.2-rc1", prerelease = true)}," +
            "${releaseJson(22, "1.9.1-coloros.1")}]"
        assertEquals(22L, GitHubReleaseChecker.parseReleaseList(payload, false).getOrThrow()?.versionCode)
        assertEquals(23L, GitHubReleaseChecker.parseReleaseList(payload, true).getOrThrow()?.versionCode)
    }

    @Test
    fun `RC suffix cannot be promoted to a stable update by metadata`() {
        val payload = "[${releaseJson(23, "1.9.1-coloros.2-rc1")}]"
        assertEquals(null, GitHubReleaseChecker.parseReleaseList(payload, false).getOrThrow())
    }

    @Test
    fun `release ordering uses version code rather than API array order`() {
        val payload = "[${releaseJson(22, "1.9.1-coloros.1")},${releaseJson(24, "1.9.1-coloros.2")}]"
        assertEquals(24L, GitHubReleaseChecker.parseReleaseList(payload, false).getOrThrow()?.versionCode)
    }

    @Test
    fun `empty channel and unrelated releases do not become updates`() {
        assertEquals(null, GitHubReleaseChecker.parseReleaseList("[]", true).getOrThrow())
        assertEquals(null, GitHubReleaseChecker.parseReleaseList("[${releaseJson(100, "1.10.0")}]", true).getOrThrow())
        assertTrue(GitHubReleaseChecker.parseReleaseList("invalid", true).isFailure)
    }

    @Test
    fun `release URL must identify the declared ColorOS tag`() {
        val payload = releaseJson(22, "1.9.1-coloros.1").replace("tag/22-", "tag/23-")
        assertTrue(GitHubReleaseChecker.parseReleaseResponse(payload).isFailure)
    }

    @Test
    fun `parses HuaweiPods version code and version name tag`() {
        assertEquals(
            ReleaseTag(versionCode = 4L, versionName = "1.2.0-coloros.1"),
            GitHubReleaseChecker.parseTag("4-1.2.0-coloros.1"),
        )
        assertEquals(
            ReleaseTag(versionCode = 12L, versionName = "2.0.0-coloros.1-beta.1"),
            GitHubReleaseChecker.parseTag("12-2.0.0-coloros.1-beta.1"),
        )
    }

    @Test
    fun `rejects legacy and malformed release tags`() {
        listOf(
            "v1.2.0-coloros.1",
            "1.2.0-coloros.1",
            "0-1.0.0",
            "4-",
            "4-1.2.0-coloros.1 beta",
            "not-a-release",
        ).forEach { tag ->
            assertEquals(tag, null, GitHubReleaseChecker.parseTag(tag))
        }
    }

    @Test
    fun `uses Android version code as update ordering`() {
        val remote = ReleaseTag(versionCode = 4L, versionName = "1.2.0-coloros.1")

        assertTrue(GitHubReleaseChecker.isNewer(remote, currentVersionCode = 3L))
        assertFalse(GitHubReleaseChecker.isNewer(remote, currentVersionCode = 4L))
        assertFalse(GitHubReleaseChecker.isNewer(remote, currentVersionCode = 5L))
    }

    @Test
    fun `parses trusted latest release response`() {
        val result = GitHubReleaseChecker.parseReleaseResponse(
            """
            {
              "tag_name": "4-1.2.0-coloros.1",
              "html_url": "https://github.com/Nshpiter/HuaweiPods/releases/tag/4-1.2.0-coloros.1",
              "body": "Battery fixes and model integration",
              "ignored": true
            }
            """.trimIndent(),
        )

        assertTrue(result.isSuccess)
        assertEquals(
            GitHubRelease(
                tag = "4-1.2.0-coloros.1",
                versionCode = 4L,
                versionName = "1.2.0-coloros.1",
                releaseUrl = "https://github.com/Nshpiter/HuaweiPods/releases/tag/4-1.2.0-coloros.1",
                changelog = "Battery fixes and model integration",
            ),
            result.getOrNull(),
        )
    }

    @Test
    fun `accepts only HTTPS release pages from the HuaweiPods GitHub repository`() {
        assertTrue(
            GitHubReleaseChecker.isTrustedReleaseUrl(
                "https://github.com/Nshpiter/HuaweiPods/releases/tag/4-1.2.0-coloros.1",
            ),
        )

        listOf(
            "http://github.com/Nshpiter/HuaweiPods/releases/tag/4-1.2.0-coloros.1",
            "https://github.com.evil.example/Nshpiter/HuaweiPods/releases/tag/4-1.2.0-coloros.1",
            "https://github.com/other/HuaweiPods/releases/tag/4-1.2.0-coloros.1",
            "https://github.com/Nshpiter/HuaweiPods/issues",
            "https://user@github.com/Nshpiter/HuaweiPods/releases/tag/4-1.2.0-coloros.1",
            "https://github.com:444/Nshpiter/HuaweiPods/releases/tag/4-1.2.0-coloros.1",
        ).forEach { url ->
            assertFalse(url, GitHubReleaseChecker.isTrustedReleaseUrl(url))
        }
    }

    @Test
    fun `parses trusted latest release redirect without release notes`() {
        val result = GitHubReleaseChecker.parseLatestReleaseRedirect(
            "https://github.com/Nshpiter/HuaweiPods/releases/tag/6-1.3.1-coloros.1",
        )

        assertEquals(
            GitHubRelease(
                tag = "6-1.3.1-coloros.1",
                versionCode = 6L,
                versionName = "1.3.1-coloros.1",
                releaseUrl = "https://github.com/Nshpiter/HuaweiPods/releases/tag/6-1.3.1-coloros.1",
                changelog = "",
            ),
            result.getOrNull(),
        )
    }

    @Test
    fun `rejects malformed or untrusted latest release redirects`() {
        listOf(
            "https://github.com/other/HuaweiPods/releases/tag/6-1.3.1-coloros.1",
            "https://github.com/Nshpiter/HuaweiPods/releases/latest",
            "https://github.com/Nshpiter/HuaweiPods/releases/tag/v1.3.1-coloros.1",
            "https://github.com/Nshpiter/HuaweiPods/releases/tag/6-1.3.1-coloros.1/extra",
            "https://github.com/Nshpiter/HuaweiPods/releases/tag/6-1.3.1-coloros.1?download=1",
            "https://github.com/Nshpiter/HuaweiPods/releases/tag/6-1.3.1-coloros.1%20",
            "https://github.com/Nshpiter/HuaweiPods/releases/tag/6-1.3.1-coloros.1%09",
        ).forEach { location ->
            assertTrue(location, GitHubReleaseChecker.parseLatestReleaseRedirect(location).isFailure)
        }
    }

    @Test
    fun `rejects a release response with an untrusted URL`() {
        val result = GitHubReleaseChecker.parseReleaseResponse(
            """
            {
              "tag_name": "4-1.2.0-coloros.1",
              "html_url": "https://example.com/HuaweiPods.apk",
              "body": ""
            }
            """.trimIndent(),
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun `caps release notes before rendering`() {
        val longReleaseNotes = "x".repeat(GitHubReleaseChecker.MAX_CHANGELOG_CHARS + 1_000)
        val result = GitHubReleaseChecker.parseReleaseResponse(
            """
            {
              "tag_name": "4-1.2.0-coloros.1",
              "html_url": "https://github.com/Nshpiter/HuaweiPods/releases/tag/4-1.2.0-coloros.1",
              "body": "$longReleaseNotes"
            }
            """.trimIndent(),
        )

        assertTrue(result.isSuccess)
        assertEquals(
            GitHubReleaseChecker.MAX_CHANGELOG_CHARS,
            result.getOrThrow().changelog.length,
        )
    }
}
