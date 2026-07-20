package moe.chenxy.huaweipods.ui.components

import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import moe.chenxy.huaweipods.pods.NoiseControlMode
import moe.chenxy.huaweipods.pods.isNoiseCancellation
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
        HuaweiAncSimpleHeader(
            enabled = ancStatus.isNoiseCancellation(),
            onToggle = {
                onAncModeChange(
                    if (ancStatus.isNoiseCancellation()) NoiseControlMode.OFF else NoiseControlMode.NOISE_CANCELLATION
                )
            },
            compact = compact
        )

        if (ancStatus.isNoiseCancellation() && onHuaweiAncLevelChange != null) {
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
