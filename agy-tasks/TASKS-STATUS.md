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
| **25** | `25-large-packages-release-assets.md` | ✅ (dieser Commit) | Große Pakete (> 95 MiB) über Release-Asset `large-packages` & `large-packages.txt` (+ `.asc`) in `codeforge-env` (`install_repo_pkg` / `pkg_install`), Mock-Tests (`docs/task-25-report.md`) |

---

## 🔍 Offene Arbeitsliste

### Task 25 – Große Pakete (> 100 MB) per Release-Asset installieren (✅ erledigt)
1. **Snippet & `codeforge-env` (25.1 / 25.2)**: `apt_base_url()` und `install_repo_pkg()` in `libs/terminal-engine/snippets/large-pkg-snippet.sh` und direkt nach `download()` in `codeforge-env` integriert; `pkg_install()` iteriert über alle Pakete via `install_repo_pkg` (inkl. Logdatei und Fehlerausgabe); `codeforge-env pkg <name...>` und `setup --gradle` ergänzt.
2. **Code-weite Prüfung (25.3)**: Alle `pkg install`/`apt install`-Stellen geprüft; Hinweis im Extensions-Manager (`ExtensionsManager.kt`, `strings.xml`) auf `codeforge-env pkg <pkg>` umgestellt.
3. **Sicherheit & Signatur (25.4)**: Nur `https://github.com/`-URLs erlaubt, SHA-256 geprüft, detachte GPG-Signatur `large-packages.txt.asc` im Workflow `.github/workflows/build-bootstrap-repo.yml` erzeugt und in `install_repo_pkg()` per `gpgv` gegen `codeforge.gpg` geprüft.
4. **Doku & Bericht (25.5 / 25.6)**: Mock-Tests (`large-pkg-snippet.test.sh`) ausgeführt, Abschnitt „Große Pakete (> 100 MB)“ in `docs/BOOTSTRAP-WORKFLOW.md` und `README.md`/`README_DE.md` ergänzt, Bericht in `docs/task-25-report.md`.

### Außerhalb der Tasks
* Fork `scto/terminal-packages-codeforge`: Etappe `H1-jdk17` (`openjdk-17` fehlt aktuell noch im APT-Repo, siehe `docs/task-24-report.md` 24.7) und Gruppe A (`publish = true`, legt Release `large-packages` + `large-packages.txt` an) über `build-all-stages.yml` bauen; Rezepte aus `agy-output/packages/` committen.
* Gerätetests: `docs/device-test-checklist.md` + `codeforge-env setup --gradle` nach Veröffentlichung des `large-packages`-Releases.
