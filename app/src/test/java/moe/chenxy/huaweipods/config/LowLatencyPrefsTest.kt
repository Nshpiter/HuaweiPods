package moe.chenxy.huaweipods.config

import android.content.SharedPreferences
import java.lang.reflect.Proxy
import java.util.concurrent.ConcurrentHashMap
import moe.chenxy.huaweipods.pods.HuaweiDeviceRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LowLatencyPrefsTest {
    private val address = "AA:BB:CC:DD:EE:FF"

    @Test
    fun `uses one canonical per-address key for verified low latency routes`() {
        assertEquals(
            "low_latency_auto_v1_AABBCCDDEEFF",
            LowLatencyPrefs.preferenceKey(address.lowercase(), HuaweiDeviceRoute.HUAWEI_FREEBUDS5),
        )
        assertEquals(
            "low_latency_auto_v1_AABBCCDDEEFF",
            LowLatencyPrefs.preferenceKey(address, HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3),
        )
    }

    @Test
    fun `rejects unsupported routes and malformed addresses`() {
        assertNull(LowLatencyPrefs.preferenceKey(address, HuaweiDeviceRoute.HUAWEI_FREEBUDS3))
        assertNull(LowLatencyPrefs.preferenceKey("not-an-address", HuaweiDeviceRoute.HUAWEI_FREEBUDS5))
    }

    @Test
    fun `distinguishes unset disabled and enabled policies`() {
        val prefs = inMemoryPreferences()
        assertNull(LowLatencyPrefs.desiredOrNull(prefs, address, HuaweiDeviceRoute.HUAWEI_FREEBUDS5))

        prefs.edit()
            .putBoolean(requireNotNull(LowLatencyPrefs.preferenceKey(address, HuaweiDeviceRoute.HUAWEI_FREEBUDS5)), false)
            .commit()
        assertFalse(requireNotNull(LowLatencyPrefs.desiredOrNull(prefs, address, HuaweiDeviceRoute.HUAWEI_FREEBUDS5)))

        prefs.edit()
            .putBoolean(requireNotNull(LowLatencyPrefs.preferenceKey(address, HuaweiDeviceRoute.HUAWEI_FREEBUDS5)), true)
            .commit()
        assertTrue(requireNotNull(LowLatencyPrefs.desiredOrNull(prefs, address, HuaweiDeviceRoute.HUAWEI_FREEBUDS5)))
    }

    @Test
    fun `reads legacy FreeBuds 5 and FreeClip 2 values after upgrade`() {
        val prefs = inMemoryPreferences()
        prefs.edit().putBoolean("freebuds5_AA:BB:CC:DD:EE:FF_low_latency", true).commit()
        assertTrue(
            requireNotNull(
                LowLatencyPrefs.desiredOrNull(
                    prefs,
                    address,
                    HuaweiDeviceRoute.HUAWEI_FREEBUDS5,
                ),
            ),
        )
    }

    @Test
    fun `local policy wins while missing remote policies are recovered`() {
        val local = inMemoryPreferences()
        val remote = inMemoryPreferences()
        val freeBudsKey = requireNotNull(
            LowLatencyPrefs.preferenceKey(address, HuaweiDeviceRoute.HUAWEI_FREEBUDS5),
        )
        val otherAddress = "11:22:33:44:55:66"
        val freeClipKey = requireNotNull(
            LowLatencyPrefs.preferenceKey(otherAddress, HuaweiDeviceRoute.HUAWEI_FREECLIP2),
        )
        local.edit().putBoolean(freeBudsKey, true).commit()
        remote.edit().putBoolean(freeBudsKey, false).putBoolean(freeClipKey, true).commit()

        LowLatencyPrefs.syncWithRemote(local, remote)

        assertTrue(remote.getBoolean(freeBudsKey, false))
        assertTrue(local.getBoolean(freeClipKey, false))
    }

    @Test
    fun `hook processes share the same desired state writer`() {
        val prefs = inMemoryPreferences()
        LowLatencyPrefs.attachHookPreferences(prefs)

        assertTrue(
            LowLatencyPrefs.setDesiredFromHook(
                address,
                HuaweiDeviceRoute.HUAWEI_FREEBUDS5,
                true,
            ),
        )
        assertTrue(
            requireNotNull(
                LowLatencyPrefs.desiredForHook(
                    address,
                    HuaweiDeviceRoute.HUAWEI_FREEBUDS5,
                ),
            ),
        )
        assertFalse(
            LowLatencyPrefs.setDesiredFromHook(
                address,
                HuaweiDeviceRoute.HUAWEI_FREEBUDS3,
                true,
            ),
        )
    }

    @Test
    fun `read-only hook preferences fail closed instead of crashing the host`() {
        val readOnlyPrefs = Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader ?: javaClass.classLoader,
            arrayOf(SharedPreferences::class.java),
        ) { _, method, _ ->
            if (method.name == "edit") {
                throw UnsupportedOperationException("Read only implementation")
            }
            null
        } as SharedPreferences
        LowLatencyPrefs.attachHookPreferences(readOnlyPrefs)

        try {
            assertFalse(
                LowLatencyPrefs.setDesiredFromHook(
                    address,
                    HuaweiDeviceRoute.HUAWEI_FREEBUDS5,
                    true,
                ),
            )
        } finally {
            LowLatencyPrefs.attachHookPreferences(inMemoryPreferences())
        }
    }

    private fun inMemoryPreferences(): SharedPreferences {
        val values = ConcurrentHashMap<String, Any>()
        return Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "getAll" -> values.toMap()
                "getBoolean" -> values[args?.get(0) as String] as? Boolean ?: args[1]
                "contains" -> values.containsKey(args?.get(0) as String)
                "edit" -> inMemoryEditor(values)
                "registerOnSharedPreferenceChangeListener",
                "unregisterOnSharedPreferenceChangeListener" -> Unit
                "toString" -> "LowLatencyPrefsTestPreferences"
                else -> when (method.returnType) {
                    Boolean::class.javaPrimitiveType -> false
                    Int::class.javaPrimitiveType -> 0
                    Long::class.javaPrimitiveType -> 0L
                    Float::class.javaPrimitiveType -> 0f
                    else -> proxy
                }
            }
        } as SharedPreferences
    }

    private fun inMemoryEditor(values: ConcurrentHashMap<String, Any>): SharedPreferences.Editor {
        val pending = linkedMapOf<String, Boolean?>()
        lateinit var editor: SharedPreferences.Editor
        editor = Proxy.newProxyInstance(
            SharedPreferences.Editor::class.java.classLoader,
            arrayOf(SharedPreferences.Editor::class.java),
        ) { _, method, args ->
            when (method.name) {
                "putBoolean" -> {
                    pending[args?.get(0) as String] = args[1] as Boolean
                    editor
                }
                "remove" -> {
                    pending[args?.get(0) as String] = null
                    editor
                }
                "commit", "apply" -> {
                    pending.forEach { (key, value) ->
                        if (value == null) values.remove(key) else values[key] = value
                    }
                    if (method.name == "commit") true else Unit
                }
                else -> editor
            }
        } as SharedPreferences.Editor
        return editor
    }
}
