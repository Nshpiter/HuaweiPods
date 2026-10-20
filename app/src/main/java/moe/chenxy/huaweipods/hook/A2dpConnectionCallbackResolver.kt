package moe.chenxy.huaweipods.hook

import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** Upstream 59d5fa8 method-name fallback, with an exact instance/void signature. */
internal fun findA2dpConnectionCallback(serviceType: Class<*>, deviceType: Class<*>): Method {
    for (name in listOf("handleConnectionStateChanged", "connectionStateChanged")) {
        var type: Class<*>? = serviceType
        while (type != null) {
            val method = try {
                type.getDeclaredMethod(name, deviceType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            } catch (_: NoSuchMethodException) {
                null
            }
            if (method != null && method.returnType == Void.TYPE && !Modifier.isStatic(method.modifiers)) {
                return method.apply { isAccessible = true }
            }
            type = type.superclass
        }
    }
    throw NoSuchMethodException("${serviceType.name}: expected void A2DP callback(device, int, int)")
}
