# Kotlin
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    public static void check*(...);
    public static void throw*(...);
}

-repackageclasses
-allowaccessmodification
-overloadaggressively
-renamesourcefileattribute SourceFile

# Keep Xposed entry point
-keep class moe.chenxy.huaweipods.hook.HookEntry { *; }

# Keep Parcelable data classes (used in broadcast extras)
-keep class moe.chenxy.huaweipods.utils.miuiStrongToast.data.** { *; }
