# Vorlage: Bundle

Fasst mehrere Plugins zusammen; der Store installiert fehlende Mitglieder mit.

* **Dateien:** `extensionPack` listet Plugin-IDs
* **Verwendung:** Plugins-Screen zeigt die Mitglieder
* **Bauen:** `python3 templates/build.py extension-pack` → `templates/build/extension-pack.zip`
* **Installieren:** Einstellungen → Plugins → „+“ (Sideload) oder als Store-Eintrag (Kategorie siehe `docs/extension-authoring.md`).
* **Anpassen:** `id` in `plugin.json` ändern (eindeutig, z. B. `com.deinname.mein-plugin`), Texte und Dateien ersetzen.
