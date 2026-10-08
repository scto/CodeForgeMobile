# libs:termux-emulator — Consumer-R8-Regeln: werden automatisch in jede App übernommen, die dieses Modul einbindet.
# JNI: native Methoden und die von C aus per Name angesprochenen Klassen/Felder dürfen nicht umbenannt werden.
-keepclasseswithmembernames class * { native <methods>; }
-keep class com.codeforge.terminal.** { *; }
