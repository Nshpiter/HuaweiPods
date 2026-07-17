package moe.chenxy.huaweipods.ui.components

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import moe.chenxy.huaweipods.R
import moe.chenxy.huaweipods.pods.NoiseControlMode
import moe.chenxy.huaweipods.pods.isNoiseCancellation
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.SinkFeedback
import top.yukonga.miuix.kmp.utils.pressable
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private const val ANIM_DURATION = 300

@Composable
fun AncSwitch(
    ancStatus: NoiseControlMode,
    onAncModeChange: (NoiseControlMode) -> Unit,
    smartAncLevel: NoiseControlMode? = null,
    compact: Boolean = false,
    simpleMode: Boolean = false,
    adaptiveModeEnabled: Boolean = true,
    huaweiAncLevel: Int = 0,
    onHuaweiAncLevelChange: ((Int) -> Unit)? = null,
    transparencyVocalEnhancement: Boolean = false,
    onTransparencyVocalEnhancementChange: ((Boolean) -> Unit)? = null
) {
    val verticalPadding = if (compact) 8.dp else 16.dp
    val tabMinWidth = 0.dp
    val tabMaxWidth = if (compact) 72.dp else 98.dp
    val tabHeight = if (compact) 38.dp else 45.dp
    val tabSpacing = if (compact) 4.dp else 9.dp
    val tabOuterPadding = if (compact) 8.dp else 12.dp
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = verticalPadding)
    ) {
        if (simpleMode) {
            HuaweiAncSimpleHeader(
                enabled = ancStatus.isNoiseCancellation(),
                onToggle = {
                    onAncModeChange(
                        if (ancStatus.isNoiseCancellation()) NoiseControlMode.OFF else NoiseControlMode.NOISE_CANCELLATION
                    )
                },
                compact = compact
            )
        } else {        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AncButton(
                offIconRes = R.drawable.ic_openanc_off,
                onIconRes = R.drawable.ic_openanc_on,
                label = stringResource(R.string.noise_cancellation_title),
                isSelected = ancStatus.isNoiseCancellation(),
                onClick = { onAncModeChange(NoiseControlMode.NOISE_CANCELLATION) },
                modifier = Modifier.weight(1f),
                compact = compact
            )
            // Adaptive模式按钮：仅当设置中启用Adaptive模式时显示
            if (!simpleMode && adaptiveModeEnabled) {
                AncButton(
                    offIconRes = R.drawable.ic_adaptive_off,
                    onIconRes = R.drawable.ic_adaptive_on,
                    label = stringResource(R.string.adaptive_title),
                    isSelected = ancStatus == NoiseControlMode.ADAPTIVE,
                    onClick = { onAncModeChange(NoiseControlMode.ADAPTIVE) },
                    modifier = Modifier.weight(1f),
                    compact = compact
                )
            }
            if (!simpleMode) {
                AncButton(
                    offIconRes = R.drawable.ic_transparent_off,
                    onIconRes = R.drawable.ic_transparent_on,
                    label = stringResource(R.string.transparency_title),
                    isSelected = ancStatus == NoiseControlMode.TRANSPARENCY,
                    onClick = { onAncModeChange(NoiseControlMode.TRANSPARENCY) },
                    modifier = Modifier.weight(1f),
                    compact = compact
                )
            }
            AncButton(
                offIconRes = R.drawable.ic_closeanc_off,
                onIconRes = R.drawable.ic_closeanc_on,
                label = stringResource(R.string.off),
                isSelected = ancStatus == NoiseControlMode.OFF,
                onClick = { onAncModeChange(NoiseControlMode.OFF) },
                modifier = Modifier.weight(1f),
                compact = compact
            )
        }
        }

        if (simpleMode && ancStatus.isNoiseCancellation() && onHuaweiAncLevelChange != null) {
            HuaweiAncLevelDial(
                level = huaweiAncLevel.coerceIn(0, 8),
                onLevelChange = onHuaweiAncLevelChange,
                compact = compact,
                modifier = Modifier.padding(top = if (compact) 8.dp else 14.dp)
            )
        }

        if (!simpleMode && ancStatus.isNoiseCancellation()) {
            val modes = listOf(
                NoiseControlMode.NOISE_CANCELLATION_SMART,
                NoiseControlMode.NOISE_CANCELLATION_LIGHT,
                NoiseControlMode.NOISE_CANCELLATION_MEDIUM,
                NoiseControlMode.NOISE_CANCELLATION_DEEP
            )
            val isSmart = ancStatus == NoiseControlMode.NOISE_CANCELLATION_SMART
            val smartLevelIndex = when (smartAncLevel) {
                NoiseControlMode.NOISE_CANCELLATION_LIGHT -> 1
                NoiseControlMode.NOISE_CANCELLATION_MEDIUM -> 2
                NoiseControlMode.NOISE_CANCELLATION_DEEP -> 3
                else -> null
            }
            val smartName = stringResource(R.string.noise_cancellation_smart)
            val tabs = listOf(
                smartName,
                stringResource(R.string.noise_cancellation_light),
                stringResource(R.string.noise_cancellation_medium),
                stringResource(R.string.noise_cancellation_deep)
            )

            AncStrengthTabRow(
                tabs = tabs,
                selectedTabIndex = modes.indexOf(ancStatus).takeIf { it >= 0 } ?: 0,
                assistHighlightedIndex = if (isSmart) smartLevelIndex else null,
                onTabSelected = { onAncModeChange(modes[it]) },
                compact = compact,
                minWidth = tabMinWidth,
                tabMaxWidth = tabMaxWidth,
                height = tabHeight,
                itemSpacing = tabSpacing,
                outerPadding = tabOuterPadding,
                topPadding = if (compact) 8.dp else 16.dp
            )
        }

        if (!simpleMode && ancStatus == NoiseControlMode.TRANSPARENCY && onTransparencyVocalEnhancementChange != null) {
            val tabs = listOf(
                stringResource(R.string.transparency_title),
                stringResource(R.string.transparency_vocal_enhancement)
            )
            ResponsiveAncTabRow(
                tabs = tabs,
                selectedTabIndex = if (transparencyVocalEnhancement) 1 else 0,
                onTabSelected = { onTransparencyVocalEnhancementChange(it == 1) },
                compact = compact,
                minWidth = tabMinWidth,
                tabMaxWidth = tabMaxWidth,
                height = tabHeight,
                itemSpacing = tabSpacing,
                outerPadding = tabOuterPadding
            )
        }
    }
}

