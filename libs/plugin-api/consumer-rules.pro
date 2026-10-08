# libs:plugin-api — Consumer-R8-Regeln: werden automatisch in jede App übernommen, die dieses Modul einbindet.
# Plugin-API: Plugins werden zur Laufzeit per DexClassLoader geladen und binden gegen diese Schnittstellen.
# Ohne diese Regeln würde R8 Interfaces/Methoden umbenennen oder entfernen, die nur Plugins aufrufen.
-keep interface com.codeforge.libs.plugin_api.** { *; }
-keep class com.codeforge.libs.plugin_api.CodeForgePlugin* { *; }
