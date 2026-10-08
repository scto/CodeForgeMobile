# agy-tasks/ — KI-Agent-Aufträge für agy (antigravity-cli)

Jede Datei in diesem Ordner ist ein **eigenständiger, vollständiger Prompt** für
einen KI-Coding-Agenten (hier: `agy`/antigravity-cli, laufend in Termux auf deinem
Smartphone). Jede Datei enthält genug Kontext, um ohne Rückfragen gestartet zu
werden — Repo-Struktur, betroffene Dateien, exakte Akzeptanzkriterien.

**Hinweis zur Nutzung:** Ich kenne die exakte CLI-Syntax von `agy` nicht
zuverlässig genug, um dir einen garantiert korrekten Aufrufbefehl zu geben — prüfe
`agy --help` bzw. deine eigene Doku. In der Praxis heißt das vermutlich, den Inhalt
einer Datei entweder per `agy -f agy-tasks/01-....md` zu übergeben oder als Prompt
einzufügen, während `agy` im Projekt-Root (`CodeForgeMobile/`) läuft. Starte `agy`
immer mit dem Projekt-Root als Arbeitsverzeichnis, damit relative Pfade stimmen.

## Reihenfolge / Abhängigkeiten

| # | Datei | Abhängig von | Typ |
|---|---|---|---|
| 01 | `01-textmate-grammars-themes.md` | — | unabhängig, kann zuerst laufen |
| 02 | `02-treesitter-native-libs.md` | — | unabhängig; Core-Library + 5 Sprachen per Maven lösbar (Kotlin/Java/JSON/XML/C++), 5 weitere (C/Bash/CMake/TOML/YAML) ggf. NDK + `tree-sitter-cli` nötig |
| 03 | `03-termux-bootstrap-ui-wiring.md` | — | reine Code-Verdrahtung, unabhängig |
| 04 | `04-termux-bootstrap-package-build.md` | **dein eigener `terminal-packages-codeforge`-Release muss zuerst existieren** | Repo-übergreifend |
| 05 | `05-termux-sdk-path-visibility.md` | 03 (sinnvoll danach, nicht zwingend) | Architektur-Entscheidung + Implementierung |
| 06 | `06-optional-full-termux-ui.md` | 03, 04 | optional — nur falls du die volle Termux-Multi-Session-UI statt `:feature:terminal` willst |
| 07 | `07-bonsai-sora-app-followups.md` | — | unabhängig, klein — ein konkreter Fund aus der Bonsai/Sora-Editor/App-Review (CMakeLists.txt-Erkennung) |
| 08 | `08-verify-indexing-updater-overlays.md` | — | Build-/Gerätetest der neuen Indexierungs-/Updater-/Overlay-Module |
| 09 | `09-verify-project-wizard-template-engine.md` | — | Build-/Gerätetest des neuen Wizards + Gradle-9/Kotlin-2.1-Check der erzeugten Projekte |
| 10 | `10-verify-editor-git-drawer.md` | — | Build-/Gerätetest: Editor-Werkzeuge, Git-Panel, Drawer-Leiste, Submodule Maker |
| 11 | `11-verify-termux-sdk-script.md` | — | Build-/Gerätetest: Termux-Umgebung, `codeforge-env`, SDK-Manager, Onboarding (PRoot entfernt; 03 und 05 sind erledigt/überholt) |
| 12 | `12-verify-build-logic.md` | — | Build-Test: build-logic, Convention-Plugins, Wrapper/AGP 8.13.2 |
| 13 | `13-verify-resources.md` | — | Build-/Gerätetest: `:core:resources` (zentrale Strings, TestRes) |
| 14 | `14-verify-adaptive-expressive.md` | — | Build-/Gerätetest: Adaptive Layouts, Edge-to-Edge, Expressive, Versionsanhebung |
| 15 | `15-verify-layout-designer.md` | — | Build-/Gerätetest: `:feature:layoutdesigner` (Layout-Designer) |
| 16 | `16-port-local-experiment-features.md` | 12 (Build muss laufen) | Umsetzung: Features des lokalen Experimentzweigs (Minimap, Rainbow Brackets, Extensions-Manager, 15 Presets, Dateibaum-Details) |
| 17 | `17-externalize-hardcoded-strings.md` | 13 (Konventionen) | **wiederkehrend**: harte UI-Texte finden (`scripts/find_hardcoded_strings.py`), nach `:core:resources` auslagern; vor Commits/PRs ausführen |

**Stand 2026-10-08 / empfohlene Reihenfolge** (nicht bauen-verifiziert):

| Phase | Tasks | Ziel |
|---|---|---|
| 0 | — | Fix-Stand einspielen (Termux-Ressourcen, Namespace `com.codeforge.termux`, Bootstrap-Properties) und `./gradlew :app:assembleDebug --no-build-cache` laufen lassen |
| 1 Build grün | **12**, dann **13** | build-logic/Wrapper, `:core:resources`; Compile-Fehler iterativ beheben |
| 2 App startet | **14**, **15**, **11** | Adaptive/Expressive, Layout-Designer, Termux-Umgebung inkl. Bootstrap auf dem Gerät (W^X prüfen) |
| 3 Module prüfen | **08**, **09**, **10** | Indexierung/Updater, Wizard, Editor/Git/Drawer |
| 4 Inhalte vervollständigen | **01**, **02**, **07** | TextMate-Grammars/Themes, Tree-sitter-Libs, CMake-Erkennung |
| 5 Features nachziehen | **16** | Funktionen des lokalen Experimentzweigs |
| laufend | **17** | harte UI-Texte nach `:core:resources` (vor jedem Commit/PR) |
| entfällt | 03, 04, 05 | erledigt/überholt |
| optional | 06 | volle Termux-UI |

01–03, 05, 07, 08, 09 und 10 kannst du sofort und unabhängig voneinander laufen lassen. 04 braucht
zwingend zuerst einen echten GitHub-Release deines `terminal-packages-codeforge`-
Forks (das baut `agy` nicht für dich — das ist dein eigener, separater
Bootstrap-Build). 06 ist komplett optional und nur relevant, falls du mehr als den
aktuell aktiven schlanken Terminal-Pfad willst.

Quellen für den jeweiligen Kontext stehen in den referenzierten `.md`-Dateien im
Projekt selbst (`ASSETS.md`, `TREESITTER.md`, `docs/sub/TERMUX-PORTING.md`,
`libs/terminal-engine/BOOTSTRAP.md`, `docs/bonsai-sora-app-integration.md`) —
jeder Task-Prompt verweist darauf, falls der Agent mehr Hintergrund braucht.

## Sicherheitshinweis

Diese Prompts weisen den Agenten an, Code zu ändern, Dateien herunterzuladen und
Build-Artefakte zu erzeugen. Lies jede Datei einmal selbst durch, bevor du sie
startest — insbesondere 04 und 06, die native Builds bzw. Manifest-/Dependency-
Änderungen anstoßen.
