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

## Übersicht (Stand 2026-10-25)

Repo-Stand: `952f324`. Alle im Verzeichnis vorhandenen Prompts (**Tasks 01–50**) sind **vollständig umgesetzt und verifiziert** (Commits im Repo bzw. Fork). ✅ umgesetzt, 🟡 teilweise, ⬜ offen. Berichte zu den Verifikations-Tasks: `docs/*-verification.md`, `docs/apt-stages-verification.md`, `docs/restore-lost-editor-features.md`, `docs/verification-followups-report.md`, `docs/settings-overhaul-report.md`, `docs/openssh-prefix-report.md`, `docs/group-chain-report.md`, `docs/j2k-report.md`. Der Build läuft ohne Fehler; Gerätetests stehen aus (`docs/device-test-checklist.md`).

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
| 33 | `33-snippets-live-templates.md` | ✅ `36745de` | Code-Snippets & Live-Templates: Vorlagen mit Tabstopps (`$1`, `$0`), Navigation via Tab/Shift+Tab/Esc in sora-editor 0.23.4, dynamische Variablen (`$PACKAGE`, `$CLASSNAME`, etc.), reiner Kotlin-Parser mit Zeilen-/Spaltenvalidierung, VS-Code JSON Import/Export & Einstellungs-UI (`docs/snippets-live-templates-report.md`) |
| 34 | `34-signing-keystore-manager.md` | ✅ `6749b2a` | Signatur-Verwaltung & Release-Signierung: Keystores im privaten Speicher (`filesDir/keystores/`), Passwörter in `SecretStorage` (AES-256-GCM), argument-leak-freie Ausführung von `keytool`/`apksigner`, PKCS12, RSA 4096 / EC, V1–V4 Schemas, Gradle-Build-Integration (`docs/signing-keystore-manager-report.md`) |
| 35 | `35-apk-analyzer.md` | ✅ `ba0fb74` | APK-Analyse: Größe nach Bereichen, Berechtigungen, Min-/Ziel-SDK, Signatur, Vergleich zweier APKs. Analyse in reinem Kotlin/JVM; keine harte Abhängigkeit (`docs/apk-analyzer-report.md`) |
| 36 | `36-test-runner.md` | ✅ `2f04ba5` | Test-Läufer mit Ergebnisbaum (Status, Dauer, klickbarer Fehlerort) aus JUnit-XML. Abhängigkeit: Task 28 (`docs/test-runner-report.md`) |
| 37a | `37a-debugger-spike-and-session-core.md` | ✅ `5edd695` | Debugger: Machbarkeitsspike (JDI/JDWP in Termux) erfolgreich durchgeführt; UI-freier Sitzungskern (`:libs:debug-client`), DAP-Framing, State-Machine und Unit-Tests grün (`docs/debugger-spike-and-session-core-report.md`) |
| 37b | `37b-debugger-editor-ui.md` | ✅ `27750f5` | Debugger: Editor-Gutter Breakpoints, Dialoge, Zeilennachführung, neues Modul `:feature:debug` (Toolbar, Call-Stack, Variablenbaum, Auswerten), Tests grün (`docs/debugger-editor-ui-report.md`) |
| 37c | `37c-debugger-attach-android-app.md` | ✅ `03533b9` | Debugger: Android-App anhängen via ADB/JDWP (`am start -D`, `adb forward tcp:port jdwp:pid`), `SourceToClassMapper` für Kotlin/Java (`@file:JvmName`, Facades, verschachtelte Klassen), sauberes Cleanup (`adb forward --remove`), UI-Dialog & Auto-Erkennung (`docs/debugger-attach-android-app-report.md`) |
| 38 | `38-ai-assistant-editor.md` | ✅ `bfd4366` | KI-Assistent im Editor (Chat, Fehler erklären, Änderungsvorschläge mit Diff, nur nach Bestätigung); Anbieter-Abstraktion, API-Schlüssel im `SecretStorage`, Datenschutzhinweis (`docs/38-ai-assistant-editor-report.md`) |
| 39 | `39-git-conflict-ui-extend.md` | ✅ `13beaa3` | Git-Konfliktoberfläche ausgebaut: Multi-File- und Block-Navigation, Rebase-Schrittanzeige, Inline-Freitexteditor, 3-Way Diff3, Word-Level-Diffs, Binärdateien & Deleted-vs-Modified Behandlung, Undo-Historie und Marker-Schutz (`docs/39-git-conflict-ui-extend-report.md`) |
| 40 | `40-dependency-graph.md` | ✅ `202ca14` | Abhängigkeitsgraph: iterativer Stack-Parser (`dependencies` & `dependencyInsight`), Konflikt- & Duplikaterkennung, Prüfsummen-Cache, UI-Modul `:feature:dependencies` mit Suche/Filtern/Detail-Sheet & Export (`docs/40-dependency-graph-report.md`) |
| 41 | `41-layout-designer-drag-drop-and-features.md` | ✅ `3cb5e59` | Layout-Designer ausbauen: Drag & Drop, Zoom/Pan, ConstraintLayout/RelativeLayout echt darstellen, Linter & QuickFixes, Ressourcen-Auflösung, Vorlagen, Varianten, 500-Knoten-Benchmark, 117 Tests (`docs/layout-designer-extended-report.md`) |
| 42 | `42-hardcoded-strings-module.md` | ✅ `ad00997` | Modul `:libs:hardcoded-strings` + `:feature:hardcodedstrings`: harte Texte in **Nutzerprojekten** finden, nach `res/values/strings.xml` auslagern und im Code ersetzen (Vorschau, Sicherung, Rückgängig). Abgrenzung: Task 17 prüft nur das CodeForge-Repo (`docs/hardcoded-strings-module-report.md`) |
| 43 | `43-lsp-extensions-xml-yaml-toml-bash.md` | ✅ `cf24ff3` | LSP-Erweiterungen: XML (LemMinX), YAML, TOML (`taplo`), Bash; Groovy-Gradle bewusst ausgenommen. Offen: Abhängigkeiten der npm-Tarballs, `taplo lsp`-Feature, `taplo` im APT-Repo (`docs/lsp-extensions-xml-yaml-toml-bash-report.md`) |
| 44 | `44-lsp-multi-session.md` | ✅ `46fe905` | LSP: mehrere Server gleichzeitig (eine Sitzung pro Server und Workspace), Begrenzung und Leerlauf-Timeout, Wiederanmelden offener Dateien. Sinnvoll zusammen mit oder vor Task 43 (`docs/lsp-multi-session-report.md`) |
| 45 | `45-bootstrap-consistency.md` | ✅ `dd50537` | Bootstrap/APT-Konsistenzprüfung (Bootstrap-ZIP und Repo-Abhängigkeiten vor dem Veröffentlichen), `dpkg-perl → clang`, `codeforge-env repair`, Download-Fortschritt im Setup (`docs/bootstrap-consistency-report.md`) |
| 46 | `46-viewmodel-init-order-crash.md` | ✅ `dd50537` | Absturz beim Öffnen des Editors (NPE im `EditorViewModel`-`init`, Feld-Reihenfolge); Fix, Regressionstest, Durchsicht anderer ViewModels (`docs/viewmodel-init-order-report.md`) |
| 47 | `47-settings-editor-welcome-project-keys.md` | ✅ `952f324` | Einstellungen/UI in fünf Teilen: Zurück-Navigation, Editor-Einstellungen (Theme-Dropdown, Dateikopf), Willkommensbildschirm, Projekteinstellungen, Schlüssel↔Projekt-Zuweisung (`docs/settings-overhaul-report.md`) |
| 48 | `48-openssh-prefix-gate.md` | ✅ `fd43404` / `aed406a` | openssh-Prefix im Service-Skript (`fd43404`), Sichtbarkeit verworfener Pakete im APT-Lauf, `fail_on_skipped` (`docs/openssh-prefix-report.md`) |
| 49 | `49-group-chain-order-and-prefix-exceptions.md` | ✅ `b70bceb` | Gruppenkette: Reihenfolge (B→E→C→D→F→A), Abbruch bei Fehlern (`stop_on_error: true`), ELF-Ausnahmen (`prefix-exceptions.conf`), Download-Retry (`docs/group-chain-report.md`) |
| 50 | `50-j2k-java-to-kotlin-migration.md` | ✅ `964073a` | Neues Modul `j2k`: Java-Projekte nach Kotlin umwandeln (Dialog nach Import/Clone, optional Gradle Groovy → Kotlin DSL, optional Versionskatalog `libs.versions.toml`), ZIP-Backup unter `Backup/`, Rückgängig, Build-Prüfung und Bericht (`docs/j2k-report.md`) |
| 51 | `51-consistency-check-large-packages.md` | ⬜ offen | Zusammenbau-Prüfung zählt große Pakete (Release-Asset `large-packages`) mit: `--large-packages` mehrfach, `BIG_NEW_FILE`, Test; texinfo-Prüfsumme (arm, salsa-Archiv) gezielt behandeln. Behebt den Abbruch von Gruppe H2-jdk21 (`openjdk-21-x`, Lauf 2026-10-23/24). Voraussetzung: Task 49 |
| 99 | `99-docs-mermaid-dokka.md` | ⬜ offen (Dauer-Task / Prompt fehlt) | Anwendungsdoku (Mermaid) und API-Doku (Dokka), wiederholbar über `docsGenerate`/`docsCheck`, CI-Wochenlauf · **+ 99.8 Betriebshandbuch** für Einsteiger (GPG-Schlüssel erzeugen/sichern, GitHub-Token, Pflege nach Auslöser, „neuer Schlüssel = Bootstrap `full` + alle Stages“). *Hinweis: Datei muss in agy-tasks/ noch angelegt werden.* |

