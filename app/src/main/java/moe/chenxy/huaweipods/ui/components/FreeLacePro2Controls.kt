package moe.chenxy.huaweipods.ui.components

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import moe.chenxy.huaweipods.R
import moe.chenxy.huaweipods.config.ConfigManager
import moe.chenxy.huaweipods.pods.HuaweiFreeBuds5Controller
import moe.chenxy.huaweipods.pods.HuaweiDeviceRoute
import moe.chenxy.huaweipods.pods.HuaweiEqualizerCodec
import moe.chenxy.huaweipods.pods.HuaweiEqualizerController
import moe.chenxy.huaweipods.pods.HuaweiEqualizerState
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private data class FreeLacePro2Preset(val id: Int, val labelRes: Int)

/**
 * FreeLace Pro 2 官方音效与自定义均衡器。
 *
 * 启用依据来自 RFCOMM 抓包：内置预设写包为 `2B 49 01 01 <id>`（id ∈ 1/2/3/9），
 * 自定义音效「我的音效」为自定义均衡预设 0x64（写包与 FreeBuds 6i 逐字节一致）。
 * 协议侧能力见 HuaweiEqualizerCodec.buildBuiltInPresetPacket / customWriteOperation。
 */
private val freeLacePro2Presets = listOf(
    FreeLacePro2Preset(0x01, R.string.freebuds5_sound_effect_default),
    FreeLacePro2Preset(0x02, R.string.freebuds5_sound_effect_bass),
    FreeLacePro2Preset(0x03, R.string.freebuds5_sound_effect_treble),
    FreeLacePro2Preset(0x09, R.string.freebuds5_sound_effect_clear_voice),
)

@Composable
fun FreeLacePro2Controls(address: String) {
    val context = LocalContext.current
    val selectedIdCache = remember(address) { context.eqSelectedId(address) }
    val highQualityKey = remember(address) { "freelace_pro2_${address.uppercase()}_high_quality" }
    var highQualityAudio by remember(address) {
        mutableStateOf(context.nullableBoolean(highQualityKey))
    }
    var equalizerState by remember(address) {
        mutableStateOf<HuaweiEqualizerState?>(
            selectedIdCache?.let { cachedId ->
                cachedInitState(context, address, cachedId)
            },
        )
    }

    fun updateCache(state: HuaweiEqualizerState?) {
        if (state == null) return
        equalizerState = state
        context.saveEqSelectedId(address, state.selectedId)
    }

    DisposableEffect(address, context) {
        var disposed = false
        // 进页面先用本地缓存立即展示当前音效，不阻塞等耳机回包
        equalizerState = selectedIdCache?.let { cachedInitState(context, address, it) }
            ?: equalizerState
        val device = context.freeLacePro2BluetoothDevice(address)
        if (device != null) {
            // 后台再向耳机请求一次权威状态；拿不到也不影响已展示的缓存
            HuaweiEqualizerController.requestState(
                context = context,
                device = device,
                route = HuaweiDeviceRoute.HUAWEI_FREELACE_PRO2,
            ) { state ->
                if (!disposed && state != null) updateCache(state)
            }
            HuaweiFreeBuds5Controller.requestHighQualityAudioState(context, device, HuaweiDeviceRoute.HUAWEI_FREELACE_PRO2) { value ->
                if (!disposed && value != null) {
                    highQualityAudio = value
                    context.saveBoolean(highQualityKey, value)
                }
            }
        }
        onDispose { disposed = true }
    }

    val selectedPreset = freeLacePro2Presets.firstOrNull { it.id == equalizerState?.selectedId }
    val customEffectName = stringResource(R.string.freebuds7i_custom_equalizer)
    val customSummary = if (equalizerState?.isCustom == true) {
        customEffectName
    } else {
        null
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        FreeLacePro2FeatureToggle(
            titleRes = R.string.freebuds5_high_quality_audio,
            value = highQualityAudio,
            onChange = { enabled, complete ->
                val device = context.freeLacePro2BluetoothDevice(address)
                if (device == null) {
                    complete(false)
                } else {
                    HuaweiFreeBuds5Controller.setHighQualityAudio(
                        context = context,
                        device = device,
                        route = HuaweiDeviceRoute.HUAWEI_FREELACE_PRO2,
                        enabled = enabled,
                    ) { success ->
                        if (success) {
                            highQualityAudio = enabled
                            context.saveBoolean(highQualityKey, enabled)
                        }
                        complete(success)
                    }
                }
            },
        )
        FreeBuds7iChoicePreference(
            title = stringResource(R.string.freebuds5_sound_effect),
            selected = selectedPreset,
            values = freeLacePro2Presets,
            label = { stringResource(it.labelRes) },
            summaryOverride = customSummary,
            onSelected = { preset, complete ->
                val device = context.freeLacePro2BluetoothDevice(address)
                if (device == null) {
                    complete(false)
                } else {
                    HuaweiEqualizerController.setBuiltInPreset(
                        context = context,
                        device = device,
                        route = HuaweiDeviceRoute.HUAWEI_FREELACE_PRO2,
                        presetId = preset.id,
                    ) { success ->
                        if (success) {
                            equalizerState = equalizerState?.copy(
                                selectedId = preset.id,
                                selectedName = null,
                                selectedGains = null,
                            ) ?: HuaweiEqualizerState(
                                supported = true,
                                selectedId = preset.id,
                                builtInIds = freeLacePro2Presets.map { it.id },
                                bandCount = HuaweiEqualizerCodec.BAND_COUNT,
                                selectedName = null,
                                selectedGains = null,
                                customPresets = emptyList(),
                            )
                            context.saveEqSelectedId(address, preset.id)
                        }
                        complete(success)
                    }
                }
            },
        )
        HuaweiEqualizerPreference(
            address = address,
            route = HuaweiDeviceRoute.HUAWEI_FREELACE_PRO2,
            readback = equalizerState,
            requestOnMount = false,
            onCustomApplied = { gains ->
                equalizerState = equalizerState?.copy(
                    selectedId = 0x64,
                    selectedName = customEffectName,
                    selectedGains = gains,
                ) ?: HuaweiEqualizerState(
                    supported = true,
                    selectedId = 0x64,
                    builtInIds = freeLacePro2Presets.map { it.id },
                    bandCount = HuaweiEqualizerCodec.BAND_COUNT,
                    selectedName = customEffectName,
                    selectedGains = gains,
                    customPresets = emptyList(),
                )
                context.saveEqSelectedId(address, 0x64)
            },
        )
    }
}

