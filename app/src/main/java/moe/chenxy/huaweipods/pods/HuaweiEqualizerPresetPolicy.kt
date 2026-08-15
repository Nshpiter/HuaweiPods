package moe.chenxy.huaweipods.pods

import java.nio.charset.StandardCharsets

/** Pure rules shared by the custom-effect editor and the official EQ bridge. */
object HuaweiEqualizerPresetPolicy {
    const val FIRST_CUSTOM_ID = 0x64
    const val LAST_CUSTOM_ID = 0x66
    const val MAX_NAME_BYTES = 32

    fun nextAvailableId(presets: Collection<HuaweiEqualizerPreset>): Int? {
        val occupied = presets.mapTo(mutableSetOf()) { it.id }
        return (FIRST_CUSTOM_ID..LAST_CUSTOM_ID).firstOrNull { it !in occupied }
    }

    fun normalizeName(value: String): String? = value.trim()
        .takeIf { name ->
            name.isNotEmpty() && name.toByteArray(StandardCharsets.UTF_8).size <= MAX_NAME_BYTES
        }

    fun upsert(
        presets: Collection<HuaweiEqualizerPreset>,
        preset: HuaweiEqualizerPreset,
    ): List<HuaweiEqualizerPreset> {
        require(preset.id in FIRST_CUSTOM_ID..LAST_CUSTOM_ID)
        return (presets.filterNot { it.id == preset.id } + preset).sortedBy(HuaweiEqualizerPreset::id)
    }
}
