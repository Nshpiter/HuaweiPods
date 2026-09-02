package moe.chenxy.huaweipods.ui.pages

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import moe.chenxy.huaweipods.R
import moe.chenxy.huaweipods.ui.components.effect.Os3BackgroundEffect
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.icon.extended.Messages
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

sealed interface UpdateCheckSummary {
    data class UpToDate(val versionName: String) : UpdateCheckSummary

    data class Available(val versionName: String) : UpdateCheckSummary

    data object Failure : UpdateCheckSummary
}

/** Actions used by the dedicated About tab. */
internal data class AboutPageActions(
    val appVersion: String,
    val checkingForUpdates: Boolean,
    val updateCheckSummary: UpdateCheckSummary?,
    val onCheckForUpdates: () -> Unit,
    val onPreviewUpdateDialog: (() -> Unit)?,
    val onOpenDeveloper: () -> Unit,
    val onOpenGitHub: () -> Unit,
    val onOpenChangelog: () -> Unit,
    val onOpenIssues: () -> Unit,
    val onCopyQqGroup: () -> Unit,
    val qqGroupNumber: String,
    val onOpenOnboarding: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onOpenReferences: () -> Unit,
)

/**
 * About page following the SonyPods/HyperIsland composition: an OS3 colour field,
 * a fixed hero, and the developer/source cards anchored to the first screen.
 * Background animation pauses while the list is moving so it does not compete with a fling.
 */
