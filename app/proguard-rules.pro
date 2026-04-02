-keep class com.topjohnwu.superuser.** { *; }
-keep class com.topjohnwu.superuser.internal.** { *; }

-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
}
