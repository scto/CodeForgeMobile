# Task 55 – Projekt-Stammverzeichnis im externen Speicher, `.cfm/` und `cfm.json` Workspace mit „Speichern beim Schließen“

## 1. Zusammenfassung

In Task 55 wurde das Standard-Projektstammverzeichnis auf den öffentlichen externen Speicher standardisiert (`/storage/emulated/0/CodeForgeMobileProjects/`). Das IDE-Verzeichnis `.cfm/` wurde für rotierende Anwendungslogs (`.cfm/log/`) und synchrone Absturzprotokolle (`.cfm/crash/`) eingeführt. Jedes Projekt verwaltet nun seinen Workspace-Zustand (`version: 1`, `project`, `updatedAt`, `files`, `openFiles`, `modifiedFiles`) in `<Projekt>/.cfm/cfm.json`. Beim Verlassen des Editors mit ungespeicherten Änderungen fordert ein Material 3 Dialog zum Speichern oder Verwerfen auf; defekte Dateien oder Abstürze werden robust abgefangen und Altdatenbestände aus dem internen Speicher (`filesDir/projects`) können mit Backup- und Verifikationsschritt migriert werden.

---

## 2. Historischer Befund (55.1)

### 2.1 Verzeichnisname in der Git-Historie
Eine Suche nach `git log -S"CodeForgeProjects"` und `git log -S"CodeForgeMobileProjects"` ergab:
* **Treffer:** Commit `4bdb999 ("feat(terminal): integrate termux-shared with native-lib and robust pathing")`
* **Implementierung:** Der Pfad `/storage/emulated/0/CodeForgeMobileProjects` bzw. `Environment.getExternalStorageDirectory()/CodeForgeMobileProjects` wurde offiziell verwendet.
* **Entscheidung:** Der Standardname lautet einheitlich **`CodeForgeMobileProjects`**.
* **Zentraler Ort:** Konstante `ProjectsDirectoryProvider.PROJECTS_DIR_NAME = "CodeForgeMobileProjects"` in `:core:domain`, implementiert in `:core:data:ProjectsDirectoryProviderImpl`.

### 2.2 Relevante Stellen für Projektpfade
1. **Template Engine / Assistent:** `TemplateEngineRepositoryImpl.defaultProjectsDirectory()` nutzt nun `projectsDirectoryProvider.getProjectsRoot().absolutePath`.
2. **Projekt-Import:** `ProjectImportRepositoryImpl.uniqueTargetDirectory()` nutzt `projectsDirectoryProvider.resolveProjectPath(sanitizedName)`.
3. **Git Clone:** `GitCloneViewModel` initialisiert das Zielverzeichnis über `projectsDirectoryProvider.getProjectsRoot().absolutePath`.
4. **Recent Projects:** `RecentProjectsRepositoryImpl` filtert das IDE-eigene Verzeichnis `.cfm` automatisch aus.
5. **Dateibaum:** `FileTreeScreen` filtert `.cfm` aus der Projekt-Dateistruktur heraus.
6. **Git Exclude:** `WorkspaceRepositoryImpl` trägt `.cfm/` automatisch und idempotent in `<Projekt>/.git/info/exclude` ein, sodass Workspace-Zustände nie in Commits oder `git status` erscheinen.

### 2.3 Speicherzugriff und Berechtigungen
* **Android 11+ (API 30+):** `Environment.isExternalStorageManager()` (`MANAGE_EXTERNAL_STORAGE`).
* **Android 10 und älter:** `ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)`.
* **Rückfall ohne Berechtigung:** `context.getExternalFilesDir("projects") ?: File(context.filesDir, "projects")`.
* **Hinweis & Dialog:** Die App informiert den Nutzer transparent, falls keine Speicherberechtigung vorliegt, und bietet einen Absprung in die Android-Systemeinstellungen.

---

## 3. Architektur & Komponenten

### 3.1 Stammverzeichnis & Sicherheit (`ProjectsDirectoryProvider`)
* Bereinigt Projektnamen mit Regex `[^A-Za-z0-9._-]+` und Verbot des reservierten Namens `cfm`.
* Verhindert Directory Traversal (`..`, `/`, Symlink-Ausbrüche) via `isSubpathOfProjectsRoot()`.