@Composable
fun AboutPage(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    appVersion: String,
    checkingForUpdates: Boolean,
    updateCheckSummary: UpdateCheckSummary?,
    onCheckForUpdates: () -> Unit,
    onPreviewUpdateDialog: (() -> Unit)? = null,
    onOpenGitHub: () -> Unit,
    onOpenChangelog: () -> Unit,
    onOpenIssues: () -> Unit,
    onCopyQqGroup: () -> Unit,
    qqGroupNumber: String,
    onOpenOnboarding: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenReferences: () -> Unit,
    onOpenDeveloper: () -> Unit = onOpenGitHub,
    showTopBar: Boolean = false,
) {
    val surface = MiuixTheme.colorScheme.surface
    val darkTheme = isSystemInDarkTheme()
    val density = LocalDensity.current
    var heroHeight by remember { mutableStateOf(280.dp) }
    val listState = rememberLazyListState()
    val topAppBarScrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    val scrollProgress by remember(listState) {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) {
                1f
            } else {
                val spacer = listState.layoutInfo.visibleItemsInfo
                    .firstOrNull { it.key == HERO_SPACER_KEY }
                val budget = spacer?.size?.toFloat()
                    ?: with(density) { heroSpacerHeight(heroHeight).toPx() }
                (listState.firstVisibleItemScrollOffset / budget).coerceIn(0f, 1f)
            }
        }
    }
    val collapsed by remember { derivedStateOf { scrollProgress >= 0.98f } }

    @Composable
    fun Body(scaffoldPadding: PaddingValues) {
        val contentTopPadding = scaffoldPadding.calculateTopPadding()
        val bottomPadding = scaffoldPadding.calculateBottomPadding() + contentPadding.calculateBottomPadding()
        Os3BackgroundEffect(
            dynamicBackground = !listState.isScrollInProgress,
            modifier = modifier.fillMaxSize(),
            isFullSize = true,
            isDarkTheme = darkTheme,
            alpha = { 1f - scrollProgress },
        ) {
            AboutHero(
                appVersion = appVersion,
                darkTheme = darkTheme,
                scrollProgress = scrollProgress,
                contentTopPadding = contentTopPadding,
                onHeightChanged = { measuredHeight -> heroHeight = measuredHeight },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
            )
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .overScrollVertical()
                    .then(
                        if (showTopBar) {
                            Modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection)
                        } else {
                            Modifier
                        },
                    ),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = contentTopPadding,
                    end = 16.dp,
                    bottom = bottomPadding + 28.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = HERO_SPACER_KEY) {
                    Spacer(Modifier.height(heroSpacerHeight(heroHeight)))
                }
                item(key = ABOUT_CONTENT_KEY) {
                    Column {
                        AboutSectionTitle(stringResource(R.string.about_developer_section))
                        DeveloperCard(onOpenDeveloper = onOpenDeveloper)
                        Spacer(Modifier.height(12.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.defaultColors(),
                        ) {
                            AboutSourceRow(
                                title = stringResource(R.string.about_source_code),
                                endLabel = "GitHub",
                                onClick = onOpenGitHub,
                            )
                        }
                    }
                }
                item(key = "aboutCommunity") {
                    AboutSectionTitle(stringResource(R.string.about_community_section))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.defaultColors(),
                    ) {
                        AboutAvatarRow(
                            title = stringResource(R.string.qq_group),
                            summary = stringResource(R.string.qq_group_summary, qqGroupNumber),
                            onClick = onCopyQqGroup,
                        )
                    }
                }
                item(key = "aboutModule") {
                    AboutSectionTitle(stringResource(R.string.about_module_section))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.defaultColors(),
                    ) {
                        AboutLinkRow(
                            title = stringResource(R.string.check_for_updates),
                            summary = updateSummary(
                                checkingForUpdates = checkingForUpdates,
                                updateCheckSummary = updateCheckSummary,
                            ),
                            icon = MiuixIcons.Refresh,
                            onClick = onCheckForUpdates,
                            trailing = if (checkingForUpdates) {
                                {
                                    InfiniteProgressIndicator(
                                        color = MiuixTheme.colorScheme.primary,
                                        size = 21.dp,
                                    )
                                }
                            } else {
                                null
                            },
                        )
                        if (onPreviewUpdateDialog != null) {
                            AboutLinkRow(
                                title = stringResource(R.string.preview_update_dialog),
                                summary = stringResource(R.string.preview_update_dialog_summary),
                                icon = MiuixIcons.Info,
                                onClick = onPreviewUpdateDialog,
                            )
                        }
                        AboutLinkRow(
                            title = stringResource(R.string.open_onboarding),
                            summary = stringResource(R.string.open_onboarding_summary),
                            icon = MiuixIcons.Info,
                            onClick = onOpenOnboarding,
                        )
                    }
                }
                item(key = "aboutProject") {
                    AboutSectionTitle(stringResource(R.string.about_project_section))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.defaultColors(),
                    ) {
                        AboutLinkRow(
                            title = stringResource(R.string.changelog),
                            summary = stringResource(R.string.changelog_summary),
                            icon = MiuixIcons.Info,
                            trailingIcon = MiuixIcons.Link,
                            onClick = onOpenChangelog,
                        )
                        AboutLinkRow(
                            title = stringResource(R.string.github_issues),
                            summary = stringResource(R.string.github_issues_summary),
                            icon = MiuixIcons.Messages,
                            trailingIcon = MiuixIcons.Link,
                            onClick = onOpenIssues,
                        )
                        AboutLinkRow(
                            title = stringResource(R.string.about_references),
                            summary = stringResource(R.string.about_references_summary),
                            icon = MiuixIcons.Info,
                            onClick = onOpenReferences,
                        )
                    }
                }
            }
        }
    }

    if (showTopBar) {
        Scaffold(
            topBar = {
                SmallTopAppBar(
                    title = stringResource(R.string.about_tab),
                    scrollBehavior = topAppBarScrollBehavior,
                    color = if (collapsed) surface else Color.Transparent,
                    titleColor = MiuixTheme.colorScheme.onSurface.copy(
                        alpha = ((scrollProgress - 0.35f) / 0.65f).coerceIn(0f, 1f),
                    ),
                    actions = {
                        IconButton(
                            onClick = onOpenSettings,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(surface.copy(alpha = 0.72f))
                                .border(
                                    width = 1.dp,
                                    color = MiuixTheme.colorScheme.outline.copy(alpha = 0.28f),
                                    shape = CircleShape,
                                ),
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Settings,
                                contentDescription = stringResource(R.string.settings),
                                tint = MiuixTheme.colorScheme.onSurface,
                            )
                        }
                    },
                )
            },
        ) { scaffoldPadding ->
            Body(scaffoldPadding)
        }
    } else {
        Body(PaddingValues(0.dp))
    }

}

