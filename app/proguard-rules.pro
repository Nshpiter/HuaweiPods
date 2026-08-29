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

# API 102 的入口只在 META-INF/xposed/java_init.list 中以类名引用，R8
# 无法从字节码调用关系推断该入口；保留类名和成员，确保 LSPosed 能加载。
-keep class moe.chenxy.huaweipods.hook.Api102HookEntry { *; }

# Keep Parcelable data classes (used in broadcast extras)
-keep class moe.chenxy.huaweipods.utils.miuiStrongToast.data.** { *; }
