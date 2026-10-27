# Vorlage: Snippets

Code-Bausteine im VS-Code-Format, schreibgeschützt in der App.

* **Dateien:** `snippets/kotlin.json` (`prefix`, `body`, `description`)
* **Verwendung:** Auto-Vervollständigung im Editor; Snippet-Einstellungen
* **Bauen:** `python3 templates/build.py snippets` → `templates/build/snippets.zip`
* **Installieren:** Einstellungen → Plugins → „+“ (Sideload) oder als Store-Eintrag (Kategorie siehe `docs/extension-authoring.md`).
* **Anpassen:** `id` in `plugin.json` ändern (eindeutig, z. B. `com.deinname.mein-plugin`), Texte und Dateien ersetzen.
