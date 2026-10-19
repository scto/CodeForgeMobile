# Task 39 – Git-Konfliktoberfläche prüfen und ausbauen

**Wichtig vorab:** Eine Konfliktoberfläche gibt es **bereits**. Der Vorschlag „Git-Konflikt-Oberfläche“ aus der Ideenliste war zu grob. Gefunden:
* `feature/git/.../ConflictEditor.kt` (Dialog `ConflictEditorDialog`, Konfliktblöcke mit Wahl je Block), `ConflictEditorState` in `GitContract.kt`.
* `core/domain/.../GitConflict.kt`: `ConflictResolution` (`OURS`, `THEIRS`, `BOTH`, `BOTH_REVERSED`), `ConflictSegment` (`Plain`, `Conflict` mit optionaler Basis `base`/`baseLabel`), `ConflictParser` mit Tests (`GitPatchTest`).
* `GitRepository.resolveConflict(repoPath, path, side)` und `abortMerge`, `rebaseControl`, `cherryPick`, `revert`.
Dieser Task prüft deshalb zuerst, was tatsächlich fehlt, und baut nur das.

**Gemeinsame Regeln (gelten für jeden Task):**
* Clean Architecture wie im Rest des Repos: Schnittstellen und Modelle in `:core:domain`, Implementierung in `:libs:*` bzw. im Feature-Modul, UI in `:feature:*` (MVVM/MVI mit Hilt, `@Immutable`-Daten-UI-States, `StateFlow`; Material 3; `modifier: Modifier = Modifier` als erster optionaler Parameter; keine Logik in Composables). Gradle Kotlin DSL, Versionen im Version Catalog (`gradle/libs.versions.toml`), Convention-Plugins aus `build-logic`.
* Alle UI-Texte über `:core:resources` (Englisch Standard, Deutsch in `values-de`); der String-Check (Task 17) muss grün bleiben.
* Nichts erfinden: Was du nicht selbst ausgeführt hast, steht im Bericht unter **nicht getestet** (insbesondere Gerätetests; Zielgerät des Inhabers: Pixel 9 Pro XL, Android 17). Kein „funktioniert“ ohne Beleg. Aussagen dieses Tasks über den Bestand stammen aus Stichwortsuchen; prüfe sie vor dem Start selbst und korrigiere sie im Bericht.
* Geheimnisse (Passwörter, Tokens, API-Schlüssel) nie ins Log, nie in Dateien im Projektordner, nie in Berichte.
* Bericht in `docs/<name>-report.md` mit Abschnitten **getestet / nicht getestet / offene Punkte**; `agy-tasks/TASKS-STATUS.md` und `agy-tasks/README.md` um den Task ergänzen (Commit-Hash eintragen).
* Build muss grün sein: `./gradlew :app:assembleDebug` und die Tests der betroffenen Module (`./gradlew :<modul>:testDebugUnitTest`).

## 39.0 Bestandsaufnahme (Pflicht, ehrlich)
Lies den Code und teste mit echten Konflikten (kleines Testrepo mit JGit im Test oder auf dem Gerät: Merge, Rebase, Cherry-pick, Stash-Apply). Lege eine Tabelle im Bericht an: Funktion → **vorhanden / teilweise / fehlt**, jeweils mit Fundstelle oder Testbeleg. Prüfe mindestens:
1. Konfliktliste über alle Dateien (Zähler, Sprung zum nächsten Konflikt), Anzeige im Git-Panel und als Zeichen im Dateibaum.
2. Drei-Wege-Ansicht (Basis, Ours, Theirs, Ergebnis) bei diff3-Markern; Verhalten ohne Basis.
3. Freies Bearbeiten des Ergebnisses je Block (nicht nur vier Auswahlknöpfe), Vorschau des Endtexts.
4. Wortweise Hervorhebung der Unterschiede innerhalb eines Blocks.
5. Sonderfälle: gelöscht-vs-geändert, beidseitig hinzugefügt, Umbenennungen, Binärdateien, Zeilenende-Konflikte (CRLF/LF), Dateimodus.
6. Abschluss: Datei als gelöst markieren und stagen, danach **Weiterführen** bei Merge, Rebase (`--continue`), Cherry-pick, Revert; Abbrechen; sichtbarer Zustand „Rebase 3/7“.
7. Sicherheit: Marker-Reste (`<<<<<<<`) in der Datei werden vor dem Stagen erkannt und gemeldet; Rückgängig für die letzte Auflösung.

## 39.1 Lücken schließen (nur was 39.0 als fehlend/teilweise belegt)
Reihenfolge nach Nutzen: (a) Konfliktliste + Sprung + Abschlussablauf (Punkte 1 und 6), (b) Bearbeiten des Ergebnisses je Block und Marker-Prüfung (3 und 7), (c) Drei-Wege-Ansicht und Wortvergleich (2 und 4), (d) Sonderfälle (5). Je Punkt eigener Commit. Bestehende Klassen erweitern, kein Parallelmodul; `ConflictParser` bleibt reine Kotlin-Logik mit Tests.

## 39.2 UI-Vorgaben
Auf dem Smartphone nutzbar: Blöcke untereinander (nicht nebeneinander), Ours/Theirs/Basis einklappbar, große Tippflächen, Sprung „vorheriger/nächster Konflikt“ in einer festen Leiste, Fortschritt „2 von 5 gelöst“. Farben aus dem Theme, nicht fest.

## 39.3 Tests
* `ConflictParser`: Verschachtelung, Marker im Text, CRLF, fehlende Basis, leere Seiten, mehrere Blöcke, Idempotenz.
* Echtes Testrepo mit JGit: Merge-Konflikt, Rebase-Konflikt (Weiterführen), Cherry-pick, gelöscht-vs-geändert; erwartet wird jeweils der Endzustand des Arbeitsverzeichnisses.
* ViewModel: Auswahl, Bearbeiten, Abschluss, Abbruch.

## Abnahme
Build grün, Tests grün, Tabelle aus 39.0 im Bericht, keine doppelte Konfliktlogik im Repo.