@Composable
private fun FreeLacePro2FeatureToggle(
    titleRes: Int,
    value: Boolean?,
    onChange: (Boolean, (Boolean) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    var pending by remember { mutableStateOf(false) }
    val toggle = {
        if (!pending) {
            pending = true
            onChange(value != true) { success ->
                pending = false
                if (!success) Toast.makeText(context, R.string.connect_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = !pending, role = Role.Switch, onClick = toggle)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(titleRes),
            color = MiuixTheme.colorScheme.onSurface,
            style = MiuixTheme.textStyles.headline1,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Checkbox(
            state = when (value) {
                true -> ToggleableState.On
                false -> ToggleableState.Off
                null -> ToggleableState.Indeterminate
            },
            enabled = !pending,
            onClick = toggle,
        )
    }
}

/** 依据本地缓存构建初始音效状态：缓存了内置预设 id 则直接还原，自定义则补上增益曲线。 */
private fun cachedInitState(
    context: Context,
    address: String,
    cachedId: Int,
): HuaweiEqualizerState = HuaweiEqualizerState(
    supported = true,
    selectedId = cachedId,
    builtInIds = freeLacePro2Presets.map { it.id },
    bandCount = HuaweiEqualizerCodec.BAND_COUNT,
    selectedName = if (cachedId == 0x64) {
        context.getString(R.string.freebuds7i_custom_equalizer)
    } else {
        null
    },
    selectedGains = if (cachedId == 0x64) context.eqCachedGains(address) else null,
    customPresets = emptyList(),
)

private const val EQ_SELECTED = "huawei_eq_selected_"

private fun Context.eqSelectedId(address: String): Int? =
    getSharedPreferences(ConfigManager.PREFS_NAME, Context.MODE_PRIVATE)
        .getInt(EQ_SELECTED + address.uppercase(), -1)
        .takeIf { it > 0 }

private fun Context.saveEqSelectedId(address: String, id: Int) {
    getSharedPreferences(ConfigManager.PREFS_NAME, Context.MODE_PRIVATE)
        .edit().putInt(EQ_SELECTED + address.uppercase(), id).apply()
}

private fun Context.eqCachedGains(address: String): List<Int> {
    val key = "huawei_eq_" + address.uppercase() + "_equalizer"
    return getSharedPreferences(ConfigManager.PREFS_NAME, Context.MODE_PRIVATE)
        .getString(key, null)
        ?.split(',')
        ?.mapNotNull(String::toIntOrNull)
        ?.takeIf { it.size == HuaweiEqualizerCodec.BAND_COUNT && it.all { value -> value in -60..60 } }
        ?: List(HuaweiEqualizerCodec.BAND_COUNT) { 0 }
}

private fun Context.nullableBoolean(key: String): Boolean? =
    getSharedPreferences(ConfigManager.PREFS_NAME, Context.MODE_PRIVATE)
        .takeIf { it.contains(key) }
        ?.getBoolean(key, false)

private fun Context.saveBoolean(key: String, value: Boolean) {
    getSharedPreferences(ConfigManager.PREFS_NAME, Context.MODE_PRIVATE)
        .edit().putBoolean(key, value).apply()
}

@SuppressLint("MissingPermission")
private fun Context.freeLacePro2BluetoothDevice(address: String) =
    takeIf { BluetoothAdapter.checkBluetoothAddress(address) }
        ?.getSystemService(BluetoothManager::class.java)
        ?.adapter
        ?.getRemoteDevice(address)