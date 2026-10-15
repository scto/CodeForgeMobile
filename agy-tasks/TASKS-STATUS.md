# 📊 Übersicht aller agy-Tasks & Offene Punkte

**Stand:** 2026-10-15  
**Projekt:** CodeForge Mobile

---

## 📌 Statusübersicht

| Task | Datei | Status | Beschreibung / Offene Punkte |
|---|---|---|---|
| **01** | `01-textmate-grammars-themes.md` | ✅ **Erledigt** | TextMate-Grammatiken, Themes & Language-Configurations (`52e4c9b`) |
| **02** | `02-treesitter-native-libs.md` | ✅ **Erledigt** | Tree-sitter Core + native Parser für 6 Kernsprachen (`2d66ecc`) |
| **03** | `03-termux-bootstrap-ui-wiring.md` | ✅ **Erledigt** | Onboarding-Flow mit Termux-Bootstrap verdrahtet (`OnboardingViewModel`) |
| **04** | `04-termux-bootstrap-package-build.md` | ✅ **Erledigt** | Eigenes Bootstrap-Release `bootstrap-2026.10.11` eingebunden (`a2edcaf`) |
| **05** | `05-termux-sdk-path-visibility.md` | ✅ **Erledigt** | SDK-Sichtbarkeit gelöst; PRoot/Rootfs vollständig entfernt |
| **06** | `06-optional-full-termux-ui.md` | ⚪ **Optional** | Volle Termux-Multi-Session-UI statt schlankem `:feature:terminal`-Pfad |
| **07** | `07-bonsai-sora-app-followups.md` | ✅ **Erledigt** | Nachbesserungen aus Bonsai/Sora/App-Review (`0dbce28`) |
| **08** | `08-verify-indexing-updater-overlays.md` | ✅ **Erledigt** | Indexierung, Dependency-Updater und Overlays verifiziert (`3051341`) |
| **09** | `09-verify-project-wizard-template-engine.md` | ✅ **Erledigt** | Project Wizard & Template Engine verifiziert (`f1dd5ce`) |
| **10** | `10-verify-editor-git-drawer.md` | ✅ **Erledigt** | Editor-Tools, Git-Panel, Drawer, Submodule Maker verifiziert (`0ea8b78`) |
| **11** | `11-verify-termux-sdk-script.md` | ✅ **Erledigt** | Termux-Umgebung, `codeforge-env`, SDK-Manager verifiziert (`26bc869`) |
| **12** | `12-verify-build-logic.md` | ✅ **Erledigt** | Convention-Plugins & build-logic verifiziert (`4826199`) |
| **13** | `13-verify-resources.md` | ✅ **Erledigt** | `:core:resources` & String-Zentralisierung verifiziert (`a8771ba`) |
| **14** | `14-verify-adaptive-expressive.md` | ✅ **Erledigt** | Adaptive Layouts & Material 3 Expressive verifiziert (`0c0715a`) |
| **15** | `15-verify-layout-designer.md` | ✅ **Erledigt** | Layout-Designer verifiziert (`42924e2`) |
| **16** | `16-port-local-experiment-features.md` | ✅ **Erledigt** | Alle 6 Items aus lokalem Experimentzweig portiert (`321c817`, `daa8a9d`) |
| **17** | `17-externalize-hardcoded-strings.md` | 🔄 **Wiederkehrend** | Harte Texte nach `:core:resources` auslagern (aktuell 0 Funde, 818 synchron) |
| **18** | `18-translate-strings-en-de.md` | ✅ **Erledigt** | Englisch als Standard (`values/`), Deutsch in `values-de/` (`6bcf72f`) |
| **19** | `19-restore-lost-editor-features.md` | ✅ **Erledigt** | Verlorene Editor-Funktionen (`EditorSymbolBar`, TextActionWindow, etc.) |
| **20** | `20-treesitter-remaining-native-langs.md` | ✅ **Erledigt** | Native Tree-sitter Parser für Bash, CMake, TOML, YAML (`a689aed`) |
| **21** | `21-verify-apt-stages-pipeline.md` | ⬜ **Offen** | APT-Repo-Etappen-Pipeline & Paketliste abgleichen |
| **22** | `22-verification-followups.md` | ⬜ **Offen** | Folgepunkte aus den Verifikationsberichten (SAF, Warnings, Checklist) |

---

## 🔍 Detaillierte Arbeitsliste für offene Tasks

### Task 19 – Verlorene Editor-Funktionen wiederherstellen
1. **`EditorSymbolBar`**: Composable in `:feature:editor/ui` fertigstellen, Tastatur-Erkennung / Textdatei-Bedingung in `EditorScreen` anbinden, `EditorIntent.InsertText(String)` auslösen, Strings zweisprachig anlegen.
2. **`SoraEditorTextActionWindow`**: Aktionsleiste (Kopieren, Ausschneiden, Einfügen, Alles auswählen) gegen Sora 0.23.x API prüfen/anbinden.
3. **`EditorSearchPanel` vs. `EditorSearchBar`**: Funktionsabgleich durchführen und Lücken analysieren.
4. **Alte Klassen prüfen**: `EditorSession`, `EditorAssetRegistry`, `SoraEditorHost` bewerten und dokumentieren.
5. **Terminal-Extras prüfen**: Nyamux-UI Extra-Keys gegen Termux abgleichen.
6. **Abschluss**: `docs/restore-lost-editor-features.md`, `CHANGELOG.md` und `STATUS.md` aktualisieren.

### Task 21 – APT-Repo-Etappen-Pipeline prüfen
1. **Skriptprüfung**: `bash -n` und `shellcheck` für `build_codeforge_repo.sh` und `.github/scripts/plan-apt-build.sh`.
2. **Paketabgleich**: `apt-stages.conf` gegen `packages/<name>/build.sh` des Forks abgleichen.
3. **Lücken analysieren**: Fehlende Pakete für IDE-Werkzeuge (Flutter, TypeScript, jdtls, lemminx, kotlin-lsp).
4. **Risikoanalyse**: Laufzeit (> 5h auf GitHub-Runnern) und GitHub Pages Speicherlimit (~1 GB) evaluieren.
5. **Abschluss**: Verifikationsbericht `docs/apt-stages-verification.md` erstellen.

### Task 22 – Folgepunkte aus Verifikationsberichten
1. **SAF im Projekt-Wizard**: Evaluation der Speicherort-Auswahl per SAF vs. Termux-Pfadkompatibilität.
2. **Material 3 Expressive**: Verfügbarkeit und Kompatibilität von Compose M3 Expressive prüfen.
3. **Gradle 9 / AGP**: Warnungen mit `--warning-mode all` analysieren und beheben.
4. **ktlint**: `ktlintCheck` ausführen und Befunde dokumentieren.
5. **Gerätetest-Checkliste**: `docs/device-test-checklist.md` erstellen.
