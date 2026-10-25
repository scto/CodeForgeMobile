# Task 55 – Projekt-Stammverzeichnis im externen Speicher, `.cfm/` (Logs, Crash-Logs) und `cfm.json` (Workspace, „Speichern beim Schließen“)

**Ziel:** Die IDE hat wieder ein **primäres Projektverzeichnis im externen Speicher** (`CodeForgeMobileProjects/`), in dem alle erstellten, importierten und geklonten Projekte liegen. Darin liegt ein IDE-Ordner `.cfm/` mit `log/` (IDE-Logdateien) und `crash/` (Crash-Logs). Jedes Projekt bekommt `<Projekt>/.cfm/cfm.json` mit dem Workspace-Zustand (alle Dateien, geöffnete Dateien, geänderte Dateien). Beim Schließen von Projekt oder Editor fragt ein Dialog, ob geänderte Dateien gespeichert werden sollen; bei „OK“ werden alle gespeichert und `cfm.json` wird so aktualisiert, dass die Liste der geänderten Dateien leer ist.

**Warum jetzt (Befund des Assistenten, selbst nachprüfen):**
* Ein älterer Build hatte ein solches Verzeichnis im externen Speicher (Name laut Inhaber „CodeForgeProjects“ bzw. „CodeForgeMobileProjects“). Im aktuellen Stand gibt es **keinen** Treffer für beide Namen. Projekte liegen **app-intern** unter `context.filesDir/projects`: `TemplateEngineRepositoryImpl.defaultProjectsDirectory()` und `ProjectImportRepositoryImpl.uniqueTargetDirectory()`, also nicht für den Nutzer oder andere Apps erreichbar.
* Es gibt keinen Crash-Handler und keine Logdateien der IDE im Projektbaum; der Absturzbericht aus Task 54 wurde nur manuell gesichert (Format `=== Crash at <Datum> in thread main ===` + Stacktrace).
* `MANAGE_EXTERNAL_STORAGE` ist im Manifest (`app`) und im Onboarding (`OnboardingRoute`) bereits vorhanden.

**Gemeinsame Regeln:** Kotlin, Clean Architecture (UI / Domain / Data getrennt), MVI/MVVM, Coroutines und `StateFlow`, UI-States unveränderliche Datenklassen, Material Design 3, Texte als String-Ressourcen (`de` + Standard), keine Geschäftslogik in Composables. Nichts erfinden; was nicht ausgeführt wurde, steht im Bericht unter **nicht getestet**. Keine Geheimnisse und keine Dateiinhalte in Logs. Bericht in `docs/projects-root-cfm-report.md`; `agy-tasks/TASKS-STATUS.md` und `README.md` ergänzen (Commit-Hash). Rücksicht auf Task 50 (`j2k`) und Task 52 (`x2c`): deren Backup-/Journal-Pfade werden an die neue Struktur angepasst, nicht doppelt gebaut.

## 55.1 Befund (erst selbst prüfen)
1. **Name des Verzeichnisses:** `git log -S"CodeForgeProjects" --all` und `git log -S"CodeForgeMobileProjects" --all` suchen, welcher Name im älteren Build galt und wie er ermittelt wurde. Gab es ihn dort, **diesen Namen übernehmen**; sonst gilt `CodeForgeMobileProjects`. Der Name steht an **einer** Stelle (Konstante in `:core:common`/`:core:domain`), nirgends sonst als Literal.
2. Alle Stellen, die Projektpfade bilden oder `filesDir/projects` kennen (`grep -rn "\"projects\"\|defaultProjectsDirectory\|filesDir"`): Projekt-Assistent (`ProjectWizardViewModel`, `lastSaveLocation`), Import (`ProjectImportRepositoryImpl`), Git-Klon (`GitRepositoryImpl`), Willkommensseite (zuletzt geöffnet), Terminal-Arbeitsverzeichnis, Indexierung. Liste im Bericht.
3. Speicherzugriff je Android-Version: `MANAGE_EXTERNAL_STORAGE` (Android 11+) bzw. `WRITE_EXTERNAL_STORAGE`; was passiert ohne Berechtigung (siehe 55.2 Rückfall). Beleg im Bericht, nicht aus dem Gedächtnis.

