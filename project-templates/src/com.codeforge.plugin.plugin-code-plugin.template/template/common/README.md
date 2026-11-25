# {{projectName}}

Plugin: Code (voll) – Kotlin-Plugin mit Befehl, Menü und Ereignissen (nur Sideload).

## Anpassen

* `plugin.json`: `id` ist das Package des Projekts (`{{packageName}}`, weltweit eindeutig halten), `name` der
  Anzeigename, `publisher` dein Name, `description` die Store-Beschreibung. Optional `categories` und `tags` (API 4).
* Läuft im App-Prozess und darf nach Berechtigung Dateien lesen, Befehle und Menüs anlegen. Der Store nimmt nur Sandbox-Plugins an – dieses nur per Sideload.
* Referenz aller Felder: https://github.com/scto/CodeForgeMobile/blob/main/docs/extensions.md

## Bauen und testen

* `src/main/kotlin/…` enthält den Code, `plugin.json` das Manifest. `./gradlew packagePlugin` kompiliert, dext (d8, braucht `ANDROID_HOME` mit build-tools) und schreibt `build/plugin/<id>.zip`. `libs/codeforge-plugin-sdk.jar` ist die Plugin-API (nur zum Kompilieren).
* Im Terminal von CodeForge: `./gradlew packagePlugin`
* Installieren: Einstellungen → Store → Installiert → „+“ → ZIP aus `build/plugin/` wählen. Nach Änderungen
  `version` erhöhen und erneut installieren.

## Veröffentlichen

Veröffentlichen: Diese Plugin-Art nimmt der Store nicht an. Weitergeben als ZIP (Sideload).
