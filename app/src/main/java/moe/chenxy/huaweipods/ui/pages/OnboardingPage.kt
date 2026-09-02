package moe.chenxy.huaweipods.ui.pages

import android.content.Context
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.libxposed.service.XposedService
import kotlinx.coroutines.launch
import moe.chenxy.huaweipods.R
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val requiredCoreScopes = setOf(
    "com.android.bluetooth",
    "com.xiaomi.bluetooth",
)

private fun colorWithWhiteTextContrast(source: Color): Color {
    var result = source
    var overlayAlpha = 0f
    while (1.05f / (result.luminance() + 0.05f) < 4.5f && overlayAlpha < 0.64f) {
        overlayAlpha += 0.08f
        result = Color.Black.copy(alpha = overlayAlpha).compositeOver(source)
    }
    return result
}

private fun readAnimatorScale(context: Context): Float = runCatching {
    Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    )
}.getOrDefault(1f)

@Composable
fun OnboardingPage(
    xposedService: XposedService?,
    isReplay: Boolean = false,
    onFinish: () -> Unit,
    onSkip: () -> Unit = onFinish,
) {
    val pagerState = rememberPagerState(pageCount = { ONBOARDING_PAGE_COUNT })
    val scope = rememberCoroutineScope()
    var navigationLocked by remember { mutableStateOf(false) }
    var terminalActionInvoked by rememberSaveable { mutableStateOf(false) }
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val lifecycleOwner = LocalActivity.current as? LifecycleOwner
    val layoutPolicy = onboardingLayoutPolicy(
        widthDp = configuration.screenWidthDp,
        heightDp = configuration.screenHeightDp,
    )
    var animatorScale by remember(context) {
        mutableFloatStateOf(readAnimatorScale(context))
    }
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                animatorScale = readAnimatorScale(context)
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer) }
    }
    val motionEnabled = onboardingMotionEnabled(animatorScale)
    val surface = MiuixTheme.colorScheme.surface
    val accent = MiuixTheme.colorScheme.primary
    val actionColor = remember(accent) { colorWithWhiteTextContrast(accent) }
    val successColor = if (surface.luminance() < 0.5f) Color(0xFF63D6A0) else Color(0xFF25865B)
    val backgroundBrush = remember(surface, accent) {
        Brush.verticalGradient(
            colors = listOf(
                accent.copy(alpha = 0.12f).compositeOver(surface),
                surface,
                Color(0xFF6E9FE8).copy(alpha = 0.07f).compositeOver(surface),
            ),
        )
    }

    fun invokeTerminalOnce(action: () -> Unit) {
        if (terminalActionInvoked) return
        terminalActionInvoked = true
        action()
    }

    fun navigate(action: OnboardingNavigationAction) {
        if (navigationLocked || pagerState.isScrollInProgress || terminalActionInvoked) return
        val result = reduceOnboardingNavigation(pagerState.currentPage, action)
        if (result.finish) {
            invokeTerminalOnce(onFinish)
            return
        }
        if (result.page == pagerState.currentPage) return
        navigationLocked = true
        scope.launch {
            try {
                if (motionEnabled) {
                    pagerState.animateScrollToPage(result.page)
                } else {
                    pagerState.scrollToPage(result.page)
                }
            } finally {
                navigationLocked = false
            }
        }
    }

    LaunchedEffect(pagerState) {
        if (pagerState.currentPageOffsetFraction != 0f) {
            pagerState.scrollToPage(pagerState.currentPage)
        }
    }

    BackHandler(
        enabled = !terminalActionInvoked && (
            pagerState.currentPage > 0 || navigationLocked || pagerState.isScrollInProgress
        ),
    ) {
        if (!navigationLocked && !pagerState.isScrollInProgress) {
            navigate(OnboardingNavigationAction.PREVIOUS)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        SetupTopBar(
            isReplay = isReplay,
            enabled = !terminalActionInvoked,
            onSkip = { invokeTerminalOnce(onSkip) },
        )

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            SetupScene(
                pagerState = pagerState,
                xposedService = xposedService,
                accent = accent,
                successColor = successColor,
                motionEnabled = motionEnabled,
                landscape = layoutPolicy.landscape,
                compact = layoutPolicy.compact,
                viewportHeight = maxHeight,
            )
        }

        SetupFooter(
            currentPage = pagerState.currentPage,
            accent = accent,
            actionColor = actionColor,
            navigationEnabled = !navigationLocked && !pagerState.isScrollInProgress && !terminalActionInvoked,
            motionEnabled = motionEnabled,
            onPrevious = { navigate(OnboardingNavigationAction.PREVIOUS) },
            onNext = { navigate(OnboardingNavigationAction.NEXT) },
        )
    }
}

