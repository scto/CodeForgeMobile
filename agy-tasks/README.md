# agy-tasks/ — KI-Agent-Aufträge für agy (antigravity-cli)

Jede Datei in diesem Ordner ist ein **eigenständiger, vollständiger Prompt** für
einen KI-Coding-Agenten (hier: `agy`/antigravity-cli, laufend in Termux auf deinem
Smartphone). Jede Datei enthält genug Kontext, um ohne Rückfragen gestartet zu
werden — Repo-Struktur, betroffene Dateien, exakte Akzeptanzkriterien.

**Aufruf (geprüft gegen `agy --help`, Version agy.va39):** Es gibt keine Option, eine Datei als Prompt
zu übergeben (kein `-f`). Starte `agy` im Projekt-Root (`CodeForgeMobile/`) und gib den Auftrag als Prompt mit:

```bash
cd ~/CodeForgeMobile
agy --mode plan -i "Lies agy-tasks/18-translate-strings-en-de.md und führe den Auftrag aus."
```

- `-i` / `--prompt-interactive`: startet mit dem Prompt und bleibt interaktiv (Rückfragen und Freigaben möglich).
- `--mode plan`: Der Agent plant erst, ohne Dateien zu ändern. Danach (oder direkt) `--mode accept-edits`, damit Änderungen ohne Einzelfreigabe geschrieben werden.
- `-p` / `--print`: einmaliger Lauf ohne Dialog, nur für kleine Aufgaben.
- `-c` / `--continue`: letzte Sitzung fortsetzen.
- `--dangerously-skip-permissions` nur verwenden, wenn du den Task gelesen hast und dem Ablauf vertraust.

Der Agent liest die Task-Datei selbst; so entfällt das Einfügen langer Texte in die Shell.

## Übersicht (Stand 2026-10-14)

Repo-Stand: `origin/main` @ `916ea8b`. ✅ = umgesetzt (Commit im Repo), ⬜ = offen. Die Tasks 01–15 sind abgearbeitet; die Berichte liegen als `docs/*-verification.md` im Repo.

| # | Datei | Status | Typ / Hinweis |
|---|---|---|---|
| 01 | `01-textmate-grammars-themes.md` | ✅ `52e4c9b` | TextMate-Grammatiken, Themes (`dark_plus`, `light_plus`) |
| 02 | `02-treesitter-native-libs.md` | ✅ teilweise `2d66ecc` | 6 von 10 Sprachen nativ; Rest → Task 20 |
| 03 | `03-termux-bootstrap-ui-wiring.md` | ✅ überholt | Onboarding startet Bootstrap |
| 04 | `04-termux-bootstrap-package-build.md` | ✅ `a2edcaf` | Release `bootstrap-2026.10.11` (aarch64, arm) eingebunden |
| 05 | `05-termux-sdk-path-visibility.md` | ✅ überholt | PRoot entfällt, SDK unter `$PREFIX/opt/android-sdk` |
| 06 | `06-optional-full-termux-ui.md` | ⬜ optional | nur bei Wunsch nach voller Termux-UI |
| 07 | `07-bonsai-sora-app-followups.md` | ✅ `0dbce28` | `CMakeLists.txt`-Erkennung |
| 08 | `08-verify-indexing-updater-overlays.md` | ✅ `3051341` | Indexierung, Updater, Overlays |
| 09 | `09-verify-project-wizard-template-engine.md` | ✅ `f1dd5ce` | Wizard, Template-Engine |
| 10 | `10-verify-editor-git-drawer.md` | ✅ `0ea8b78` | Editor-Werkzeuge, Git-Panel, Drawer |
| 11 | `11-verify-termux-sdk-script.md` | ✅ `26bc869` | `codeforge-env`, `targetSdk = 28` |
| 12 | `12-verify-build-logic.md` | ✅ `4826199` | build-logic, AGP 8.13.2 |
| 13 | `13-verify-resources.md` | ✅ `a8771ba` | `:core:resources` |
| 14 | `14-verify-adaptive-expressive.md` | ✅ `0c0715a` | Adaptive/Edge-to-Edge/Expressive |
| 15 | `15-verify-layout-designer.md` | ✅ `42924e2` | Layout-Designer |
| 16 | `16-port-local-experiment-features.md` | 🟡 Punkte 1–3 ✅ (`f265bf0`, `57469c0`, `451c5e4`); **4–6 offen** | Extensions-Manager, Hintergrundbefehle, Fluent 2 |
| 17 | `17-externalize-hardcoded-strings.md` | 🔁 wiederkehrend | harte UI-Texte nach `:core:resources`; nach Task 18 zweisprachig |
| 18 | `18-translate-strings-en-de.md` | ⬜ **als Nächstes** | `values/` → Englisch (Standard), bisheriges Deutsch → `values-de`; Skript `scripts/check_string_locales.py` |
| 19 | `19-restore-lost-editor-features.md` | ⬜ neu | `EditorSymbolBar`, Text-Action-Window u. a. aus dem alten Stand `de2caf0` |
| 20 | `20-treesitter-remaining-native-langs.md` | ⬜ neu | Tree-sitter nativ für Bash, CMake, TOML, YAML |
| 21 | `21-verify-apt-stages-pipeline.md` | ⬜ neu | APT-Etappen-Pipeline (`apt-stages.conf`, Workflow) prüfen, Paketliste abgleichen |
| 22 | `22-verification-followups.md` | ⬜ neu | Folgepunkte aus den Verifikationen: SAF-Ordnerwahl im Wizard, Expressive, Gradle-9-Warnungen, ktlint, Gerätetest-Checkliste |

