package moe.chenxy.huaweipods.ui.pages

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import moe.chenxy.huaweipods.R
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.theme.MiuixTheme

private data class AboutReference(
    val name: String,
    @StringRes val summaryRes: Int,
    val url: String,
)

/** Dedicated, scrollable page for open-source references and acknowledgements. */
@Composable
internal fun AboutReferencesPage(
    onOpenReference: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = contentPadding.calculateTopPadding() + 12.dp,
            end = 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "referencesIntroduction") {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.about_references_page_intro),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                )
            }
        }
        item(key = "referencesIntegration") {
            ReferenceGroup(
                title = stringResource(R.string.about_references_integration_section),
                references = integrationReferences,
                onOpenReference = onOpenReference,
            )
        }
        item(key = "referencesInterface") {
            ReferenceGroup(
                title = stringResource(R.string.about_references_interface_section),
                references = interfaceReferences,
                onOpenReference = onOpenReference,
            )
        }
    }
}

@Composable
private fun ReferenceGroup(
    title: String,
    references: List<AboutReference>,
    onOpenReference: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SmallTitle(
            text = title,
            insideMargin = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            references.forEach { reference ->
                BasicComponent(
                    title = reference.name,
                    summary = stringResource(reference.summaryRes),
                    endActions = {
                        Icon(
                            imageVector = MiuixIcons.Link,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 6.dp),
                            tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                        )
                    },
                    insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    onClick = { onOpenReference(reference.url) },
                )
            }
        }
    }
}

private val integrationReferences = listOf(
    AboutReference(
        name = "OppoPods · 1812z",
        summaryRes = R.string.reference_oppopods_fork_summary,
        url = "https://github.com/1812z/OppoPods",
    ),
    AboutReference(
        name = "OppoPods · Leaf-lsgtky",
        summaryRes = R.string.reference_oppopods_upstream_summary,
        url = "https://github.com/Leaf-lsgtky/OppoPods",
    ),
    AboutReference(
        name = "HyperPods · Art_Chen",
        summaryRes = R.string.reference_hyperpods_summary,
        url = "https://github.com/Art-Chen/HyperPods",
    ),
    AboutReference(
        name = "OpenFreebuds · melianmiko",
        summaryRes = R.string.reference_openfreebuds_summary,
        url = "https://github.com/melianmiko/OpenFreebuds",
    ),
)

private val interfaceReferences = listOf(
    AboutReference(
        name = "HyperIsland · 1812z",
        summaryRes = R.string.reference_hyperisland_summary,
        url = "https://github.com/1812z/HyperIsland",
    ),
    AboutReference(
        name = "HyperLight · KiminonawaResa",
        summaryRes = R.string.reference_hyperlight_summary,
        url = "https://github.com/KiminonawaResa/HyperLight",
    ),
    AboutReference(
        name = "Miuix · YuKongA",
        summaryRes = R.string.reference_miuix_summary,
        url = "https://github.com/compose-miuix-ui/miuix",
    ),
)
