package moe.chenxy.huaweipods.ui.pages

import moe.chenxy.huaweipods.pods.HuaweiDeviceRoute
import moe.chenxy.huaweipods.pods.displayName

/** Keeps the manual fallback compact as the supported model list grows. */
internal fun filterDeviceModelRoutes(
    routes: List<HuaweiDeviceRoute>,
    query: String,
): List<HuaweiDeviceRoute> {
    val normalizedQuery = query.normalizedModelSearchText()
    if (normalizedQuery.isBlank()) return routes
    return routes.filter { route ->
        route.displayName.normalizedModelSearchText().contains(normalizedQuery)
    }
}

internal fun compactDeviceModelName(route: HuaweiDeviceRoute): String =
    route.displayName.removePrefix("HUAWEI ").ifBlank { route.displayName }

private fun String.normalizedModelSearchText(): String =
    lowercase().filter { it.isLetterOrDigit() }
