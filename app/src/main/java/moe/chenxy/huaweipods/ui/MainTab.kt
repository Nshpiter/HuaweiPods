package moe.chenxy.huaweipods.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import moe.chenxy.huaweipods.R
import moe.chenxy.huaweipods.ui.components.AppIcons
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Info

internal enum class MainTab(val icon: ImageVector) {
    Module(AppIcons.Home),
    Earphones(AppIcons.Headphones),
    About(MiuixIcons.Info),
}

@Composable
internal fun MainTab.title(): String = when (this) {
    MainTab.Module -> stringResource(R.string.module)
    MainTab.Earphones -> stringResource(R.string.earphones)
    MainTab.About -> stringResource(R.string.about_tab)
}
