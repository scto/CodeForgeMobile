# Tasks & Arbeitsfortschritt

> **Für Assistenten:** Vor jedem Start die jeweilige Task-Datei lesen (z. B. `agy-tasks/01-projekt-setup.md`). Nichts erfinden, keine Annahmen ohne Prüfung des Repos.

---

## 🎯 Übergeordnete Regeln (gelten für jeden Task)
1. **Clean Architecture** wie im Rest des Repos: Schnittstellen und Modelle in `:core:domain`, Implementierung in `:libs:*` bzw. im Feature-Modul, UI in `:feature:*` (MVVM/MVI mit Hilt, `@Immutable`-Daten-UI-States, `StateFlow`; Material 3; `modifier: Modifier = Modifier` als erster optionaler Parameter; keine Logik in Composables). Gradle Kotlin DSL, Versionen im Version Catalog (`gradle/libs.versions.toml`), Convention-Plugins aus `build-logic`.
2. **Alle UI-Texte über `:core:resources`** (Englisch Standard, Deutsch in `values-de`); der String-Check (Task 17) muss grün bleiben.
3. **Nichts erfinden:** Was du nicht selbst ausgeführt hast, steht im Bericht unter **nicht getestet** (insbesondere Gerätetests; Zielgerät des Inhabers: Pixel 9 Pro XL, Android 17). Kein „funktioniert“ ohne Beleg.
4. **Geheimnisse** (Passwörter, Tokens) nie ins Log, nie in Dateien im Projektordner, nie in Berichte.
5. **Bericht** in `docs/<name>-report.md` mit Abschnitten **getestet / nicht getestet / offene Punkte**; `agy-tasks/TASKS-STATUS.md` und `agy-tasks/README.md` um den Task ergänzen (Commit-Hash eintragen).
6. **Build muss grün sein:** `./gradlew :app:assembleDebug` und die Tests der betroffenen Module (`./gradlew :<modul>:testDebugUnitTest`).

---

## 📊 Status-Übersicht

Repo-Stand: `87e4e6d`. ✅ umgesetzt (Commit-Hash verlinkt / eingetragen), ⬜ noch offen.

