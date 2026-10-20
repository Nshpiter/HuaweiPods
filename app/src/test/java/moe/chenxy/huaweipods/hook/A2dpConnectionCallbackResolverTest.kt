package moe.chenxy.huaweipods.hook

import org.junit.Assert.*
import org.junit.Test

@Suppress("UNUSED_PARAMETER")
class A2dpConnectionCallbackResolverTest {
    class Device
    class Modern {
        private fun handleConnectionStateChanged(device: Device, previous: Int, current: Int) {}
        fun connectionStateChanged(device: Device, previous: Int, current: Int) {}
    }
    class Alternate {
        fun handleConnectionStateChanged(device: Device, previous: String, current: String) {}
        fun connectionStateChanged(device: Device, previous: Int, current: Int) {}
    }
    open class Parent { protected fun connectionStateChanged(device: Device, previous: Int, current: Int) {} }
    class Child : Parent()
    class WrongReturn {
        fun handleConnectionStateChanged(device: Device, previous: Int, current: Int): Int = 0
        fun connectionStateChanged(device: Device, previous: Int, current: Int) {}
    }
    class WrongCount { fun handleConnectionStateChanged(device: Device, state: Int) {} }
    class WrongDevice { fun handleConnectionStateChanged(device: Any, previous: Int, current: Int) {} }
    class StaticOnly { companion object { @JvmStatic fun connectionStateChanged(device: Device, previous: Int, current: Int) {} } }

    private fun find(type: Class<*>) = findA2dpConnectionCallback(type, Device::class.java)

    @Test fun modernExactSignatureIsPreferredAndAccessible() {
        val method = find(Modern::class.java)
        assertEquals("handleConnectionStateChanged", method.name)
        method.invoke(Modern(), Device(), 0, 2)
    }
    @Test fun alternateNameIsUsedInsteadOfSameArityWrongTypes() {
        assertEquals("connectionStateChanged", find(Alternate::class.java).name)
    }
    @Test fun inheritedCallbackCanBeHooked() {
        assertEquals(Parent::class.java, find(Child::class.java).declaringClass)
    }
    @Test fun wrongReturnTypeDoesNotMatch() {
        assertEquals("connectionStateChanged", find(WrongReturn::class.java).name)
    }
    @Test(expected = NoSuchMethodException::class) fun unknownSignatureFailsClosed() { find(Any::class.java) }
    @Test(expected = NoSuchMethodException::class) fun wrongParameterCountIsRejected() { find(WrongCount::class.java) }
    @Test(expected = NoSuchMethodException::class) fun wrongDeviceTypeIsRejected() { find(WrongDevice::class.java) }
    @Test(expected = NoSuchMethodException::class) fun staticCallbackIsRejected() { find(StaticOnly::class.java) }
}
