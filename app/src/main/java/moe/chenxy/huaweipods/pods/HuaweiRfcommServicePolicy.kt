package moe.chenxy.huaweipods.pods

import java.util.UUID

/** Ported from upstream 59d5fa8: audio services do not accept Huawei AAM control frames. */
internal object HuaweiRfcommServicePolicy {
    private val audioServiceIds = setOf(0x1108, 0x110A, 0x110B, 0x110C, 0x110D, 0x110E, 0x110F,
        0x1112, 0x111E, 0x111F)

    fun isControlCandidate(uuid: UUID): Boolean {
        val value = uuid.toString()
        if (!value.endsWith("-0000-1000-8000-00805f9b34fb") || !value.startsWith("0000")) return true
        val serviceId = value.substring(4, 8).toIntOrNull(16) ?: return true
        return serviceId !in audioServiceIds
    }
}
