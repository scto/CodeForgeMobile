# Vorlage: Farbschema

App-Farben (Material-3-Rollen) plus optionales Editor-Theme.

* **Dateien:** `themes/midnight.json` (Rollen → Hex), `themes/syntax/midnight-tm.json` (TextMate-Theme)
* **Verwendung:** Einstellungen → Design → „Aus Erweiterungen“
* **Bauen:** `python3 templates/build.py color-theme` → `templates/build/color-theme.zip`
* **Installieren:** Einstellungen → Plugins → „+“ (Sideload) oder als Store-Eintrag (Kategorie siehe `docs/extension-authoring.md`).
* **Anpassen:** `id` in `plugin.json` ändern (eindeutig, z. B. `com.deinname.mein-plugin`), Texte und Dateien ersetzen.
