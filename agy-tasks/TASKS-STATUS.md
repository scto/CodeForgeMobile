# 📊 Übersicht aller agy-Tasks & offene Punkte

**Stand:** 2026-10-16 (Repo `adb101f`) · **Projekt:** CodeForge Mobile

| Task | Datei | Status | Beschreibung / offene Punkte |
|---|---|---|---|
| **01** | `01-textmate-grammars-themes.md` | ✅ `52e4c9b` | TextMate-Grammatiken, Themes |
| **02** | `02-treesitter-native-libs.md` | ✅ `2d66ecc` (+ Task 20) | Tree-sitter Core, 6 Sprachen per Maven |
| **03** | `03-termux-bootstrap-ui-wiring.md` | ✅ überholt | Onboarding startet Bootstrap |
| **04** | `04-termux-bootstrap-package-build.md` | ✅ `a2edcaf` | Release `bootstrap-2026.10.11` (aarch64, arm) |
| **05** | `05-termux-sdk-path-visibility.md` | ✅ überholt | PRoot entfällt, SDK unter `$PREFIX/opt/android-sdk` |
| **06** | `06-optional-full-termux-ui.md` | ⚠️ ersetzt durch 24.4 | volle Termux-Multi-Session-UI (in Task 24.4 als Standard umgesetzt) |
| **07** | `07-bonsai-sora-app-followups.md` | ✅ `0dbce28` | `CMakeLists.txt`-Erkennung |
| **08** | `08-verify-indexing-updater-overlays.md` | ✅ `3051341` | Indexierung, Updater, Overlays |
| **09** | `09-verify-project-wizard-template-engine.md` | ✅ `f1dd5ce` | Wizard, Template-Engine |
| **10** | `10-verify-editor-git-drawer.md` | ✅ `0ea8b78` | Editor-Werkzeuge, Git-Panel, Drawer |
| **11** | `11-verify-termux-sdk-script.md` | ✅ `26bc869` | `codeforge-env`, `targetSdk = 28` |
| **12** | `12-verify-build-logic.md` | ✅ `4826199` | build-logic, AGP 8.13.2 |
| **13** | `13-verify-resources.md` | ✅ `a8771ba` | `:core:resources` |
| **14** | `14-verify-adaptive-expressive.md` | ✅ `0c0715a` | Adaptive/Edge-to-Edge, stabiles `MaterialTheme` |
| **15** | `15-verify-layout-designer.md` | ✅ `42924e2` | Layout-Designer |
| **16** | `16-port-local-experiment-features.md` | ✅ Alle Punkte 1–6 (`f498c47`) | Editor, Dateibaum, Theme Studio, Hintergrundbefehle, Fluent 2; Extensions-Manager repariert (Task 23) |
| **17** | `17-externalize-hardcoded-strings.md` | 🔁 wiederkehrend | harte UI-Texte nach `:core:resources` (zweisprachig); letzter Lauf 0 Funde |
| **18** | `18-translate-strings-en-de.md` | ✅ `6bcf72f` | Englisch Standard, Deutsch in `values-de` (825 Strings) |
| **19** | `19-restore-lost-editor-features.md` | ✅ `8383ade` | `EditorSymbolBar`; Rest ersetzt/geprüft |
| **20** | `20-treesitter-remaining-native-langs.md` | ✅ `a689aed` | Bash, CMake, TOML, YAML (16 `.so`, SHA-256 geprüft) |
| **21** | `21-verify-apt-stages-pipeline.md` | ✅ `73bb2af` | Skripte geprüft, 12 Etappen gegen den Fork abgeglichen; Rezepte nur im Fork |
| **22** | `22-verification-followups.md` | ✅ `adb101f` | Ordnerwahl im Wizard, Expressive-Entscheidung, Gradle-/ktlint-Bericht, Gerätetest-Checkliste |
| **23** | `23-fix-extensions-manager.md` | ✅ `baa1e07` | Katalog-Prüfsummen/Archivtypen/Plattform korrigieren, LSP-Anbindung, Repo aufräumen (`docs/extensions-manager-fix-report.md`) |
| **24** | `24-onboarding-permissions-icon-termux-ui.md` | ✅ `2aa0cc6` | Onboarding-Surface/Previews, Rechte (`MANAGE_EXTERNAL_STORAGE`, Pakete installieren/löschen), Original-Launcher-Icon (`de2caf0`), volle Termux-UI (`TermuxActivity`), sichtbare Fehler in `codeforge-env`, Second-Stage-Fix (`docs/task-24-report.md`) |
| **25** | `25-large-packages-release-assets.md` | ✅ `ae1824f` | Große Pakete (> 95 MiB) über Release-Asset `large-packages` & `large-packages.txt` (+ `.asc`) in `codeforge-env` (`install_repo_pkg` / `pkg_install`), Mock-Tests (`docs/task-25-report.md`) |
| **26** | `26-compose-preview-compiler-runtime.md` | ✅ `9e09f84` | Compose-Preview: Gradle-Task `packCompilerRuntime` (30 JARs ohne Plugin, 21.80 MiB unpacked / 20.22 MiB ZIP), CI-Workflow `build-compiler-runtime.yml`, `CompilerRuntimeProviderImpl` mit SHA-256/Zip-Slip/Versions-Check, Classpath-Trennung & Android-SDK-Einbindung, JVM-Unit-Tests (`docs/compose-preview-runtime-report.md`) |
| **27a** | `27a-compose-preview-kotlinc-in-termux.md` | ✅ `3029076` | Compose-Preview: Kotlin-Compiler als Termux-Prozess (`$PREFIX/opt/kotlin/bin/kotlinc`), Spike auf Gerät (4–6 s, `javap` verifiziert), Entfernung embeddable (-4,67 MB APK), `KotlincProcessCompiler` & `KotlincOutputParser`, JVM-Tests (`docs/compose-preview-kotlinc-report.md`) |
| **27b** | `27b-compose-preview-offscreen-render.md` | ✅ `43c55b7` | Compose-Preview: Offscreen-Rendering mit GraphicsLayer, Frame-Synchronisation via `withFrameNanos`, 5s-Timeout, Fehlerisolation/Host-Schutz, Signaturvalidierung `(Composer, Int)`, dynamische Größe, androidTest kompiliert (`assembleDebugAndroidTest`) (`docs/compose-preview-render-report.md`) |
| **28** | `28-gradle-build-output-problems.md` | ✅ `3399403` | Gradle-Build-Ausgabe und Problemfenster: Entscheidung 28.0 (Option A Termux-Prozess), Domain-Modelle `BuildProblem` & `BuildSession`, reiner Kotlin-Parser `GradleOutputParser` mit 5 Fixtures, `CircularLineBuffer` (20.000 Zeilen), `ProcessGradleBuildRepository`, `BuildForegroundService`, UI-Modul `:feature:build` mit MVI/Hilt, `OutputTab`, `ProblemsTab` mit Klick-Navigation via `OpenFileRequestBridge`, Integration in `ProjectWorkspaceRoute` (`docs/gradle-build-output-problems-report.md`) |
| **29** | `29-install-and-run-apk.md` | ✅ `a35aee7` | APK installieren und starten: `ApkMetadataParser`, `PackageInstaller`-Session, Statuscode-Mapping, Selbstaktualisierungs-Schutz, „Bauen & Starten“-Ablauf (`docs/install-and-run-apk-report.md`) |
| **30** | `30-logcat-viewer.md` | ✅ `9c0215f` | Logcat-Ansicht: Filter nach Level, Paket & Tag, Erkennung von Abstürzen mit Klick-Navigation zum Stacktrace im Sora Editor, 3 Quellen (`OWN_PROCESS`, `READ_LOGS_PERMISSION`, `ADB_LOOPBACK`), 50k Ringpuffer (`docs/logcat-viewer-report.md`) |
| **31** | `31-lsp-code-actions-rename.md` | ✅ `df4ded4` | LSP Code-Aktionen, Umbenennen (atomar, Multi-File), Struktur-Ansicht (Outline mit Fallback), Definition & Referenzen, Beantwortung von `workspace/applyEdit` (`docs/lsp-code-actions-rename-report.md`) |