## Empfohlene Reihenfolge der offenen Tasks

| Schritt | Task | Warum zuerst |
|---|---|---|
| 1 | **18** | Strings zweisprachig, bevor weitere Tasks neue Texte anlegen |
| 2 | **19**, **20**, **21** | unabhängig voneinander (19 ändert `:feature:editor`, 20 `:feature:editor`-Assets/Gradle → nicht parallel mit 19; 21 betrifft nur Pipeline/Doku) |
| 3 | **16** (Punkte 4–6), danach **22** | Features nachziehen, Folgepunkte, je Punkt ein Commit |
| laufend | **17** | vor jedem Commit/PR |
| optional | **06** | volle Termux-UI |

Nicht Teil der Tasks (läuft im Fork `scto/terminal-packages-codeforge` als GitHub-Actions-Workflow): das Bauen von Bootstrap und APT-Paketen. Offen sind dort der erste echte Etappen-Lauf, Pakete ohne Termux-Vorlage (Flutter, TypeScript, jdtls, lemminx, kotlin-lsp → Task 21) und der Pages-Platz (~1 GB).

Weitere offene Punkte stehen in `STATUS.md` (Abschnitt „Noch offene Features"): `:libs:gradle-tooling-bridge` (Build-Ausführung aus der IDE), LSP gegen echten Server, Compose-Preview, Plugin-Runtime.

Quellen für den jeweiligen Kontext stehen in den referenzierten `.md`-Dateien im
Projekt selbst (`ASSETS.md`, `TREESITTER.md`, `docs/sub/TERMUX-PORTING.md`,
`libs/terminal-engine/BOOTSTRAP.md`, `docs/bonsai-sora-app-integration.md`, `docs/BOOTSTRAP-WORKFLOW.md`) —
jeder Task-Prompt verweist darauf, falls der Agent mehr Hintergrund braucht.

## Sicherheitshinweis

Diese Prompts weisen den Agenten an, Code zu ändern, Dateien herunterzuladen und
Build-Artefakte zu erzeugen. Lies jede Datei einmal selbst durch, bevor du sie
startest — insbesondere 06 und 20, die Manifest-/Dependency-Änderungen bzw. native Binaries anstoßen.
