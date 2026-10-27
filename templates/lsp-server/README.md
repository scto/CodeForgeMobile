# Vorlage: Sprachserver

Bindet einen bereits installierten Language Server für eine Sprache ein. Keine Skripte im Paket (der Store lehnt sie ab).

* **Dateien:** `command` mit `${home}`/`${pluginDir}`; Server selbst muss vorhanden sein
* **Verwendung:** Editor startet den Server für die Sprache `mylang`
* **Bauen:** `python3 templates/build.py lsp-server` → `templates/build/lsp-server.zip`
* **Installieren:** Einstellungen → Plugins → „+“ (Sideload) oder als Store-Eintrag (Kategorie siehe `docs/extension-authoring.md`).
* **Anpassen:** `id` in `plugin.json` ändern (eindeutig, z. B. `com.deinname.mein-plugin`), Texte und Dateien ersetzen.
