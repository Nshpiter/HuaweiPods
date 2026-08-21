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
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt

/** 系统设置与融合设备中心共用的紧凑分段选择器。 */
internal class HuaweiAncSubModeSelectorView(
    context: Context,
    private val onSelected: (Int) -> Unit,
) : LinearLayout(context) {
    enum class Appearance {
        MODULE,
        HOST_GLASS,
    }

    data class Option(
        val value: Int,
        val label: String,
    )

    init {
        orientation = VERTICAL
    }

    fun render(
        options: List<Option>,
        selectedValue: Int,
        darkSurface: Boolean,
        appearance: Appearance = Appearance.MODULE,
        accentColor: Int? = null,
    ) {
        removeAllViews()
        if (options.isEmpty()) return
        setPadding(context.dp(5), context.dp(3), context.dp(5), context.dp(3))
        val resolvedAccent = accentColor ?: resolveAccentColor()
        addView(
            LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(context.dp(3), context.dp(3), context.dp(3), context.dp(3))
                background = roundedBackground(
                    selectorBackgroundColor(darkSurface, appearance),
                    if (appearance == Appearance.HOST_GLASS) 18 else 13,
                )

                options.forEachIndexed { index, option ->
                    addView(
                        optionView(
                            option = option,
                            selected = option.value == selectedValue,
                            darkSurface = darkSurface,
                            appearance = appearance,
                            accent = resolvedAccent,
                        ),
                        LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                            if (index > 0) marginStart = context.dp(3)
                        },
                    )
                }
            },
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, context.dp(42)),
        )
    }

    private fun optionView(
        option: Option,
        selected: Boolean,
        darkSurface: Boolean,
        appearance: Appearance,
        accent: Int,
    ): TextView = TextView(context).apply {
        text = option.label
        gravity = Gravity.CENTER
        isClickable = true
        isFocusable = true
        isSelected = selected
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        contentDescription = option.label
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
            if (!selected) onSelected(option.value)
        }
    }

    private fun segmentBackground(
        selected: Boolean,
        darkSurface: Boolean,
        appearance: Appearance,
        accent: Int,
    ): RippleDrawable {
        val fill = when {
            !selected && darkSurface -> Color.argb(6, 255, 255, 255)
            !selected -> Color.TRANSPARENT
            appearance == Appearance.HOST_GLASS && darkSurface ->
                Color.argb(232, 248, 249, 252)
            appearance == Appearance.HOST_GLASS ->
                Color.argb(28, Color.red(accent), Color.green(accent), Color.blue(accent))
            else -> accent
        }
        val ripple = if (selected) {
            Color.argb(40, 255, 255, 255)
        } else {
            Color.argb(34, Color.red(accent), Color.green(accent), Color.blue(accent))
        }
        return RippleDrawable(
            ColorStateList.valueOf(ripple),
            roundedBackground(fill, if (appearance == Appearance.HOST_GLASS) 21 else 10),
            null,
        )
    }

    private fun selectorBackgroundColor(
        darkSurface: Boolean,
        appearance: Appearance,
    ): Int = when {
        appearance == Appearance.HOST_GLASS -> Color.TRANSPARENT
        darkSurface -> Color.argb(42, 255, 255, 255)
        else -> Color.argb(18, 35, 49, 75)
    }

    private fun roundedBackground(color: Int, radiusDp: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = context.dp(radiusDp).toFloat()
    }

    private fun resolveAccentColor(): Int {
        val attributes = context.obtainStyledAttributes(intArrayOf(android.R.attr.colorAccent))
        return try {
            attributes.getColor(0, Color.rgb(33, 150, 243))
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
        if (selected && appearance == Appearance.HOST_GLASS) return accent
        if (selected) return Color.WHITE
        return if (darkSurface) Color.rgb(218, 221, 229) else Color.rgb(93, 101, 116)
    }

    private fun Context.dp(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()
}