@Composable
private fun HuaweiAncSimpleHeader(
    enabled: Boolean,
    onToggle: () -> Unit,
    compact: Boolean
) {
    val interactionSource = remember { MutableInteractionSource() }
    val primary = MiuixTheme.colorScheme.primary
    val trackColor = if (enabled) primary else MiuixTheme.colorScheme.onBackground.copy(alpha = 0.16f)
    val thumbOffset = if (enabled) 22.dp else 2.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = if (compact) 10.dp else 14.dp, vertical = if (compact) 2.dp else 4.dp)
            .pressable(interactionSource = interactionSource, indication = SinkFeedback())
            .clickable(interactionSource = interactionSource, indication = null, onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.noise_cancellation_title),
            fontSize = if (compact) 14.sp else 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = MiuixTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .size(width = 46.dp, height = 26.dp)
                .background(trackColor, RoundedCornerShape(13.dp))
                .padding(2.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .padding(start = thumbOffset)
                    .size(22.dp)
                    .background(Color.White, RoundedCornerShape(11.dp))
            )
        }
    }
}
@Composable
private fun HuaweiAncLevelDial(
    level: Int,
    onLevelChange: (Int) -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    val primary = MiuixTheme.colorScheme.primary
    val tickColor = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.28f)
    val diskColor = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.035f)
    val ringColor = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.10f)
    val dialSize = if (compact) 116.dp else 188.dp
    var displayedLevel by remember { mutableIntStateOf(level.coerceIn(0, HUAWEI_ANC_LEVEL_LAST)) }
    var sentLevel by remember { mutableIntStateOf(level.coerceIn(0, HUAWEI_ANC_LEVEL_LAST)) }

    LaunchedEffect(level) {
        val safeLevel = level.coerceIn(0, HUAWEI_ANC_LEVEL_LAST)
        displayedLevel = safeLevel
        sentLevel = safeLevel
    }

    fun updateLevel(nextLevel: Int) {
        val safeLevel = nextLevel.coerceIn(0, HUAWEI_ANC_LEVEL_LAST)
        displayedLevel = safeLevel
        if (safeLevel != sentLevel) {
            sentLevel = safeLevel
            onLevelChange(safeLevel)
        }
    }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        ComposeCanvas(
            modifier = Modifier
                .size(dialSize)
                .pointerInput(onLevelChange) {
                    detectTapGestures { position ->
                        updateLevel(position.toHuaweiAncLevel(size.width.toFloat(), size.height.toFloat()))
                    }
                }
                .pointerInput(onLevelChange) {
                    detectDragGestures(
                        onDragStart = { position ->
                            updateLevel(position.toHuaweiAncLevel(size.width.toFloat(), size.height.toFloat()))
                        },
                        onDrag = { change, _ ->
                            updateLevel(change.position.toHuaweiAncLevel(size.width.toFloat(), size.height.toFloat()))
                        }
                    )
                }
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = min(size.width, size.height) * 0.34f
            val outerTickRadius = radius + 19.dp.toPx()
            val innerTickRadius = radius + 8.dp.toPx()
            val selectedTick = displayedLevel.toDialTick()

            drawCircle(
                color = diskColor,
                radius = radius * 1.08f,
                center = center
            )
            drawCircle(
                color = ringColor,
                radius = radius,
                center = center,
                style = Stroke(width = 1.5.dp.toPx())
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.18f),
                radius = radius * 0.72f,
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )

            repeat(HUAWEI_ANC_DIAL_TICKS) { tick ->
                val major = tick % HUAWEI_ANC_TICKS_PER_LEVEL == 0
                val highlighted = circularDistance(tick, selectedTick, HUAWEI_ANC_DIAL_TICKS) <= 2
                val angle = Math.toRadians(tick * HUAWEI_ANC_DIAL_TICK_DEGREES.toDouble())
                val start = center.pointOnCircle(if (major) innerTickRadius - 3.dp.toPx() else innerTickRadius, angle)
                val end = center.pointOnCircle(outerTickRadius, angle)
                drawLine(
                    color = if (highlighted) primary else tickColor,
                    start = start,
                    end = end,
                    strokeWidth = if (highlighted) 2.dp.toPx() else 1.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            val knobAngle = Math.toRadians(displayedLevel.toDialDegrees().toDouble())
            val knobCenter = center.pointOnCircle(radius * 0.86f, knobAngle)
            val knobRadius = if (compact) 9.dp.toPx() else 15.dp.toPx()
            drawCircle(
                color = primary.copy(alpha = 0.16f),
                radius = knobRadius * 1.35f,
                center = knobCenter
            )
            drawCircle(
                color = primary,
                radius = knobRadius,
                center = knobCenter
            )
        }
    }
}
@Composable
private fun AncStrengthTabRow(
    tabs: List<String>,
    selectedTabIndex: Int,
    assistHighlightedIndex: Int?,
    onTabSelected: (Int) -> Unit,
    compact: Boolean,
    minWidth: Dp,
    tabMaxWidth: Dp,
    height: Dp,
    itemSpacing: Dp,
    outerPadding: Dp,
    topPadding: Dp
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
            .padding(top = topPadding),
        contentAlignment = Alignment.Center
    ) {
        val rowModifier = Modifier
            .padding(horizontal = outerPadding)
            .fillMaxWidth(if (maxWidth >= 480.dp) 0.8f else 1f)

        TabRowWithContour(
            tabs = tabs.map { "" },
            selectedTabIndex = selectedTabIndex,
            onTabSelected = onTabSelected,
            modifier = rowModifier,
            minWidth = minWidth,
            maxWidth = tabMaxWidth,
            height = height,
            itemSpacing = itemSpacing
        )

        Row(
            modifier = rowModifier.height(height),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEachIndexed { index, tab ->
                val isSelected = index == selectedTabIndex
                val isAssistHighlighted = index == assistHighlightedIndex && !isSelected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(height),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tab,
                        fontSize = if (compact) 13.sp else 14.sp,
                        fontWeight = if (isSelected || isAssistHighlighted) FontWeight.SemiBold else FontWeight.Medium,
                        color = when {
                            isSelected -> MiuixTheme.colorScheme.primary
                            isAssistHighlighted -> MiuixTheme.colorScheme.primary.copy(alpha = 0.72f)
                            else -> MiuixTheme.colorScheme.onBackground
                        }
                    )
                }

                if (index != tabs.lastIndex) {
                    Spacer(modifier = Modifier.size(itemSpacing))
                }
            }
        }
    }
}

