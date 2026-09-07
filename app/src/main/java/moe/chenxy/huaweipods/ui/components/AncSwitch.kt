package moe.chenxy.huaweipods.ui.components

import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import moe.chenxy.huaweipods.R
import moe.chenxy.huaweipods.pods.HuaweiAncLevel
import moe.chenxy.huaweipods.pods.HuaweiDeviceRoute
import moe.chenxy.huaweipods.pods.NoiseControlMode
import moe.chenxy.huaweipods.pods.ancLevelOptions
import moe.chenxy.huaweipods.pods.isNoiseCancellation
import moe.chenxy.huaweipods.pods.supportsAncDirectionDial
import moe.chenxy.huaweipods.pods.supportsDiscreteAncLevels
import moe.chenxy.huaweipods.pods.supportsTransparency
import moe.chenxy.huaweipods.pods.defaultTransparencySubMode
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun AncSwitch(
    ancStatus: NoiseControlMode,
    onAncModeChange: (NoiseControlMode) -> Unit,
    deviceRoute: HuaweiDeviceRoute = HuaweiDeviceRoute.HUAWEI_FREEBUDS3,
    compact: Boolean = false,
    huaweiAncLevel: Int = 0,
    onHuaweiAncLevelChange: ((Int) -> Unit)? = null,
) {
    val verticalPadding = if (compact) 8.dp else 16.dp
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = verticalPadding)
    ) {
        HyperOSNoiseModeSelector(
            selectedMode = ancStatus,
            onModeChange = onAncModeChange,
            supportsTransparency = deviceRoute.supportsTransparency,
            compact = compact,
        )

        if (
            ancStatus.isNoiseCancellation() &&
            onHuaweiAncLevelChange != null &&
            deviceRoute.supportsDiscreteAncLevels
        ) {
            HyperOSSubModeSelector(
                title = stringResource(R.string.anc_level_title),
                values = deviceRoute.ancLevelOptions.map { option ->
                    val label = when (option.level) {
                        HuaweiAncLevel.ADAPTIVE -> stringResource(
                            if (deviceRoute == HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO5) {
                                R.string.freebuds_pro5_anc_level_adaptive
                            } else {
                                R.string.anc_level_adaptive
                            },
                        )
                        HuaweiAncLevel.LIGHT -> stringResource(R.string.anc_level_light)
                        HuaweiAncLevel.BALANCED -> stringResource(R.string.anc_level_balanced)
                        HuaweiAncLevel.DEEP -> stringResource(R.string.anc_level_deep)
                    }
                    option.protocolValue to label
                },
                selectedValue = huaweiAncLevel,
                onValueChange = onHuaweiAncLevelChange,
                compact = compact,
                modifier = Modifier.padding(top = if (compact) 10.dp else 16.dp),
            )
        } else if (
            ancStatus == NoiseControlMode.TRANSPARENCY &&
            onHuaweiAncLevelChange != null &&
            deviceRoute.supportsTransparency
        ) {
            val standardValue = deviceRoute.defaultTransparencySubMode ?: 0xFF
            val standard = standardValue to stringResource(R.string.transparency_standard)
            val voice = 0x01 to stringResource(R.string.transparency_voice)
            val values = if (deviceRoute == HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO5) {
                listOf(
                    standard,
                    voice,
                    0x04 to stringResource(R.string.transparency_adaptive),
                )
            } else {
                listOf(standard, voice)
            }
            HyperOSSubModeSelector(
                title = stringResource(R.string.transparency_level_title),
                values = values,
                selectedValue = huaweiAncLevel,
                onValueChange = onHuaweiAncLevelChange,
                compact = compact,
                modifier = Modifier.padding(top = if (compact) 10.dp else 16.dp),
            )
        } else if (
            ancStatus.isNoiseCancellation() &&
            onHuaweiAncLevelChange != null &&
            deviceRoute.supportsAncDirectionDial
        ) {
            HuaweiAncLevelDial(
                level = huaweiAncLevel.coerceIn(0, 8),
                onLevelChange = onHuaweiAncLevelChange,
                compact = compact,
                modifier = Modifier.padding(top = if (compact) 10.dp else 16.dp)
            )
        }
    }
}