## 55.2 Projekt-Stammverzeichnis
1. Pfad: `<externer Speicher>/CodeForgeMobileProjects/` (`Environment.getExternalStorageDirectory()`), wird beim ersten Start und nach Erteilen der Berechtigung angelegt. Neue und importierte/geklonte Projekte landen **standardmäßig** hier (`defaultProjectsDirectory()`, Import, Klon, Assistent). Der Nutzer kann den Zielordner im Assistenten weiter ändern (`lastSaveLocation`).
2. **Rückfall ohne Berechtigung:** `context.getExternalFilesDir("projects")` (app-spezifisch, ohne Sonderrechte), dazu ein deutlicher Hinweis (Banner/Dialog) mit Weg in die Systemeinstellung. Kein stiller Absturz, kein stilles Schreiben in `filesDir`.
3. **Migration des Bestands (nur mit Rückfrage):** Projekte unter `filesDir/projects` werden erkannt; Dialog „N Projekte in den öffentlichen Projektordner verschieben?“ mit **Kopieren, Prüfen (Dateianzahl und Größe), erst dann Löschen der Quelle**; vorher ZIP-Backup wie in Task 50/52. Abbruch lässt alles unverändert. Eine abgelehnte Migration wird nicht bei jedem Start erneut gefragt (Einstellung merken, über die Einstellungen anstoßbar).
4. Pfad-Sicherheit: Projektname bereinigen (wie bisher `[^A-Za-z0-9._-]`), kein `..`, keine Symlink-Ausbrüche aus dem Stammverzeichnis; Test dafür.

## 55.3 `.cfm/` im Stammverzeichnis: `log/` und `crash/`
Struktur: 
```
CodeForgeMobileProjects/
├── .cfm/
│   ├── log/     IDE-Logdateien
│   └── crash/   Crash-Logs
└── <Projekt-Name>/
    └── .cfm/cfm.json
```
1. **Log (`log/`):** `Logger`-Schnittstelle in `:core:common`; eine Implementierung schreibt in `log/cfm-YYYY-MM-DD.log` (eine Datei pro Tag, Zeile: Zeit, Stufe, Tag, Nachricht). Rotation/Aufbewahrung: z. B. 14 Tage oder 10 MiB je Datei, Werte als Konstanten und im Bericht begründet. Schreiben asynchron (Kanal + eine Schreib-Coroutine), nie auf dem Hauptthread. Keine Dateiinhalte, Tokens, Passwörter oder Passphrasen loggen.
2. **Crash (`crash/`):** In `CodeForgeApplication` ein `Thread.UncaughtExceptionHandler`, der **synchron** (der Prozess stirbt gleich) `crash/crash-YYYYMMDD-HHmmss.txt` schreibt: Kopf `=== Crash at <Datum> in thread <name> ===`, App-Version, Android-Version, Gerät, danach der Stacktrace samt `Caused by`. Danach den vorherigen Handler aufrufen (Standardverhalten bleibt). Der Handler darf selbst nie werfen (try/catch um alles). Ist das Stammverzeichnis nicht beschreibbar, Rückfall auf `getExternalFilesDir("crash")`.
3. **Beim nächsten Start:** Liegt ein neuer Crash-Log vor, bietet ein Dialog „Absturzbericht ansehen/teilen/löschen“ an (teilen über `FileProvider`, wie `EditorFileProvider`). Alte Crash-Logs werden nach Anzahl begrenzt (z. B. letzte 20).
4. `.cfm/` im Stammverzeichnis wird von der Projektliste und vom Dateibaum **nicht** als Projekt angezeigt; Import/Klon darf kein Projekt namens `.cfm` anlegen.

## 55.4 `<Projekt>/.cfm/cfm.json` (Workspace)
Schema (Version 1; Felder fest, Pfade **relativ zum Projektordner**, `/` als Trenner):
```json
{
  "version": 1,
  "project": "MeinProjekt",
  "updatedAt": "2026-10-25T22:10:41Z",
  "files": [ { "path": "app/src/Main.kt", "size": 1234, "modified": 1790000000000 } ],
  "openFiles": [ { "path": "app/src/Main.kt", "active": true, "cursor": { "line": 10, "column": 4 } } ],
  "modifiedFiles": [ "app/src/Main.kt" ]
}
```
1. **`files`:** alle Dateien des Projekts (rekursiv), ohne `.cfm/`, `.git/`, `build/`, `.gradle/`, `node_modules/` und weitere über eine Ausschlussliste (Konstante, im Bericht begründet). Ein Scan läuft im Hintergrund (`Dispatchers.IO`), bei sehr großen Projekten mit Obergrenze und Hinweis; danach inkrementelle Aktualisierung bei Anlegen/Löschen/Umbenennen über den Dateibaum (`:feature:filetree`), nicht bei jeder Tastatureingabe neu scannen.
2. **`openFiles`:** die offenen Tabs (`EditorUiState.openFiles`), aktive Datei und Cursorposition; beim Öffnen des Projekts **wiederherstellen** (Tabs wieder öffnen; Dateien, die es nicht mehr gibt, still auslassen).
3. **`modifiedFiles`:** Pfade mit ungespeicherten Änderungen (`OpenFile.isDirty`). Wird bei Änderung des Dirty-Zustands (entprellt, z. B. 500 ms) geschrieben.
4. **Schreiben atomar:** in `cfm.json.tmp` schreiben, dann umbenennen; defekte oder unbekannte Version → Datei als `cfm.json.broken-<ts>` sichern und neu aufbauen, nie crashen. Nebenläufige Schreibzugriffe über einen `Mutex` je Projekt serialisieren.
5. **Git-Verträglichkeit:** `.cfm/` wird beim Anlegen in `.git/info/exclude` eingetragen (nicht in die versionierte `.gitignore` schreiben), damit `cfm.json` nie in Commits landet. Bestehende Einträge nicht doppeln.
6. Architektur: Modell `CfmWorkspace` in `:core:domain`, Schnittstelle `WorkspaceRepository` (laden, speichern, `files` neu einlesen) dort, Implementierung in `:core:data`, Nutzung aus `EditorViewModel`/Projekt-ViewModel; Composables kennen nur den State.

