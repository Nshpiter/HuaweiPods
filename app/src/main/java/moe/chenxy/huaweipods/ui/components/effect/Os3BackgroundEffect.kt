// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package moe.chenxy.huaweipods.ui.components.effect

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.blur.RuntimeShader
import top.yukonga.miuix.kmp.blur.asBrush
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/** HyperOS 3 flowing background used by the SonyPods reference About page. */
@Composable
internal fun Os3BackgroundEffect(
    dynamicBackground: Boolean,
    modifier: Modifier = Modifier,
    backgroundModifier: Modifier = Modifier,
    isFullSize: Boolean = false,
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    alpha: () -> Float = { 1f },
    content: @Composable BoxScope.() -> Unit,
) {
    val shaderSupported = remember { isRuntimeShaderSupported() }
    if (!shaderSupported) {
        Box(modifier = modifier, content = content)
        return
    }

    Box(modifier = modifier) {
        val surface = MiuixTheme.colorScheme.surface
        val preset = remember(isDarkTheme) { Os3EffectConfig.get(isDarkTheme) }
        val painter = remember { Os3EffectPainter() }
        val colorStage = remember { Animatable(0f) }

        LaunchedEffect(dynamicBackground, preset) {
            if (!dynamicBackground) return@LaunchedEffect
            var targetStage = floor(colorStage.value) + 1f
            while (isActive) {
                delay((preset.colorInterpolationPeriod * 500).toLong())
                colorStage.animateTo(
                    targetValue = targetStage,
                    animationSpec = spring(dampingRatio = 0.9f, stiffness = 35f),
                )
                targetStage += 1f
            }
        }

        Spacer(
            modifier = Modifier
                .fillMaxSize()
                .then(backgroundModifier)
                .os3EffectDraw(
                    painter = painter,
                    preset = preset,
                    surface = surface,
                    isFullSize = isFullSize,
                    playing = dynamicBackground,
                    colorStage = { colorStage.value },
                    alpha = alpha,
                ),
        )
        content()
    }
}

private object Os3EffectConfig {
    class Config(
        val points: FloatArray,
        val colors1: FloatArray,
        val colors2: FloatArray,
        val colors3: FloatArray,
        val colorInterpolationPeriod: Float,
        val lightOffset: Float,
        val saturationOffset: Float,
        val pointOffset: Float,
    )

    private val light = Config(
        points = floatArrayOf(0.8f, 0.2f, 1f, 0.8f, 0.9f, 1f, 0.2f, 0.9f, 1f, 0.2f, 0.2f, 1f),
        colors1 = floatArrayOf(1f, 0.9f, 0.94f, 1f, 1f, 0.84f, 0.89f, 1f, 0.97f, 0.73f, 0.82f, 1f, 0.64f, 0.65f, 0.98f, 1f),
        colors2 = floatArrayOf(0.58f, 0.74f, 1f, 1f, 1f, 0.9f, 0.93f, 1f, 0.74f, 0.76f, 1f, 1f, 0.97f, 0.77f, 0.84f, 1f),
        colors3 = floatArrayOf(0.98f, 0.86f, 0.9f, 1f, 0.6f, 0.73f, 0.98f, 1f, 0.92f, 0.93f, 1f, 1f, 0.56f, 0.69f, 1f, 1f),
        colorInterpolationPeriod = 5f,
        lightOffset = 0.1f,
        saturationOffset = 0.2f,
        pointOffset = 0.2f,
    )

