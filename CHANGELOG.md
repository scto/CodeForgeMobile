# Changelog

Alle wesentlichen Änderungen an diesem Projekt werden in dieser Datei dokumentiert.
Das Format basiert auf [Keep a Changelog](https://keepachangelog.com/de/1.1.0/),
dieses Projekt folgt [Semantic Versioning](https://semver.org/lang/de/).

> **Versionierung:** `versionName` und `versionCode` stehen in `app/build.gradle.kts`.
> Schema für den Code: `MAJOR*10000 + MINOR*100 + PATCH` (3.0.0 → `30000`).
> **Alle Einträge seit 3.0.0 sind ungebaut** (kein Gradle-/Android-Lauf); geprüft wurde nur die
> reine JVM-Logik (Git, Code-Tools, Layout-Modell, XML) und die Kotlin-Syntax. Siehe `STATUS.md`.

## [3.0.0] - 2026-10-08

### 🏗️ Termux statt Rootfs, zentrale Strings, Build-Logic, Adaptive/Expressive, Layout-Designer

Breaking: Die Rootfs-/PRoot-/Multi-Distro-Umgebung ist entfernt; die Shell läuft im Termux-Prefix
(`/data/data/com.codeforge.app/files/usr`). Die Version springt von der 2.x-Linie auf 3.0.0
(vorher im Repository noch `0.1.0`/`versionCode 1`).

#### Added
- **Termux-Vendoring** (GPLv3): `:libs:termux-emulator`, `:libs:termux-view`, `:libs:termux-shared`, `:libs:termux-app` (aus `scto/AndroidIDE`, auf `com.codeforge` umgeschrieben, JNI-Symbole angepasst). `TerminalSessionRepositoryImpl` startet echte Termux-Shells; Lizenzfolge in `docs/sub/NOTICE.md`.
- **`codeforge-env`** (`:libs:terminal-engine`, Asset): einziges Setup-Skript für JDK 17/21, Android-SDK (`$PREFIX/opt/android-sdk`), cmdline-tools, platform-tools, CMake, NDK; `--machine`-Protokoll `CFSDK|…` für den SDK-Manager (GUI). Onboarding: Bootstrap → Skript → Terminal mit Autostart-Kommando. `ScriptInstaller` als Fallback.
- **`:core:resources`**: ~720 Texte zentral in `strings.xml`; Zugriff `Res.string(…)` (Nicht-UI) / `stringRes(…)` (Compose), Enum-/Listen-Labels als `@StringRes`; `TestRes` für JVM-Tests in `:core:testing`. Doku `docs/resources-and-strings.md`.
- **build-logic** mit Convention-Plugins (`codeforge.android.application|library|library.compose|hilt`, `codeforge.kotlin.library`, `codeforge.quality`, `codeforge.terminal.bootstrap`), Version-Catalog, Gradle 8.14.3 / AGP 8.13.2. Doku `docs/build-logic.md`.
- **Modul-Pflichtdateien** für alle 43 Module (`.gitignore`, `consumer-rules.pro`, `proguard-rules.pro`, Manifeste) sowie `:app`-Ressourcen (Theme, Farben, Launcher-Icons, Backup-/Extraktionsregeln). Doku `docs/module-files-and-app-resources.md`.
- **Adaptive Layouts, Edge-to-Edge, Material 3 Expressive**: `WidthClass` (`:core:ui`), dauerhafter Drawer ab 840 dp, `NavigableListDetailPaneScaffold` im Welcome-Screen, `enableEdgeToEdge` mit themenabhängigen Systemleisten, Inset-Behandlung, `MaterialExpressiveTheme` + `MotionScheme.expressive()`. Doku `docs/adaptive-edge-to-edge-expressive.md`.
- **Layout-Designer** (`:feature:layoutdesigner`): visueller Editor für Android-Layout-XML (Palette, Compose-Vorschau, Baum, Eigenschaften, XML-Tab, Undo/Redo, Speichern), Drawer-Bereich „Layouts“. Doku `docs/layout-designer.md`.
- **Git-Panel** (`:feature:git`, JGit): Diff, Merge, Commit, Push/Pull, Graph, Stash, Tags, Rebase, Cherry-Pick, Revert, Reset, Amend, Blame, Hunk-weises Stage, Konflikt-Editor; Git-Einstellungen. Doku `docs/editor-tools-git-drawer.md`.
- **Suche & Ersetzen** (`:feature:search`), **Submodule Maker** (`:feature:modulemaker`), `:libs:code-tools` (reine JVM-Logik, 53 Tests).
- **Indexierung und Dependency-Updater** (`:libs:indexing-*`, `:libs:dependency-updater-*`, `:feature:dependencyupdates`) mit Editor-Overlays (Versions-Hinweis im TOML, Farb-Kästchen). Doku `docs/indexing-and-dependency-updater.md`.
- **Projekt-Wizard und Template-Engine** komplett neu (9 Vorlagen, kein Freemarker-Einsatz mehr). Doku `docs/project-wizard-and-template-engine.md`.
- **Dokumentation/Betrieb:** `docs/architecture-decisions.md` (ADRs), `agy-tasks/01`–`16` (Prüf- und Umsetzungsaufträge für agy), `build_codeforge_repo.sh` (Bootstrap- und APT-Repo-Build).

#### Changed
- Toolchain: Kotlin 2.1.21, KSP 2.1.21-2.0.1, Hilt 2.56.2, Compose BOM 2025.09.00, material3-adaptive 1.1.0, activity-compose 1.10.1 (**nicht gegen Maven/Google verifiziert**).
- `versionName` `0.1.0` → `3.0.0`, `versionCode` `1` → `30000`.
- Termux-/Lizenzdokumente liegen unter `docs/sub/` (`TERMUX-PORTING.md`, `NOTICE.md`, `LICENSE.termux`); Referenzen im Code angepasst.
- `README.md`/`README_DE.md`, `STATUS.md` und dieses Changelog auf den tatsächlichen Repository-Stand gebracht (vorher teils Beschreibungen des lokalen Experimentzweigs).

#### Removed
- PRoot, Rootfs-Downloader, `DistroBootstrapRepository`, Multi-Distro-Auswahl, `setup-ide`/`xmltool.sh`/`tools/cfmide`/`codeforge-tools`.

#### Fixed
- Verschachtelte-Kommentar-Syntaxfehler in KDoc (`ComposePreviewRendererImpl.kt`, `TextMateAssetLoader.kt`).
- `$PREFIX` wurde im SDK-Manager-Text als Kotlin-Template interpretiert.
- Klartext-GPG-Passphrase in `build_codeforge_repo.sh`/`.md` durch Umgebungsvariable `CODEFORGE_GPG_PASSPHRASE` ersetzt (**die Passphrase gilt als kompromittiert, falls das Repository öffentlich war – rotieren**).

#### Known Issues
- Nichts davon wurde mit Gradle/Android gebaut. Offen: W^X-/Exec-Frage für Binaries unter `files/usr` (targetSdk 35), echter Bootstrap-Release für `com.codeforge.app`, Verifikation der angehobenen Versionen. Siehe `STATUS.md` und `agy-tasks/README.md`.
- Features aus dem lokalen Experimentzweig (u. a. Minimap, Rainbow Brackets, Extensions-Manager, 15 Theme-Presets, Datei-Eigenschaften im Dateibaum) sind in diesem Stand **nicht** enthalten – siehe `agy-tasks/16-port-local-experiment-features.md`.

---

> Die folgenden Einträge stammen aus der **lokalen Experimentlinie** (Termix/Nyamux-Terminal, Rootfs) und beschreiben
> Code, der in diesem Repository-Stand nicht vorhanden ist.

## [2.2.0] - 2026-09-24

### 🚀 FileTree, Theme Studio, Editor & Extensions Overhaul

#### Added
- **FileTree Features (`:feature:filetree`)**:
  - Dynamische, kontrastreiche & theme-adaptive Einrückungslinien (`start = Offset(lineX, 0f)`, `end = Offset(lineX, size.height)`), die sich zeilenübergreifend nahtlos verbinden.
  - 2-zeilige Datei-Informationen (Dateigröße & Änderungsdatum) unterhalb des Dateinamens in reduzierter Schriftgröße (`10.sp * uiScale`).
  - Ganzzeiliges Tap-Target (`fillMaxWidth()`) für alle Datei- und Ordnerzeilen im Drawer.
  - Kontextmenü-Erweiterung um **Eigenschaften (Properties)** (Name, Pfad, Typ, Größe, Änderungsdatum, Berechtigungen) und Bestätigungsdialog für **Löschen**.
- **Theme Studio Presets (`:feature:themebuilder`)**:
  - 15 integrierte Themes (Monokai, Darcula, Quiet Light, Ayu Dark, Solarized Dark, GitHub Light, VS Code Dark+, Notepad++, Eclipse, Cyberpunk Neon, Forest, Ocean, Sunset, Monochrome, Violet).
  - Horizontale scrollbare `LazyRow`-Vorschau mit Live-Farbkarten.
  - Interaktiver RGB-Colorpicker (`ColorPickerDialog`) mit 10 Custom Presets.
- **Erweiterungen (Extensions) Settings Screen (`:feature:settings`)**:
  - Neuer Screen `ExtensionsScreen.kt` & `ExtensionsManager.kt` unter `com.codeforge.feature.settings.extensions`.
  - Download, SHA-256 Checksummen-Verifizierung, Zip/Jar-Entpackung & Verwendungsprüfung für LSP Language Server.
  - Navigation über `Routes.EXTENSIONS` in `CodeForgeNavHost.kt`.

#### Fixed
- **SoraEditor Scrollbars (`:feature:editor`)**:
  - `editor.isVerticalScrollBarEnabled` und `editor.isHorizontalScrollBarEnabled` werden in `SoraEditorAppearance.kt` nun strikt über `config.scrollbarEnabled` geschaltet.

---

## [2.0.0] - 2026-09-03

### 🎯 Migration abgeschlossen: Clean Architecture Refactor (Termix → CodeForgeMobile)

Vollständige Migration der Codebasis auf ein Multi-Module Clean-Architecture-Setup
mit Jetpack Compose. Verifiziert und sign-off-erteilt gemäß strikter Audit-Prüfung
(siehe [`08_final_audit.md`](.agent/docs/migration-reports/08_final_audit.md)).

#### Added
- Neue Multi-Module-Struktur: `:app`, `:core`, `:feature`, `:libs`
- Terminal-Feature vollständig in Jetpack Compose (`TerminalScreen.kt`) mit
  Unidirectional Data Flow via `StateFlow`
- `TerminalForegroundService` mit `systemExempt`-Konfiguration in
  `AndroidManifest.xml` (`core/data`)
- `RunCommandWorker` für Hintergrund-Kommando-Ausführung

#### Changed
- Terminal-Session-Handling refaktoriert: `TerminalSessionRepositoryImpl.kt`
  ruft nun korrekt `finishIfRunning()` zum sauberen nativen Session-Abbau auf
- Settings-Feature auf neue Modulstruktur migriert
  (siehe `03_settings_feature_migration_report.md`)

#### Removed
- Legacy-Package `com.termix` vollständig aus allen produktiven Modulen
  (`app`, `core`, `feature`, `libs`) entfernt

#### Known Issues
- Verzeichnis `Termix-main-extract/` (Legacy-Backup) enthält weiterhin
  altes `com.termix`-Package. **Kein Einfluss auf Build/Runtime**, da nicht im
  Gradle-Include-Pfad. Empfehlung: Ordner archivieren oder via `.gitignore`
  ausschließen (Cleanup-Task, kein Blocker).

#### Verification
- Vollständige Code-Inspektion aller migrierten Module durchgeführt
- Historische Migrationsreports (`01`–`07`) gegen aktuellen Codebestand
  gegengeprüft — ein dokumentierter Widerspruch (s. o.), keine Blocker
- **Sign-off erteilt:** ✅ `.agent/docs/migration-reports/08_final_audit.md`

---

## [1.x.x] - Vorherige Migrationsphasen
<!-- Optional: Verlinke ältere Phasen, falls gewünscht -->
- Phase 3 Verifizierung: `PHASE_3_VERIFICATION_REPORT.md`
- Phase 2 Implementierung: `PHASE_2_IMPLEMENTATION_REPORT.md`
- Terminal-Feature-Migration: `02_terminal_feature_migration_report.md`
- Initiales Repo-Audit: `01_full_repo_audit_report.md`