---

## 🔍 Offene Arbeitsliste

### Task 31 – Code-Aktionen, Umbenennen und Struktur über LSP (✅ erledigt)
1. **Protokoll & Modelle (31.1)**: `LspCodeAction`, `LspWorkspaceEdit`, `LspTextEdit`, `LspDocumentSymbol`, `LspLocation`, `LspServerCapabilities`; Methoden `requestCodeActions`, `requestPrepareRename`, `requestRename`, `requestDocumentSymbols`, `requestDefinition`, `requestReferences`, `executeCommand` in `core:domain` & `libs:lsp-client`.
2. **Bidirektionales JSON-RPC & Capabilities (31.1)**: Beantwortung eingehender Server-Anfragen `workspace/applyEdit` mit `{ applied: true }`; Auslesen von `ServerCapabilities` aus `initializeResult` und dynamisches Ausblenden nicht unterstützter UI-Elemente.
3. **WorkspaceEdit anwenden (31.2)**: `LspWorkspaceEditApplier` mit Validierung, Puffer-Edits bei offenen Dateien, Dateisystem-Edits bei geschlossenen Dateien, Vorab-Sicherung und atomarem Rollback bei Fehlern; UTF-16 Code-Unit Offset-Umrechnung (validiert mit Emojis und Umlauten).
4. **UI im Editor (31.3)**: Glühbirnen-Symbol am Cursor/bei Diagnosen mit `QuickFixSheet`, Alt+Eingabe Shortcut; Rename-Dialog mit Namensvorschlag, Vorschau ("n Änderungen in m Dateien") und Undo-Snackbar; Struktur-Ansicht (Outline) im BottomSheet mit SymbolKind-Icons, Suchfilter und `SymbolOutlineExtractor`-Fallback für Kotlin/Java/Python/Rust/JSON; Definition & Referenzen Sheets.
5. **Tests & Doku (31.4)**: Fake JSON-RPC Server über Piped Streams (`LspFakeServerTest`), UTF-16- & Overlap-Tests (`LspJsonMapperTest`), atomare Rollback- & Undo-Tests (`LspWorkspaceEditApplierTest`), ViewModel-Tests (`EditorViewModelLspTest`); 965 Strings zweisprachig; Bericht in `docs/lsp-code-actions-rename-report.md`.