    private val dark = Config(
        points = floatArrayOf(0.8f, 0.2f, 1f, 0.8f, 0.9f, 1f, 0.2f, 0.9f, 1f, 0.2f, 0.2f, 1f),
        colors1 = floatArrayOf(0.2f, 0.06f, 0.88f, 0.4f, 0.3f, 0.14f, 0.55f, 0.5f, 0f, 0.64f, 0.96f, 0.5f, 0.11f, 0.16f, 0.83f, 0.4f),
        colors2 = floatArrayOf(0.07f, 0.15f, 0.79f, 0.5f, 0.62f, 0.21f, 0.67f, 0.5f, 0.06f, 0.25f, 0.84f, 0.5f, 0f, 0.2f, 0.78f, 0.5f),
        colors3 = floatArrayOf(0.58f, 0.3f, 0.74f, 0.4f, 0.27f, 0.18f, 0.6f, 0.5f, 0.66f, 0.26f, 0.62f, 0.5f, 0.12f, 0.16f, 0.7f, 0.6f),
        colorInterpolationPeriod = 8f,
        lightOffset = 0f,
        saturationOffset = 0.17f,
        pointOffset = 0.4f,
    )

    fun get(isDark: Boolean): Config = if (isDark) dark else light
}

private fun Modifier.os3EffectDraw(
    painter: Os3EffectPainter,
    preset: Os3EffectConfig.Config,
    surface: Color,
    isFullSize: Boolean,
    playing: Boolean,
    colorStage: () -> Float,
    alpha: () -> Float,
): Modifier = this then Os3EffectElement(
    painter = painter,
    preset = preset,
    surface = surface,
    isFullSize = isFullSize,
    playing = playing,
    colorStage = colorStage,
    alpha = alpha,
)

private data class Os3EffectElement(
    val painter: Os3EffectPainter,
    val preset: Os3EffectConfig.Config,
    val surface: Color,
    val isFullSize: Boolean,
    val playing: Boolean,
    val colorStage: () -> Float,
    val alpha: () -> Float,
) : ModifierNodeElement<Os3EffectNode>() {
    override fun create(): Os3EffectNode = Os3EffectNode(
        painter = painter,
        preset = preset,
        surface = surface,
        isFullSize = isFullSize,
        playing = playing,
        colorStage = colorStage,
        alpha = alpha,
    )

    override fun update(node: Os3EffectNode) {
        node.update(
            painter = painter,
            preset = preset,
            surface = surface,
            isFullSize = isFullSize,
            playing = playing,
            colorStage = colorStage,
            alpha = alpha,
        )
    }
}

