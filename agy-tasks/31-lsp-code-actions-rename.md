# Task 31 – Code-Aktionen, Umbenennen und Struktur über LSP

**Ziel:** Der Editor bietet Quick-Fixes (Glühbirne), „Umbenennen“ über mehrere Dateien und eine Struktur-Ansicht (Outline), sobald ein Language Server läuft.
**Ausgangslage:** `LspClientRepository` kennt nur `start`, `stop`, `didOpen`, `didChange`, `didClose`, `requestCompletion`, `requestHover`, `requestFormat`. Diagnostics kommen über `LspDiagnosticsBridge`. Kein `codeAction`, `rename`, `documentSymbol`, `definition`, `references`, `workspace/applyEdit` (Suche im Repo ohne Treffer, bitte selbst gegenprüfen).
**Voraussetzung auf dem Gerät:** Ein Server muss laufen (`kotlin-language-server`/`jdtls` brauchen `openjdk-17`, das noch nicht im APT-Repo liegt). Bis dahin: Logik gegen einen Fake-Server testen und im Bericht klar „nicht gegen echten Server getestet“ schreiben.

**Gemeinsame Regeln (gelten für jeden Task):**
* Clean Architecture wie im Rest des Repos: Schnittstellen und Modelle in `:core:domain`, Implementierung in `:libs:*` bzw. im Feature-Modul, UI in `:feature:*` (MVVM/MVI mit Hilt, `@Immutable`-Daten-UI-States, `StateFlow`; Material 3; `modifier: Modifier = Modifier` als erster optionaler Parameter; keine Logik in Composables). Gradle Kotlin DSL, Versionen im Version Catalog (`gradle/libs.versions.toml`), Convention-Plugins aus `build-logic`.
* Alle UI-Texte über `:core:resources` (Englisch Standard, Deutsch in `values-de`); der String-Check (Task 17) muss grün bleiben.
* Nichts erfinden: Was du nicht selbst ausgeführt hast, steht im Bericht unter **nicht getestet** (insbesondere Gerätetests; Zielgerät des Inhabers: Pixel 9 Pro XL, Android 17). Kein „funktioniert“ ohne Beleg.
* Geheimnisse (Passwörter, Tokens) nie ins Log, nie in Dateien im Projektordner, nie in Berichte.
* Bericht in `docs/<name>-report.md` mit Abschnitten **getestet / nicht getestet / offene Punkte**; `agy-tasks/TASKS-STATUS.md` und `agy-tasks/README.md` um den Task ergänzen (Commit-Hash eintragen).
* Build muss grün sein: `./gradlew :app:assembleDebug` und die Tests der betroffenen Module (`./gradlew :<modul>:testDebugUnitTest`).

## 31.1 Protokoll erweitern (`:core:domain` + `:libs:lsp-client`)
Neue Methoden mit Modellen (`LspCodeAction`, `LspWorkspaceEdit`, `LspTextEdit`, `LspSymbol`, `LspLocation`):
`requestCodeActions(path, range, diagnostics)`, `requestRename(path, position, newName)` mit vorherigem `prepareRename`, `requestDocumentSymbols(path)`, `requestDefinition`, `requestReferences`, `executeCommand`. Fähigkeiten aus `initialize` (`ServerCapabilities`) lesen; nicht unterstützte Aktionen im UI ausblenden statt Fehler.
Eingehende Anfrage `workspace/applyEdit` vom Server beantworten.

## 31.2 WorkspaceEdit anwenden
* Änderungen an offenen Dateien über den Editor-Puffer (ein Undo-Schritt je Datei), an geschlossenen Dateien über das Dateisystem.
* **Atomar bei Fehlern:** Vorher Sicherung der betroffenen Dateien im Cache; schlägt eine Datei fehl, alles zurückrollen und Fehler anzeigen. Offsets/Zeilen korrekt von UTF-16 (LSP) auf die Editor-Positionen umrechnen; Test mit Umlauten und Emoji.
* Dateioperationen (`documentChanges` mit Anlegen, Umbenennen, Löschen) unterstützen oder ausdrücklich ablehnen mit Meldung.

## 31.3 UI im Editor (`:feature:editor`)
* Glühbirne am Cursor/an Diagnose; Tipp öffnet die Liste der Quick-Fixes, Auswahl wendet sie an. Tastaturkürzel analog zu den vorhandenen (Alt+Eingabe).
* „Umbenennen“ im Kontextmenü: Dialog mit Vorschlag des alten Namens, danach Zusammenfassung „n Änderungen in m Dateien“ vor dem Anwenden (Rückfrage), nach dem Anwenden Hinweis mit Rückgängig.
* Struktur-Ansicht (Outline) als Seitenfläche/BottomSheet: Hierarchie der Symbole mit Icons je Art (Klasse, Funktion, Feld …), Tipp springt zur Stelle, Filter. Wenn der Server `documentSymbol` nicht liefert, Fallback auf Tree-sitter-Abfrage (vorhandene Queries wiederverwenden).
* „Gehe zu Definition“ und „Verwendungen“ (Liste, Tipp springt).

## 31.4 Tests
* Fake-Server (JSON-RPC über Pipes): jede neue Anfrage hin und zurück.
* WorkspaceEdit: mehrere Dateien, überlappende Edits werden abgelehnt, Rollback bei Fehler, UTF-16-Umrechnung.
* ViewModel-Tests für Quick-Fix- und Rename-Zustände.

## Abnahme
Build grün, Tests grün, Bericht trennt sauber „gegen Fake-Server“ und „gegen echten Server“.
