package moe.chenxy.huaweipods.pods

enum class GameModeImplementation(val preferenceValue: String) {
    STANDARD("standard"),
    COMPATIBLE("compatible");

    companion object {
        const val PREF_KEY = "game_mode_implementation"

        fun fromPreference(value: String?): GameModeImplementation {
            return entries.firstOrNull { it.preferenceValue == value } ?: STANDARD
        }

        fun fromSelectedIndex(index: Int): GameModeImplementation {
            return entries.getOrNull(index) ?: STANDARD
        }

        fun selectedIndexOf(implementation: GameModeImplementation): Int {
            return entries.indexOf(implementation).takeIf { it >= 0 } ?: 0
        }
    }
}