### Task 30 – Logcat-Ansicht (✅ erledigt)
1. **Spike & Quellen (30.0)**: Dokumentation der Vor-/Nachteile von `OWN_PROCESS`, `READ_LOGS_PERMISSION` und `ADB_LOOPBACK`. Permission `android.permission.READ_LOGS` im Manifest ergänzt. Transparenter Quell-Auswahldialog `LogcatSourceDialog` mit Terminal-Befehlen.
2. **Domain-Modelle & Parser (30.1)**: `LogLevel`, `LogEntry`, `StackFrame`, `LogcatFilter`, `LogcatSource`. Reiner Kotlin-Parser `LogcatParser` für `-v threadtime`, Fortsetzungszeilen, Crash-Gruppierung und Stacktrace-Regex. 4 Fixtures und Tests in `LogcatParserTest`.
3. **Ringpuffer & Repository (30.2)**: `LogcatRingBuffer` (50.000 Einträge), `LogcatRepository` & `LogcatRepositoryImpl` mit Prozess-Streaming auf `Dispatchers.IO`, Prozess-Zerstörung bei Flow-Abbruch, Fake-Tests in `LogcatRepositoryImplTest`.
4. **UI-Modul `:feature:build` (30.3)**: Neuer Tab `BuildTab.LOGCAT` neben Ausgabe & Probleme, roter Crash-Badge im Tab-Header; `LogcatFilterBar` (Paket, Level, Tag, Volltext, Nur-Abstürze), `LogcatActionBar` (Pause/Fortsetzen, Leeren, Quelle, Zähler, Kopieren, Teilen).
5. **Stacktrace-Navigation (30.4)**: Klickbare Stacktrace-Zeilen mit Sprung-Icon; automatische Dateiauflösung im Projektverzeichnis (`resolveStackFrameFile`); Sprung via `OpenFileRequestBridge.requestOpenAt(path, line, 1)` direkt in den Sora Editor.
6. **Verifikation & Tests (30.5)**: JVM-Unit-Tests in `:core:domain`, `:core:data`, `:feature:build`; 941 Strings zweisprachig verifiziert; Bericht in `docs/logcat-viewer-report.md`.