private class Os3EffectNode(
    private var painter: Os3EffectPainter,
    private var preset: Os3EffectConfig.Config,
    private var surface: Color,
    private var isFullSize: Boolean,
    private var playing: Boolean,
    private var colorStage: () -> Float,
    private var alpha: () -> Float,
) : Modifier.Node(), DrawModifierNode {
    private var animationJob: Job? = null
    private var animationTime = 0f
    private var startOffset = 0f

    override fun onAttach() {
        if (playing) startAnimation()
    }

    override fun onDetach() {
        animationJob?.cancel()
        animationJob = null
    }

    fun update(
        painter: Os3EffectPainter,
        preset: Os3EffectConfig.Config,
        surface: Color,
        isFullSize: Boolean,
        playing: Boolean,
        colorStage: () -> Float,
        alpha: () -> Float,
    ) {
        this.painter = painter
        this.preset = preset
        this.surface = surface
        this.isFullSize = isFullSize
        this.colorStage = colorStage
        this.alpha = alpha
        if (this.playing != playing) {
            this.playing = playing
            if (playing) startAnimation() else animationJob?.cancel()
            if (!playing) animationJob = null
        }
        invalidateDraw()
    }

    private fun startAnimation() {
        animationJob?.cancel()
        startOffset = animationTime
        animationJob = coroutineScope.launch {
            val frameInterval = 1_000_000_000L / 60L
            val origin = withFrameNanos { it }
            var lastFrame = origin
            while (isActive) {
                val now = withFrameNanos { it }
                if (now - lastFrame < frameInterval) continue
                lastFrame = now
                animationTime = startOffset + (now - origin) / 1_000_000_000f
                invalidateDraw()
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawRect(surface)
        val effectAlpha = alpha()
        if (effectAlpha <= 0f) {
            animationJob?.cancel()
            animationJob = null
        } else {
            if (playing && animationJob == null) startAnimation()
            val drawHeight = if (isFullSize) size.height * 0.8f else size.height * 0.5f
            painter.updateResolution(size.width, size.height)
            painter.updateBounds(drawHeight, size.height, size.width)
            painter.updatePreset(preset)
            painter.updateColors(preset, colorStage())
            painter.updateAnimationTime(animationTime)
            painter.updateAnimatedPoints(animationTime, preset)
            drawRect(painter.brush, alpha = effectAlpha)
        }
        drawContent()
    }
}

private class Os3EffectPainter {
    private val runtimeShader by lazy {
        RuntimeShader(OS3_BACKGROUND_SHADER).also { shader ->
            shader.setFloatUniform("uTranslateY", 0f)
            shader.setFloatUniform("uNoiseScale", 1.5f)
            shader.setFloatUniform("uPointRadiusMulti", 1f)
            shader.setFloatUniform("uAlphaMulti", 1f)
        }
    }
    val brush: Brush get() = runtimeShader.asBrush()

    private val resolution = FloatArray(2)
    private val bounds = FloatArray(4)
    private val colorBuffer = FloatArray(16)
    private val animatedPointBuffer = FloatArray(8)
    private var lastAnimationTime = Float.NaN
    private var lastWidth = Float.NaN
    private var lastHeight = Float.NaN
    private var lastDrawHeight = Float.NaN
    private var lastColorStage = Float.NaN
    private var lastColorPreset: Os3EffectConfig.Config? = null
    private var lastPointTime = Float.NaN
    private var lastPointPreset: Os3EffectConfig.Config? = null
    private var lastPreset: Os3EffectConfig.Config? = null

    fun updateResolution(width: Float, height: Float) {
        if (resolution[0] == width && resolution[1] == height) return
        resolution[0] = width
        resolution[1] = height
        runtimeShader.setFloatUniform("uResolution", resolution)
    }

    fun updateAnimationTime(time: Float) {
        if (lastAnimationTime == time) return
        lastAnimationTime = time
        runtimeShader.setFloatUniform("uAnimTime", time)
    }

    fun updateAnimatedPoints(time: Float, preset: Os3EffectConfig.Config) {
        if (lastPointTime == time && lastPointPreset === preset) return
        repeat(4) { index ->
            val sourceX = preset.points[index * 3]
            val sourceY = preset.points[index * 3 + 1]
            val animatedX = sourceX + sin(time + sourceY) * preset.pointOffset
            val animatedY = sourceY + cos(time + animatedX) * preset.pointOffset
            animatedPointBuffer[index * 2] = animatedX
            animatedPointBuffer[index * 2 + 1] = animatedY
        }
        runtimeShader.setFloatUniform("uPointsAnim", animatedPointBuffer)
        lastPointTime = time
        lastPointPreset = preset
    }

    fun updateColors(preset: Os3EffectConfig.Config, stage: Float) {
        if (lastColorPreset === preset && lastColorStage == stage) return
        val base = stage.toInt()
        val fraction = stage - base
        val start = colorsForCycle(preset, base)
        val end = colorsForCycle(preset, base + 1)
        repeat(16) { index ->
            colorBuffer[index] = start[index] + (end[index] - start[index]) * fraction
        }
        runtimeShader.setFloatUniform("uColors", colorBuffer)
        lastColorPreset = preset
        lastColorStage = stage
    }

    fun updateBounds(drawHeight: Float, totalHeight: Float, totalWidth: Float) {
        if (lastDrawHeight == drawHeight && lastHeight == totalHeight && lastWidth == totalWidth) return
        val heightRatio = drawHeight / totalHeight
        if (totalWidth <= totalHeight) {
            bounds[0] = 0f
            bounds[1] = 1f - heightRatio
            bounds[2] = 1f
            bounds[3] = heightRatio
        } else {
            val aspectRatio = totalWidth / totalHeight
            val centerY = 1f - heightRatio / 2f
            bounds[0] = 0f
            bounds[1] = centerY - aspectRatio / 2f
            bounds[2] = 1f
            bounds[3] = aspectRatio
        }
        runtimeShader.setFloatUniform("uBound", bounds)
        lastDrawHeight = drawHeight
        lastHeight = totalHeight
        lastWidth = totalWidth
    }

    fun updatePreset(preset: Os3EffectConfig.Config) {
        if (lastPreset === preset) return
        runtimeShader.setFloatUniform("uPoints", preset.points)
        runtimeShader.setFloatUniform("uLightOffset", preset.lightOffset)
        runtimeShader.setFloatUniform("uSaturateOffset", preset.saturationOffset)
        lastPreset = preset
    }

    private fun colorsForCycle(preset: Os3EffectConfig.Config, index: Int): FloatArray =
        when (index.mod(4)) {
            1 -> preset.colors1
            3 -> preset.colors3
            else -> preset.colors2
        }
}

private const val OS3_BACKGROUND_SHADER = """
    uniform vec2 uResolution;
    uniform float uAnimTime;
    uniform vec4 uBound;
    uniform float uTranslateY;
    uniform vec3 uPoints[4];
    uniform vec2 uPointsAnim[4];
    uniform vec4 uColors[4];
    uniform float uAlphaMulti;
    uniform float uNoiseScale;
    uniform float uPointRadiusMulti;
    uniform float uSaturateOffset;
    uniform float uLightOffset;

    vec3 rgb2hsv(vec3 c) {
        vec4 K = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
        vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));
        vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));
        float d = q.x - min(q.w, q.y);
        float e = 1.0e-10;
        return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + e)), d / (q.x + e), q.x);
    }

    vec3 hsv2rgb(vec3 c) {
        vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
        vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
        return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
    }

    float hash(vec2 p) {
        vec3 p3 = fract(vec3(p.xyx) * 0.13);
        p3 += dot(p3, p3.yzx + 3.333);
        return fract((p3.x + p3.y) * p3.z);
    }

    float perlin(vec2 x) {
        vec2 i = floor(x); vec2 f = fract(x);
        float a = hash(i); float b = hash(i + vec2(1.0, 0.0));
        float c = hash(i + vec2(0.0, 1.0)); float d = hash(i + vec2(1.0, 1.0));
        vec2 u = f * f * (3.0 - 2.0 * f);
        return mix(a, b, u.x) + (c - a) * u.y * (1.0 - u.x) + (d - b) * u.x * u.y;
    }

    float gradientNoise(in vec2 uv) {
        return fract(52.9829189 * fract(dot(uv, vec2(0.06711056, 0.00583715))));
    }

    vec4 main(vec2 fragCoord) {
        vec2 vUv = fragCoord / uResolution;
        vUv.y = 1.0 - vUv.y;
        vec2 uv = vUv;
        uv -= vec2(0.0, uTranslateY);
        uv.xy -= uBound.xy;
        uv.xy /= uBound.zw;

        vec4 color = vec4(0.0);
        float noiseValue = perlin(vUv * uNoiseScale + vec2(-uAnimTime, -uAnimTime));
        for (int i = 0; i < 4; i++) {
            vec4 pointColor = uColors[i];
            pointColor.rgb *= pointColor.a;
            vec2 point = uPointsAnim[i];
            float radius = uPoints[i].z * uPointRadiusMulti;
            float distanceFromPoint = distance(uv, point);
            float percentage = smoothstep(radius, 0.0, distanceFromPoint);
            color.rgb = mix(color.rgb, pointColor.rgb, percentage);
            color.a = mix(color.a, pointColor.a, percentage);
        }

        float oppositeNoise = smoothstep(0.0, 1.0, noiseValue);
        color.rgb /= color.a;
        vec3 hsv = rgb2hsv(color.rgb);
        hsv.y = mix(hsv.y, 0.0, oppositeNoise * uSaturateOffset);
        color.rgb = hsv2rgb(hsv);
        color.rgb += oppositeNoise * uLightOffset;
        color.a = clamp(color.a, 0.0, 1.0) * uAlphaMulti;
        color += (10.0 / 255.0) * gradientNoise(fragCoord.xy) - (5.0 / 255.0);
        return vec4(color.rgb * color.a, color.a);
    }
"""
