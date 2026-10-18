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
| **27a** | `27a-compose-preview-kotlinc-in-termux.md` | ✅ (dieser Commit) | Compose-Preview: Kotlin-Compiler als Termux-Prozess (`$PREFIX/opt/kotlin/bin/kotlinc`), Spike auf Gerät (4–6 s, `javap` verifiziert), Entfernung embeddable (-4,67 MB APK), `KotlincProcessCompiler` & `KotlincOutputParser`, JVM-Tests (`docs/compose-preview-kotlinc-report.md`) |
| **27b** | Folgetask aus 26/27a | ⏳ offen | Verifikation und Härtung des Offscreen-`ComposeView`-Renderers (`ComposeViewBitmapRenderer`) auf echten Geräten (Themes, Density, State) |

---

## 🔍 Offene Arbeitsliste

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

### Außerhalb der Tasks
* Fork `scto/terminal-packages-codeforge`: Etappe `H1-jdk17` (`openjdk-17` fehlt aktuell noch im APT-Repo, siehe `docs/task-24-report.md` 24.7) und Gruppe A (`publish = true`, legt Release `large-packages` + `large-packages.txt` an) über `build-all-stages.yml` bauen; Rezepte aus `agy-output/packages/` committen.
* Release `compiler-runtime-2025.09.00-k2.1.21` in `scto/codeforge-tools` über den Workflow `.github/workflows/build-compiler-runtime.yml` veröffentlichen.
* Gerätetests: `docs/device-test-checklist.md` + Folge-Task 27b auf einem physischen Android-Gerät.
