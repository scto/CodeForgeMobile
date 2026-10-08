# Auftrag: TextMate-Grammars, vollständige Themes & Language-Configurations vervollständigen

## Kontext

Repo: CodeForgeMobile (Android, Kotlin, Gradle-Multi-Module, Clean Architecture).
Arbeitsverzeichnis: Projekt-Root (`CodeForgeMobile/`).

Modul `:feature:editor` nutzt `language-textmate` (sora-editor) für Syntax-
Highlighting. Die Registry liegt in
`feature/editor/src/main/assets/textmate/languages.json` und referenziert
Grammar-, Theme- und Language-Configuration-Dateien, von denen aktuell nur ein
Teil vorhanden ist. Lies zuerst `feature/editor/ASSETS.md` im Repo — dort steht
der vollständige Hintergrund (Bezugsquellen, Lizenzlage, aktueller Stand). Lies
außerdem `feature/editor/src/main/kotlin/com/codeforge/feature/editor/textmate/TextMateAssetLoader.kt`,
um zu verstehen, wie die Dateien geladen werden (Fehlen wird aktuell abgefangen
und fällt auf Plain-Text zurück — dein Ziel ist, dass dieser Fallback nicht mehr
nötig ist).

## Aufgabe

1. Beschaffe folgende Dateien aus den in `ASSETS.md` genannten Quellen (MIT/Apache-2.0,
   lizenzkompatibel) und lege sie an den exakt in `languages.json` referenzierten
   Pfaden ab:
   - `feature/editor/src/main/assets/textmate/grammars/kotlin.tmLanguage.json`
     (Quelle: `fwcd/vscode-kotlin` bzw. offizielle Kotlin-VSCode-Extension,
     `syntaxes/Kotlin.tmLanguage.json`)
   - `feature/editor/src/main/assets/textmate/grammars/java.tmLanguage.json`
     (Quelle: `microsoft/vscode`, `extensions/java/syntaxes/java.tmLanguage.json`)
   - `feature/editor/src/main/assets/textmate/grammars/xml.tmLanguage.json`
     (Quelle: `microsoft/vscode`, `extensions/xml/syntaxes/xml.tmLanguage.json`)
   - `feature/editor/src/main/assets/textmate/grammars/json.tmLanguage.json`
     (Quelle: `microsoft/vscode`, `extensions/json/syntaxes/JSON.tmLanguage.json`)
   - `feature/editor/src/main/assets/textmate/languages/kotlin-language-configuration.json`
   - `feature/editor/src/main/assets/textmate/languages/java-language-configuration.json`
   - `feature/editor/src/main/assets/textmate/languages/xml-language-configuration.json`
   - `feature/editor/src/main/assets/textmate/languages/json-language-configuration.json`
     (jeweils `language-configuration.json` aus demselben VS-Code-Extension-Verzeichnis
     wie die zugehörige Grammar-Datei — enthält Bracket-Pairs, Kommentar-Syntax,
     Auto-Closing-Pairs)

2. Ersetze die aktuell minimalen Platzhalter-Themes durch die vollständigen
   Originale:
   - `feature/editor/src/main/assets/textmate/themes/dark_plus.json`
   - `feature/editor/src/main/assets/textmate/themes/light_plus.json`
     (Quelle: `microsoft/vscode`, `extensions/theme-defaults/themes/dark_plus.json`
     bzw. `light_plus.json`)

3. Lege `feature/editor/src/main/assets/textmate/docs/sub/NOTICE.md` an (Analogon zu
   `docs/sub/NOTICE.md` für die Termux-Vendoring) mit: Ursprungsprojekt
   je Datei, Lizenz (MIT/Apache-2.0 je nach Quelle), Datum des Imports.

4. `languages.json` selbst NICHT verändern — die Pfade stimmen bereits, du musst
   nur die referenzierten Dateien an den existierenden Stellen ablegen.

## Akzeptanzkriterien

- Alle 10 oben genannten Dateien existieren an exakt den genannten Pfaden und
  sind valides JSON.
- `./gradlew :feature:editor:assembleDebug` baut erfolgreich (reine Asset-Dateien,
  kein Code geändert — sollte ohnehin nicht brechen, aber zur Sicherheit prüfen).
- `feature/editor/src/main/assets/textmate/docs/sub/NOTICE.md` existiert und listet alle
  importierten Dateien mit Quelle + Lizenz.
- Keine Änderung an `TextMateAssetLoader.kt` oder `languages.json` nötig oder
  vorgenommen — falls doch eine Anpassung nötig wird, dokumentiere warum in der
  Commit-Message.

## Hinweis

Falls eine der Quellen (z. B. `fwcd/vscode-kotlin`) nicht erreichbar ist: nimm
ersatzweise die Grammar-Datei aus einer anderen aktiv gepflegten, lizenzkonformen
Quelle (z. B. JetBrains' eigene TextMate-Kotlin-Grammar, falls vorhanden) und
dokumentiere den tatsächlich genutzten Ursprung in `docs/sub/NOTICE.md` statt der hier
vorgeschlagenen Quelle.