### Task 29 – APK installieren und starten (✅ erledigt)
1. **APK-Erkennung & Parser (29.1)**: `ApkMetadataParser` für `output-metadata.json` (ABI-Splits gegen `Build.SUPPORTED_ABIS`, `versionCode`, `versionName`, `outputFile`); Fallback-Scan nach `*.apk` unter `build/outputs/apk/`; Erkennung nicht signierter Release-APKs (`*-unsigned.apk`).
2. **Installation über PackageInstaller (29.2)**: `ApkInstallerRepository` & `ApkInstallerRepositoryImpl` mit `PackageInstaller`-Session (`MODE_FULL_INSTALL`), Streamen und `fsync()`; Weiterleitung des Systemdialogs bei `STATUS_PENDING_USER_ACTION`; `ApkInstallStatusMapper` mit vollständiger Statuscode- und Fehlerabbildung (Signaturkonflikt, Downgrade, Speicherplatz); `PackageManager.getPackageArchiveInfo` und Rechteprüfung (`ACTION_MANAGE_UNKNOWN_APP_SOURCES`).
3. **Starten & Selbstaktualisierungs-Schutz (29.3)**: Starten via `getLaunchIntentForPackage`; Einstellung für automatisches Starten nach Installation; Bestätigungsdialog bei Selbstaktualisierung (`packageName == context.packageName`).
4. **Ablauf-Integration in `:feature:build` (29.4)**: Neuer Button „Bauen & Starten“ (`StartBuildAndRun`); `@Immutable`-Zustände (`Idle`, `Building`, `ReadyToInstall`, `Installing`, `NeedsUserAction`, `NeedsPermission`, `Installed`, `Failed`); kein Installationsversuch bei Build-Fehlern; Dialog für Signaturkonflikte mit Option „Deinstallieren & neu installieren“.
5. **Wireless ADB Spike (29.5)**: Erreichbarkeit von `adb` in Termux (`android-tools 37.0.0p1-1`) und Serverstart verifiziert; Koppelungs- und Verbindungsablauf für Task 30 in `docs/install-and-run-apk-report.md` dokumentiert.
6. **Tests & Doku (29.6)**: JVM-Tests in `ApkMetadataParserTest`, `ApkInstallStatusMapperTest`, `BuildViewModelTest`; String-Check grün; Bericht in `docs/install-and-run-apk-report.md`.

