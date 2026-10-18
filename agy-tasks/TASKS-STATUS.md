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
| **24** | `24-onboarding-permissions-icon-termux-ui.md` | ✅ (dieser Commit) | Onboarding-Surface/Previews, Rechte (`MANAGE_EXTERNAL_STORAGE`, Pakete installieren/löschen), Original-Launcher-Icon (`de2caf0`), volle Termux-UI (`TermuxActivity`), sichtbare Fehler in `codeforge-env`, Second-Stage-Fix (`docs/task-24-report.md`) |

---

## 🔍 Offene Arbeitsliste

### Task 24 – Onboarding, Rechte, Icon, Termux-UI & Setup-Fehler (✅ erledigt)
1. **Onboarding-Farben & Previews (24.1)**: `OnboardingRoute` und `GitCloneScreen` in `Surface` eingebettet; Light/Dark-`@Preview` für alle drei Onboarding-Schritte ergänzt.
2. **Berechtigungen (24.2)**: `MANAGE_EXTERNAL_STORAGE` (API 30+), `REQUEST_INSTALL_PACKAGES` und `REQUEST_DELETE_PACKAGES` integriert; `ON_RESUME`-Refresh und klickbare Berechtigungskarten.
3. **Launcher-Icon & Logo (24.3)**: Original-Drawables (`ic_launcher_background.xml`, `ic_launcher_foreground.xml`, `icon.xml`) aus `de2caf0` wiederhergestellt und in Onboarding/Welcome eingebunden.
4. **Volle Termux-UI (24.4 / Task 06)**: `:libs:termux-app` in `:app` eingebunden, `TermuxActivity` als Standard-Terminal mit `EXTRA_INITIAL_COMMAND` für das Setup-Skript, Umschalter Termux-UI/Kompakt in den Einstellungen.
5. **Sichtbare Fehler in `codeforge-env` (24.5)**: `pkg_update_once()` + `pkg_install()` mit Logdatei, Konsolenausgabe der letzten 20 Zeilen, `apt-cache policy` und klarer Meldung bei fehlendem APT-Paket.
6. **Bootstrap Second-Stage (24.6)**: Zeile 290 quotiert, `TERMUX_PACKAGE_MANAGER=apt` und `TERMUX_PACKAGE_ARCH` in `build_codeforge_repo.sh`/`scripts/fix-fork.sh` sowie In-Place-Reparatur und regulärer Aufruf in `TermuxInstaller.java`.

### Außerhalb der Tasks
* Fork `scto/terminal-packages-codeforge`: Etappe `H1-jdk17` (`openjdk-17` fehlt aktuell noch im APT-Repo, siehe `docs/task-24-report.md` 24.7) über `build-all-stages.yml` bauen; Rezepte aus `agy-output/packages/` committen.
* Gerätetests: `docs/device-test-checklist.md`.
