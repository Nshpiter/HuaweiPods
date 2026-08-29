package moe.chenxy.huaweipods.config

import android.content.SharedPreferences
import java.lang.reflect.Proxy
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import moe.chenxy.huaweipods.pods.HuaweiDeviceRoute
import moe.chenxy.huaweipods.pods.enabledHuaweiDeviceRoutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceRoutePrefsTest {
    @Test
    fun `enabled address binding wins after device is renamed`() {
        assertEquals(
            HuaweiDeviceRoute.HUAWEI_FREEBUDS3,
            resolveBoundOrNamedRoute(
                boundRoute = HuaweiDeviceRoute.HUAWEI_FREEBUDS3,
                deviceName = "Piter's headphones",
            ),
        )
    }

    @Test
    fun `enabled FreeBuds Pro 3 binding wins after device is renamed`() {
        assertEquals(
            HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3,
            resolveBoundOrNamedRoute(
                boundRoute = HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3,
                deviceName = "Renamed Pro headset",
            ),
        )
    }

    @Test
    fun `every integrated model binding survives a user rename`() {
        enabledHuaweiDeviceRoutes().forEach { route ->
            assertEquals(
                route,
                resolveBoundOrNamedRoute(
                    boundRoute = route,
                    deviceName = "Renamed device",
                ),
            )
        }
    }

    @Test
    fun `unsupported binding remains rejected`() {
        assertEquals(
            HuaweiDeviceRoute.UNSUPPORTED,
            resolveBoundOrNamedRoute(
                boundRoute = HuaweiDeviceRoute.UNSUPPORTED,
                deviceName = "Renamed device",
            ),
        )
    }

    @Test
    fun `official name remains the first recognition fallback`() {
        assertEquals(
            HuaweiDeviceRoute.HUAWEI_FREEBUDS3,
            resolveBoundOrNamedRoute(
                boundRoute = null,
                deviceName = "HUAWEI FreeBuds 3",
            ),
        )
    }

    @Test
    fun `official name conflict rejects an old address binding`() {
        assertEquals(
            HuaweiDeviceRoute.UNSUPPORTED,
            resolveBoundOrNamedRoute(
                boundRoute = HuaweiDeviceRoute.HUAWEI_FREEBUDS3,
                deviceName = "HUAWEI FreeClip 2",
            ),
        )
    }

    @Test
    fun `binding key normalizes address case and rejects invalid values`() {
        assertEquals(
            DeviceRoutePrefs.bindingKey("AA:BB:CC:DD:EE:FF"),
            DeviceRoutePrefs.bindingKey("aa:bb:cc:dd:ee:ff"),
        )
        assertNull(DeviceRoutePrefs.bindingKey("not-a-bluetooth-address"))
    }

    @Test
    fun `read-only preferences fail closed instead of crashing the host`() {
        val readOnlyPrefs = Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader ?: javaClass.classLoader,
            arrayOf(SharedPreferences::class.java),
        ) { _, method, _ ->
            if (method.name == "edit") {
                throw UnsupportedOperationException("Read only implementation")
            }
            null
        } as SharedPreferences

        assertFalse(
            DeviceRoutePrefs.bind(
                readOnlyPrefs,
                "AA:BB:CC:DD:EE:FF",
                HuaweiDeviceRoute.HUAWEI_FREEBUDS3,
            ),
        )
    }

    @Test
    fun `automatic binding never overwrites a different manual route`() {
        val prefs = inMemoryPreferences()
        val address = "AA:BB:CC:DD:EE:FF"
        assertTrue(DeviceRoutePrefs.bind(prefs, address, HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3))

        assertFalse(
            DeviceRoutePrefs.bindIfAbsent(
                prefs,
                address,
                HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
            ),
        )
        assertEquals(
            HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3,
            DeviceRoutePrefs.find(prefs, address),
        )
        assertTrue(
            DeviceRoutePrefs.bindIfAbsent(
                prefs,
                address,
                HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3,
            ),
        )
    }

    @Test
    fun `manual binding wins a concurrent late automatic bind`() {
        repeat(50) {
            val prefs = inMemoryPreferences()
            val address = "AA:BB:CC:DD:EE:FF"
            val start = CountDownLatch(1)
            val executor = Executors.newFixedThreadPool(2)
            try {
                val automatic = executor.submit<Boolean> {
                    start.await()
                    DeviceRoutePrefs.bindIfAbsent(
                        prefs,
                        address,
                        HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
                    )
                }
                val manual = executor.submit<Boolean> {
                    start.await()
                    DeviceRoutePrefs.bind(
                        prefs,
                        address,
                        HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3,
                    )
                }
                start.countDown()
                automatic.get()
                assertTrue(manual.get())
                assertEquals(
                    HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3,
                    DeviceRoutePrefs.find(prefs, address),
                )
            } finally {
                executor.shutdownNow()
            }
        }
    }

    private fun inMemoryPreferences(): SharedPreferences {
        val values = ConcurrentHashMap<String, Any>()
        lateinit var preferences: SharedPreferences
        preferences = Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java),
        ) { _, method, args ->
            when (method.name) {
                "getAll" -> values.toMap()
                "getString" -> values[args?.get(0) as String] as? String ?: args[1]
                "contains" -> values.containsKey(args?.get(0) as String)
                "edit" -> inMemoryEditor(values)
                "registerOnSharedPreferenceChangeListener",
                "unregisterOnSharedPreferenceChangeListener" -> Unit
                "toString" -> "DeviceRoutePrefsTestPreferences"
                else -> when (method.returnType) {
                    Boolean::class.javaPrimitiveType -> false
                    Int::class.javaPrimitiveType -> 0
                    Long::class.javaPrimitiveType -> 0L
                    Float::class.javaPrimitiveType -> 0f
                    else -> null
                }
            }
        } as SharedPreferences
        return preferences
    }

    private fun inMemoryEditor(values: ConcurrentHashMap<String, Any>): SharedPreferences.Editor {
        val pending = linkedMapOf<String, String?>()
        var clear = false
        lateinit var editor: SharedPreferences.Editor
        editor = Proxy.newProxyInstance(
            SharedPreferences.Editor::class.java.classLoader,
            arrayOf(SharedPreferences.Editor::class.java),
        ) { _, method, args ->
            when (method.name) {
                "putString" -> {
                    pending[args?.get(0) as String] = args[1] as String?
                    editor
                }
                "remove" -> {
                    pending[args?.get(0) as String] = null
                    editor
                }
                "clear" -> {
                    clear = true
                    editor
                }
                "commit", "apply" -> {
                    synchronized(values) {
                        if (clear) values.clear()
                        pending.forEach { (key, value) ->
                            if (value == null) values.remove(key) else values[key] = value
                        }
                    }
                    if (method.name == "commit") true else Unit
                }
                else -> editor
            }
        } as SharedPreferences.Editor
        return editor
    }
}
