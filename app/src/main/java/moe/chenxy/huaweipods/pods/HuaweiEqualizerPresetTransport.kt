package moe.chenxy.huaweipods.pods

import android.content.Intent
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.HuaweiPodsAction

/** 自定义均衡器预设在模块、蓝牙与融合中心进程之间传递时使用的紧凑格式。 */
internal object HuaweiEqualizerPresetTransport {
    data class Payload(
        val ids: List<Int>,
        val names: List<String>,
        val gains: List<Int>,
    )

    fun encode(presets: Collection<HuaweiEqualizerPreset>): Payload {
        val valid = presets
            .mapNotNull { preset ->
                val name = HuaweiEqualizerPresetPolicy.normalizeName(preset.name)
                    ?: return@mapNotNull null
                preset.takeIf {
                    it.id in HuaweiEqualizerPresetPolicy.FIRST_CUSTOM_ID..
                        HuaweiEqualizerPresetPolicy.LAST_CUSTOM_ID &&
                        it.gains.size == HuaweiEqualizerCodec.BAND_COUNT &&
                        it.gains.all { gain -> gain in HuaweiEqualizerCodec.GAIN_RANGE }
                }?.copy(name = name)
            }
            .distinctBy(HuaweiEqualizerPreset::id)
            .sortedBy(HuaweiEqualizerPreset::id)
        return Payload(
            ids = valid.map(HuaweiEqualizerPreset::id),
            names = valid.map(HuaweiEqualizerPreset::name),
            gains = valid.flatMap(HuaweiEqualizerPreset::gains),
        )
    }

    fun decode(
        ids: List<Int>?,
        names: List<String>?,
        gains: List<Int>?,
    ): List<HuaweiEqualizerPreset>? {
        if (ids == null || names == null || gains == null) return null
        if (ids.size != names.size || ids.size > 3 ||
            gains.size != ids.size * HuaweiEqualizerCodec.BAND_COUNT ||
            ids.distinct().size != ids.size
        ) {
            return null
        }
        val presets = ids.mapIndexed { index, id ->
            val name = HuaweiEqualizerPresetPolicy.normalizeName(names[index]) ?: return null
            if (id !in HuaweiEqualizerPresetPolicy.FIRST_CUSTOM_ID..
                HuaweiEqualizerPresetPolicy.LAST_CUSTOM_ID
            ) {
                return null
            }
            val values = gains.subList(
                index * HuaweiEqualizerCodec.BAND_COUNT,
                (index + 1) * HuaweiEqualizerCodec.BAND_COUNT,
            )
            if (values.any { it !in HuaweiEqualizerCodec.GAIN_RANGE }) return null
            HuaweiEqualizerPreset(id = id, name = name, gains = values.toList())
        }
        return presets.sortedBy(HuaweiEqualizerPreset::id)
    }
}

internal fun Intent.putHuaweiEqualizerCustomPresets(
    presets: Collection<HuaweiEqualizerPreset>,
) {
    val payload = HuaweiEqualizerPresetTransport.encode(presets)
    putExtra(HuaweiPodsAction.EXTRA_FREECLIP2_BRIDGE_EQ_CUSTOM_IDS, payload.ids.toIntArray())
    putStringArrayListExtra(
        HuaweiPodsAction.EXTRA_FREECLIP2_BRIDGE_EQ_CUSTOM_NAMES,
        ArrayList(payload.names),
    )
    putExtra(
        HuaweiPodsAction.EXTRA_FREECLIP2_BRIDGE_EQ_CUSTOM_GAINS,
        payload.gains.toIntArray(),
    )
}

/** null 表示广播没有携带预设字段，空列表表示发送方明确报告当前没有自定义预设。 */
internal fun Intent.readHuaweiEqualizerCustomPresets(): List<HuaweiEqualizerPreset>? {
    if (!hasExtra(HuaweiPodsAction.EXTRA_FREECLIP2_BRIDGE_EQ_CUSTOM_IDS) &&
        !hasExtra(HuaweiPodsAction.EXTRA_FREECLIP2_BRIDGE_EQ_CUSTOM_NAMES) &&
        !hasExtra(HuaweiPodsAction.EXTRA_FREECLIP2_BRIDGE_EQ_CUSTOM_GAINS)
    ) {
        return null
    }
    return HuaweiEqualizerPresetTransport.decode(
        ids = getIntArrayExtra(HuaweiPodsAction.EXTRA_FREECLIP2_BRIDGE_EQ_CUSTOM_IDS)?.toList(),
        names = getStringArrayListExtra(
            HuaweiPodsAction.EXTRA_FREECLIP2_BRIDGE_EQ_CUSTOM_NAMES,
        ),
        gains = getIntArrayExtra(
            HuaweiPodsAction.EXTRA_FREECLIP2_BRIDGE_EQ_CUSTOM_GAINS,
        )?.toList(),
    )
}
