# Gesamtstatus aller Tasks (CodeForge Mobile)

Zuletzt aktualisiert: 2026-10-21. Stand Basis-Branch: `87e4e6d`.

---

## 🧭 Legende
* ✅ **erledigt** — Umgesetzt, auf Gerät getestet (sofern möglich), Bericht liegt unter `docs/<name>-report.md`, Commit im Repo.
* 🟡 **in Arbeit** — Wird gerade bearbeitet.
* ⬜ **offen** — Noch nicht begonnen.
* ⛔ **blockiert** — Abhängigkeit fehlt oder technischer Blocker (Details in der Spalte *Bemerkungen*).

---

## 📋 Task-Tabelle

| Nr. | Task-Datei | Status | Bemerkungen / Commit |
|---|---|---|---|
| **01** | `01-projekt-setup.md` | ✅ `d48ee00` | Bereinigung Altlasten, Modulstruktur (`:app`, `:core:*`, `:feature:*`, `:libs:*`), Version Catalog |
| **02** | `02-navigation-shell.md` | ✅ `6c22f03` | NavigationSuite / NavigationRail, adaptive Shell, Theme, Top-Bar |
| **03** | `03-filetree-bonsai.md` | ✅ `3455b85` | Bonsai-Treeview, Dateioperationen (anlegen, umbenennen, löschen) |
| **04** | `04-editor-sora.md` | ✅ `9db4024` | Sora-Editor 0.23.4 Einbindung, Mehrfach-Tabs, Pufferverwaltung |
| **05** | `05-textmate-highlighting.md` | ✅ `9aebc19` | TextMate-Grammatiken & Themes, Asset-Loader, Offline-Betrieb |
| **06** | `06-editor-extended-keyboard.md` | ✅ `7d54b47` | IME-Erweiterungsleiste (Klammern, Einrückung, Navigation) |
| **07** | `07-tree-sitter-spike.md` | ✅ `e035043` | Tree-sitter-Spike, Machbarkeitsprüfung, Fallback auf TextMate |
| **08** | `08-lsp-client-core.md` | ✅ `e7c5b73` | LSP-Client (JSON-RPC über stdin/stdout), Lebenszyklus |
| **09** | `09-lsp-autocompletion-diagnostics.md` | ✅ `76cefa4` | Autovervollständigung, Diagnoseanzeige, Quick-Fix-Brücke |
| **10** | `10-lsp-kotlin-language-server.md` | ✅ `5ee761a` | Kotlin Language Server (KLS) Einbindung via Termux/PRoot |
| **11** | `11-project-indexer.md` | ✅ `a30f36f` | Volltext- & Symbolindex über gesamtes Projekt |
| **12** | `12-editor-code-navigation.md` | ✅ `111ce31` | Gehe zu Definition / Implementierung / Referenzen |
| **13** | `13-editor-diagnostics-quickfix.md` | ✅ `1cb3a6c` | Fehler-/Warnungs-Panel, Problem-Zähler, Quick-Fix-Anwendung |
| **14** | `14-git-libgit2.md` | ✅ `f1604a7` | Git-Integration via libgit2 / CLI: Status, Diff, Commit, Branch |
| **15** | `15-layout-designer-canvas.md` | ✅ `269d0ff` | Visueller Android-Layout-Designer: XML-Vorschau, Canvas, Zoom/Pan |
| **16** | `16-editor-code-formatter.md` | ✅ `42c332d` | Code-Formatierung (ktlint / google-java-format) on-demand & on-save |
| **17** | `17-hardcoded-strings-script.md` | ✅ `76aaefd` | CI-Skript `scripts/find_hardcoded_strings.py`, Zero-Tolerance |
| **18** | `18-editor-find-replace.md` | ✅ `981a8b3` | Suchen & Ersetzen: Regex, Groß-/Kleinschreibung, Weitersuchen |
| **19** | `19-settings-storage.md` | ✅ `6a9a95f` | Einstellungen via DataStore: Theme, Schriftgröße, Tab-Breite |
| **20** | `20-project-templates-wizard.md` | ✅ `1231f24` | Neuer-Projekt-Assistent (Compose, View, Kotlin-Library) |
| **21** | `21-project-import-saf.md` | ✅ `a274db4` | Projekt-Import über Android Storage Access Framework (SAF) |
| **22** | `22-terminal-engine.md` | ✅ `9ca906e` | Integriertes Terminal: PTY, Shell-Sitzungen, Termux-Brücke |
| **23** | `23-module-maker.md` | ✅ `6b0c681` | Modul-Erstellungs-Assistent für neue Gradle-Submodule |
| **24** | `24-git-branches-remotes.md` | ✅ `8ef13df` | Branch anlegen/wechseln/löschen, Remotes, Push & Pull |
| **25** | `25-editor-rename-refactoring.md` | ✅ `5cb24d2` | LSP-basiertes Umbenennen (Rename Refactoring) mit Vorschau |
| **26** | `26-editor-symbol-outline.md` | ✅ `47fc7e8` | Dokument-Gliederung (Symbol Outline): Klassen, Methoden, Felder |
| **27** | `27-git-interactive-diff.md` | ✅ `7945d71` | Interaktiver Diff-Viewer: Side-by-Side / Unified, Hunk-Staging |
| **28** | `28-build-runner.md` | ✅ `f14f17f` | Gradle-Build-Runner: Tasks starten, Log-Streaming, Problem-Liste |
| **29** | `29-apk-installer.md` | ✅ `f14f17f` | Direkte APK-Installation über PackageInstaller-API nach Build |
| **29.5** | `29-5-logcat-viewer.md` | ✅ `348b64e` | Logcat-Viewer im Build-Reiter: Ringpuffer, Filter (PID, Tag, Level), Crash-Markierung, Stacktrace-Navigation in Editor |
| **30** | `30-theme-builder-fluent.md` | ✅ `897a4a8` | Theme-Builder mit Fluent Design (M3-Fluent-Hybrid), RGB-Farbregler, Dunkel/Hell-Vorschau, Export/Import (`docs/theme-builder-report.md`) |
| **31** | `31-editor-quickfix-actions.md` | ✅ `ff4ff41` | Quick-Fix-Aktionen im Editor: Glühbirnen-Symbol, `textDocument/codeAction`, automatische Anwendung via `LspWorkspaceEditApplier` (`docs/editor-quickfix-actions-report.md`) |
| **32** | `32-unified-formatter.md` | ✅ `ff4ff41` | Einheitlicher „Formatieren“-Befehl mit 3-Stufen-Fallback (LSP → CLI-Prozess `ktlint`/`google-java-format` → Built-in `CodeFormatter`), `.editorconfig`, Format-on-Save, Cursor-/Scroll-Erhalt (`docs/unified-formatter-report.md`) |
| **33** | `33-snippets-live-templates.md` | ✅ `36745de` | Code-Snippets & Live-Templates: Vorlagen mit Tabstopps ($1, $0), Navigation via Tab/Shift+Tab/Esc in sora-editor 0.23.4, dynamische Variablen ($PACKAGE, $CLASSNAME, etc.), reiner Kotlin-Parser mit Zeilen-/Spaltenvalidierung, VS-Code JSON Import/Export & Einstellungs-UI (`docs/snippets-live-templates-report.md`) |
| **34** | `34-signing-keystore-manager.md` | ✅ `6749b2a` | Signatur-Verwaltung & Release-Signierung: Keystores im privaten Speicher (`filesDir/keystores/`), Passwörter in `SecretStorage` (AES-256-GCM), argument-leak-freie Ausführung von `keytool`/`apksigner`, PKCS12, RSA 4096 / EC, V1–V4 Schemas, Gradle-Build-Integration (`docs/signing-keystore-manager-report.md`) |
| **35** | `35-apk-analyzer.md` | ✅ `ba0fb74` | APK-Analyse & Vergleich: Größenanalyse nach Bereichen (`ApkZipAnalyzer`), Metadaten & Komponenten (`AxmlManifestParser`, `AaptBadgingParser`, `AaptPermissionsParser`), Signaturprüfung (`ApksignerOutputParser`), Zwei-APK-Vergleich (`ApkComparator`), Zip-Slip-Schutz, UI in `:feature:build`, Kontextmenü in Dateibaum & Build-Ausgabe, Markdown-Export (`docs/apk-analyzer-report.md`) |
| **36** | `36-test-runner.md` | ✅ `2f04ba5` | Test-Läufer mit Ergebnisbaum (Status, Dauer, klickbarer Fehlerort) aus JUnit-XML, Re-Run, Gutter-Integration im Editor (`docs/test-runner-report.md`) |
| **37a** | `37a-debugger-spike-and-session-core.md` | ⬜ offen | Debugger: Machbarkeitsspike (JDI/JDWP in Termux) und UI-freier Sitzungskern. Messung auf dem Gerät (2026-10-19): `jdk.jdi` und `jdk.jdwp.agent` 17.0.20 vorhanden, JDWP-Agent lauscht auf `127.0.0.1:5005`; JDI-Attach durch einen Client noch nicht getestet |
| **37b** | `37b-debugger-editor-ui.md` | ⬜ offen | Debugger: Breakpoints, Variablen, Schritte im Editor. Abhängigkeit: 37a mit positivem Spike |
| **37c** | `37c-debugger-attach-android-app.md` | ⬜ offen | Debugger: laufende Android-App anhängen (ADB/JDWP). Abhängigkeit: 37a, 37b, Task 29 und 29.5 (erledigt); ART-Kompatibilität ungeprüft |
| **38** | `38-ai-assistant-editor.md` | ⬜ offen | KI-Assistent im Editor (Chat, Fehler erklären, Änderungsvorschläge mit Diff, nur nach Bestätigung); Anbieter-Abstraktion, API-Schlüssel im `SecretStorage`, Datenschutzhinweis (Inhalte verlassen das Gerät) |
| **39** | `39-git-conflict-ui-extend.md` | ⬜ offen | Git-Konfliktoberfläche: erst Bestandsaufnahme (`ConflictEditor`/`ConflictParser` existieren bereits), dann gezielt ausbauen |
| **40** | `40-dependency-graph.md` | ⬜ offen | Abhängigkeitsgraph: Baum, „Warum ist das drin?“, Versionskonflikte und Duplikate. Abhängigkeit: Task 28 (erledigt) |
| **41** | `41-layout-designer-drag-drop-and-features.md` | ⬜ offen | Layout-Designer ausbauen: Drag & Drop, Größen-Griffe, ConstraintLayout/RelativeLayout echt darstellen, Ressourcen, Lint. Keine harte Abhängigkeit (Task 15 erledigt) |
| **42** | `42-hardcoded-strings-module.md` | ⬜ offen | Modul `:libs:hardcoded-strings` + `:feature:hardcodedstrings`: harte Texte in **Nutzerprojekten** finden, nach `res/values/strings.xml` auslagern und im Code ersetzen (Vorschau, Sicherung, Rückgängig). Abgrenzung: Task 17 prüft nur das CodeForge-Repo |
| **43** | `43-lsp-extensions-xml-yaml-toml-bash.md` | ⬜ offen | LSP-Erweiterungen: XML (LemMinX), YAML, TOML (`taplo`), Bash; Groovy-Gradle bewusst ausgenommen. Offen: Abhängigkeiten der npm-Tarballs, `taplo lsp`-Feature, `taplo` im APT-Repo |
| **44** | `44-lsp-multi-session.md` | ✅ `46fe905` | LSP: mehrere Server gleichzeitig (eine Sitzung pro Server und Workspace), Begrenzung und Leerlauf-Timeout, Wiederanmelden offener Dateien (`docs/lsp-multi-session-report.md`) |

