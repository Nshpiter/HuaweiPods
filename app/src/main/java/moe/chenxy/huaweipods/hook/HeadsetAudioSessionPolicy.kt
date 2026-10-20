package moe.chenxy.huaweipods.hook

import android.bluetooth.BluetoothProfile

internal enum class AudioSessionDecision { IGNORE, CONNECT, KEEP, DISCONNECT }

/** Broadcasts are hints. Only fresh states for this address can finish or start a session. */
internal object HeadsetAudioSessionPolicy {
    fun decide(currentAddress: String?, address: String, profile: Int?, eventState: Int?,
               a2dp: Int?, hfp: Int?, restore: Boolean = false): AudioSessionDecision {
        val current = currentAddress?.equals(address, true) == true
        val connected = a2dp == BluetoothProfile.STATE_CONNECTED || hfp == BluetoothProfile.STATE_CONNECTED
        if (connected) {
            if (current) return AudioSessionDecision.KEEP
            val actual = if (profile == BluetoothProfile.A2DP) a2dp else if (profile == BluetoothProfile.HEADSET) hfp else null
            val newConnection = eventState == BluetoothProfile.STATE_CONNECTED && actual == BluetoothProfile.STATE_CONNECTED
            return if (newConnection || (restore && currentAddress == null)) AudioSessionDecision.CONNECT
                else AudioSessionDecision.IGNORE
        }
        return if (current && a2dp == BluetoothProfile.STATE_DISCONNECTED && hfp == BluetoothProfile.STATE_DISCONNECTED)
            AudioSessionDecision.DISCONNECT else if (current) AudioSessionDecision.KEEP else AudioSessionDecision.IGNORE
    }
}

internal class AudioSessionProbeGuard {
    data class Token(val epoch: Long, val address: String, val revision: Long)
    private var epoch = 0L
    private var revision = 0L
    private val latest = linkedMapOf<String, Long>()
    fun begin(address: String): Token {
        val key = address.uppercase()
        latest[key] = ++revision
        if (latest.size > 32) latest.remove(latest.keys.first())
        return Token(epoch, key, revision)
    }
    fun accepts(token: Token): Boolean = token.epoch == epoch && latest[token.address] == token.revision
    fun reset() { epoch++; latest.clear() }
}
