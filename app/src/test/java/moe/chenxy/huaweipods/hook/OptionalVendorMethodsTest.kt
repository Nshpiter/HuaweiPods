package moe.chenxy.huaweipods.hook

import org.junit.Assert.*
import org.junit.Test

class OptionalVendorMethodsTest {
    @Test fun reflectivePermissionDenialStopsUnsupportedIconRetries() {
        assertTrue(isVendorPermissionDenied(SecurityException()))
        assertTrue(isVendorPermissionDenied(java.lang.reflect.InvocationTargetException(SecurityException())))
        assertFalse(isVendorPermissionDenied(java.lang.reflect.InvocationTargetException(IllegalStateException())))
        assertFalse(isVendorPermissionDenied(NoSuchMethodException()))
    }
    class Firmware {
        fun battery(value: Int): Int = value
        fun failure(): Nothing = error("host failure")
    }

    @Test fun absentOptionalMethodDoesNotBlockSupportedMethods() {
        val methods = OptionalVendorMethods(Firmware::class.java)
        repeat(2) { assertNull(methods.find("transport", emptyArray())) }
        val battery = methods.find("battery", arrayOf(Int::class.javaPrimitiveType!!))!!
        assertEquals(42, battery.invoke(Firmware(), 42))
        assertSame(battery, methods.find("battery", arrayOf(Int::class.javaPrimitiveType!!)))
    }

    @Test fun onlyExactSignatureMatches() {
        val methods = OptionalVendorMethods(Firmware::class.java)
        assertNull(methods.find("battery", arrayOf(String::class.java)))
        assertNotNull(methods.find("battery", arrayOf(Int::class.javaPrimitiveType!!)))
    }

    @Test fun invocationFailureIsNotReportedAsMissingMethod() {
        val method = OptionalVendorMethods(Firmware::class.java).find("failure", emptyArray())!!
        val failure = runCatching { method.invoke(Firmware()) }.exceptionOrNull()
        assertTrue(failure is java.lang.reflect.InvocationTargetException)
    }
}