| Nr. | Task-Datei | Status | Kurzbeschreibung |
|---|---|---|---|
| 01 | `01-projekt-setup.md` | ✅ `d48ee00` | Projektstruktur bereinigen, Gradle-Wrapper, Module `:app`, `:core:*`, `:feature:*`, `:libs:*` anlegen |
| 02 | `02-navigation-shell.md` | ✅ `6c22f03` | Navigation, adaptive Shell (NavigationSuite/NavigationRail), Theme, Top-Bar |
| 03 | `03-filetree-bonsai.md` | ✅ `3455b85` | Bonsai-Treeview, Dateioperationen (anlegen, umbenennen, löschen), Icons |
| 04 | `04-editor-sora.md` | ✅ `9db4024` | Sora-Editor-Einbindung, Pufferverwaltung, Tab-Leiste, Mehrfachdateien |
| 05 | `05-textmate-highlighting.md` | ✅ `9aebc19` | TextMate-Grammatiken & Themes für Sora-Editor, Asset-Loader, Caching |
| 06 | `06-editor-extended-keyboard.md` | ✅ `7d54b47` | Erweiterte Tastaturleiste über IME: Klammern, Einrückung, Pfeiltasten |
| 07 | `07-tree-sitter-spike.md` | ✅ `e035043` | Tree-sitter-Spike für schnelles Syntax-Highlighting: Machbarkeit & Fallback |
| 08 | `08-lsp-client-core.md` | ✅ `e7c5b73` | LSP-Client-Kern: JSON-RPC über stdin/stdout, Lebenszyklus, Dokument-Sync |
| 09 | `09-lsp-autocompletion-diagnostics.md` | ✅ `76cefa4` | Autovervollständigung, Diagnoseanzeige (Wellenlinien), Quick-Fix-Brücke |
| 10 | `10-lsp-kotlin-language-server.md` | ✅ `5ee761a` | Kotlin Language Server (KLS) Einbindung via Termux/PRoot-Umgebung |
| 11 | `11-project-indexer.md` | ✅ `a30f36f` | Projektweiter Indexer: Symbole, Klassen, Methoden, Volltextsuche |
| 12 | `12-editor-code-navigation.md` | ✅ `111ce31` | Gehe zu Definition / Implementierung / Referenzen, Symbol-Suche |
| 13 | `13-editor-diagnostics-quickfix.md` | ✅ `1cb3a6c` | Fehler-/Warnungs-Panel, Quick-Fix-Aktionen anwenden, Problem-Zähler |
| 14 | `14-git-libgit2.md` | ✅ `f1604a7` | Git-Integration über libgit2 / Command-Line: Status, Diff, Commit, Branch |
| 15 | `15-layout-designer-canvas.md` | ✅ `269d0ff` | Visueller Android-Layout-Designer: XML-Vorschau, Canvas, Zoom/Pan |
| 16 | `16-editor-code-formatter.md` | ✅ `42c332d` | Code-Formatierung (ktlint / google-java-format) on-demand und on-save |
| 17 | `17-hardcoded-strings-script.md` | ✅ `76aaefd` | CI-Skript `scripts/find_hardcoded_strings.py`, Zero-Tolerance-Prüfung |
| 18 | `18-editor-find-replace.md` | ✅ `981a8b3` | Suchen & Ersetzen im Editor: Regex, Groß-/Kleinschreibung, Weitersuchen |
| 19 | `19-settings-storage.md` | ✅ `6a9a95f` | Einstellungen via DataStore: Theme, Schriftgröße, Tab-Breite, Verhalten |
| 20 | `20-project-templates-wizard.md` | ✅ `1231f24` | Neuer-Projekt-Assistent: Empty Compose, View-based, Kotlin-Library |
| 21 | `21-project-import-saf.md` | ✅ `a274db4` | Projekt-Import über Android Storage Access Framework (SAF) |
| 22 | `22-terminal-engine.md` | ✅ `9ca906e` | Integriertes Terminal: PTY, Shell-Sitzungen, ANSI-Farben, Termux-Brücke |
| 23 | `23-module-maker.md` | ✅ `6b0c681` | Modul-Erstellungs-Assistent: Neues Gradle-Submodul anlegen & verdrahten |
| 24 | `24-git-branches-remotes.md` | ✅ `8ef13df` | Git-Erweiterung: Branch anlegen/wechseln/löschen, Remotes, Push & Pull |
| 25 | `25-editor-rename-refactoring.md` | ✅ `5cb24d2` | LSP-basiertes Umbenennen (Rename Refactoring) mit Vorschau |
| 26 | `26-editor-symbol-outline.md` | ✅ `47fc7e8` | Dokument-Gliederung (Symbol Outline): Klassen, Methoden, Felder als Baum |
| 27 | `27-git-interactive-diff.md` | ✅ `7945d71` | Interaktiver Diff-Viewer: Side-by-Side / Unified, Hunk-weises Staging |
| 28 | `28-build-runner.md` | ✅ `f14f17f` | Gradle-Build-Runner: Tasks starten, Konsolenausgabe streamen, Fehlerliste |
| 29 | `29-apk-installer.md` | ✅ `f14f17f` | Direkte APK-Installation nach Build über PackageInstaller-API |
| 29.5 | `29-5-logcat-viewer.md` | ✅ `348b64e` | Logcat-Viewer im Build-Reiter: Ringpuffer, Filter (PID, Tag, Level), Crash-Markierung, Stacktrace-Navigation in Editor |
| 30 | `30-theme-builder-fluent.md` | ✅ `897a4a8` | Theme-Builder mit Fluent Design (M3-Fluent-Hybrid), RGB-Farbregler, Dunkel/Hell-Vorschau, Export/Import (`docs/theme-builder-report.md`) |
| 31 | `31-editor-quickfix-actions.md` | ✅ `ff4ff41` | Quick-Fix-Aktionen im Editor: Glühbirnen-Symbol, `textDocument/codeAction`, automatische Anwendung via `LspWorkspaceEditApplier` (`docs/editor-quickfix-actions-report.md`) |
| 32 | `32-unified-formatter.md` | ✅ `ff4ff41` | Einheitlicher „Formatieren“-Befehl mit 3-Stufen-Fallback (LSP → CLI-Prozess `ktlint`/`google-java-format` → Built-in `CodeFormatter`), `.editorconfig`, Format-on-Save, Cursor-/Scroll-Erhalt (`docs/unified-formatter-report.md`) |
| 33 | `33-snippets-live-templates.md` | ✅ `36745de` | Code-Snippets & Live-Templates: Vorlagen mit Tabstopps (`$1`, `$0`), Navigation via Tab/Shift+Tab/Esc in sora-editor 0.23.4, dynamische Variablen (`$PACKAGE`, `$CLASSNAME`, etc.), reiner Kotlin-Parser mit Zeilen-/Spaltenvalidierung, VS-Code JSON Import/Export & Einstellungs-UI (`docs/snippets-live-templates-report.md`) |
| 34 | `34-signing-keystore-manager.md` | ✅ `6749b2a` | Signatur-Verwaltung & Release-Signierung: Keystores im privaten Speicher (`filesDir/keystores/`), Passwörter in `SecretStorage` (AES-256-GCM), argument-leak-freie Ausführung von `keytool`/`apksigner`, PKCS12, RSA 4096 / EC, V1–V4 Schemas, Gradle-Build-Integration (`docs/signing-keystore-manager-report.md`) |
| 35 | `35-apk-analyzer.md` | ✅ `b3388eb` | APK-Analyse & Vergleich: Größenanalyse nach Bereichen (`ApkZipAnalyzer`), Metadaten & Komponenten (`AxmlManifestParser`, `AaptBadgingParser`, `AaptPermissionsParser`), Signaturprüfung (`ApksignerOutputParser`), Zwei-APK-Vergleich (`ApkComparator`), Zip-Slip-Schutz, UI in `:feature:build`, Kontextmenü in Dateibaum & Build-Ausgabe, Markdown-Export (`docs/apk-analyzer-report.md`) |
| 36 | `36-test-runner.md` | ✅ umgesetzt | Test-Läufer mit Ergebnisbaum (Status, Dauer, klickbarer Fehlerort) aus JUnit-XML, Re-Run, Gutter-Integration im Editor (`docs/test-runner-report.md`) |
| 37a | `37a-debugger-spike-and-session-core.md` | ⬜ offen | Debugger: Machbarkeitsspike (JDI/JDWP in Termux) und UI-freier Sitzungskern. Messung auf dem Gerät (2026-10-19): `jdk.jdi` und `jdk.jdwp.agent` 17.0.20 vorhanden, JDWP-Agent lauscht auf `127.0.0.1:5005`; JDI-Attach durch einen Client noch nicht getestet |
| 37b | `37b-debugger-editor-ui.md` | ⬜ offen | Debugger: Breakpoints, Variablen, Schritte im Editor. Abhängigkeit: 37a mit positivem Spike |
| 37c | `37c-debugger-attach-android-app.md` | ⬜ offen | Debugger: laufende Android-App anhängen (ADB/JDWP). Abhängigkeit: 37a, 37b, Task 29 und 29.5 (erledigt); ART-Kompatibilität ungeprüft |
| 38 | `38-ai-assistant-editor.md` | ⬜ offen | KI-Assistent im Editor (Chat, Fehler erklären, Änderungsvorschläge mit Diff, nur nach Bestätigung); Anbieter-Abstraktion, API-Schlüssel im `SecretStorage`, Datenschutzhinweis (Inhalte verlassen das Gerät) |
| 39 | `39-git-conflict-ui-extend.md` | ⬜ offen | Git-Konfliktoberfläche: erst Bestandsaufnahme (`ConflictEditor`/`ConflictParser` existieren bereits), dann gezielt ausbauen |
| 40 | `40-dependency-graph.md` | ⬜ offen | Abhängigkeitsgraph: Baum, „Warum ist das drin?“, Versionskonflikte und Duplikate. Abhängigkeit: Task 28 (erledigt) |
| 41 | `41-layout-designer-drag-drop-and-features.md` | ⬜ offen | Layout-Designer ausbauen: Drag & Drop, Größen-Griffe, ConstraintLayout/RelativeLayout echt darstellen, Ressourcen, Lint. Keine harte Abhängigkeit (Task 15 erledigt) |
| 42 | `42-hardcoded-strings-module.md` | ⬜ offen | Modul `:libs:hardcoded-strings` + `:feature:hardcodedstrings`: harte Texte in **Nutzerprojekten** finden, nach `res/values/strings.xml` auslagern und im Code ersetzen (Vorschau, Sicherung, Rückgängig). Abgrenzung: Task 17 prüft nur das CodeForge-Repo |
| 43 | `43-lsp-extensions-xml-yaml-toml-bash.md` | ⬜ offen | LSP-Erweiterungen: XML (LemMinX), YAML, TOML (`taplo`), Bash; Groovy-Gradle bewusst ausgenommen. Offen: Abhängigkeiten der npm-Tarballs, `taplo lsp`-Feature, `taplo` im APT-Repo |
| 44 | `44-lsp-multi-session.md` | ⬜ offen | LSP: mehrere Server gleichzeitig (eine Sitzung pro Server und Workspace), Begrenzung und Leerlauf-Timeout, Wiederanmelden offener Dateien. Sinnvoll zusammen mit oder vor Task 43 |

## Nächste Schritte

Tasks 35–44 sind spezifiziert und liegen im Ordner `agy-tasks/`. Tasks 35 und 36 sind umgesetzt. Siehe [TASKS-STATUS.md](TASKS-STATUS.md) für Details zur Reihenfolge und zu den Abhängigkeiten.