## Nächste Schritte

| Schritt | Aufgabe | Anmerkung |
|---|---|---|
| 1 | Fork `scto/terminal-packages-codeforge` | Alle Gruppen und `openjdk-17`/`-21`/`-25` sind laut Inhaber/Paketliste (2026-10-21) im APT-Repo (`-21`/`-25` als große Pakete). Offen: Vollständigkeit gegen `apt-groups.json` prüfen, Installation und LSP-Start auf dem Gerät testen |
| 2 | Compiler-Runtime und `gradle` | Release `compiler-runtime-2025.09.00-k2.1.21` und Paket `gradle` existieren laut Inhaber. Offen: Prüfsumme/Manifest gegen die App und Gerätetest |
| 3 | Gerätetests | `docs/device-test-checklist.md` + Punkte aus `docs/task-24-report.md` & `docs/task-25-report.md` (`codeforge-env setup --gradle`) + Folge-Task 27b |
| 4 | Offener Task 51 | Tasks 01–50 sind umgesetzt. Nächster auszuführender Auftrag: **Task 51** (`51-consistency-check-large-packages.md`), um das Abbrechen der Zusammenbau-Prüfung bei großen Paketen (Release-Assets) zu beheben. |
| laufend | **17** | vor jedem Commit/PR; neue Texte in `values/` **und** `values-de/`. Task 42 ist die App-Variante für Nutzerprojekte, nicht ein Ersatz |

