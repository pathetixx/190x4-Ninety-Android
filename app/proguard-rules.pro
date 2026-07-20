# Compatibility facades still use enum names and manually persisted wire values.
# Keep the app package until those legacy reflection/string contracts are removed.
-keep class pw.x4.ninety.** { *; }

# libbox/gomobile bindings are called from generated JNI code.
-keep class io.nekohasekai.libbox.** { *; }
-keep class go.** { *; }
-keepclassmembers class * {
    native <methods>;
}

# WARP registration uses WireGuard crypto model classes directly.
-keep class com.wireguard.** { *; }

-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations,AnnotationDefault,Signature,InnerClasses,EnclosingMethod

# Optional TLS providers referenced by OkHttp.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