## 55.5 „Speichern beim Schließen“ (Dialog)
1. Auslöser: Projekt schließen, zur Projektliste/Willkommensseite zurück, Editor verlassen (Zurück-Taste) und App-Beenden, soweit abfangbar. Ist `modifiedFiles` leer, kein Dialog.
2. Dialog (Material 3): Titel „Änderungen speichern?“, Liste der geänderten Dateien (scrollbar, max. Höhe), Schaltflächen **Speichern** (OK), **Verwerfen**, **Abbrechen** (bleibt im Editor).
3. **Speichern:** alle geänderten Dateien schreiben (atomar, Fehler je Datei sammeln); nur **erfolgreich** gespeicherte Dateien verlassen `modifiedFiles`. Danach `cfm.json` aktualisieren; bei vollständigem Erfolg ist `modifiedFiles` **leer** und der Schließvorgang läuft weiter. Schlägt eine Datei fehl, bleibt sie in der Liste, der Dialog nennt die Datei und den Grund, das Schließen wird **nicht** durchgeführt.
4. **Verwerfen:** Änderungen im Speicher verwerfen, `modifiedFiles` leeren, Dateien auf der Platte bleiben unverändert.
5. **Wiederherstellung nach Absturz:** Steht beim Start in `cfm.json` noch `modifiedFiles` ≠ leer (Absturz oder Prozess beendet), weist ein Hinweis darauf hin, dass ungespeicherte Änderungen verloren sein können. Optional (nur wenn mit vertretbarem Aufwand möglich und im Bericht begründet): Entwürfe der geänderten Dateien in `.cfm/drafts/` ablegen und anbieten. Nicht erfinden, nicht stillschweigend versprechen.
6. Zusammenspiel mit Task 54: Ohne geöffnete Datei zeigt der Editor den Leerzustand, kein Dialog; `activeFileIndex` bleibt gültig.

## 55.6 Tests
* Unit: Pfadbildung (Stammverzeichnis, Rückfall, Bereinigung, kein Ausbruch), Logger (Rotation, Format), Crash-Handler (Datei, Kopfzeile, Handler-Kette, wirft nie), `WorkspaceRepository` (Schema, atomares Schreiben, defekte Datei, unbekannte Version, Ausschlussliste), `.git/info/exclude` (kein Doppeln).
* ViewModel: Dirty-Wechsel → `modifiedFiles` entprellt aktualisiert; Schließen-Dialog: Speichern leert die Liste und aktualisiert `cfm.json`; teilweiser Fehler lässt Rest in der Liste und bricht Schließen ab; Verwerfen; Abbrechen.
* Migration: Kopieren → Prüfen → Löschen; Abbruch und Prüfungsfehler lassen die Quelle unangetastet.
* `./gradlew testDebugUnitTest` für die betroffenen Module grün; Gerätetest auf dem Pixel 9 Pro XL **offen**, bis der Inhaber ihn bestätigt.

## 55.7 Bericht und Status
`docs/projects-root-cfm-report.md` mit Befund (Name des Verzeichnisses aus der Historie), Pfadentscheidung, Schema, Dialog-Ablauf, **getestet / nicht getestet / offene Punkte**.

## Akzeptanz
* Neue, importierte und geklonte Projekte liegen standardmäßig unter `…/CodeForgeMobileProjects/<Projekt>`; ohne Berechtigung greift der dokumentierte Rückfall mit Hinweis.
* `…/CodeForgeMobileProjects/.cfm/log/` und `…/crash/` existieren; ein provozierter Absturz erzeugt eine Datei in `crash/` und der Absturzdialog erscheint beim nächsten Start.
* `<Projekt>/.cfm/cfm.json` enthält `files`, `openFiles`, `modifiedFiles`; nach „Speichern“ im Schließen-Dialog ist `modifiedFiles` leer und alle Dateien sind geschrieben.
* Geöffnete Tabs werden beim erneuten Öffnen wiederhergestellt; `.cfm/` taucht in keinem Git-Status auf.
* Migration des alten Bestands nur mit Rückfrage, mit Backup und Prüfung vor dem Löschen.
* Texte als Ressourcen (`de` + Standard); keine Geschäftslogik in Composables; Tests grün.

## Commit-Vorschlag
```
feat(projects): external projects root, .cfm log/crash dirs and cfm.json workspace with save-on-close

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01Lf4ojCvX11BeECzDFXhL7X
```
