# agy-tasks/ — KI-Agent-Aufträge für agy (antigravity-cli)

Jede Datei in diesem Ordner ist ein **eigenständiger, vollständiger Prompt** für
einen KI-Coding-Agenten (hier: `agy`/antigravity-cli, laufend in Termux auf deinem
Smartphone). Jede Datei enthält genug Kontext, um ohne Rückfragen gestartet zu
werden — Repo-Struktur, betroffene Dateien, exakte Akzeptanzkriterien.

**Aufruf (geprüft gegen `agy --help`, Version agy.va39):** Es gibt keine Option, eine Datei als Prompt
zu übergeben (kein `-f`). Starte `agy` im Projekt-Root (`CodeForgeMobile/`) und gib den Auftrag als Prompt mit:

```bash
cd ~/CodeForgeMobile
agy --mode plan -i "Lies agy-tasks/23-fix-extensions-manager.md und führe den Auftrag aus."
```

- `-i` / `--prompt-interactive`: startet mit dem Prompt und bleibt interaktiv (Rückfragen und Freigaben möglich).
- `--mode plan`: Der Agent plant erst, ohne Dateien zu ändern. Danach (oder direkt) `--mode accept-edits`, damit Änderungen ohne Einzelfreigabe geschrieben werden.
- `-p` / `--print`: einmaliger Lauf ohne Dialog, nur für kleine Aufgaben.
- `-c` / `--continue`: letzte Sitzung fortsetzen.
- `--dangerously-skip-permissions` nur verwenden, wenn du den Task gelesen hast und dem Ablauf vertraust.

Der Agent liest die Task-Datei selbst; so entfällt das Einfügen langer Texte in die Shell.

## Übersicht (Stand 2026-10-16)

Repo-Stand: `adb101f`. ✅ umgesetzt (Commit im Repo), 🟡 teilweise, ⬜ offen. Berichte zu den Verifikations-Tasks: `docs/*-verification.md`, `docs/apt-stages-verification.md`, `docs/restore-lost-editor-features.md`, `docs/verification-followups-report.md`. Der Build läuft laut Inhaber ohne Fehler; Gerätetests stehen aus (`docs/device-test-checklist.md`).

