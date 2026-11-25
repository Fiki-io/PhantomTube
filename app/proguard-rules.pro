# Phantom R8 Shrinking & Safe Optimization Rules
# Designed for maximum dead-code elimination while preserving clean package hierarchy
# to avoid false-positive heuristic detections in Google Play Protect and AV scanners.

# 1. JSInterface Protection
-keepattributes JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.phantom.tube.player.PhantomPlayerBridge { *; }

# 2. Networking & Coroutines Warnings Suppression
-dontwarn javax.annotation.**
-dontwarn kotlinx.coroutines.**

# 3. Safe Bytecode Shrinking (Repackage into legitimate app namespace, no aggressive overloading)
-repackageclasses 'com.phantom.tube.internal'
-allowaccessmodification
-optimizationpasses 5

# Eliminate Kotlin null-check assertions, Compose trace/source markers, and debug logs in release bytecode
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    public static void checkNotNullParameter(...);
    public static void checkNotNull(...);
    public static void checkExpressionValueIsNotNull(...);
    public static void checkNotNullExpressionValue(...);
    public static void checkReturnedValueIsNotNull(...);
    public static void checkFieldIsNotNull(...);
    public static void throwNpe(...);
    public static void throwJavaNpe(...);
    public static void throwUninitializedPropertyAccessException(...);
    public static void throwAssert(...);
    public static void throwIllegalArgument(...);
    public static void throwIllegalState(...);
}

-assumenosideeffects class androidx.compose.runtime.ComposerKt {
    public static void sourceInformation(androidx.compose.runtime.Composer, java.lang.String);
    public static void sourceInformationMarkerStart(androidx.compose.runtime.Composer, int, java.lang.String);
    public static void sourceInformationMarkerEnd(androidx.compose.runtime.Composer);
    public static boolean isTraceInProgress();
    public static void traceEventStart(int, int, int, java.lang.String);
    public static void traceEventEnd();
}

-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
