# Vorlage: Terminal-Farbschema (`terminalThemes`)

Farben des eingebauten Terminals im Format von Termux `colors.properties`: `foreground`, `background`, `cursor`
und die 16 ANSI-Farben `color0`…`color15` (`#RRGGBB`). Fehlende Farben ergänzt die App mit den Termux-Standardfarben.

* **Dateien:** `plugin.json`, `colors.properties`
* **Bauen:** `python3 templates/build.py terminal-theme` → `templates/build/terminal-theme.zip`
* **Nutzen:** Einstellungen → Terminal → Farbschema
