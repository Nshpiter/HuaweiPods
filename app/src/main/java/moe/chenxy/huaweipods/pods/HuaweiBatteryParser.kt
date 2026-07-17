package moe.chenxy.huaweipods.pods

import moe.chenxy.huaweipods.utils.miuiStrongToast.data.BatteryParams
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.PodParams

object HuaweiBatteryParser {
    private val batteryPattern = Regex(
        """(?:AT)?\+?HUAWEIBATTERY\s*[=:]\s*([0-9,\s]+)""",
        RegexOption.IGNORE_CASE
    )

    data class Result(
        val battery: BatteryParams,
        val values: Map<Int, Int>
    )

    fun parse(text: String?): Result? {
        if (text.isNullOrBlank()) return null
        val payload = batteryPattern.find(text)?.groupValues?.getOrNull(1) ?: return null
        val numbers = payload.split(',')
            .mapNotNull { it.trim().takeIf(String::isNotEmpty)?.toIntOrNull() }
        if (numbers.size < 2) return null

        val pairValues = payloadValues(numbers)
        if (pairValues.size < 2) return null

        val values = linkedMapOf<Int, Int>()
        var index = 0
        while (index + 1 < pairValues.size) {
            values[pairValues[index]] = pairValues[index + 1]
            index += 2
        }

        val battery = BatteryParams(
            left = pod(values, BATTERY_LEFT, CHARGING_LEFT),
            right = pod(values, BATTERY_RIGHT, CHARGING_RIGHT),
            case = pod(values, BATTERY_CASE, CHARGING_CASE),
        )

        return Result(battery, values).takeIf {
            battery.left != null || battery.right != null || battery.case != null
        }
    }

    private fun payloadValues(numbers: List<Int>): List<Int> {
        val count = numbers.firstOrNull() ?: return numbers
        val expectedPayloadSize = count * 2
        return if (count > 0 && numbers.size >= expectedPayloadSize + 1) {
            numbers.drop(1).take(expectedPayloadSize)
        } else {
            numbers
        }
    }

    private fun pod(values: Map<Int, Int>, batteryKey: Int, chargingKey: Int): PodParams? {
        val level = values[batteryKey]?.takeIf { it in 0..100 } ?: return null
        val chargingValue = values[chargingKey] ?: 0
        return PodParams(
            battery = level,
            isCharging = chargingValue != 0,
            isConnected = true,
            rawStatus = chargingValue
        )
    }

    private const val BATTERY_LEFT = 2
    private const val CHARGING_LEFT = 3
    private const val BATTERY_RIGHT = 4
    private const val CHARGING_RIGHT = 5
    private const val BATTERY_CASE = 6
    private const val CHARGING_CASE = 7
}
