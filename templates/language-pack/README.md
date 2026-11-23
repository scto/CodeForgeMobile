# Vorlage: Sprachpaket

Übersetzt UI-Texte; fehlende Schlüssel fallen auf die App-Sprache zurück.

* **Dateien:** `l10n/fr.json` (`strings`: Ressourcenname → Text)
* **Verwendung:** Einstellungen → Erscheinungsbild → „Sprachpakete“
* **Bauen:** `python3 templates/build.py language-pack` → `templates/build/language-pack.zip`
* **Installieren:** Einstellungen → Store → Installiert → „+“ (Sideload) oder als Store-Eintrag (Kategorie siehe `docs/extension-authoring.md`).
* **Anpassen:** `id` in `plugin.json` ändern (eindeutig, z. B. `com.deinname.mein-plugin`), Texte und Dateien ersetzen.