@Composable
private fun SetupTopBar(
    isReplay: Boolean,
    enabled: Boolean,
    onSkip: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HuaweiPodsAppIcon(size = 30.dp)
        Spacer(Modifier.width(10.dp))
        Text(
            text = stringResource(R.string.app_name),
            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.58f),
            style = MiuixTheme.textStyles.body2,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.weight(1f))
        TextButton(
            text = stringResource(
                if (isReplay) R.string.onboarding_close else R.string.onboarding_skip,
            ),
            onClick = onSkip,
            enabled = enabled,
            modifier = Modifier.heightIn(min = 48.dp),
        )
    }
}

@Composable
private fun SetupScene(
    pagerState: PagerState,
    xposedService: XposedService?,
    accent: Color,
    successColor: Color,
    motionEnabled: Boolean,
    landscape: Boolean,
    compact: Boolean,
    viewportHeight: Dp,
) {
    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        userScrollEnabled = false,
        beyondViewportPageCount = 0,
    ) { page ->
        when (page) {
            0 -> WelcomeSetupPage(
                motionEnabled = motionEnabled,
                landscape = landscape,
                compact = compact,
                viewportHeight = viewportHeight,
            )

            1 -> EnvironmentSetupPage(
                xposedService = xposedService,
                accent = accent,
                successColor = successColor,
                motionEnabled = motionEnabled,
                landscape = landscape,
                compact = compact,
                viewportHeight = viewportHeight,
            )

            else -> ReadySetupPage(
                accent = accent,
                successColor = successColor,
                motionEnabled = motionEnabled,
                landscape = landscape,
                compact = compact,
                viewportHeight = viewportHeight,
            )
        }
    }
}

private fun setupPageEnter(
    motionEnabled: Boolean,
    delayMillis: Int = 0,
    withScale: Boolean = false,
): EnterTransition {
    if (!motionEnabled) return fadeIn(snap())
    var transition: EnterTransition = fadeIn(
        animationSpec = tween(240, delayMillis = delayMillis),
    ) + slideInVertically(
        animationSpec = tween(340, delayMillis = delayMillis, easing = FastOutSlowInEasing),
        initialOffsetY = { height -> height.coerceAtLeast(48) / 12 },
    )
    if (withScale) {
        transition += scaleIn(
            animationSpec = tween(340, delayMillis = delayMillis, easing = FastOutSlowInEasing),
            initialScale = 0.92f,
        )
    }
    return transition
}

@Composable
private fun WelcomeSetupPage(
    motionEnabled: Boolean,
    landscape: Boolean,
    compact: Boolean,
    viewportHeight: Dp,
) {
    var visible by remember(motionEnabled) { mutableStateOf(!motionEnabled) }
    LaunchedEffect(motionEnabled) { visible = true }
    val modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .heightIn(min = viewportHeight)
        .padding(horizontal = if (compact) 24.dp else 36.dp, vertical = 16.dp)

    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.fillMaxSize(),
        enter = setupPageEnter(motionEnabled = motionEnabled, withScale = true),
    ) {
        if (landscape) {
            Row(
                modifier = modifier,
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OnboardingDevicePreview(
                    motionEnabled = motionEnabled,
                    compact = true,
                    landscape = true,
                    modifier = Modifier.width(270.dp),
                )
                Spacer(Modifier.width(42.dp))
                WelcomeCopy(
                    modifier = Modifier.widthIn(max = 430.dp),
                    centered = false,
                    compact = true,
                )
            }
        } else {
            Column(
                modifier = modifier,
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                OnboardingDevicePreview(
                    motionEnabled = motionEnabled,
                    compact = compact,
                    modifier = Modifier
                        .widthIn(max = 320.dp)
                        .fillMaxWidth(),
                )
                Spacer(Modifier.height(if (compact) 22.dp else 28.dp))
                WelcomeCopy(
                    modifier = Modifier.widthIn(max = 540.dp),
                    centered = true,
                    compact = compact,
                )
            }
        }
    }
}

