# {{projectName}}

Plugin: Code (Sandbox) – Kotlin-Plugin (API 5) mit Editor-Aktion, Befehl, Menüs, Tastenkürzel und Statusleiste, läuft isoliert – Store-fähig.

## Anpassen

* `plugin.json`: `id` ist das Package des Projekts (`{{packageName}}`, weltweit eindeutig halten), `name` der
  Anzeigename, `publisher` dein Name, `description` die Store-Beschreibung. Optional `categories` und `tags` (API 4).
* Läuft mit `"sandbox": true` im isolierten Prozess: sieht nur den Text, den die App schickt, und liefert Änderungen zurück. Kein Datei-, Netz- oder Prozesszugriff. Befehle hängen über `contributes.menus` (Editor, Dateibaum, Palette) und `contributes.keybindings` mit `when`-Bedingungen in der IDE; `context.ide()` bietet Befehle, Ereignisse und Statusleiste.
* Referenz aller Felder: https://github.com/scto/CodeForgeMobile/blob/main/docs/extensions.md

## Bauen und testen

* `src/main/kotlin/…` enthält den Code, `plugin.json` das Manifest. `./gradlew packagePlugin` kompiliert, dext (d8 aus den Android-Build-Tools) und schreibt `build/plugin/<id>.zip`. Im Terminal von CodeForge ist `ANDROID_HOME` bereits gesetzt; fehlen die Build-Tools, im SDK-Manager installieren. `libs/codeforge-plugin-sdk.jar` ist die Plugin-API (nur zum Kompilieren).
* Im Terminal von CodeForge: `./gradlew packagePlugin`
* Installieren: Einstellungen → Store → Installiert → „+“ → ZIP aus `build/plugin/` wählen. Nach Änderungen
  `version` erhöhen und erneut installieren.

## Veröffentlichen

Veröffentlichen: Issue **„Plugin einreichen“** im Store-Repo öffnen (https://github.com/scto/codeforge-store/issues/new?template=submit.yml), ZIP anhängen, kurz beschreiben. Nach der Prüfung erscheint das Plugin im Store; Updates mit höherer `version` genauso einreichen.