| # | Datei | Status | Hinweis |
|---|---|---|---|
| 01 | `01-textmate-grammars-themes.md` | ✅ `52e4c9b` | TextMate-Grammatiken, Themes |
| 02 | `02-treesitter-native-libs.md` | ✅ `2d66ecc` (+ Task 20) | Tree-sitter Core, 6 Sprachen per Maven |
| 03 | `03-termux-bootstrap-ui-wiring.md` | ✅ überholt | Onboarding startet Bootstrap |
| 04 | `04-termux-bootstrap-package-build.md` | ✅ `a2edcaf` | Release `bootstrap-2026.10.11` (aarch64, arm) |
| 05 | `05-termux-sdk-path-visibility.md` | ✅ überholt | PRoot entfällt, SDK unter `$PREFIX/opt/android-sdk` |
| 06 | `06-optional-full-termux-ui.md` | ⚠️ ersetzt durch 24.4 | volle Termux-Multi-Session-UI (in Task 24.4 umgesetzt) |
| 07 | `07-bonsai-sora-app-followups.md` | ✅ `0dbce28` | `CMakeLists.txt`-Erkennung |
| 08 | `08-verify-indexing-updater-overlays.md` | ✅ `3051341` | Indexierung, Updater, Overlays |
| 09 | `09-verify-project-wizard-template-engine.md` | ✅ `f1dd5ce` | Wizard, Template-Engine |
| 10 | `10-verify-editor-git-drawer.md` | ✅ `0ea8b78` | Editor-Werkzeuge, Git-Panel, Drawer |
| 11 | `11-verify-termux-sdk-script.md` | ✅ `26bc869` | `codeforge-env`, `targetSdk = 28` |
| 12 | `12-verify-build-logic.md` | ✅ `4826199` | build-logic, AGP 8.13.2 |
| 13 | `13-verify-resources.md` | ✅ `a8771ba` | `:core:resources` |
| 14 | `14-verify-adaptive-expressive.md` | ✅ `0c0715a` | Adaptive/Edge-to-Edge, stabiles `MaterialTheme` |
| 15 | `15-verify-layout-designer.md` | ✅ `42924e2` | Layout-Designer |
| 16 | `16-port-local-experiment-features.md` | ✅ `f498c47` (+ Task 23) | Editor, Dateibaum, Theme Studio, Hintergrundbefehle, Fluent 2, Extensions-Manager |
| 17 | `17-externalize-hardcoded-strings.md` | 🔁 wiederkehrend | harte UI-Texte nach `:core:resources` (zweisprachig); letzter Lauf 0 Funde |
| 18 | `18-translate-strings-en-de.md` | ✅ `6bcf72f` | Englisch Standard, Deutsch in `values-de` (825 Strings) |
| 19 | `19-restore-lost-editor-features.md` | ✅ `8383ade` | `EditorSymbolBar`; Rest ersetzt/geprüft |
| 20 | `20-treesitter-remaining-native-langs.md` | ✅ `a689aed` | Bash, CMake, TOML, YAML (16 `.so`, SHA-256 geprüft) |
| 21 | `21-verify-apt-stages-pipeline.md` | ✅ `73bb2af` | Skripte geprüft, 12 Etappen gegen den Fork abgeglichen; Rezepte nur im Fork |
| 22 | `22-verification-followups.md` | ✅ `adb101f` | Ordnerwahl im Wizard, Expressive-Entscheidung, Gradle-/ktlint-Bericht, Gerätetest-Checkliste |
| 23 | `23-fix-extensions-manager.md` | ✅ `baa1e07` | Katalog-Prüfsummen/Archivtypen/Plattform korrigieren, LSP-Anbindung, Repo aufräumen |
| 24 | `24-onboarding-permissions-icon-termux-ui.md` | ✅ `2aa0cc6` | Onboarding-Surface/Previews, Rechte, Launcher-Icon (`de2caf0`), volle Termux-UI, `codeforge-env`-Fehlerausgabe, Second-Stage-Fix |
| 25 | `25-large-packages-release-assets.md` | ✅ `ae1824f` | Große Pakete (> 95 MiB) per Release-Asset `large-packages` & `large-packages.txt` (+ `.asc`) in `codeforge-env` (`docs/task-25-report.md`) |
| 26 | `26-compose-preview-compiler-runtime.md` | ✅ `9e09f84` | Compose-Preview: Gradle-Task `packCompilerRuntime` (30 JARs ohne Plugin, 21.80 MiB unpacked / 20.22 MiB ZIP), CI-Workflow `build-compiler-runtime.yml`, `CompilerRuntimeProviderImpl` mit SHA-256/Zip-Slip/Versions-Check, Classpath-Trennung & Android-SDK-Einbindung, JVM-Unit-Tests (`docs/compose-preview-runtime-report.md`) |
| 27a | `27a-compose-preview-kotlinc-in-termux.md` | ✅ `3029076` | Compose-Preview: Kotlin-Compiler als Termux-Prozess (`$PREFIX/opt/kotlin/bin/kotlinc`), Spike auf Gerät (4–6 s, `javap` verifiziert), Entfernung embeddable (-4,67 MB APK), `KotlincProcessCompiler` & `KotlincOutputParser`, JVM-Tests (`docs/compose-preview-kotlinc-report.md`) |
| 27b | `27b-compose-preview-offscreen-render.md` | ✅ `43c55b7` | Compose-Preview: Offscreen-Rendering mit GraphicsLayer, Frame-Synchronisation via `withFrameNanos`, 5s-Timeout, Fehlerisolation/Host-Schutz, Signaturvalidierung `(Composer, Int)`, dynamische Größe, androidTest kompiliert (`assembleDebugAndroidTest`) (`docs/compose-preview-render-report.md`) |
| 28 | `28-gradle-build-output-problems.md` | ✅ `3399403` | Gradle-Build-Ausgabe und Problemfenster: Entscheidung 28.0 (Option A Termux-Prozess), Domain-Modelle `BuildProblem` & `BuildSession`, reiner Kotlin-Parser `GradleOutputParser` mit 5 Fixtures, `CircularLineBuffer` (20.000 Zeilen), `ProcessGradleBuildRepository`, `BuildForegroundService`, UI-Modul `:feature:build` mit MVI/Hilt, `OutputTab`, `ProblemsTab` mit Klick-Navigation via `OpenFileRequestBridge`, Integration in `ProjectWorkspaceRoute` (`docs/gradle-build-output-problems-report.md`) |
| 29 | `29-install-and-run-apk.md` | ✅ `a35aee7` | APK installieren und starten: `ApkMetadataParser`, `PackageInstaller`-Session, Statuscode-Mapping, Selbstaktualisierungs-Schutz, „Bauen & Starten“-Ablauf (`docs/install-and-run-apk-report.md`) |
| 30 | `30-logcat-viewer.md` | ✅ `293fdc6` | Logcat-Ansicht: Filter nach Level, Paket & Tag, Erkennung von Abstürzen mit Klick-Navigation zum Stacktrace im Sora Editor, 3 Quellen (`OWN_PROCESS`, `READ_LOGS_PERMISSION`, `ADB_LOOPBACK`), 50k Ringpuffer (`docs/logcat-viewer-report.md`) |
| 31 | `31-lsp-code-actions-rename.md` | ✅ `25af384` | LSP Code-Aktionen, Umbenennen (atomar, Multi-File), Struktur-Ansicht (Outline mit Fallback), Definition & Referenzen, Beantwortung von `workspace/applyEdit` (`docs/lsp-code-actions-rename-report.md`) |
| 32 | `32-unified-formatter.md` | ✅ `ff4ff41` | Einheitlicher „Formatieren“-Befehl mit 3-Stufen-Fallback (LSP → CLI-Prozess `ktlint`/`google-java-format` → Built-in `CodeFormatter`), `.editorconfig`, Format-on-Save, Cursor-/Scroll-Erhalt (`docs/unified-formatter-report.md`) |
| 33 | `33-snippets-live-templates.md` | ✅ `HEAD` | Code-Snippets & Live-Templates: Vorlagen mit Tabstopps (`$1`, `$0`), Navigation via Tab/Shift+Tab/Esc in sora-editor 0.23.4, dynamische Variablen (`$PACKAGE`, `$CLASSNAME`, etc.), reiner Kotlin-Parser mit Zeilen-/Spaltenvalidierung, VS-Code JSON Import/Export & Einstellungs-UI (`docs/snippets-live-templates-report.md`) |

