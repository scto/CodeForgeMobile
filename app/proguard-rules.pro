# :app — Release-Regeln (aktiv, sobald buildTypes.release.isMinifyEnabled = true).
# Hilt/Dagger, Compose, Protobuf und AndroidX bringen eigene Consumer-Regeln mit.
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
# Plugins (DexClassLoader) und Termux-Komponenten werden über consumer-rules.pro der Module bewahrt.

# --- Release (R8) ---
# TermuxConstants/TermuxBootstrap lesen BuildConfig per Reflection (BUILD_CONFIG_CLASS_NAME)
-keep class com.codeforge.app.BuildConfig { *; }
# JNI
-keepclasseswithmembernames class * { native <methods>; }
# Enums: values()/valueOf() per Reflection (z. B. StudioAction.valueOf aus Navigationsargumenten)
-keepclassmembers enum * { public static **[] values(); public static ** valueOf(java.lang.String); }
