# Vorlage: Grammatik

Syntaxfarben für Dateien ohne eingebaute Grammatik (TextMate-JSON).

* **Dateien:** `grammars/mylang.tmLanguage.json`, Endung `.myl`
* **Verwendung:** Wirkt automatisch beim Öffnen einer `.myl`-Datei
* **Bauen:** `python3 templates/build.py grammar` → `templates/build/grammar.zip`
* **Installieren:** Einstellungen → Plugins → „+“ (Sideload) oder als Store-Eintrag (Kategorie siehe `docs/extension-authoring.md`).
* **Anpassen:** `id` in `plugin.json` ändern (eindeutig, z. B. `com.deinname.mein-plugin`), Texte und Dateien ersetzen.
