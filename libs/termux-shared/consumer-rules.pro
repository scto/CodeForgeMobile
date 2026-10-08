# libs:termux-shared — Consumer-R8-Regeln: werden automatisch in jede App übernommen, die dieses Modul einbindet.
# JNI (LocalSocket/Exec) und Reflection auf Konstanten.
-keepclasseswithmembernames class * { native <methods>; }
-keep class com.codeforge.shared.** { *; }
