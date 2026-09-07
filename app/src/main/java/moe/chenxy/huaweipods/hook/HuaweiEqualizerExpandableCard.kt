package moe.chenxy.huaweipods.hook

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt

/**
 * 可展开/收起的音效设置卡片。
 *
 * 折叠状态：标题行 "音效设置 · 当前预设名" + 箭头图标
 * 展开状态：标题行 + 圆形图标选项行（XIAOMI_ICON 风格）
 * 选择选项后自动折叠。
 */
internal class HuaweiEqualizerExpandableCard(
    context: Context,
    private val onPresetSelected: (Int) -> Unit,
) : LinearLayout(context) {

    data class PresetOption(
        val id: Int,
        val label: String,
    )

    private var isExpanded = false
    private var selectedId: Int = -1
    private var darkSurface = false
    private var options: List<PresetOption> = emptyList()

    private val headerRow: LinearLayout
    private val titleText: TextView
    private val subtitleText: TextView
    private val arrowView: TextView
    private val expandContainer: LinearLayout

    private companion object {
        val MIUIX_PRIMARY = Color.rgb(0x34, 0x82, 0xFF)
        val MIUIX_TEXT_LIGHT = Color.rgb(0x30, 0x30, 0x30)
        val MIUIX_TEXT_DARK = Color.rgb(0xE0, 0xE0, 0xE0)
        val MIUIX_SURFACE_LIGHT = Color.rgb(0xF0, 0xF0, 0xF0)
        val MIUIX_SURFACE_DARK = Color.rgb(0x30, 0x30, 0x30)
    }

    init {
        orientation = VERTICAL
        background = GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
        }

        headerRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            setPadding(dp(18), dp(14), dp(14), dp(14))
            setOnClickListener { toggleExpand() }
        }

        titleText = TextView(context).apply {
            text = "音效设置"
            textSize = 15f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        subtitleText = TextView(context).apply {
            text = "选择耳机音效"
            textSize = 12f
            setPadding(0, dp(3), 0, 0)
        }
        val titleColumn = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }
        titleColumn.addView(titleText)
        titleColumn.addView(subtitleText)
        headerRow.addView(titleColumn)

        arrowView = TextView(context).apply {
            text = ">"
            textSize = 18f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(30), dp(30))
        }
        headerRow.addView(arrowView)

        addView(headerRow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        // Expandable container (initially hidden)
        expandContainer = LinearLayout(context).apply {
            orientation = VERTICAL
            visibility = GONE
            setPadding(dp(8), 0, dp(8), dp(8))
        }
        addView(expandContainer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    fun configure(
        presets: List<PresetOption>,
        selectedPresetId: Int,
        dark: Boolean,
        currentTitle: String = "音效设置",
    ) {
        options = presets
        selectedId = selectedPresetId
        darkSurface = dark
        titleText.text = currentTitle
        subtitleText.text = options.firstOrNull { it.id == selectedId }?.label
            ?: "选择耳机音效"
        titleText.setTextColor(if (dark) MIUIX_TEXT_DARK else MIUIX_TEXT_LIGHT)
        subtitleText.setTextColor(if (dark) Color.rgb(0xAA, 0xAA, 0xAA) else Color.rgb(0x88, 0x88, 0x88))
        arrowView.setTextColor(if (dark) MIUIX_TEXT_DARK else Color.rgb(0xAA, 0xAA, 0xAA))
        background = roundedBackground(if (dark) MIUIX_SURFACE_DARK else MIUIX_SURFACE_LIGHT, 16)
        rebuildOptions()
    }

    fun setSelected(presetId: Int, title: String? = null) {
        selectedId = presetId
        if (title != null) titleText.text = title
        rebuildOptions()
        if (isExpanded) collapse()
    }

    private fun toggleExpand() {
        if (isExpanded) collapse() else expand()
    }

    private fun expand() {
        isExpanded = true
        expandContainer.visibility = VISIBLE
        expandContainer.alpha = 0f
        expandContainer.translationY = -dp(8).toFloat()
        expandContainer.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(200)
            .setInterpolator(DecelerateInterpolator())
            .start()
        arrowView.animate().rotation(90f).setDuration(200).start()
    }

    private fun collapse() {
        isExpanded = false
        expandContainer.animate()
            .alpha(0f)
            .translationY(-dp(8).toFloat())
            .setDuration(150)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction { expandContainer.visibility = GONE }
            .start()
        arrowView.animate().rotation(0f).setDuration(150).start()
    }

    private fun rebuildOptions() {
        expandContainer.removeAllViews()
        if (options.isEmpty()) return

        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val weight = 1f / options.size.coerceAtLeast(1)

        options.forEach { option ->
            val selected = option.id == selectedId
            val itemView = createOptionItem(option, selected)
            row.addView(itemView, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, weight))
        }
        expandContainer.addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    private fun createOptionItem(option: PresetOption, selected: Boolean): View {
        val wrapper = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            isClickable = true
            isFocusable = true
            setPadding(dp(4), dp(4), dp(4), dp(4))
            setOnClickListener {
                if (!selected) {
                    selectedId = option.id
                    onPresetSelected(option.id)
                    subtitleText.text = option.label
                    collapse()
                }
            }
        }

        // Circle icon
        val iconSize = dp(48)
        val iconView = TextView(context).apply {
            text = option.label.first().toString()
            gravity = Gravity.CENTER
            textSize = 18f
            isClickable = false
            isFocusable = false
            val circle = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                if (selected) {
                    setColor(MIUIX_PRIMARY)
                } else {
                    setColor(if (darkSurface) MIUIX_SURFACE_DARK else MIUIX_SURFACE_LIGHT)
                }
            }
            background = circle
            setTextColor(if (selected) Color.WHITE else if (darkSurface) MIUIX_TEXT_DARK else MIUIX_TEXT_LIGHT)
            layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(4)
            }
        }
        wrapper.addView(iconView)

        // Label
        val labelView = TextView(context).apply {
            text = option.label
            gravity = Gravity.CENTER
            textSize = 11f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            if (selected) {
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                setTextColor(MIUIX_PRIMARY)
            } else {
                setTextColor(if (darkSurface) MIUIX_TEXT_DARK else MIUIX_TEXT_LIGHT)
            }
            layoutParams = LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        }
        wrapper.addView(labelView)

        return wrapper
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()

    private fun roundedBackground(color: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
    }
}
