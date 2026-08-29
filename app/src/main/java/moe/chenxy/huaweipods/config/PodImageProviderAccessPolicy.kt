package moe.chenxy.huaweipods.config

internal object PodImageProviderAccessPolicy {
    private const val MODULE_PACKAGE = "moe.chenxy.huaweipods"

    private val imageConsumerPackages = setOf(
        "com.android.bluetooth",
        "com.android.settings",
        "com.milink.service",
        "com.xiaomi.bluetooth",
    )

    fun mayOpenImage(callingPackage: String?): Boolean =
        callingPackage == MODULE_PACKAGE || callingPackage in imageConsumerPackages

    /** Android 蓝牙与小米蓝牙共用 UID 时，Binder 只能可靠给出同 UID 的包集合。 */
    fun mayOpenImage(callingPackages: Iterable<String?>): Boolean =
        callingPackages.any(::mayOpenImage)

    fun maySubmitOfficialImageIdentity(callingPackage: String?): Boolean =
        callingPackage == MODULE_PACKAGE ||
            callingPackage == "com.android.bluetooth"
}