---

## 🧭 Geplante Tasks 37a–44: Abhängigkeiten und Reihenfolge

Tasks 37a–44 liegen als Spezifikationsdateien vor. Tasks 35 (`ba0fb74`) und 36 (`2f04ba5`) sind umgesetzt. Die Reihenfolge ist eine Empfehlung, keine Vorgabe.

| Gruppe | Tasks | Abhängigkeiten / Hinweise |
|---|---|---|
| Sofort startbar, unabhängig | 39, 40, 41, 42 | 40 baut auf Task 28 auf (erledigt). 39 beginnt mit einer Bestandsaufnahme. 41 hat keine harte Abhängigkeit. 42 darf Task 17 (`scripts/find_hardcoded_strings.py`) nicht widersprechen |
| LSP | 43, 44 | Unabhängig voneinander umsetzbar; 44 vor oder zusammen mit 43, sonst startet jeder Tab-Wechsel zwischen Sprachen den Server neu (heute: genau eine Sitzung, `LspSessionController`/`LspClientRepositoryImpl`). 44 ändert `LspClientRepository`, das auch Plugins nutzen: quellkompatibel bleiben |
| Debugger | 37a → 37b → 37c | 37a zuerst (Spike belegt Machbarkeit). 37b und 37c erst bei positivem Spike. 37c nutzt Task 29/29.5 |
| KI | 38 | Unabhängig. Inhalte verlassen das Gerät: Hinweis- und Schlüsselregeln im Task sind verbindlich |
