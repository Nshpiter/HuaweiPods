package moe.chenxy.huaweipods.ui.pages

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.chenxy.huaweipods.R
import moe.chenxy.huaweipods.config.ConfigManager
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference

@Composable
fun DeviceCapabilitiesPage(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    adaptiveCapabilityOverride: MutableState<Int> = mutableStateOf(ConfigManager.CAPABILITY_OVERRIDE_AUTO),
    onAdaptiveCapabilityOverrideChange: (Int) -> Unit = {},
    spatialAudioCapabilityOverride: MutableState<Int> = mutableStateOf(ConfigManager.CAPABILITY_OVERRIDE_AUTO),
    onSpatialAudioCapabilityOverrideChange: (Int) -> Unit = {},
    spatialSoundSwitchCapabilityOverride: MutableState<Int> = mutableStateOf(ConfigManager.CAPABILITY_OVERRIDE_AUTO),
    onSpatialSoundSwitchCapabilityOverrideChange: (Int) -> Unit = {},
    ancImplementationCapabilityOverride: MutableState<Int> = mutableStateOf(ConfigManager.CAPABILITY_OVERRIDE_AUTO),
    onAncImplementationCapabilityOverrideChange: (Int) -> Unit = {},
) {
    val overrideValues = listOf(
        ConfigManager.CAPABILITY_OVERRIDE_AUTO,
        ConfigManager.CAPABILITY_OVERRIDE_FORCE_DISABLED,
        ConfigManager.CAPABILITY_OVERRIDE_FORCE_ENABLED,
    )
    val overrideOptions = listOf(
        stringResource(R.string.capability_auto),
        stringResource(R.string.capability_force_disabled),
        stringResource(R.string.capability_force_enabled),
    )

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 12.dp,
            bottom = contentPadding.calculateBottomPadding() + 12.dp,
            start = 12.dp,
            end = 12.dp,
        ),
    ) {
        item {
            Card {
                OverlayDropdownPreference(
                    title = stringResource(R.string.adaptive_mode),
                    summary = stringResource(R.string.capability_override_summary),
                    items = overrideOptions,
                    selectedIndex = overrideValues.indexOf(adaptiveCapabilityOverride.value).coerceAtLeast(0),
                    onSelectedIndexChange = { onAdaptiveCapabilityOverrideChange(overrideValues[it]) },
                )
                OverlayDropdownPreference(
                    title = stringResource(R.string.spatial_audio),
                    summary = stringResource(R.string.capability_override_summary),
                    items = overrideOptions,
                    selectedIndex = overrideValues.indexOf(spatialAudioCapabilityOverride.value).coerceAtLeast(0),
                    onSelectedIndexChange = { onSpatialAudioCapabilityOverrideChange(overrideValues[it]) },
                )
                OverlayDropdownPreference(
                    title = stringResource(R.string.spatial_sound),
                    summary = stringResource(R.string.capability_override_summary),
                    items = overrideOptions,
                    selectedIndex = overrideValues.indexOf(spatialSoundSwitchCapabilityOverride.value).coerceAtLeast(0),
                    onSelectedIndexChange = { onSpatialSoundSwitchCapabilityOverrideChange(overrideValues[it]) },
                )
                OverlayDropdownPreference(
                    title = stringResource(R.string.anc_implementation),
                    summary = stringResource(R.string.anc_implementation_summary),
                    items = listOf(
                        stringResource(R.string.capability_auto),
                        stringResource(R.string.anc_implementation_standard),
                        stringResource(R.string.anc_implementation_compatible),
                    ),
                    selectedIndex = overrideValues.indexOf(ancImplementationCapabilityOverride.value).coerceAtLeast(0),
                    onSelectedIndexChange = { onAncImplementationCapabilityOverrideChange(overrideValues[it]) },
                )
            }
        }
    }
}
