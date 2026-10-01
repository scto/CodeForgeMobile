# Changelog

Alle wesentlichen Änderungen an diesem Projekt werden in dieser Datei dokumentiert.
Das Format basiert auf [Keep a Changelog](https://keepachangelog.com/de/1.1.0/),
dieses Projekt folgt [Semantic Versioning](https://semver.org/lang/de/).

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