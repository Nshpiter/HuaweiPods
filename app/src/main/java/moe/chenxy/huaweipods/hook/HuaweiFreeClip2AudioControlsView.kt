package moe.chenxy.huaweipods.hook

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import moe.chenxy.huaweipods.R
import moe.chenxy.huaweipods.pods.FreeClip2SoundEffect
import moe.chenxy.huaweipods.pods.FreeClip2SpatialAudioMode
import moe.chenxy.huaweipods.pods.FreeClip2SpatialScene
import kotlin.math.roundToInt

/** FreeClip 2 在系统宿主页面中使用的紧凑音频控制区。 */
internal class HuaweiFreeClip2AudioControlsView(
    context: Context,
    private val onSpatialModeSelected: (FreeClip2SpatialAudioMode) -> Unit,
    private val onSpatialSceneSelected: (FreeClip2SpatialScene) -> Unit,
    private val onSoundEffectSelected: (FreeClip2SoundEffect) -> Unit,
    private val onBuiltInSoundEffectSelected: (Int) -> Unit = {},
) : LinearLayout(context) {
    /** 仅复制文字外观，不复制宿主 View 的尺寸和间距。 */
    internal data class SectionTitleStyle(
        val textSizePx: Float,
        val typeface: Typeface?,
        val textColor: Int,
        val includeFontPadding: Boolean,
        val letterSpacing: Float,
        val textScaleX: Float,
        val paintFlags: Int,
        val lineSpacingExtra: Float,
        val lineSpacingMultiplier: Float,
    ) {
        fun applyTo(target: TextView) {
            target.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizePx)
            target.typeface = typeface
            target.setTextColor(textColor)
            target.includeFontPadding = includeFontPadding
            target.letterSpacing = letterSpacing
            target.textScaleX = textScaleX
            target.paintFlags = paintFlags
            target.setLineSpacing(lineSpacingExtra, lineSpacingMultiplier)
        }

        companion object {
            fun capture(source: TextView) = SectionTitleStyle(
                textSizePx = source.textSize,
                typeface = source.typeface,
                textColor = source.currentTextColor,
                includeFontPadding = source.includeFontPadding,
                letterSpacing = source.letterSpacing,
                textScaleX = source.textScaleX,
                paintFlags = source.paintFlags,
                lineSpacingExtra = source.lineSpacingExtra,
                lineSpacingMultiplier = source.lineSpacingMultiplier,
            )
        }
    }

    data class Labels(
        val spatialAudio: String,
        val spatialModeOff: String,
        val spatialModeFixed: String,
        val spatialModeHeadTracking: String,
        val spatialScene: String,
        val spatialSceneDefault: String,
        val spatialSceneTheater: String,
        val spatialSceneCinema: String,
        val spatialSceneConcert: String,
        val soundEffect: String,
        val soundEffectDefault: String,
        val soundEffectSport: String,
        val soundEffectTreble: String,
        val soundEffectClearVoice: String,
        val soundEffectCustom: String,
    )

    data class BuiltInSoundEffectOption(
        val id: Int,
        val label: String,
    )

    init {
        orientation = VERTICAL
    }

    private var sectionTitleStyle: SectionTitleStyle? = null
    private var hostAccentColor: Int? = null

    /**
     * 融合设备中心各版本的字号和字体可能变化，优先继承当前宿主卡片的标题样式。
     * Settings 等没有稳定参考 View 的入口继续使用模块自己的安全默认值。
     */
    internal fun setSectionTitleStyle(style: SectionTitleStyle?) {
        sectionTitleStyle = style
    }

    internal fun setHostAccentColor(color: Int?) {
        hostAccentColor = color
    }

    fun render(
        spatialMode: FreeClip2SpatialAudioMode,
        spatialScene: FreeClip2SpatialScene,
        soundEffect: FreeClip2SoundEffect,
        labels: Labels,
        darkSurface: Boolean,
        showSpatialMode: Boolean = true,
        showSpatialScene: Boolean,
        showSoundEffect: Boolean = true,
        showSoundEffectTitle: Boolean = true,
        compact: Boolean,
    ) {
        removeAllViews()
        setPadding(
            context.dp(if (compact) 6 else 12),
            context.dp(6),
            context.dp(if (compact) 6 else 12),
            context.dp(10),
        )

        if (showSpatialMode) {
            addSelector(
                title = labels.spatialAudio,
                labels = listOf(
                    labels.spatialModeOff,
                    labels.spatialModeFixed,
                    labels.spatialModeHeadTracking,
                ),
                selectedIndex = FreeClip2SpatialAudioMode.entries.indexOf(spatialMode),
                darkSurface = darkSurface,
                hostGlassStyle = compact,
            ) { index ->
                FreeClip2SpatialAudioMode.entries.getOrNull(index)?.let(onSpatialModeSelected)
            }
        }

        if (showSpatialMode && showSpatialScene && spatialMode != FreeClip2SpatialAudioMode.OFF) {
            addSelector(
                title = labels.spatialScene,
                labels = listOf(
                    labels.spatialSceneDefault,
                    labels.spatialSceneTheater,
                    labels.spatialSceneCinema,
                    labels.spatialSceneConcert,
                ),
                selectedIndex = FreeClip2SpatialScene.entries.indexOf(spatialScene),
                darkSurface = darkSurface,
                hostGlassStyle = compact,
            ) { index ->
                FreeClip2SpatialScene.entries.getOrNull(index)?.let(onSpatialSceneSelected)
            }
        }

        if (showSoundEffect) {
            val selectableSoundEffects = FreeClip2SoundEffect.selectableEntries
            addSelector(
                title = if (soundEffect == FreeClip2SoundEffect.CUSTOM) {
                    "${labels.soundEffect} · ${labels.soundEffectCustom}"
                } else {
                    labels.soundEffect
                },
                labels = listOf(
                    labels.soundEffectDefault,
                    labels.soundEffectSport,
                    labels.soundEffectTreble,
                    labels.soundEffectClearVoice,
                ),
                selectedIndex = selectableSoundEffects.indexOf(soundEffect),
                darkSurface = darkSurface,
                showTitle = showSoundEffectTitle,
                hostGlassStyle = compact,
            ) { index ->
                selectableSoundEffects.getOrNull(index)?.let(onSoundEffectSelected)
            }
        }
    }

    fun renderBuiltInSoundEffects(
        selectedId: Int?,
        options: List<BuiltInSoundEffectOption>,
        title: String,
        customTitle: String,
        darkSurface: Boolean,
        compact: Boolean,
    ) {
        removeAllViews()
        setPadding(
            context.dp(if (compact) 6 else 12),
            context.dp(6),
            context.dp(if (compact) 6 else 12),
            context.dp(10),
        )
        addSelector(
            title = if (selectedId in 0x64..0x66) "$title · $customTitle" else title,
            labels = options.map(BuiltInSoundEffectOption::label),
            selectedIndex = options.indexOfFirst { it.id == selectedId },
            darkSurface = darkSurface,
            hostGlassStyle = compact,
        ) { index ->
            options.getOrNull(index)?.id?.let(onBuiltInSoundEffectSelected)
        }
    }

    private fun addSelector(
        title: String,
        labels: List<String>,
        selectedIndex: Int,
        darkSurface: Boolean,
        showTitle: Boolean = true,
        hostGlassStyle: Boolean = false,
        onSelected: (Int) -> Unit,
    ) {
        if (showTitle) {
            addView(
                TextView(context).apply {
                    text = title
                    val inheritedStyle = sectionTitleStyle
                    if (inheritedStyle != null) {
                        inheritedStyle.applyTo(this)
                    } else {
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                        setTextColor(titleColor(darkSurface))
                    }
                    setPadding(context.dp(6), context.dp(7), context.dp(6), context.dp(6))
                },
                LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
            )
        }
        addView(
            HuaweiAncSubModeSelectorView(context, onSelected).apply {
                render(
                    options = labels.mapIndexed { index, label ->
                        HuaweiAncSubModeSelectorView.Option(index, label)
                    },
                    selectedValue = selectedIndex,
                    darkSurface = darkSurface,
                    appearance = if (hostGlassStyle) {
                        HuaweiAncSubModeSelectorView.Appearance.HOST_GLASS
                    } else {
                        HuaweiAncSubModeSelectorView.Appearance.MODULE
                    },
                    accentColor = hostAccentColor,
                )
            },
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
    }

    private fun titleColor(darkSurface: Boolean): Int =
        if (darkSurface) Color.rgb(225, 228, 235) else Color.rgb(46, 52, 64)

    private fun Context.dp(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()
}

/** 三个宿主入口共用同一套文案，避免空间音频和音效的名称再次漂移。 */
internal fun huaweiFreeClip2AudioLabels(
    resolve: (resId: Int, fallback: String) -> String,
) = HuaweiFreeClip2AudioControlsView.Labels(
    spatialAudio = resolve(R.string.freeclip2_spatial_audio, "空间音频"),
    spatialModeOff = resolve(R.string.off, "关闭"),
    spatialModeFixed = resolve(R.string.freeclip2_spatial_fixed, "固定"),
    spatialModeHeadTracking = resolve(R.string.freeclip2_spatial_head_tracking, "头部跟踪"),
    spatialScene = resolve(R.string.freeclip2_spatial_scene, "空间模式"),
    spatialSceneDefault = resolve(R.string.freeclip2_spatial_scene_default, "默认空间"),
    spatialSceneTheater = resolve(R.string.freeclip2_spatial_scene_theater, "有声剧场"),
    spatialSceneCinema = resolve(R.string.freeclip2_spatial_scene_cinema, "电影院"),
    spatialSceneConcert = resolve(R.string.freeclip2_spatial_scene_concert, "音乐厅"),
    soundEffect = resolve(R.string.freeclip2_sound_effect, "音效"),
    soundEffectDefault = resolve(R.string.freeclip2_sound_effect_default, "默认"),
    soundEffectSport = resolve(R.string.freeclip2_sound_effect_sport, "运动增效"),
    soundEffectTreble = resolve(R.string.freeclip2_sound_effect_treble, "高音增强"),
    soundEffectClearVoice = resolve(R.string.freeclip2_sound_effect_clear_voice, "清晰人声"),
    soundEffectCustom = resolve(R.string.freeclip2_sound_effect_custom, "官方/自定义音效"),
)
