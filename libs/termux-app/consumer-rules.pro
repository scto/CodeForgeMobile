# libs:termux-app — Consumer-R8-Regeln: werden automatisch in jede App übernommen, die dieses Modul einbindet.
# Manifest-Komponenten (TermuxService, Activities, Receiver) und Installer (JNI-Blob) bewahren.
-keepclasseswithmembernames class * { native <methods>; }
-keep class com.codeforge.app.** { *; }