## Nächste Schritte

| Schritt | Aufgabe | Anmerkung |
|---|---|---|
| 1 | Fork `scto/terminal-packages-codeforge` | Etappe `H1-jdk17` sowie Gruppe A (`publish = true`, erzeugt Release `large-packages` + `large-packages.txt`) in `build-all-stages.yml` starten; Rezepte aus `agy-output/packages/` committen |
| 2 | Release `compiler-runtime-2025.09.00-k2.1.21` | In `scto/codeforge-tools` über Workflow `.github/workflows/build-compiler-runtime.yml` ausrollen |
| 3 | Gerätetests | `docs/device-test-checklist.md` + Punkte aus `docs/task-24-report.md` & `docs/task-25-report.md` (`codeforge-env setup --gradle`) + Folge-Task 27b |
| laufend | **17** | vor jedem Commit/PR; neue Texte in `values/` **und** `values-de/` |

Nicht Teil der Tasks: das Bauen von Bootstrap und APT-Paketen (GitHub-Actions im Fork). Weitere Backlog-Punkte: `STATUS.md` (Abschnitt „Noch offene Features"): `:libs:gradle-tooling-bridge`, Layout-Designer-Erweiterungen, Expressive ab stabiler material3 ≥ 1.4, ktlint-Umbenennung `template_engine`.

Wichtig: `targetSdk = 28` (Voraussetzung für Termux-Binaries) schließt den Play Store aus; Verteilung über F-Droid/GitHub/Sideload.

Quellen für den jeweiligen Kontext stehen in den referenzierten `.md`-Dateien im
Projekt selbst (`ASSETS.md`, `TREESITTER.md`, `docs/sub/TERMUX-PORTING.md`,
`libs/terminal-engine/BOOTSTRAP.md`, `docs/BOOTSTRAP-WORKFLOW.md`).

## Sicherheitshinweis

Diese Prompts weisen den Agenten an, Code zu ändern, Dateien herunterzuladen und
Build-Artefakte zu erzeugen. Lies jede Datei einmal selbst durch, bevor du sie
startest — insbesondere 06 und 23 (Netzwerk-Downloads, Archiv-Entpacken, Prozessstart für Language Server).