/** Full-bleed version used by the About bottom-navigation item. */
@Composable
internal fun AboutTabPage(
    actions: AboutPageActions,
    pageBottomContentPadding: Dp,
) {
    AboutPage(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = pageBottomContentPadding),
        appVersion = actions.appVersion,
        checkingForUpdates = actions.checkingForUpdates,
        updateCheckSummary = actions.updateCheckSummary,
        onCheckForUpdates = actions.onCheckForUpdates,
        onPreviewUpdateDialog = actions.onPreviewUpdateDialog,
        onOpenDeveloper = actions.onOpenDeveloper,
        onOpenGitHub = actions.onOpenGitHub,
        onOpenChangelog = actions.onOpenChangelog,
        onOpenIssues = actions.onOpenIssues,
        onCopyQqGroup = actions.onCopyQqGroup,
        qqGroupNumber = actions.qqGroupNumber,
        onOpenOnboarding = actions.onOpenOnboarding,
        onOpenSettings = actions.onOpenSettings,
        onOpenReferences = actions.onOpenReferences,
        showTopBar = true,
    )
}

@Composable
private fun AboutHero(
    appVersion: String,
    darkTheme: Boolean,
    scrollProgress: Float,
    contentTopPadding: Dp,
    onHeightChanged: (Dp) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val heroDescription = stringResource(R.string.about_hero_description, appVersion)
    Column(
        modifier = modifier
            .padding(top = contentTopPadding + HERO_EXTRA_TOP + HERO_TOP_BAR_BUDGET)
            .onSizeChanged { size ->
                with(density) { onHeightChanged(size.height.toDp()) }
            }
            .clearAndSetSemantics { contentDescription = heroDescription },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AboutArtwork(
            resourceId = R.drawable.about_logo_mark,
            darkTheme = darkTheme,
            modifier = Modifier
                .size(88.dp)
                .graphicsLayer {
                    val progress = ((scrollProgress - 0.35f) / 0.15f).coerceIn(0f, 1f)
                    alpha = 1f - progress
                    scaleX = 1f - progress * 0.05f
                    scaleY = 1f - progress * 0.05f
                },
        )
        Spacer(Modifier.height(12.dp))
        AboutArtwork(
            resourceId = R.drawable.about_wordmark,
            darkTheme = darkTheme,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .width(305.8.dp)
                .height(40.dp)
                .graphicsLayer {
                    val progress = ((scrollProgress - 0.20f) / 0.15f).coerceIn(0f, 1f)
                    alpha = 1f - progress
                    scaleX = 1f - progress * 0.05f
                    scaleY = 1f - progress * 0.05f
                },
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = appVersion.replace(" (", "("),
            modifier = Modifier.graphicsLayer {
                val progress = ((scrollProgress - 0.05f) / 0.15f).coerceIn(0f, 1f)
                alpha = 1f - progress
                scaleX = 1f - progress * 0.05f
                scaleY = 1f - progress * 0.05f
            },
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun AboutArtwork(
    resourceId: Int,
    darkTheme: Boolean,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
) {
    Image(
        painter = painterResource(resourceId),
        contentDescription = null,
        contentScale = contentScale,
        colorFilter = ColorFilter.tint(aboutArtworkTint(darkTheme)),
        modifier = modifier,
    )
}

@Composable
private fun DeveloperCard(onOpenDeveloper: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(),
        onClick = onOpenDeveloper,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.about_developer_avatar),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .border(2.dp, Color.White.copy(alpha = 0.68f), CircleShape),
            )
            Column(
                modifier = Modifier.padding(start = 14.dp),
            ) {
                Text(
                    text = stringResource(R.string.about_developer_name),
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.about_developer_handle),
                    modifier = Modifier.padding(top = 1.dp),
                    fontSize = 14.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Spacer(Modifier.weight(1f))
            Icon(
                imageVector = MiuixIcons.ChevronForward,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
            )
        }
    }
}

