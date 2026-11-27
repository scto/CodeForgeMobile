# libs:plugin-api — Consumer-R8-Regeln: werden automatisch in jede App übernommen, die dieses Modul einbindet.
#
# Code-Plugins werden zur Laufzeit per DexClassLoader geladen (im App-Prozess und im Sandbox-Prozess). Ihr Dex enthält
# nur die eigenen Klassen; alles andere linken sie gegen die Klassen der App: Plugin-SDK, Domain-Typen, die
# Kotlin-Standardbibliothek und Coroutines. Benennt R8 diese um oder entfernt „unbenutzte“ Teile, scheitert das Laden
# mit NoClassDefFoundError/NoSuchMethodError – nur im Release-Build, Debug-Builds sind nicht betroffen.
# Deshalb bleiben diese Pakete vollständig erhalten (Namen und alle Mitglieder).

# Plugin-SDK
-keep class com.codeforge.libs.plugin_api.** { *; }
-keep interface com.codeforge.libs.plugin_api.** { *; }

# Domain-Typen im SDK (ExtensionPoint, Disposable, IdeApi-Typen der API 5, Modelle)
-keep class com.codeforge.core.domain.** { *; }
-keep interface com.codeforge.core.domain.** { *; }
-keep enum com.codeforge.core.domain.** { *; }

# Terminal-Engine (Teil des Plugin-SDK: ShellLaunchSpec für Test- und Ausführen-Einträge)
-keep class com.codeforge.libs.terminal_engine.** { *; }
-keep interface com.codeforge.libs.terminal_engine.** { *; }

# Laufzeitbibliotheken, die Plugins mitbenutzen
-keep class kotlin.** { *; }
-keep interface kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }
-keep interface kotlinx.coroutines.** { *; }
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*
-dontwarn kotlin.**
-dontwarn kotlinx.coroutines.**