Nicht Teil der Tasks: das Bauen von Bootstrap und APT-Paketen (GitHub-Actions im Fork). Weitere Backlog-Punkte: `STATUS.md` (Abschnitt „Noch offene Features"): Expressive ab stabiler material3 ≥ 1.4, ktlint-Umbenennung `template_engine`. (`:libs:gradle-tooling-bridge` ist seit Task 28 als `@Deprecated` markiert, die Layout-Designer-Erweiterungen sind Task 41.)

Wichtig: `targetSdk = 28` (Voraussetzung für Termux-Binaries) schließt den Play Store aus; Verteilung über F-Droid/GitHub/Sideload.

Quellen für den jeweiligen Kontext stehen in den referenzierten `.md`-Dateien im
Projekt selbst (`ASSETS.md`, `TREESITTER.md`, `docs/sub/TERMUX-PORTING.md`,
`libs/terminal-engine/BOOTSTRAP.md`, `docs/BOOTSTRAP-WORKFLOW.md`).

## Sicherheitshinweis

Diese Prompts weisen den Agenten an, Code zu ändern, Dateien herunterzuladen und
Build-Artefakte zu erzeugen. Lies jede Datei einmal selbst durch, bevor du sie
startest — insbesondere 06, 23 und 43 (Netzwerk-Downloads, Archiv-Entpacken, Prozessstart für Language Server), 37c (ADB/Prozesszugriff auf laufende Apps) und 38 (Quellcode verlässt das Gerät, API-Schlüssel).
