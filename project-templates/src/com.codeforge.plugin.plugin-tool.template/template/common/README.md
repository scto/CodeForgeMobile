# {{projectName}}

Plugin: Werkzeug – Befehl im PT-Menü des Dateibaums (Skript, Eingabe, Zeitlimit).

## Anpassen

* `plugin/plugin.json`: `id` ist das Package des Projekts (`{{packageName}}`, weltweit eindeutig halten), `name` der
  Anzeigename, `publisher` dein Name, `description` die Store-Beschreibung. Optional `categories` und `tags` (API 4).
* Skript `plugin/tool.py` (Python 3, über `requires` installiert); Platzhalter `${pluginDir}`, `${projectDir}`, `${input}`. Braucht `shell`.
* Referenz aller Felder: https://github.com/scto/CodeForgeMobile/blob/main/docs/extensions.md

## Bauen und testen

* `plugin/` enthält das Plugin (`plugin.json` und Dateien). `./gradlew packagePlugin` prüft JSON und Verweise und schreibt `build/plugin/<id>-<version>.zip`.
* Im Terminal von CodeForge: `./gradlew packagePlugin`
* Installieren: Einstellungen → Store → Installiert → „+“ → ZIP aus `build/plugin/` wählen. Nach Änderungen
  `version` erhöhen und erneut installieren.

## Veröffentlichen

Veröffentlichen: Issue **„Plugin einreichen“** im Store-Repo öffnen (https://github.com/scto/codeforge-store/issues/new?template=submit.yml), ZIP anhängen, kurz beschreiben. Nach der Prüfung erscheint das Plugin im Store; Updates mit höherer `version` genauso einreichen.