### Task 28 – Gradle-Build-Ausgabe und Problemfenster (✅ erledigt)
1. **Entscheidung 28.0**: Option A (Termux-Prozess `./gradlew <task> --console=plain`) gewählt; bisherige Tooling-API-Bridge als `@Deprecated` markiert.
2. **Domain-Modelle & Schnittstelle (28.1)**: `BuildProblem`, `BuildSession`, `BuildStatus`, `BuildEvent.Problem` in `:core:domain`; `GradleBuildRepository` mit `currentSession` aktualisiert.
3. **Parser & Fixtures (28.2)**: `GradleOutputParser` mit ANSI-Stripping, Kotlin-, Java-javac-, AAPT2- und Gradle-Execution-Erkennung. 5 Fixtures unter `core/domain/src/test/resources/fixtures/` und JVM-Tests in `GradleOutputParserTest`.
4. **Prozessausführung & Ringpuffer (28.3)**: `CircularLineBuffer` (20.000 Zeilen Obergrenze), `ProcessGradleBuildRepository` mit Mehrfachstartschutz und Abbruch via `cancelBuild()`, `BuildForegroundService` für Hintergrundausführung mit Statusbar-Benachrichtigung und Abbruchaktion.
5. **UI-Modul `:feature:build` (28.4)**: `BuildViewModel`, `BuildContract`, `BuildScreen` / `BuildBottomSheet` mit Reiter „Ausgabe“ (Auto-Scroll, Monospace, Suche, Kopieren, Löschen, Syntax-Farben) und „Probleme“ (Dateigruppierung, Schweregrad-Filter, Navigation via `OpenFileRequestBridge.requestOpenAt`), `TaskSelectorDialog`.
6. **Integration in `:app` & Verifikation (28.5)**: Bauen-Button und Fehler-Snackbar in `ProjectWorkspaceRoute.kt`; alle Modul-Tests und `:app:assembleDebug` erfolgreich.

### Task 26 – Compose-Preview: Jar-Bundle für den On-Device-Compiler bereitstellen (✅ erledigt)
1. **Gradle-Task `packCompilerRuntime` (26.1)**: Compose BOM 2025.09.00 + Kotlin 2.1.21 Artefakte aufgelöst, AARs entpackt (`classes.jar`), alle 31 JARs geprüft (`unzip -t`), `manifest.json`, ZIP (`compiler-runtime-2025.09.00-k2.1.21.zip`) und `.sha256` erzeugt. Optional `-PwithExtendedIcons`.
2. **Release-Workflow (26.2)**: `.github/workflows/build-compiler-runtime.yml` erstellt, via `workflow_dispatch` triggerbar, Upload zu Release `compiler-runtime-<bom>-k<kotlin>` in `scto/codeforge-tools`. `actionlint` fehlerfrei.
3. **`CompilerRuntimeProvider` (26.3)**: Interface in `:core:domain`, Implementierung in `:feature:composepreview`. Integritätsprüfung gegen `manifest.json`, Download von GitHub-Release-Asset, SHA-256-Validierung, Zip-Slip-Schutz, atomarer Austausch des Verzeichnisses. UI-Zustand `PreparingRuntime(progress)` und Wiederholen-Button bei `Failed`.
4. **Classpath & Platform (26.4)**: `kotlin-compose-compiler-plugin-embeddable.jar` aus User-Classpath exkludiert (nur in `pluginClasspaths`), höchstes `platforms;android-N` via `SdkRepository` ermittelt und `android.jar` angehängt. Lokalisierte Fehlermeldung bei fehlender Plattform.
5. **Strings (26.5)**: `composepreview_compose_compiler_plugin_jar_fehlt` korrigiert (de/en), `composepreview_android_plattform_fehlt` und `composepreview_laufzeit_wird_vorbereitet` zweisprachig ergänzt.
6. **Tests & Report (26.6 / 26.7)**: JVM-Unit-Tests für Manifest-Parser, SHA-Mismatch, Zip-Slip, Versions-Mismatch, erzeugtes `manifest.json` gegen Build-JARs und Classpath-Exklusion. Doku-Bericht in `docs/compose-preview-runtime-report.md`.

