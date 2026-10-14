# Task 19 – Verlorene Editor-Funktionen aus dem alten Stand wiederherstellen

**Hintergrund:** Das Repo wurde neu initialisiert (Commit `de2caf0` „reinit", 2026-10-01 = alter Stand; Commit `4bdb999`, 2026-10-08, löschte viele Dateien).
Ein Vergleich `git diff -M --name-status de2caf0 HEAD` ergab 116 gelöschte Kotlin-Dateien. Die meisten sind durch den Umbau ersetzt (Editor → `SoraCodeEditor`/`EditorController`,
Dateibaum → `FileTreeScreen`, Terminal → Termux, Themes → TextMate-JSON). Hier geht es nur um Funktionen, die **fachlich fehlen** und wenig Abhängigkeiten haben.
Alte Dateien: `git show de2caf0:<pfad>` (Pfad per `git diff --name-status de2caf0 HEAD | grep '^D'` suchen). Arbeitsverzeichnis = Projekt-Root.

## Kandidaten (je ein Commit, Reihenfolge wie aufgelistet)

1. **`EditorSymbolBar`** (Symbolleiste mit `{ } ( ) ; Tab …` über der Tastatur): alte Fassung lesen, als Composable in `:feature:editor/ui` neu anlegen (MVI: Eingaben als `EditorIntent.InsertText(String)`), in `EditorScreen` über dem Editor einblenden (nur bei geöffneter Tastatur / Textdatei). Texte nach `:core:resources`.
2. **`SoraEditorTextActionWindow`** (Auswahl-Aktionsleiste: Kopieren/Ausschneiden/Einfügen/Alles auswählen): gegen die aktuelle sora-editor-API (0.23.x, siehe `EditorController`) neu schreiben – nicht 1:1 kopieren, die Event-/Window-Klassen haben sich geändert. Prüfen, ob sora-eigenes `EditorTextActionWindow` mit eigener Aktionsliste reicht.
3. **`EditorSearchPanel`** nur dann, wenn `EditorSearchBar` etwas Wesentliches nicht kann (z. B. Ersetzen, Regex, Wort/Case). Erst vergleichen und Lücken melden; nichts duplizieren.
4. **Prüfen, nicht portieren:** `EditorColorDetector` ist durch `overlay/ColorLiteralScanner` + `EditorOverlay` ersetzt (nachgewiesen: Test `ColorLiteralScannerTest`). `ImageFilePreview` ist seit Task 16 Item 1 wieder vorhanden. `EditorSession`, `EditorAssetRegistry`, `SoraEditorHost`: nur melden, ob etwas fehlt.
5. **Terminal-Extras (nur melden, nicht bauen):** Extra-Keys-Leiste (`ExtraKeys*`, `SpecialButton*`) und `FloatingTerminalWindow` waren Nyamux-UI. Prüfen, ob `:libs:termux-app`/`:feature:terminal` eine Extra-Keys-Leiste bereits bietet; Ergebnis in `docs/` festhalten.

## Grenzen

* Nichts wiederherstellen, was an `Environment`, `AppLogger`, `SystemPathsRepository`, Rootfs/PRoot/`DistroBootstrapRepository` hängt – diese Klassen gibt es nicht mehr (PRoot entfernt, Termux-Prefix).
* Keine Feature→Feature-Abhängigkeiten; Zustände immutable; `modifier` als erster optionaler Parameter; keine harten Strings (`python3 scripts/find_hardcoded_strings.py --check`).
* Nur die Themes **nicht** als Kotlin zurückholen (`CodeForge2Dark`, `Dracula`, `Monokai` …): sie werden als TextMate-JSON unter `feature/editor/src/main/assets/textmate/themes/` + `languages.json` bzw. über Task 16 Punkt 3 (Theme-Presets) abgebildet.
* Alte Doku (52 Markdown-Dateien, u. a. `docs/sora_editor_ultimate_setup_implementation.md`) liegt weiter im Git-Verlauf; nicht pauschal zurückholen. Nur das, was die obigen Punkte beschreiben, bei Bedarf nach `docs/archive/` kopieren.

## Akzeptanz

* `./gradlew :feature:editor:assembleDebug testDebugUnitTest` und `:app:assembleDebug` grün.
* Kurzbericht `docs/restore-lost-editor-features.md`: je Kandidat „wiederhergestellt / ersetzt durch X / bewusst verworfen" mit Begründung.
* `CHANGELOG.md` `[Unreleased]` und `STATUS.md` (Editor-Tabelle) ergänzt.
