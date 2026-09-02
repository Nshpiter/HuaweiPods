package moe.chenxy.huaweipods.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem
import top.yukonga.miuix.kmp.basic.FloatingToolbarDefaults
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun MainBottomNavigation(
    tabs: List<MainTab>,
    selectedTab: MainTab,
    floating: Boolean,
    blur: Boolean,
    backdrop: LayerBackdrop?,
    onTabClick: (MainTab) -> Unit,
) {
    val barShape = if (floating) {
        RoundedCornerShape(FloatingToolbarDefaults.CornerRadius)
    } else {
        RoundedCornerShape(0.dp)
    }
    val barModifier = if (blur && backdrop != null) {
        Modifier.textureBlur(
            backdrop = backdrop,
            shape = barShape,
            blurRadius = 16f,
            colors = BlurDefaults.blurColors(
                blendColors = listOf(
                    BlendColorEntry(MiuixTheme.colorScheme.surface.copy(alpha = 0.58f)),
                    BlendColorEntry(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.025f)),
                ),
            ),
        )
    } else {
        Modifier.background(MiuixTheme.colorScheme.surface, barShape)
    }

    if (floating) {
        FloatingNavigationBar(
            modifier = barModifier.zIndex(2f),
            color = if (blur) Color.Transparent else MiuixTheme.colorScheme.surfaceContainer,
        ) {
            tabs.forEach { tab ->
                FloatingNavigationBarItem(
                    selected = selectedTab == tab,
                    onClick = { onTabClick(tab) },
                    icon = tab.icon,
                    label = tab.title(),
                )
            }
        }
    } else {
        NavigationBar(
            modifier = barModifier.zIndex(2f),
            color = if (blur) Color.Transparent else MiuixTheme.colorScheme.surface,
            showDivider = true,
        ) {
            tabs.forEach { tab ->
                NavigationBarItem(
                    selected = selectedTab == tab,
                    onClick = { onTabClick(tab) },
                    icon = tab.icon,
                    label = tab.title(),
                )
            }
        }
    }
}
