# feature:composepreview — Consumer-R8-Regeln: werden automatisch in jede App übernommen, die dieses Modul einbindet.
# Die Vorschau lädt Klassen des kompilierten Nutzerprojekts per ClassLoader.loadClass (nicht aus der App) –
# nur die Preview-Host-Klassen der App selbst müssen erhalten bleiben.
-keep class com.codeforge.feature.composepreview.** { *; }