@Composable
private fun WelcomeCopy(
    centered: Boolean,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val alignment = if (centered) Alignment.CenterHorizontally else Alignment.Start
    val textAlign = if (centered) TextAlign.Center else TextAlign.Start
    Column(modifier = modifier, horizontalAlignment = alignment) {
        Text(
            text = stringResource(R.string.app_name),
            modifier = Modifier.semantics { heading() },
            color = MiuixTheme.colorScheme.onSurface,
            fontSize = if (compact) 37.sp else 42.sp,
            lineHeight = if (compact) 43.sp else 49.sp,
            fontWeight = FontWeight.Bold,
            textAlign = textAlign,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.onboarding_welcome_summary),
            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.64f),
            style = MiuixTheme.textStyles.headline1,
            textAlign = textAlign,
        )
    }
}

@Composable
private fun OnboardingDevicePreview(
    motionEnabled: Boolean,
    compact: Boolean,
    landscape: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var entered by remember(motionEnabled) { mutableStateOf(!motionEnabled) }
    LaunchedEffect(motionEnabled) { entered = true }
    val sceneProgress by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = if (motionEnabled) {
            tween(durationMillis = 420, easing = FastOutSlowInEasing)
        } else {
            snap()
        },
        label = "setup_preview_scene",
    )
    val railProgress by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = if (motionEnabled) {
            tween(durationMillis = 360, delayMillis = 90, easing = FastOutSlowInEasing)
        } else {
            snap()
        },
        label = "setup_preview_rail",
    )
    val accent = MiuixTheme.colorScheme.primary
    val surface = MiuixTheme.colorScheme.surface
    val onSurface = MiuixTheme.colorScheme.onSurface
    val stageShape = RoundedCornerShape(if (compact) 30.dp else 36.dp)
    val stageBrush = remember(surface, accent) {
        Brush.linearGradient(
            colors = listOf(
                accent.copy(alpha = 0.16f).compositeOver(surface),
                Color(0xFF8274D1).copy(alpha = 0.11f).compositeOver(surface),
                Color(0xFF5C9BDA).copy(alpha = 0.15f).compositeOver(surface),
            ),
        )
    }
    val previewDescription = stringResource(R.string.onboarding_preview_description)
    val previewHeight = when {
        landscape -> 184.dp
        compact -> 216.dp
        else -> 244.dp
    }
    val heroSize = when {
        landscape -> 88.dp
        compact -> 102.dp
        else -> 116.dp
    }

    Box(
        modifier = modifier
            .height(previewHeight)
            .graphicsLayer {
                alpha = sceneProgress
                val scale = 0.94f + sceneProgress * 0.06f
                scaleX = scale
                scaleY = scale
            }
            .clip(stageShape)
            .background(stageBrush)
            .border(1.dp, Color.White.copy(alpha = 0.34f), stageShape)
            .clearAndSetSemantics { contentDescription = previewDescription },
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(heroSize * 1.18f)
                .graphicsLayer {
                    translationX = -heroSize.toPx() * 0.46f
                    translationY = -heroSize.toPx() * 0.06f
                    rotationZ = -12f + sceneProgress * 5f
                    alpha = 0.28f + sceneProgress * 0.44f
                }
                .background(accent.copy(alpha = 0.12f), RoundedCornerShape(heroSize * 0.30f)),
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(heroSize * 1.08f)
                .graphicsLayer {
                    translationX = heroSize.toPx() * 0.48f
                    translationY = heroSize.toPx() * 0.04f
                    rotationZ = 11f - sceneProgress * 5f
                    alpha = 0.22f + sceneProgress * 0.38f
                }
                .background(
                    Color(0xFF5C9BDA).copy(alpha = 0.12f),
                    RoundedCornerShape(heroSize * 0.30f),
                ),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.app_name),
                color = onSurface,
                style = MiuixTheme.textStyles.body2,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(surface.copy(alpha = 0.64f))
                    .border(1.dp, Color.White.copy(alpha = 0.30f), CircleShape),
            ) {
                Text(
                    text = stringResource(R.string.onboarding_preview_auto),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    color = accent,
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        OnboardingHeroArtwork(
            size = heroSize,
            animateEntry = motionEnabled,
            modifier = Modifier
                .align(Alignment.Center)
                .graphicsLayer { translationY = -10.dp.toPx() },
        )
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 13.dp, end = 13.dp, bottom = 13.dp)
                .graphicsLayer {
                    alpha = railProgress
                    translationY = (1f - railProgress) * 12.dp.toPx()
                }
                .clip(RoundedCornerShape(20.dp))
                .background(surface.copy(alpha = 0.82f))
                .border(
                    width = 1.dp,
                    color = onSurface.copy(alpha = 0.05f),
                    shape = RoundedCornerShape(20.dp),
                )
                .padding(horizontal = 8.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            PreviewFeature(
                value = "100%",
                label = stringResource(R.string.onboarding_preview_battery),
                color = Color(0xFF3BA872),
                modifier = Modifier.weight(1f),
            )
            PreviewFeature(
                value = "ANC",
                label = stringResource(R.string.onboarding_preview_noise),
                color = accent,
                modifier = Modifier.weight(1f),
            )
            PreviewFeature(
                value = "OS",
                label = stringResource(R.string.onboarding_preview_center),
                color = Color(0xFF4B8FD6),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun PreviewFeature(
    value: String,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).background(color, CircleShape))
            Spacer(Modifier.width(5.dp))
            Text(
                text = value,
                color = MiuixTheme.colorScheme.onSurface,
                fontSize = 12.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = label,
            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.58f),
            fontSize = 10.sp,
            lineHeight = 12.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun EnvironmentSetupPage(
    xposedService: XposedService?,
    accent: Color,
    successColor: Color,
    motionEnabled: Boolean,
    landscape: Boolean,
    compact: Boolean,
    viewportHeight: Dp,
) {
    var visible by remember(motionEnabled) { mutableStateOf(!motionEnabled) }
    LaunchedEffect(motionEnabled) { visible = true }
    val environmentReady = remember(xposedService) {
        xposedService != null && runCatching {
            xposedService.scope.containsAll(requiredCoreScopes)
        }.getOrDefault(false)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .heightIn(min = viewportHeight)
            .padding(
                horizontal = if (compact) 24.dp else 36.dp,
                vertical = if (landscape || compact) 10.dp else 22.dp,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = if (landscape) 680.dp else 620.dp)
                .fillMaxWidth(),
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = setupPageEnter(motionEnabled = motionEnabled, withScale = true),
            ) {
                SetupSectionHeader(
                    eyebrow = stringResource(R.string.onboarding_environment_label),
                    title = stringResource(R.string.onboarding_environment_title),
                    summary = stringResource(R.string.onboarding_environment_summary),
                    accent = accent,
                    badgeColor = if (environmentReady) successColor else Color(0xFFE69A37),
                    badgeText = if (environmentReady) "✓" else "!",
                    compact = compact || landscape,
                )
            }
            Spacer(Modifier.height(if (compact || landscape) 16.dp else 24.dp))
            AnimatedVisibility(
                visible = visible,
                enter = setupPageEnter(motionEnabled = motionEnabled, delayMillis = 60),
            ) {
                EnvironmentDetails(
                    xposedService = xposedService,
                    accent = accent,
                    successColor = successColor,
                    motionEnabled = motionEnabled,
                )
            }
        }
    }
}

