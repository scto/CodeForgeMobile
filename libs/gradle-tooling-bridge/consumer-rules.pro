# libs:gradle-tooling-bridge — Consumer-R8-Regeln: werden automatisch in jede App übernommen, die dieses Modul einbindet.
# Bridge-Service läuft im eigenen Prozess; Binder-Callback-Schnittstellen nicht umbenennen.
-keep class com.codeforge.libs.gradle_tooling_bridge.** { *; }
