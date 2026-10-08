# :app — Release-Regeln (aktiv, sobald buildTypes.release.isMinifyEnabled = true).
# Hilt/Dagger, Compose, Protobuf und AndroidX bringen eigene Consumer-Regeln mit.
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
# Plugins (DexClassLoader) und Termux-Komponenten werden über consumer-rules.pro der Module bewahrt.
