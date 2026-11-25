# Vorlage: Werkzeug (`tools`)

Werkzeug im PT-Menü des Dateibaums: ein Befehl mit Platzhaltern (`${pluginDir}`, `${projectDir}`, `${input}`),
optionaler Eingabe (`prompt`) und Zeitlimit. Braucht die Berechtigung `shell`.

* **Dateien:** `plugin.json`, `tool.py` (Python 3; `requires` installiert `python` bei Bedarf)
* **Bauen:** `python3 templates/build.py tool` → `templates/build/tool.zip`
* **Nutzen:** Dateibaum → PT-Menü → „Dateien je Endung zählen“
