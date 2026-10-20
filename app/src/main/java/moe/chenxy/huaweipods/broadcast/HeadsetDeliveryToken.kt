package moe.chenxy.huaweipods.broadcast

internal data class HeadsetDeliveryToken(val client: String, val transport: Long, val event: Long) {
    fun matches(other: HeadsetDeliveryToken): Boolean = client.isNotBlank() && transport > 0 && event > 0 && this == other
}
