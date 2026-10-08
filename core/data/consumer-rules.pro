# core:data — Consumer-R8-Regeln: werden automatisch in jede App übernommen, die dieses Modul einbindet.
# JGit lädt Konfiguration/Transporte und Messages per Reflection/Resource-Bundles.
-keep class org.eclipse.jgit.** { *; }
-dontwarn org.eclipse.jgit.**
-dontwarn org.slf4j.**
