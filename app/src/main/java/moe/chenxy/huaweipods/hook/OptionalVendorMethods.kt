package moe.chenxy.huaweipods.hook

import java.lang.reflect.Method
import java.lang.reflect.InvocationTargetException

internal fun isVendorPermissionDenied(error: Throwable): Boolean = when (error) {
    is SecurityException -> true
    is InvocationTargetException -> error.targetException?.let(::isVendorPermissionDenied) == true
    else -> false
}

/** Cache positive and negative signature lookups, without hiding invocation failures. */
internal class OptionalVendorMethods(private val type: Class<*>) {
    private data class Signature(val name: String, val parameters: List<Class<*>>)
    private val methods = mutableMapOf<Signature, Method?>()

    @Synchronized
    fun find(name: String, parameters: Array<Class<*>>): Method? {
        val signature = Signature(name, parameters.toList())
        if (!methods.containsKey(signature)) {
            methods[signature] = try {
                type.getMethod(name, *parameters)
            } catch (_: NoSuchMethodException) {
                null
            }
        }
        return methods[signature]
    }
}