### 3.2 Logging & Crash-Handling (`.cfm/log/` & `.cfm/crash/`)
* **`FileCfmLogger` (`core:common`):**
  - Schreibt asynchron über einen Coroutine `Channel` in tägliche Logdateien: `.cfm/log/cfm-YYYY-MM-DD.log`.
  - Bereinigt Tokens, Passwörter und sensible Strings automatisch (`[REDACTED]`).
  - Rotiert und begrenzt Logs auf 14 Tage Retention und maximal 10 MiB pro Datei.
* **`CfmCrashHandler` (`core:common` & `CodeForgeApplication`):**
  - Fängt ungefangene Exceptions synchron vor dem JVM-Tod via `Thread.setDefaultUncaughtExceptionHandler` ab.
  - Schreibt `.cfm/crash/crash-YYYYMMDD-HHmmss.txt` mit standardisiertem Header:
    ```
    === Crash at 2026-10-26T03:00:00Z in thread main ===
    App: com.codeforge.app 1.0.0 (100)
    OS: Android API 35 (15)
    Device: Google Pixel 9 Pro XL (komodo)

    Stacktrace:
    ...
    ```
  - Pruning: Behält maximal die letzten 20 Crash-Reports.
  - Beim App-Start prüft `WelcomeViewModel` auf neue Crash-Logs und zeigt einen Material 3 Dialog mit „Ansehen“, „Teilen“ (via `CfmFileProvider`) und „Löschen“.

### 3.3 Workspace-Verwaltung (`<Projekt>/.cfm/cfm.json`)
* **Schema (Version 1):**
  ```json
  {
    "version": 1,
    "project": "MeinProjekt",
    "updatedAt": "2026-10-26T03:00:00Z",
    "files": [
      { "path": "app/src/Main.kt", "size": 1234, "modified": 1790000000000 }
    ],
    "openFiles": [
      { "path": "app/src/Main.kt", "active": true, "cursor": { "line": 10, "column": 4 } }
    ],
    "modifiedFiles": [ "app/src/Main.kt" ]
  }
  ```
* **Ausschlussliste für Scan:** `.cfm`, `.git`, `build`, `.gradle`, `.idea`, `node_modules`, `bin`, `out`, `.cxx`, `.externalNativeBuild`.
* **Atomares Schreiben:** Zuerst nach `cfm.json.tmp`, gefolgt von atomarem Rename (`StandardCopyOption.ATOMIC_MOVE`).
* **Korruptionssicherheit:** Beschädigte JSON-Dateien werden umbenannt in `cfm.json.broken-<Timestamp>` und ein intakter Standard-Workspace wird neu erzeugt. Nebenläufige Zugriffe werden pro Projekt über einen `Mutex` serialisiert.
* **Wiederherstellung:** Beim Öffnen eines Projekts stellt `EditorViewModel` alle zuvor geöffneten Tabs und den aktiven Tab wieder her.

### 3.4 Speichern beim Schließen (Material 3 Dialog)
* **Auslöser:** Navigation zurück aus dem Editor (`BackHandler`, Projekt schließen, App-Verlassen).
* **Verhalten:**
  - Ist keine Datei modifiziert (`hasDirtyFiles == false`), erfolgt der Schließvorgang sofort.
  - Sind ungespeicherte Dateien vorhanden, öffnet sich `SaveOnCloseDialog`.
  - **Speichern:** Alle modifizierten Dateien werden geschrieben. Bei vollständigem Erfolg wird `modifiedFiles` in `cfm.json` geleert und der Schließvorgang vollendet.
  - **Fehlerbehandlung:** Schlägt das Schreiben einer Datei fehl (z. B. I/O-Fehler), verbleibt diese in der Liste, der Dialog zeigt eine Fehlermeldung und das Schließen wird gestoppt.
  - **Verwerfen:** Änderungen im Editor-Puffer werden verworfen, von der Festplatte neu geladen, `modifiedFiles` geleert und das Schließen fortgesetzt.
  - **Abbrechen:** Dialog schließt sich; der Nutzer verbleibt im Editor mit unveränderten Dirty-Puffern.
