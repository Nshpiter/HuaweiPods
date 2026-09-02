package moe.chenxy.huaweipods.ui.dialogs

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp

@Composable
internal fun responsiveOverlayDialogModifier(): Modifier {
    val safeDrawingPadding = WindowInsets.safeDrawing.asPaddingValues()
    val maxHeight = with(LocalDensity.current) {
        (
            LocalWindowInfo.current.containerSize.height.toDp() -
                safeDrawingPadding.calculateTopPadding() -
                safeDrawingPadding.calculateBottomPadding() -
                32.dp
        ).coerceAtLeast(240.dp)
    }
    return Modifier
        .heightIn(max = maxHeight)
        .verticalScroll(rememberScrollState())
}