@Composable
private fun AboutLinkRow(
    title: String,
    summary: String? = null,
    icon: ImageVector,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
    trailingIcon: ImageVector = MiuixIcons.ChevronForward,
) {
    BasicComponent(
        title = title,
        summary = summary,
        startAction = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier
                    .padding(end = 16.dp)
                    .size(22.dp),
                tint = MiuixTheme.colorScheme.onBackground,
            )
        },
        endActions = {
            trailing?.invoke() ?: Icon(
                imageVector = trailingIcon,
                contentDescription = null,
                modifier = if (trailingIcon == MiuixIcons.ChevronForward) {
                    Modifier
                } else {
                    Modifier.padding(end = 8.dp).size(22.dp)
                },
                tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
            )
        },
        insideMargin = SETTINGS_ITEM_MARGIN,
        onClick = onClick,
    )
}

@Composable
private fun AboutAvatarRow(
    title: String,
    summary: String,
    onClick: () -> Unit,
) {
    BasicComponent(
        title = title,
        summary = summary,
        startAction = {
            Image(
                painter = painterResource(R.drawable.qq_group_avatar),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .padding(end = 14.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .border(
                        width = 1.dp,
                        color = MiuixTheme.colorScheme.outline.copy(alpha = 0.4f),
                        shape = CircleShape,
                    ),
            )
        },
        endActions = {
            Icon(
                imageVector = MiuixIcons.Copy,
                contentDescription = null,
                modifier = Modifier.padding(end = 8.dp).size(22.dp),
                tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
            )
        },
        insideMargin = SETTINGS_ITEM_MARGIN,
        onClick = onClick,
    )
}

@Composable
private fun AboutSourceRow(
    title: String,
    endLabel: String? = null,
    onClick: () -> Unit,
) {
    if (endLabel != null) {
        ArrowPreference(
            title = title,
            endActions = {
                Text(
                    text = endLabel,
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                )
            },
            onClick = onClick,
        )
    } else {
        ArrowPreference(
            title = title,
            onClick = onClick,
        )
    }
}

@Composable
private fun AboutSectionTitle(text: String) {
    SmallTitle(
        text = text,
        modifier = Modifier.padding(top = 4.dp),
        insideMargin = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
    )
}

@Composable
private fun updateSummary(
    checkingForUpdates: Boolean,
    updateCheckSummary: UpdateCheckSummary?,
): String = when {
    checkingForUpdates -> stringResource(R.string.checking_for_updates)
    else -> when (val result = updateCheckSummary) {
        is UpdateCheckSummary.UpToDate -> stringResource(
            R.string.already_latest_version_inline,
            result.versionName,
        )
        is UpdateCheckSummary.Available -> stringResource(
            R.string.update_available_inline,
            result.versionName,
        )
        UpdateCheckSummary.Failure -> stringResource(R.string.update_check_failed_summary)
        null -> stringResource(R.string.check_for_updates_summary)
    }
}

private fun aboutArtworkTint(darkTheme: Boolean): Color =
    if (darkTheme) ABOUT_ARTWORK_DARK_TINT else ABOUT_ARTWORK_LIGHT_TINT

private fun heroSpacerHeight(heroHeight: Dp): Dp =
    heroHeight + HERO_TOP_BAR_BUDGET + HERO_EXTRA_TOP + HERO_SCROLL_BUDGET

private const val HERO_SPACER_KEY = "aboutHeroSpacer"
private const val ABOUT_CONTENT_KEY = "aboutContent"
private val HERO_EXTRA_TOP = 40.dp
private val HERO_TOP_BAR_BUDGET = 52.dp
private val HERO_SCROLL_BUDGET = 126.dp
private val SETTINGS_ITEM_MARGIN = PaddingValues(horizontal = 18.dp, vertical = 14.dp)
private val ABOUT_ARTWORK_LIGHT_TINT = Color(0xFF8C50A0)
private val ABOUT_ARTWORK_DARK_TINT = Color(0xFFE1B9EC)