@Composable
private fun ResponsiveAncTabRow(
    tabs: List<String>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    compact: Boolean,
    minWidth: Dp,
    tabMaxWidth: Dp,
    height: Dp,
    itemSpacing: Dp,
    outerPadding: Dp,
    topPadding: Dp = if (compact) 8.dp else 16.dp
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = topPadding),
        contentAlignment = Alignment.Center
    ) {
        TabRowWithContour(
            tabs = tabs,
            selectedTabIndex = selectedTabIndex,
            onTabSelected = onTabSelected,
            modifier = Modifier
                .padding(horizontal = outerPadding)
                .fillMaxWidth(if (maxWidth >= 480.dp) 0.8f else 1f),
            minWidth = minWidth,
            maxWidth = tabMaxWidth,
            height = height,
            itemSpacing = itemSpacing
        )
    }
}

@Composable
private fun AncButton(
    offIconRes: Int,
    onIconRes: Int,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val boxSize = if (compact) 40.dp else 60.dp
    val iconSize = if (compact) (if (isSelected) 40.dp else 32.dp) else (if (isSelected) 60.dp else 48.dp)

    val textColor by animateColorAsState(
        targetValue = if (isSelected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onBackground,
        animationSpec = tween(ANIM_DURATION),
        label = "anc_text_color"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .pressable(interactionSource = interactionSource, indication = SinkFeedback())
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
    ) {
        Box(
            modifier = Modifier.size(boxSize),
            contentAlignment = Alignment.Center
        ) {
            Crossfade(
                targetState = isSelected,
                animationSpec = tween(ANIM_DURATION),
                label = "anc_icon"
            ) { selected ->
                Box(
                    modifier = Modifier.size(boxSize),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = themedPainterResource(if (selected) onIconRes else offIconRes),
                        contentDescription = label,
                        modifier = Modifier.size(if (selected) boxSize else (if (compact) 32.dp else 48.dp))
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            label,
            fontSize = if (compact) 12.sp else 14.sp,
            fontWeight = FontWeight.Medium,
            color = textColor
        )
    }
}

private const val HUAWEI_ANC_LEVEL_LAST = 8
private const val HUAWEI_ANC_DIAL_TICKS = 72
private const val HUAWEI_ANC_TICKS_PER_LEVEL = 8
private const val HUAWEI_ANC_DIAL_TICK_DEGREES = 5f
private const val HUAWEI_ANC_DIAL_START_DEGREES = 70f

private fun Int.toDialDegrees(): Float = HUAWEI_ANC_DIAL_START_DEGREES + (this * 360f / (HUAWEI_ANC_LEVEL_LAST + 1))

private fun Int.toDialTick(): Int = ((toDialDegrees() / HUAWEI_ANC_DIAL_TICK_DEGREES).roundToInt()) % HUAWEI_ANC_DIAL_TICKS

private fun Offset.toHuaweiAncLevel(width: Float, height: Float): Int {
    val dx = x - width / 2f
    val dy = y - height / 2f
    val degrees = ((atan2(dy, dx) * 180f / PI.toFloat()) + 360f) % 360f
    val normalized = (degrees - HUAWEI_ANC_DIAL_START_DEGREES + 360f) % 360f
    return ((normalized / (360f / (HUAWEI_ANC_LEVEL_LAST + 1))).roundToInt()) % (HUAWEI_ANC_LEVEL_LAST + 1)
}
private fun Offset.pointOnCircle(radius: Float, radians: Double): Offset {
    return Offset(
        x = x + cos(radians).toFloat() * radius,
        y = y + sin(radians).toFloat() * radius
    )
}

private fun circularDistance(a: Int, b: Int, modulo: Int): Int {
    val distance = abs(a - b)
    return min(distance, modulo - distance)
}
/**
 * Loads a drawable resource respecting the app's theme override.
 * Handles both bitmap and vector drawables by rendering via Canvas when needed.
 */
@Composable
@SuppressLint("LocalContextConfigurationRead")
private fun themedPainterResource(@androidx.annotation.DrawableRes id: Int): Painter {
    val context = LocalContext.current
    val themeConfig = LocalConfiguration.current
    val sysNightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
    val themeNightMode = themeConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK

    return if (sysNightMode == themeNightMode) {
        painterResource(id)
    } else {
        val themedResources = remember(context, themeNightMode) {
            context.createConfigurationContext(
                Configuration(context.resources.configuration).apply {
                    uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or themeNightMode
                }
            ).resources
        }
        remember(id, themeNightMode) {
            val drawable = themedResources.getDrawable(id, null)
            if (drawable is BitmapDrawable) {
                BitmapPainter(drawable.bitmap.asImageBitmap())
            } else {
                val bitmap = Bitmap.createBitmap(
                    drawable.intrinsicWidth.coerceAtLeast(1),
                    drawable.intrinsicHeight.coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888
                )
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                BitmapPainter(bitmap.asImageBitmap())
            }
        }
    }
}
