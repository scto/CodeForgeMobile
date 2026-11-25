# {{projectName}}

Plugin: Agent-Werkzeug – Werkzeuge für den KI-Agenten (nur Sideload).

## Anpassen

* `plugin/plugin.json`: `id` ist das Package des Projekts (`{{packageName}}`, weltweit eindeutig halten), `name` der
  Anzeigename, `publisher` dein Name, `description` die Store-Beschreibung. Optional `categories` und `tags` (API 4).
* Skripte unter `plugin/`; `kind` `read` oder `write`. Der Store nimmt Agent-Werkzeuge nicht an – nur Sideload.
* Referenz aller Felder: https://github.com/scto/CodeForgeMobile/blob/main/docs/extensions.md

## Bauen und testen

* `plugin/` enthält das Plugin (`plugin.json` und Dateien). `./gradlew packagePlugin` prüft JSON und Verweise und schreibt `build/plugin/<id>-<version>.zip`.
* Im Terminal von CodeForge: `./gradlew packagePlugin`
* Installieren: Einstellungen → Store → Installiert → „+“ → ZIP aus `build/plugin/` wählen. Nach Änderungen
  `version` erhöhen und erneut installieren.

## Veröffentlichen

Veröffentlichen: Diese Plugin-Art nimmt der Store nicht an. Weitergeben als ZIP (Sideload).
