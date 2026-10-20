package moe.chenxy.huaweipods.config

internal object PodImageProviderAccessPolicy {
    private const val MODULE_PACKAGE = "moe.chenxy.huaweipods.coloros"

    private val imageConsumerPackages = setOf(
        "com.android.bluetooth",
        "com.heytap.accessory",
    )

    fun mayOpenImage(callingPackage: String?): Boolean =
        callingPackage == MODULE_PACKAGE || callingPackage in imageConsumerPackages

    /** 共享 UID 时 Binder 只能可靠给出同 UID 的包集合，任一包被允许即可读取。 */
    fun mayOpenImage(callingPackages: Iterable<String?>): Boolean =
        callingPackages.any(::mayOpenImage)

    fun maySubmitOfficialImageIdentity(callingPackage: String?): Boolean =
        callingPackage == MODULE_PACKAGE ||
            callingPackage == "com.android.bluetooth"
}
