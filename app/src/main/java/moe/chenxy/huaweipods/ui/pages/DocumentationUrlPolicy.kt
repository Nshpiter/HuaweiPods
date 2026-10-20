package moe.chenxy.huaweipods.ui.pages

import java.net.URI

internal object DocumentationUrlPolicy {
    const val HOME_URL = "https://github.com/Nshpiter/HuaweiPods/blob/coloros/docs/guide/getting-started.md"

    private const val OFFICIAL_HOST = "huaweipods.npiter.de"

    fun destination(rawUrl: String): DocumentationUrlDestination {
        val uri = runCatching { URI(rawUrl) }.getOrNull()
            ?: return DocumentationUrlDestination.BLOCKED
        if (uri.userInfo != null || uri.host.isNullOrBlank()) {
            return DocumentationUrlDestination.BLOCKED
        }
        if (!uri.scheme.equals("https", ignoreCase = true)) {
            return DocumentationUrlDestination.BLOCKED
        }
        if (uri.port != -1 && uri.port != 443) {
            return DocumentationUrlDestination.BLOCKED
        }
        val host = uri.host.lowercase()
        return if (host == OFFICIAL_HOST ||
            (host == "github.com" && uri.path.startsWith("/Nshpiter/HuaweiPods/blob/coloros/docs/"))) {
            DocumentationUrlDestination.IN_APP
        } else if (host.contains(OFFICIAL_HOST)) {
            DocumentationUrlDestination.BLOCKED
        } else {
            DocumentationUrlDestination.EXTERNAL
        }
    }
}

internal enum class DocumentationUrlDestination {
    IN_APP,
    EXTERNAL,
    BLOCKED,
}
