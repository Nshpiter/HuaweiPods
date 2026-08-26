package moe.chenxy.huaweipods.ui.components

import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import top.yukonga.miuix.kmp.utils.SinkFeedback
import top.yukonga.miuix.kmp.utils.pressable
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
        if (deviceRoute.supportsTransparency) {
            HuaweiNoiseControlSelector(
                selectedMode = ancStatus,
                onModeChange = onAncModeChange,
                compact = compact,
            )
        } else {
            HuaweiAncSimpleHeader(
                enabled = ancStatus.isNoiseCancellation(),
                onToggle = {
                    onAncModeChange(
                        if (ancStatus.isNoiseCancellation()) {
                            NoiseControlMode.OFF
                        } else {
                            NoiseControlMode.NOISE_CANCELLATION
                        },
                    )
                },
                compact = compact,
            )
        }

        if (
            ancStatus.isNoiseCancellation() &&
            onHuaweiAncLevelChange != null &&
            deviceRoute.supportsDiscreteAncLevels
        ) {
            HuaweiAncSubModeSelector(
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
                modifier = Modifier.padding(top = if (compact) 8.dp else 14.dp),
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
            HuaweiAncSubModeSelector(
                title = stringResource(R.string.transparency_level_title),
                values = values,
                selectedValue = huaweiAncLevel,
                onValueChange = onHuaweiAncLevelChange,
                compact = compact,
                modifier = Modifier.padding(top = if (compact) 8.dp else 14.dp),
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
                modifier = Modifier.padding(top = if (compact) 8.dp else 14.dp)
            )
        }
    }
}

@Composable
private fun HuaweiNoiseControlSelector(
    selectedMode: NoiseControlMode,
    onModeChange: (NoiseControlMode) -> Unit,
    compact: Boolean,
) {
    Text(
        text = stringResource(R.string.noise_control_title),
        fontSize = if (compact) 14.sp else 16.sp,
        fontWeight = FontWeight.SemiBold,
        color = MiuixTheme.colorScheme.onBackground,
        modifier = Modifier.padding(
            horizontal = if (compact) 10.dp else 14.dp,
            vertical = if (compact) 2.dp else 4.dp,
        ),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = if (compact) 8.dp else 12.dp,
                vertical = if (compact) 6.dp else 10.dp,
            ),
        horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp),
    ) {
        listOf(
            NoiseControlMode.TRANSPARENCY to R.string.transparency_mode,
            NoiseControlMode.NOISE_CANCELLATION to R.string.noise_cancellation_title,
            NoiseControlMode.OFF to R.string.off,
        ).forEach { (mode, labelRes) ->
            HuaweiAncChoice(
                label = stringResource(labelRes),
                selected = selectedMode == mode,
                onClick = { onModeChange(mode) },
                compact = compact,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun HuaweiAncSubModeSelector(
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = if (compact) 8.dp else 12.dp,
                        end = if (compact) 8.dp else 12.dp,
                        top = if (rowIndex == 0) 4.dp else 6.dp,
                    ),
                horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp),
            ) {
                rowValues.forEach { (value, label) ->
                    HuaweiAncChoice(
                        label = label,
                        selected = selectedValue == value,
                        onClick = { onValueChange(value) },
                        compact = compact,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun HuaweiAncChoice(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val primary = MiuixTheme.colorScheme.primary
    Box(
        modifier = modifier
            .background(
                color = if (selected) {
                    primary
                } else {
                    MiuixTheme.colorScheme.onBackground.copy(alpha = 0.07f)
                },
                shape = RoundedCornerShape(if (compact) 9.dp else 11.dp),
            )
            .pressable(interactionSource = interactionSource, indication = SinkFeedback())
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(
                horizontal = if (compact) 6.dp else 10.dp,
                vertical = if (compact) 8.dp else 10.dp,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = if (compact) 12.sp else 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) Color.White else MiuixTheme.colorScheme.onBackground,
        )
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