@Composable
private fun SetupSectionHeader(
    eyebrow: String,
    title: String,
    summary: String,
    accent: Color,
    badgeColor: Color,
    badgeText: String,
    compact: Boolean,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SetupPageGlyph(
                badgeColor = badgeColor,
                badgeText = badgeText,
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.11f)),
                ) {
                    Text(
                        text = eyebrow,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = accent,
                        fontSize = 13.sp,
                        lineHeight = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(7.dp))
                Text(
                    text = title,
                    modifier = Modifier.semantics { heading() },
                    color = MiuixTheme.colorScheme.onSurface,
                    fontSize = if (compact) 29.sp else 32.sp,
                    lineHeight = if (compact) 34.sp else 38.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = summary,
            modifier = Modifier.fillMaxWidth(),
            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.64f),
            style = MiuixTheme.textStyles.headline1,
        )
    }
}

@Composable
private fun SetupPageGlyph(
    badgeColor: Color,
    badgeText: String,
) {
    Box(modifier = Modifier.size(66.dp)) {
        HuaweiPodsAppIcon(size = 62.dp)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(24.dp)
                .background(MiuixTheme.colorScheme.surface, CircleShape)
                .padding(3.dp)
                .background(badgeColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = badgeText,
                color = Color.White,
                fontSize = 12.sp,
                lineHeight = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun EnvironmentDetails(
    xposedService: XposedService?,
    accent: Color,
    successColor: Color,
    motionEnabled: Boolean,
) {
    var refreshVersion by remember { mutableIntStateOf(0) }
    val serviceConnected = xposedService != null
    val coreScopesReady = remember(xposedService, refreshVersion) {
        runCatching { xposedService?.scope?.containsAll(requiredCoreScopes) == true }
            .getOrDefault(false)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        SetupPanel {
            SetupStatusRow(
                title = stringResource(R.string.onboarding_environment_lsposed),
                ready = serviceConnected,
                accent = accent,
                successColor = successColor,
                motionEnabled = motionEnabled,
            )
            SetupDivider()
            SetupStatusRow(
                title = stringResource(R.string.onboarding_environment_scopes),
                ready = coreScopesReady,
                accent = accent,
                successColor = successColor,
                motionEnabled = motionEnabled,
            )
        }
        Spacer(Modifier.height(4.dp))
        TextButton(
            text = stringResource(R.string.onboarding_refresh),
            onClick = { refreshVersion++ },
            modifier = Modifier
                .align(Alignment.End)
                .heightIn(min = 48.dp),
        )
    }
}

@Composable
private fun SetupStatusRow(
    title: String,
    ready: Boolean,
    accent: Color,
    successColor: Color,
    motionEnabled: Boolean,
) {
    val statusText = stringResource(
        if (ready) R.string.onboarding_status_ready else R.string.onboarding_status_missing,
    )
    val statusColor by animateColorAsState(
        targetValue = if (ready) successColor else Color(0xFFE69A37),
        animationSpec = if (motionEnabled) tween(220) else snap(),
        label = "setup_status_color",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 62.dp)
            .semantics(mergeDescendants = true) {
                stateDescription = statusText
                liveRegion = LiveRegionMode.Polite
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .background(statusColor.copy(alpha = 0.14f), CircleShape)
                .clearAndSetSemantics { },
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = ready,
                transitionSpec = {
                    if (motionEnabled) {
                        (fadeIn(tween(160)) + scaleIn(tween(180), initialScale = 0.78f))
                            .togetherWith(fadeOut(tween(100)) + scaleOut(tween(120), targetScale = 1.12f))
                    } else {
                        fadeIn(snap()).togetherWith(fadeOut(snap()))
                    }
                },
                label = "setup_status_icon",
            ) { isReady ->
                Text(
                    text = if (isReady) "✓" else "!",
                    color = statusColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            color = MiuixTheme.colorScheme.onSurface,
            style = MiuixTheme.textStyles.headline1,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = statusText,
            modifier = Modifier.clearAndSetSemantics { },
            color = if (ready) accent else MiuixTheme.colorScheme.onSurface.copy(alpha = 0.58f),
            style = MiuixTheme.textStyles.body2,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun ReadySetupPage(
    accent: Color,
    successColor: Color,
    motionEnabled: Boolean,
    landscape: Boolean,
    compact: Boolean,
    viewportHeight: Dp,
) {
    var visible by remember(motionEnabled) { mutableStateOf(!motionEnabled) }
    LaunchedEffect(motionEnabled) { visible = true }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .heightIn(min = viewportHeight)
            .padding(
                horizontal = if (compact) 24.dp else 36.dp,
                vertical = if (landscape || compact) 10.dp else 22.dp,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = if (landscape) 680.dp else 620.dp)
                .fillMaxWidth(),
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = setupPageEnter(motionEnabled = motionEnabled, withScale = true),
            ) {
                SetupSectionHeader(
                    eyebrow = stringResource(R.string.onboarding_ready_label),
                    title = stringResource(R.string.onboarding_ready_title),
                    summary = stringResource(R.string.onboarding_ready_summary),
                    accent = accent,
                    badgeColor = successColor,
                    badgeText = "✓",
                    compact = compact || landscape,
                )
            }
            Spacer(Modifier.height(if (compact || landscape) 16.dp else 24.dp))
            AnimatedVisibility(
                visible = visible,
                enter = setupPageEnter(motionEnabled = motionEnabled, delayMillis = 60),
            ) {
                ReadyDetails(accent = accent)
            }
        }
    }
}

@Composable
private fun ReadyDetails(accent: Color) {
    SetupPanel {
        ReadyItem("1", stringResource(R.string.onboarding_ready_pair), accent)
        SetupDivider()
        ReadyItem("2", stringResource(R.string.onboarding_ready_model), accent)
        SetupDivider()
        ReadyItem(
            "3",
            stringResource(
                R.string.onboarding_ready_group,
                stringResource(R.string.qq_group_number),
            ),
            accent,
        )
    }
}

@Composable
private fun SetupPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(28.dp)
    val surface = MiuixTheme.colorScheme.surface
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(surface.copy(alpha = 0.88f))
            .border(
                width = 1.dp,
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                shape = shape,
            )
            .padding(horizontal = 20.dp, vertical = 8.dp),
        content = content,
    )
}

@Composable
private fun ReadyItem(
    number: String,
    text: String,
    accent: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .defaultMinSize(minWidth = 28.dp, minHeight = 28.dp)
                .background(accent.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = number,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                color = accent,
                style = MiuixTheme.textStyles.body2,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(13.dp))
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            color = MiuixTheme.colorScheme.onSurface,
            style = MiuixTheme.textStyles.headline1,
        )
    }
}

