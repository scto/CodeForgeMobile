# libs:plugin-impl — Konsumenten-Regeln
# Plugins werden zur Laufzeit per DexClassLoader geladen (PluginRuntime.loadClass(entryPointClassName)) und
# sprechen gegen die Schnittstellen aus :libs:plugin-api (dort per consumer-rules.pro gesichert).
-keep class com.codeforge.libs.plugin_impl.PluginRuntime { *; }
