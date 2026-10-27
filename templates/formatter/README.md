# Vorlage: Formatierer

Bindet ein externes Formatierungsprogramm ein: Der Editor schickt den Dateiinhalt an stdin und ersetzt ihn durch stdout.

* **Dateien:** nur `plugin.json` (`contributes.formatters`)
* **Verwendung:** Formatieren im Editor; das Programm (hier `shfmt`) muss im Terminal installiert sein (`pkg install shfmt`)
* **Bauen:** `python3 templates/build.py formatter` → `templates/build/formatter.zip`
* **Installieren:** Einstellungen → Plugins → „+“ (Sideload) oder als Store-Eintrag (Kategorie `formatter`)
* **Anpassen:** `id`, `extensions`/`fileNames`, `command` (Platzhalter `${file}`, `${fileName}`, `${pluginDir}`, `${home}`)
