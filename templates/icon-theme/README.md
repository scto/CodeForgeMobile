# Vorlage: Icon-Paket

Datei-/Ordner-Icons für Dateibaum, Tabs, Suche und Git (PNG/WebP/JPEG, kein SVG).

* **Dateien:** `icons/theme.json` ordnet Namen/Endungen den Bildern zu
* **Verwendung:** Einstellungen → Design → „Icon-Pakete“
* **Bauen:** `python3 templates/build.py icon-theme` → `templates/build/icon-theme.zip`
* **Installieren:** Einstellungen → Plugins → „+“ (Sideload) oder als Store-Eintrag (Kategorie siehe `docs/extension-authoring.md`).
* **Anpassen:** `id` in `plugin.json` ändern (eindeutig, z. B. `com.deinname.mein-plugin`), Texte und Dateien ersetzen.