* **Absturz-Wiederherstellungshinweis:** Wenn beim Öffnen eines Projekts `modifiedFiles` in `cfm.json` nicht leer ist (z. B. nach einem vorherigen Absturz), wird eine Hinweismeldung angezeigt, dass ungespeicherte Änderungen verloren gegangen sein könnten.

### 3.5 Legacy-Projekt-Migration
* `LegacyProjectMigratorImpl` erkennt vorhandene Projekte in `filesDir/projects`.
* `WelcomeScreen` fragt den Nutzer mit Projektanzahl und Pfaden.
* Bei Bestätigung:
  1. Erstellt ZIP-Archiv als Backup (`backup-projects-<ts>.zip`).
  2. Kopiert alle Projektdateien rekursiv nach `CodeForgeMobileProjects/`.
  3. Verifiziert Dateianzahl und Gesamt-Bytes.
  4. Löscht Quellverzeichnis erst nach erfolgreicher Verifikation.
* Bei Ablehnung merkt sich DataStore die Präferenz (`legacy_migration_declined = true`), sodass kein nerviges Wiederaufpoppen erfolgt.

---

## 4. Test- und Verifikationsergebnisse

| Modul | Testsuite / Task | Ergebnis |
|---|---|---|
| `:core:common` | `CfmLoggerTest`, `CfmCrashHandlerTest` | **PASSED** (14-Tage-Rotation, 10-MiB-Limit, Crash-Header, Pruning) |
| `:core:data` | `ProjectsDirectoryProviderTest`, `WorkspaceRepositoryTest`, `LegacyProjectMigratorTest` (86 Tests) | **PASSED** (Pfad-Sanitization, Traversal-Schutz, atomares Schreiben, Migration, Backup-Verifikation) |
| `:feature:editor` | `EditorViewModelSaveOnCloseTest`, `EditorTabRowCrashRegressionTest`, `EditorViewModelInitTest`, etc. | **PASSED** (Speichern beim Schließen, Teilfehler, Verwerfen, Tab-Wiederherstellung) |
| `:feature:welcome` | `WelcomeViewModelTest` | **PASSED** (Crash-Report-Handling, Migrationsabfrage) |
| `:libs:template-engine` | `TemplateEngineRepositoryTest` | **PASSED** (Verwendung des standardisierten Projektstammpfads) |
| `:feature:git` | `GitViewModelTest` | **PASSED** (Initialer Klonpfad) |
| `:app` | `assembleDebug` | **BUILD SUCCESSFUL** (`app-debug.apk`, 165 MiB) |

---

## 5. Bereitstellung

Die fertige Debug-APK wurde nach `/sdcard/Download/` kopiert:
```bash
/sdcard/Download/CodeForgeMobile-debug.apk (165 MiB)
```

---

## 6. Getestet vs. Nicht getestet

### Getestet (Automatisiert)
- [x] Standard-Verzeichnisname `CodeForgeMobileProjects` verifiziert und unit-getestet.
- [x] Pfad-Sanitization gegen Directory Traversal (`..`, Ausbrüche) und Verbot von `cfm` als Projektname.
- [x] Logger: asynchrones Schreiben, tägliche Dateibenennung, Token/Passwort-Maskierung.
- [x] Synchroner Crash-Handler: Header-Format, Pruning auf 20 Einträge, Exception-Kette, wirft nie selbst.
- [x] FileProvider-Integration für das Teilen von Crash-Logs.
- [x] `cfm.json` Workspace-Schema (Version 1): atomares Schreiben über `.tmp`, automatische Broken-Datei-Erzeugung bei ungültigem JSON, Idempotenz in `.git/info/exclude`.
- [x] SaveOnClose-Dialog: Speichern, Verwerfen, Abbrechen, Fehlerdarstellung bei Schreibfehlern.
- [x] Migration von Legacy-Projekten inkl. ZIP-Backup, Verifikation und DataStore-Persistierung bei Ablehnung.
- [x] Vollständiger `./gradlew assembleDebug` Build.

### Nicht getestet (Offen für Gerätetest)
- [ ] Physischer App-Start und Dateisystem-Schreibvorgang direkt auf dem Google Pixel 9 Pro XL im Android-Hostsystem (wird vom Nutzer auf dem echten Gerät geprüft).
- [ ] Anzeige des System-Berechtigungsdialogs für `MANAGE_EXTERNAL_STORAGE` auf Android 15.