@Composable
private fun SetupDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                MiuixTheme.colorScheme.onSurface
                    .copy(alpha = 0.07f)
                    .compositeOver(MiuixTheme.colorScheme.surface),
            ),
    )
}

@Composable
private fun HuaweiPodsAppIcon(
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val launcherForeground = colorResource(android.R.color.system_accent1_10)
    val launcherBrush = remember {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFF9865C2),
                Color(0xFF7775D4),
                Color(0xFF5A91D6),
            ),
        )
    }
    val corner = when {
        size >= 120.dp -> 32.dp
        size >= 80.dp -> 24.dp
        else -> 14.dp
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(launcherBrush)
            .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(corner)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            colorFilter = ColorFilter.tint(launcherForeground),
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun OnboardingHeroArtwork(
    size: Dp,
    animateEntry: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var entered by remember(animateEntry) { mutableStateOf(!animateEntry) }
    LaunchedEffect(animateEntry) { entered = true }
    val entryProgress by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = if (animateEntry) {
            tween(durationMillis = 420, easing = FastOutSlowInEasing)
        } else {
            snap()
        },
        label = "setup_hero_entry",
    )
    val shape = RoundedCornerShape(size * 0.27f)
    val artworkBrush = remember {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFF9865C2),
                Color(0xFF7775D4),
                Color(0xFF5A91D6),
            ),
        )
    }
    val haloBrush = remember {
        Brush.radialGradient(
            colors = listOf(
                Color(0xFF8470CC).copy(alpha = 0.22f),
                Color.Transparent,
            ),
        )
    }
    Box(
        modifier = modifier
            .size(size),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(size * 1.12f)
                .graphicsLayer {
                    alpha = 0.30f + entryProgress * 0.40f
                    val haloScale = 0.90f + entryProgress * 0.10f
                    scaleX = haloScale
                    scaleY = haloScale
                }
                .background(haloBrush, shape),
        )
        Box(
            modifier = Modifier
                .size(size)
                .clip(shape)
                .background(artworkBrush)
                .border(1.dp, Color.White.copy(alpha = 0.24f), shape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = size * 0.11f, end = size * 0.11f)
                    .size(size * 0.25f)
                    .graphicsLayer {
                        translationX = size.toPx() * 0.035f * entryProgress
                        translationY = -size.toPx() * 0.025f * entryProgress
                    }
                    .background(Color.White.copy(alpha = 0.10f), CircleShape),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(bottom = size * 0.13f, start = size * 0.13f)
                    .size(size * 0.15f)
                    .graphicsLayer {
                        translationX = -size.toPx() * 0.028f * entryProgress
                        translationY = size.toPx() * 0.032f * entryProgress
                    }
                    .background(Color.White.copy(alpha = 0.12f), CircleShape),
            )
            Box(
                modifier = Modifier
                    .size(size * 0.72f)
                    .graphicsLayer {
                        alpha = 0.08f + entryProgress * 0.08f
                        val ringScale = 0.92f + entryProgress * 0.08f
                        scaleX = ringScale
                        scaleY = ringScale
                    }
                    .border(1.dp, Color.White.copy(alpha = 0.52f), CircleShape),
            )
            Image(
                painter = painterResource(R.drawable.about_logo_mark),
                contentDescription = null,
                colorFilter = ColorFilter.tint(Color.White),
                modifier = Modifier
                    .size(size * 0.57f)
                    .graphicsLayer {
                        alpha = entryProgress
                        translationY = size.toPx() * 0.05f * (1f - entryProgress)
                        val logoScale = 0.86f + entryProgress * 0.14f
                        scaleX = logoScale
                        scaleY = logoScale
                    },
            )
        }
    }
}

