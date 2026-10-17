# 📊 Übersicht aller agy-Tasks & offene Punkte

**Stand:** 2026-10-16 (Repo `adb101f`) · **Projekt:** CodeForge Mobile

| Task | Datei | Status | Beschreibung / offene Punkte |
|---|---|---|---|
| **01** | `01-textmate-grammars-themes.md` | ✅ `52e4c9b` | TextMate-Grammatiken, Themes |
| **02** | `02-treesitter-native-libs.md` | ✅ `2d66ecc` (+ Task 20) | Tree-sitter Core, 6 Sprachen per Maven |
| **03** | `03-termux-bootstrap-ui-wiring.md` | ✅ überholt | Onboarding startet Bootstrap |
| **04** | `04-termux-bootstrap-package-build.md` | ✅ `a2edcaf` | Release `bootstrap-2026.10.11` (aarch64, arm) |
| **05** | `05-termux-sdk-path-visibility.md` | ✅ überholt | PRoot entfällt, SDK unter `$PREFIX/opt/android-sdk` |
| **06** | `06-optional-full-termux-ui.md` | ⚪ optional | volle Termux-Multi-Session-UI |
| **07** | `07-bonsai-sora-app-followups.md` | ✅ `0dbce28` | `CMakeLists.txt`-Erkennung |
| **08** | `08-verify-indexing-updater-overlays.md` | ✅ `3051341` | Indexierung, Updater, Overlays |
| **09** | `09-verify-project-wizard-template-engine.md` | ✅ `f1dd5ce` | Wizard, Template-Engine |
| **10** | `10-verify-editor-git-drawer.md` | ✅ `0ea8b78` | Editor-Werkzeuge, Git-Panel, Drawer |
| **11** | `11-verify-termux-sdk-script.md` | ✅ `26bc869` | `codeforge-env`, `targetSdk = 28` |
| **12** | `12-verify-build-logic.md` | ✅ `4826199` | build-logic, AGP 8.13.2 |
| **13** | `13-verify-resources.md` | ✅ `a8771ba` | `:core:resources` |
| **14** | `14-verify-adaptive-expressive.md` | ✅ `0c0715a` | Adaptive/Edge-to-Edge, stabiles `MaterialTheme` |
| **15** | `15-verify-layout-designer.md` | ✅ `42924e2` | Layout-Designer |
| **16** | `16-port-local-experiment-features.md` | 🟡 Punkte 1–3, 5, 6 ✅; **4 nachbessern (Task 23)** | Editor, Dateibaum, Theme Studio, Hintergrundbefehle (Entscheidung), Fluent 2; Extensions-Manager fehlerhaft |
| **17** | `17-externalize-hardcoded-strings.md` | 🔁 wiederkehrend | harte UI-Texte nach `:core:resources` (zweisprachig); letzter Lauf 0 Funde |
| **18** | `18-translate-strings-en-de.md` | ✅ `6bcf72f` | Englisch Standard, Deutsch in `values-de` (825 Strings) |
| **19** | `19-restore-lost-editor-features.md` | ✅ `8383ade` | `EditorSymbolBar`; Rest ersetzt/geprüft |
| **20** | `20-treesitter-remaining-native-langs.md` | ✅ `a689aed` | Bash, CMake, TOML, YAML (16 `.so`, SHA-256 geprüft) |
| **21** | `21-verify-apt-stages-pipeline.md` | ✅ `73bb2af` | Skripte geprüft, 12 Etappen gegen den Fork abgeglichen; Rezepte nur im Fork |
| **22** | `22-verification-followups.md` | ✅ `adb101f` | Ordnerwahl im Wizard, Expressive-Entscheidung, Gradle-/ktlint-Bericht, Gerätetest-Checkliste |
| **23** | `23-fix-extensions-manager.md` | ✅ (dieser Commit) | Katalog-Prüfsummen/Archivtypen/Plattform korrigieren, LSP-Anbindung, Repo aufräumen (`docs/extensions-manager-fix-report.md`) |

---

## 🔍 Offene Arbeitsliste

### Task 23 – Extensions-Manager (✅ erledigt)
1. **Katalog**: Reale SHA-256 Prüfsummen für alle Pakete ermittelt; Termux-Pakete für clangd & rust-analyzer integriert.
2. **Archivtypen**: `TAR_GZ` und `GZ` implementiert und mit Sicherheitsprüfungen (Zip/Tar-Slip, Symlinks, Executable-Bits) versehen.
3. **Plattform**: Android/Termux-Kompatibilität sichergestellt (Termux-Pakete statt inkompatibler glibc-Binaries).
4. **Anbindung**: `LspServerResolver` in `:core:domain`, `LspServerResolverImpl` in `:feature:settings`, `LspSessionController` in `:feature:editor` angebunden.
5. **Aufräumen**: `plan-apt-build.sh` Duplikat gelöscht, `mirror-integration.patch` enttracked, Stringzahlen synchronisiert (834). Vollständiger Bericht in `docs/extensions-manager-fix-report.md`.

### Außerhalb der Tasks
* Fork `scto/terminal-packages-codeforge`: Rezepte aus `agy-output/packages/` committen; erster echter Etappen-Lauf (Laufzeit-Risiko `openjdk-*`, `libllvm`, `dart`; Pages ~1 GB, Ist 181,6 MB).
* Gerätetests: `docs/device-test-checklist.md`.
