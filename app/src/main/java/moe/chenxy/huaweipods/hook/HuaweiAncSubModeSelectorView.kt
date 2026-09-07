package moe.chenxy.huaweipods.hook

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt

/** 系统设置与融合设备中心共用的选择器，支持多种外观模式。 */
internal class HuaweiAncSubModeSelectorView(
    context: Context,
    private val onSelected: (Int) -> Unit,
) : LinearLayout(context) {
    internal var onSelectedWithAnchor: ((Int, View) -> Unit)? = null

    enum class Appearance {
        /** 紧凑分段文字按钮（融合设备中心等） */
        MODULE,
    }

    data class Option(
        val value: Int,
        val label: String,
        val reselectable: Boolean = false,
    )

    init {
        orientation = VERTICAL
    }

    /**
     * 主渲染入口。
     * 当 [appearance] 为 [Appearance.XIAOMI_ICON] 时渲染圆形图标风格。
     */
    fun render(
        options: List<Option>,
        selectedValue: Int,
        darkSurface: Boolean,
        appearance: Appearance = Appearance.MODULE,
        accentColor: Int? = null,
        horizontallyScrollable: Boolean = false,
    ) {
        removeAllViews()
        if (options.isEmpty()) return

        // 分段文字按钮样式
        setPadding(context.dp(5), context.dp(3), context.dp(5), context.dp(3))
        val resolvedAccent = accentColor ?: resolveAccentColor()
        val optionRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(context.dp(3), context.dp(3), context.dp(3), context.dp(3))
            background = roundedBackground(
                selectorBackgroundColor(darkSurface, appearance),
                16,
            )

            options.forEachIndexed { index, option ->
                addView(
                    optionView(
                        option = option,
                        selected = option.value == selectedValue,
                        darkSurface = darkSurface,
                        appearance = appearance,
                        accent = resolvedAccent,
                        horizontallyScrollable = horizontallyScrollable,
                    ),
                    if (horizontallyScrollable) {
                        LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                    } else {
                        LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                    }.apply {
                        if (index > 0) marginStart = context.dp(3)
                    },
                )
            }
        }
        if (horizontallyScrollable) {
            addView(
                HorizontalScrollView(context).apply {
                    isFillViewport = true
                    isHorizontalScrollBarEnabled = false
                    overScrollMode = OVER_SCROLL_NEVER
                    addView(
                        optionRow,
                        ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        ),
                    )
                },
                LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, context.dp(42)),
            )
        } else {
            addView(
                optionRow,
                LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, context.dp(42)),
            )
        }
    }

    // ── 分段文字按钮 ──────────────────────────────────────

    private fun optionView(
        option: Option,
        selected: Boolean,
        darkSurface: Boolean,
        appearance: Appearance,
        accent: Int,
        horizontallyScrollable: Boolean,
    ): TextView = TextView(context).apply {
        text = option.label
        gravity = Gravity.CENTER
        isClickable = true
        isFocusable = true
        isSelected = selected
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        contentDescription = option.label
        if (horizontallyScrollable) {
            minWidth = context.dp(76)
            setPadding(context.dp(14), 0, context.dp(14), 0)
        }
        if (selected) {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        setTextColor(
            optionTextColor(selected, darkSurface, appearance, accent),
        )
        setAutoSizeTextTypeUniformWithConfiguration(
            9,
            12,
            1,
            TypedValue.COMPLEX_UNIT_SP,
        )
        background = segmentBackground(selected, darkSurface, appearance, accent)
        setOnClickListener {
            if (!selected || option.reselectable) {
                val anchoredCallback = onSelectedWithAnchor
                if (anchoredCallback != null) {
                    anchoredCallback(option.value, this)
                } else {
                    onSelected(option.value)
                }
            }
        }
    }

    private fun segmentBackground(
        selected: Boolean,
        darkSurface: Boolean,
        appearance: Appearance,
        accent: Int,
    ): RippleDrawable {
        val fill = if (selected) accent else Color.TRANSPARENT
        val ripple = if (selected) {
            Color.argb(40, 255, 255, 255)
        } else {
            Color.argb(30, 255, 255, 255)
        }
        return RippleDrawable(
            ColorStateList.valueOf(ripple),
            roundedBackground(fill, 16),
            null,
        )
    }

    private fun selectorBackgroundColor(
        darkSurface: Boolean,
        appearance: Appearance,
    ): Int = if (darkSurface) Color.argb(120, 51, 51, 51) else Color.argb(200, 240, 240, 240)

    private fun roundedBackground(color: Int, radiusDp: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = context.dp(radiusDp).toFloat()
    }

    private fun resolveAccentColor(): Int {
        val attributes = context.obtainStyledAttributes(intArrayOf(android.R.attr.colorAccent))
        return try {
            attributes.getColor(0, MIUIX_PRIMARY)
        } finally {
            attributes.recycle()
        }
    }

    private fun optionTextColor(
        selected: Boolean,
        darkSurface: Boolean,
        appearance: Appearance,
        accent: Int,
    ): Int {
        if (selected) return Color.WHITE
        return if (darkSurface) MIUIX_TEXT_DARK else MIUIX_TEXT_LIGHT
    }

    private fun Context.dp(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()

    private companion object {
        val MIUIX_PRIMARY = Color.rgb(0x34, 0x82, 0xFF)
        val MIUIX_TEXT_LIGHT = Color.rgb(0x30, 0x30, 0x30)
        val MIUIX_TEXT_DARK = Color.rgb(0xE0, 0xE0, 0xE0)
    }
}