@Composable
private fun HyperOSNoiseModeSelector(
    selectedMode: NoiseControlMode,
    onModeChange: (NoiseControlMode) -> Unit,
    supportsTransparency: Boolean,
    compact: Boolean,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.noise_control_title),
            fontSize = if (compact) 13.sp else 15.sp,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = if (compact) 10.dp else 14.dp),
        )
        val modes = buildList {
            if (supportsTransparency) {
                add(NoiseControlMode.TRANSPARENCY to R.string.transparency_mode)
            }
            add(NoiseControlMode.NOISE_CANCELLATION to R.string.noise_cancellation_title)
            add(NoiseControlMode.OFF to R.string.off)
        }
        HyperOSSegmentedButton(
            labels = modes.map { stringResource(it.second) },
            selectedIndex = modes.indexOfFirst { it.first == selectedMode }.coerceAtLeast(0),
            onIndexChange = { idx -> onModeChange(modes[idx].first) },
            compact = compact,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = if (compact) 8.dp else 12.dp,
                    end = if (compact) 8.dp else 12.dp,
                    top = if (compact) 6.dp else 10.dp,
                ),
        )
    }
}

@Composable
private fun HyperOSSubModeSelector(
    title: String,
    values: List<Pair<Int, String>>,
    selectedValue: Int,
    onValueChange: (Int) -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            fontSize = if (compact) 13.sp else 15.sp,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = if (compact) 10.dp else 14.dp),
        )
        values.chunked(2).forEachIndexed { rowIndex, rowValues ->
            HyperOSSegmentedButton(
                labels = rowValues.map { it.second },
                selectedIndex = rowValues.indexOfFirst { it.first == selectedValue },
                onIndexChange = { idx -> onValueChange(rowValues[idx].first) },
                compact = compact,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = if (compact) 8.dp else 12.dp,
                        end = if (compact) 8.dp else 12.dp,
                        top = if (rowIndex == 0) 6.dp else 10.dp,
                    ),
            )
        }
    }
}

@Composable
private fun HyperOSSegmentedButton(
    labels: List<String>,
    selectedIndex: Int,
    onIndexChange: (Int) -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val count = labels.size.coerceAtLeast(1)
    val safeIdx = if (selectedIndex < 0) -1 else selectedIndex.coerceIn(0, count - 1)
    val containerHeight = if (compact) 32.dp else 38.dp
    val outerPad = if (compact) 4.dp else 5.dp
    val innerCornerRadius = ((containerHeight - outerPad * 2) / 2).value
    val primaryColor = MiuixTheme.colorScheme.primary
    val containerBg = MiuixTheme.colorScheme.onBackground.copy(alpha = if (compact) 0.06f else 0.08f)
    val borderColor = MiuixTheme.colorScheme.outline.copy(alpha = 0.12f)

    val indicatorFraction by animateFloatAsState(
        targetValue = if (safeIdx < 0) 0f else (safeIdx + 0.5f) / count,
        animationSpec = spring(stiffness = 600f),
    )

    Box(
        modifier = modifier
            .height(containerHeight)
            .clip(RoundedCornerShape(containerHeight / 2))
            .background(containerBg)
            .drawBehind {
                // 外边线
                drawRoundRect(
                    color = borderColor,
                    cornerRadius = CornerRadius((containerHeight / 2).value * density),
                    style = Stroke(width = 0.5.dp.toPx()),
                )
                if (safeIdx >= 0) {
                    // 指示器 + 伪阴影全部在绘制阶段完成，动画零重组
                    val outerPadPx = outerPad.toPx()
                    val totalW = size.width - outerPadPx * 2
                    val indW = totalW / count
                    val indCenterX = outerPadPx + totalW * indicatorFraction
                    val indLeft = indCenterX - indW / 2
                    val indTop = outerPadPx
                    val indH = size.height - outerPadPx * 2
                    val crPx = innerCornerRadius * density

                    // 指示器伪阴影（两层偏移圆角色块）
                    drawRoundRect(
                        color = Color.Black.copy(alpha = 0.06f),
                        topLeft = Offset(indLeft, indTop + 1.5.dp.toPx()),
                        size = Size(indW, indH),
                        cornerRadius = CornerRadius(crPx),
                    )
                    // 指示器主体
                    drawRoundRect(
                        color = primaryColor,
                        topLeft = Offset(indLeft, indTop),
                        size = Size(indW, indH),
                        cornerRadius = CornerRadius(crPx),
                    )
                }
            },
    ) {
        Row(Modifier.matchParentSize().padding(horizontal = outerPad)) {
            labels.forEachIndexed { idx, label ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onIndexChange(idx) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        fontSize = if (compact) 12.sp else 14.sp,
                        fontWeight = if (idx == safeIdx) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (idx == safeIdx) Color.White
                            else MiuixTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    )
                }
            }
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