### Task 27a – Compose-Preview: Kotlin-Compiler als Prozess in Termux (✅ erledigt)
1. **Spike auf Gerät (27a.0)**: `scripts/compose-preview-spike.sh` in `plain` (4 s) und `compose` (5–6 s) erfolgreich; synthetische `@Composable`-Signatur mit `Composer`-Parameter via `javap` bestätigt; Kompatibilität von Termux `kotlinc` 2.4.21 mit Compose BOM `2025.09.00` nachgewiesen.
2. **Compiler-Prozess (27a.1)**: `PreviewCompiling` Interface, `KotlincProcessCompiler` ruft `$PREFIX/opt/kotlin/bin/kotlinc` mit `TermuxEnvironment.environment()`, `JAVA_OPTS=-Xmx768m`, 120 s Timeout und `destroyForcibly()`.
3. **Ausgabefilterung & Entkoppelung (27a.1)**: `KotlincOutputParser` parst Fehlerausgaben und filtert `libc.so.6`-Jansi-Warnungen aus. `kotlin-compiler-embeddable` und Plugin entfernt (-4,67 MB APK-Größe). Task 26 Bundle auf 30 JARs ohne Plugin angepasst.
4. **UI & Strings (27a.2)**: `composepreview_kompilierung_laeuft` (*„Kompiliere Compose-Vorschau …“*) mit `CircularProgressIndicator` im `Rendering`-Status.
5. **Tests & Report (27a.3)**: JVM-Unit-Tests in `KotlincProcessCompilerTest`. Ausführlicher Bericht in `docs/compose-preview-kotlinc-report.md`.

### Task 27b – Compose-Preview: Offscreen-Rendering (✅ erledigt)
1. **Reproduktion (27b.0)**: Analyse und instrumentierter Test belegen, dass die synchrone Ausführung von `measure/layout/draw` auf einer unangehängten `ComposeView` unmittelbar nach `setContent` ein leeres (0 % nicht-transparente Pixel) Bitmap lieferte, da der Recomposer noch nicht getaktet hatte.
2. **Aufnahme mit `rememberGraphicsLayer` (27b.1)**: Aufnahme innerhalb einer echten Komposition (`Modifier.drawWithContent { graphicsLayer.record { drawContent() } }`) und Konvertierung über `layer.toImageBitmap()`.
3. **Frame-Synchronisation & Timeout (27b.1)**: Warten auf den Frame via `withFrameNanos` im `LaunchedEffect` und Absicherung gegen Hängen durch 5-Sekunden-Timeout (`composepreview_zeitlimit_ueberschritten`).
4. **Fehlerisolation (27b.1)**: Eigene `ComposeView` entkoppelt die Vorschau von der Host-UI; `ReflectiveComposableHost` fängt `InvocationTargetException` ab, verhindert Abstürze der Activity und schaltet die UI in `PreviewRenderState.Failed(message)`.
5. **Signaturvalidierung (27b.1)**: Parameterlose Funktionen `(Composer, Int)` werden validiert; Funktionen mit Parametern werden gezielt abgewiesen (`composepreview_funktion_hat_parameter`).
6. **Dynamische Größe (27b.1)**: `ComposePreviewSizeResolver` nutzt Gerätemetriken, Seitenverhältnis und `MeasureSpec.AT_MOST` statt fixer 1080×1920.
7. **Tests & Report (27b.2)**: Instrumentierte Tests in `feature/composepreview/src/androidTest` (`ComposePreviewRenderTest.kt`, `Sample.kt`) kompilieren (`assembleDebugAndroidTest`); Bericht in `docs/compose-preview-render-report.md`.

### Außerhalb der Tasks
* Fork `scto/terminal-packages-codeforge`: Etappe `H1-jdk17` (`openjdk-17` fehlt aktuell noch im APT-Repo, siehe `docs/task-24-report.md` 24.7) und Gruppe A (`publish = true`, legt Release `large-packages` + `large-packages.txt` an) über `build-all-stages.yml` bauen; Rezepte aus `agy-output/packages/` committen.
* Release `compiler-runtime-2025.09.00-k2.1.21` in `scto/codeforge-tools` über den Workflow `.github/workflows/build-compiler-runtime.yml` veröffentlichen.
* Gerätetests: `docs/device-test-checklist.md` auf einem physischen Android-Gerät mit ADB ausführen.
