package moe.chenxy.huaweipods.hook

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import kotlin.math.abs
import kotlin.math.roundToInt

internal class HuaweiAncLevelSliderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    data class LevelEntry(
        val protocolValue: Int,
        val label: String,
    )

    private var entries: List<LevelEntry> = emptyList()
    private var selectedIndex: Int = 0
    private var onLevelSelected: ((Int) -> Unit)? = null

    private val trackView = SliderTrackView(context)

    init {
        orientation = VERTICAL
        setPadding(dp(20), dp(8), dp(20), dp(12))
    }

    fun configure(
        levels: List<LevelEntry>,
        currentProtocolValue: Int,
        darkSurface: Boolean,
        onSelected: (Int) -> Unit,
    ) {
        entries = levels
        onLevelSelected = onSelected
        selectedIndex = levels.indexOfFirst { it.protocolValue == currentProtocolValue }
            .takeIf { it >= 0 }
            ?: -1
        trackView.configure(
            stopCount = levels.size,
            selectedIndex = selectedIndex,
            darkSurface = darkSurface,
            labels = levels.map { it.label },
        ) { index ->
            selectedIndex = index
            trackView.setSelectedIndex(index)
            onLevelSelected?.invoke(levels[index].protocolValue)
        }
        // HyperOS may rebind the native ANC row after this view is inserted. Keep the labels above that row.
        (parent as? ViewGroup)?.apply {
            clipChildren = false
            clipToPadding = false
        }
        bringToFront()
        elevation = dp(1).toFloat()
        trackView.invalidate()
        invalidate()
    }

    fun setSelected(protocolValue: Int) {
        val index = entries.indexOfFirst { it.protocolValue == protocolValue }
        if (index < 0 || index == selectedIndex) return
        selectedIndex = index
        trackView.setSelectedIndex(index)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()

    private class SliderTrackView(context: Context) : View(context) {
        private var stopCount = 0
        private var selectedIndex = 0
        private var darkSurface = false
        private var onThumbTap: ((Int) -> Unit)? = null

        private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        private val activeTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = MIUIX_PRIMARY
        }
        private val stopPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        private val activeStopPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = MIUIX_PRIMARY
        }
        private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = MIUIX_PRIMARY
        }
        private val thumbHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        private var labels: List<String> = emptyList()

        private val trackHeight = dp(4)
        private val stopRadius = dp(3)
        private val thumbRadius = dp(10)
        private val thumbHaloRadius = dp(14)
        private val hitSlop = dp(24)

        private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 12f * resources.displayMetrics.scaledDensity
            textAlign = Paint.Align.CENTER
        }
        private val activeLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 12f * resources.displayMetrics.scaledDensity
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        fun configure(
            stopCount: Int,
            selectedIndex: Int,
            darkSurface: Boolean,
            labels: List<String> = emptyList(),
            onThumbTap: (Int) -> Unit,
        ) {
            this.stopCount = stopCount
            this.selectedIndex = selectedIndex
            this.darkSurface = darkSurface
            this.labels = labels
            this.onThumbTap = onThumbTap

            trackPaint.color = if (darkSurface) Color.argb(40, 255, 255, 255) else Color.rgb(0xE8, 0xE8, 0xE8)
            stopPaint.color = if (darkSurface) Color.argb(60, 255, 255, 255) else Color.rgb(0xD0, 0xD0, 0xD0)
            thumbHaloPaint.color = Color.argb(30, 0x34, 0x82, 0xFF)
            labelPaint.color = if (darkSurface) Color.rgb(0xAA, 0xAA, 0xAA) else Color.rgb(0x99, 0x99, 0x99)
            activeLabelPaint.color = MIUIX_PRIMARY

            minimumHeight = dp(72)
            requestLayout()
        }

        fun setSelectedIndex(index: Int) {
            if (index == selectedIndex) return
            selectedIndex = index
            invalidate()
        }

        fun isDarkSurface() = darkSurface

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val w = MeasureSpec.getSize(widthMeasureSpec)
            val labelH = if (labels.isNotEmpty()) dp(30) else 0
            val h = resolveSize(dp(42) + labelH, heightMeasureSpec)
            setMeasuredDimension(w, h)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (stopCount < 2) return
            val w = width.toFloat()
            val h = height.toFloat()
            val labelH = if (labels.isNotEmpty()) dp(30).toFloat() else 0f
            val trackY = (h - labelH) * 0.42f
            val padL = paddingLeft.toFloat()
            val padR = paddingRight.toFloat()
            val trackW = w - padL - padR

            val trackRect = RectF(padL, trackY - trackHeight / 2f, padL + trackW, trackY + trackHeight / 2f)
            canvas.drawRoundRect(trackRect, trackHeight / 2f, trackHeight / 2f, trackPaint)

            if (selectedIndex > 0) {
                val thumbX = padL + trackW * selectedIndex / (stopCount - 1)
                val activeRect = RectF(padL, trackY - trackHeight / 2f, thumbX, trackY + trackHeight / 2f)
                canvas.drawRoundRect(activeRect, trackHeight / 2f, trackHeight / 2f, activeTrackPaint)
            }

            for (i in 0 until stopCount) {
                val x = padL + trackW * i / (stopCount - 1)
                canvas.drawCircle(x, trackY, stopRadius.toFloat(), if (i == selectedIndex) activeStopPaint else stopPaint)
            }

            if (selectedIndex >= 0) {
                val thumbX = padL + trackW * selectedIndex / (stopCount - 1)
                canvas.drawCircle(thumbX, trackY, thumbHaloRadius.toFloat(), thumbHaloPaint)
                canvas.drawCircle(thumbX, trackY, thumbRadius.toFloat(), thumbPaint)
            }

            if (labels.isNotEmpty()) {
                val labelY = h - dp(7).toFloat()
                for (i in 0 until stopCount.coerceAtMost(labels.size)) {
                    val paint = if (i == selectedIndex) activeLabelPaint else labelPaint
                    val rawX = padL + trackW * i / (stopCount - 1)
                    val halfTextWidth = paint.measureText(labels[i]) / 2f
                    val x = rawX.coerceIn(padL + halfTextWidth, padL + trackW - halfTextWidth)
                    canvas.drawText(labels[i], x, labelY, paint)
                }
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    val index = hitTest(event.x, event.y)
                    if (index >= 0 && index != selectedIndex) {
                        selectedIndex = index
                        invalidate()
                        onThumbTap?.invoke(index)
                    }
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    parent?.requestDisallowInterceptTouchEvent(false)
                    return true
                }
            }
            return super.onTouchEvent(event)
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }

        private fun hitTest(x: Float, y: Float): Int {
            if (stopCount < 2) return -1
            val padL = paddingLeft.toFloat()
            val padR = paddingRight.toFloat()
            val trackW = (width.toFloat() - padL - padR).coerceAtLeast(1f)
            val labelH = if (labels.isNotEmpty()) dp(30).toFloat() else 0f
            val trackY = (height.toFloat() - labelH) * 0.42f
            if (abs(y - trackY) > hitSlop) return -1
            var bestIndex = -1
            var bestDist = Float.MAX_VALUE
            for (i in 0 until stopCount) {
                val stopX = padL + trackW * i / (stopCount - 1)
                val dist = abs(x - stopX)
                if (dist < bestDist) {
                    bestDist = dist
                    bestIndex = i
                }
            }
            return if (bestDist <= hitSlop) bestIndex else -1
        }

        private fun dp(value: Int): Int =
            (value * resources.displayMetrics.density).roundToInt()
    }

    private companion object {
        val MIUIX_PRIMARY = Color.rgb(0x34, 0x82, 0xFF)
        val MIUIX_TEXT_LIGHT = Color.rgb(0x30, 0x30, 0x30)
        val MIUIX_TEXT_DARK = Color.rgb(0xE0, 0xE0, 0xE0)
    }
}