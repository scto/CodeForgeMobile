# {{projectName}}

Plugin: Code (Sandbox) – Kotlin-Plugin mit Editor-Aktion, läuft isoliert – Store-fähig.

## Anpassen

* `plugin.json`: `id` ist das Package des Projekts (`{{packageName}}`, weltweit eindeutig halten), `name` der
  Anzeigename, `publisher` dein Name, `description` die Store-Beschreibung. Optional `categories` und `tags` (API 4).
* Läuft mit `"sandbox": true` im isolierten Prozess: sieht nur den Text, den die App schickt, und liefert Änderungen zurück. Kein Datei-, Netz- oder Prozesszugriff.
* Referenz aller Felder: https://github.com/scto/CodeForgeMobile/blob/main/docs/extensions.md

## Bauen und testen

* `src/main/kotlin/…` enthält den Code, `plugin.json` das Manifest. `./gradlew packagePlugin` kompiliert, dext (d8, braucht `ANDROID_HOME` mit build-tools) und schreibt `build/plugin/<id>.zip`. `libs/codeforge-plugin-sdk.jar` ist die Plugin-API (nur zum Kompilieren).
* Im Terminal von CodeForge: `./gradlew packagePlugin`
* Installieren: Einstellungen → Store → Installiert → „+“ → ZIP aus `build/plugin/` wählen. Nach Änderungen
  `version` erhöhen und erneut installieren.

## Veröffentlichen

Veröffentlichen: Issue **„Plugin einreichen“** im Store-Repo öffnen (https://github.com/scto/codeforge-store/issues/new?template=submit.yml), ZIP anhängen, kurz beschreiben. Nach der Prüfung erscheint das Plugin im Store; Updates mit höherer `version` genauso einreichen.