@Composable
private fun SetupFooter(
    currentPage: Int,
    accent: Color,
    actionColor: Color,
    navigationEnabled: Boolean,
    motionEnabled: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val pageState = stringResource(
        R.string.onboarding_page_status,
        currentPage + 1,
        ONBOARDING_PAGE_COUNT,
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.semantics(mergeDescendants = true) {
                stateDescription = pageState
                liveRegion = LiveRegionMode.Polite
            },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(ONBOARDING_PAGE_COUNT) { index ->
                val dotWidth by animateDpAsState(
                    targetValue = if (index == currentPage) 22.dp else 7.dp,
                    animationSpec = if (motionEnabled) tween(240, easing = FastOutSlowInEasing) else snap(),
                    label = "setup_step_width",
                )
                val dotColor by animateColorAsState(
                    targetValue = if (index == currentPage) {
                        accent
                    } else {
                        MiuixTheme.colorScheme.onSurface.copy(alpha = 0.18f)
                    },
                    animationSpec = if (motionEnabled) tween(180) else snap(),
                    label = "setup_step_color_$index",
                )
                Box(
                    modifier = Modifier
                        .width(32.dp)
                        .height(20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .width(dotWidth)
                            .height(7.dp)
                            .background(dotColor, CircleShape),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                text = stringResource(R.string.onboarding_previous),
                onClick = onPrevious,
                enabled = navigationEnabled && currentPage > 0,
                modifier = Modifier
                    .weight(0.38f)
                    .heightIn(min = 54.dp),
            )
            val primaryEnabled = navigationEnabled
            val primaryInteractionSource = remember { MutableInteractionSource() }
            val primaryPressed by primaryInteractionSource.collectIsPressedAsState()
            val primaryScale by animateFloatAsState(
                targetValue = if (primaryEnabled && primaryPressed) 0.975f else 1f,
                animationSpec = if (motionEnabled) {
                    tween(if (primaryPressed) 90 else 160, easing = FastOutSlowInEasing)
                } else {
                    snap()
                },
                label = "setup_primary_press",
            )
            Box(
                modifier = Modifier
                    .weight(0.62f)
                    .heightIn(min = 54.dp)
                    .graphicsLayer {
                        scaleX = primaryScale
                        scaleY = primaryScale
                    }
                    .clip(CircleShape)
                    .background(
                        if (primaryEnabled) {
                            actionColor
                        } else {
                            MiuixTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                        },
                    )
                    .clickable(
                        interactionSource = primaryInteractionSource,
                        indication = LocalIndication.current,
                        enabled = primaryEnabled,
                        role = Role.Button,
                        onClick = onNext,
                    )
                    .semantics { role = Role.Button },
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(
                    targetState = currentPage == ONBOARDING_PAGE_COUNT - 1,
                    transitionSpec = {
                        if (motionEnabled) {
                            fadeIn(tween(180)).togetherWith(fadeOut(tween(120)))
                        } else {
                            fadeIn(snap()).togetherWith(fadeOut(snap()))
                        }
                    },
                    label = "setup_primary_label",
                ) { isLastPage ->
                    Text(
                        text = stringResource(
                            if (isLastPage) R.string.onboarding_start else R.string.onboarding_next,
                        ),
                        color = Color.White.copy(alpha = if (primaryEnabled) 1f else 0.52f),
                        style = MiuixTheme.textStyles.headline1,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
