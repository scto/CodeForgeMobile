# 📊 Übersicht aller agy-Tasks & offene Punkte

**Stand:** 2026-10-21 (Repo `6749b2a`) · **Projekt:** CodeForge Mobile

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
| **27a** | `27a-compose-preview-kotlinc-in-termux.md` | ✅ `3029076` | Compose-Preview: Kotlin-Compiler als Termux-Prozess (`$PREFIX/opt/kotlin/bin/kotlinc`), Spike auf Gerät (4–6 s, `javap` verifiziert), Entfernung embeddable (-4,67 MB APK), `KotlincProcessCompiler` & `KotlincOutputParser`, JVM-Tests (`docs/compose-preview-kotlinc-report.md`) |
| **27b** | `27b-compose-preview-offscreen-render.md` | ✅ `43c55b7` | Compose-Preview: Offscreen-Rendering mit GraphicsLayer, Frame-Synchronisation via `withFrameNanos`, 5s-Timeout, Fehlerisolation/Host-Schutz, Signaturvalidierung `(Composer, Int)`, dynamische Größe, androidTest kompiliert (`assembleDebugAndroidTest`) (`docs/compose-preview-render-report.md`) |
| **28** | `28-gradle-build-output-problems.md` | ✅ `3399403` | Gradle-Build-Ausgabe und Problemfenster: Entscheidung 28.0 (Option A Termux-Prozess), Domain-Modelle `BuildProblem` & `BuildSession`, reiner Kotlin-Parser `GradleOutputParser` mit 5 Fixtures, `CircularLineBuffer` (20.000 Zeilen), `ProcessGradleBuildRepository`, `BuildForegroundService`, UI-Modul `:feature:build` mit MVI/Hilt, `OutputTab`, `ProblemsTab` mit Klick-Navigation via `OpenFileRequestBridge`, Integration in `ProjectWorkspaceRoute` (`docs/gradle-build-output-problems-report.md`) |
| **29** | `29-install-and-run-apk.md` | ✅ `a35aee7` | APK installieren und starten: `ApkMetadataParser`, `PackageInstaller`-Session, Statuscode-Mapping, Selbstaktualisierungs-Schutz, „Bauen & Starten“-Ablauf (`docs/install-and-run-apk-report.md`) |
| **30** | `30-logcat-viewer.md` | ✅ `293fdc6` | Logcat-Ansicht: Filter nach Level, Paket & Tag, Erkennung von Abstürzen mit Klick-Navigation zum Stacktrace im Sora Editor, 3 Quellen (`OWN_PROCESS`, `READ_LOGS_PERMISSION`, `ADB_LOOPBACK`), 50k Ringpuffer (`docs/logcat-viewer-report.md`) |
| **31** | `31-lsp-code-actions-rename.md` | ✅ `25af384` | LSP Code-Aktionen, Umbenennen (atomar, Multi-File), Struktur-Ansicht (Outline mit Fallback), Definition & Referenzen, Beantwortung von `workspace/applyEdit` (`docs/lsp-code-actions-rename-report.md`) |
| **32** | `32-unified-formatter.md` | ✅ `ff4ff41` | Einheitlicher „Formatieren“-Befehl mit 3-Stufen-Fallback (LSP → CLI-Prozess `ktlint`/`google-java-format` → Built-in `CodeFormatter`), `.editorconfig`, Format-on-Save, Cursor-/Scroll-Erhalt (`docs/unified-formatter-report.md`) |
| **33** | `33-snippets-live-templates.md` | ✅ `36745de` | Code-Snippets & Live-Templates: Vorlagen mit Tabstopps ($1, $0), Navigation via Tab/Shift+Tab/Esc in sora-editor 0.23.4, dynamische Variablen ($PACKAGE, $CLASSNAME, etc.), reiner Kotlin-Parser mit Zeilen-/Spaltenvalidierung, VS-Code JSON Import/Export & Einstellungs-UI (`docs/snippets-live-templates-report.md`) |
| **34** | `34-signing-keystore-manager.md` | ✅ `6749b2a` | Signatur-Verwaltung & Release-Signierung: Keystores im privaten Speicher (`filesDir/keystores/`), Passwörter in `SecretStorage` (AES-256-GCM), argument-leak-freie Ausführung von `keytool`/`apksigner`, PKCS12, RSA 4096 / EC, V1–V4 Schemas, Gradle-Build-Integration (`docs/signing-keystore-manager-report.md`) |
| **35** | `35-apk-analyzer.md` | ✅ `ba0fb74` | APK-Analyse: Größe nach Bereichen, Berechtigungen, Min-/Ziel-SDK, Signatur, Vergleich zweier APKs. Analyse in reinem Kotlin/JVM; keine harte Abhängigkeit (`docs/apk-analyzer-report.md`) |
| **36** | `36-test-runner.md` | ✅ `2f04ba5` | Test-Läufer mit Ergebnisbaum (Status, Dauer, klickbarer Fehlerort) aus JUnit-XML. Abhängigkeit: Task 28 (`docs/test-runner-report.md`) |
| **37a** | `37a-debugger-spike-and-session-core.md` | ✅ `5edd695` | Debugger: Machbarkeitsspike (JDI/JDWP in Termux) erfolgreich durchgeführt; UI-freier Sitzungskern (`:libs:debug-client`), DAP-Framing, State-Machine und Unit-Tests grün (`docs/debugger-spike-and-session-core-report.md`) |
| **37b** | `37b-debugger-editor-ui.md` | ⬜ offen | Debugger: Breakpoints, Variablen, Schritte im Editor. Abhängigkeit: 37a mit positivem Spike |
| **37c** | `37c-debugger-attach-android-app.md` | ⬜ offen | Debugger: laufende Android-App anhängen (ADB/JDWP). Abhängigkeit: 37a, 37b, Task 29 und 29.5 (erledigt); ART-Kompatibilität ungeprüft |
| **38** | `38-ai-assistant-editor.md` | ⬜ offen | KI-Assistent im Editor (Chat, Fehler erklären, Änderungsvorschläge mit Diff, nur nach Bestätigung); Anbieter-Abstraktion, API-Schlüssel im `SecretStorage`, Datenschutzhinweis (Inhalte verlassen das Gerät) |
| **39** | `39-git-conflict-ui-extend.md` | ⬜ offen | Git-Konfliktoberfläche: erst Bestandsaufnahme (`ConflictEditor`/`ConflictParser` existieren bereits), dann gezielt ausbauen |
| **40** | `40-dependency-graph.md` | ⬜ offen | Abhängigkeitsgraph: Baum, „Warum ist das drin?“, Versionskonflikte und Duplikate. Abhängigkeit: Task 28 (erledigt) |
| **41** | `41-layout-designer-drag-drop-and-features.md` | ⬜ offen | Layout-Designer ausbauen: Drag & Drop, Größen-Griffe, ConstraintLayout/RelativeLayout echt darstellen, Ressourcen, Lint. Keine harte Abhängigkeit (Task 15 erledigt) |
| **42** | `42-hardcoded-strings-module.md` | ✅ `ad00997` | Modul `:libs:hardcoded-strings` + `:feature:hardcodedstrings`: harte Texte in **Nutzerprojekten** finden, nach `res/values/strings.xml` auslagern und im Code ersetzen (Vorschau, Sicherung, Rückgängig) (`docs/hardcoded-strings-module-report.md`) |
| **43** | `43-lsp-extensions-xml-yaml-toml-bash.md` | ✅ `cf24ff3` | LSP-Erweiterungen: XML (LemMinX), YAML, TOML (`taplo`), Bash; Groovy-Gradle bewusst ausgenommen (`docs/lsp-extensions-report.md`) |
| **44** | `44-lsp-multi-session.md` | ✅ `46fe905` | LSP: mehrere Server gleichzeitig (eine Sitzung pro Server und Workspace), Begrenzung und Leerlauf-Timeout, Wiederanmelden offener Dateien (`docs/lsp-multi-session-report.md`) |
| **45** | `45-bootstrap-consistency.md` | ✅ `505133c` | Bootstrap/APT-Konsistenz: Prüfskript `scripts/check-apt-consistency.py` (Bootstrap-ZIP und Repo-Abhängigkeiten, vor dem Veröffentlichen), Entkopplung `dpkg-perl → clang`, `codeforge-env repair`, Download-Fortschritt/Stall-Timeout im Setup (`docs/bootstrap-consistency-report.md`) |
| **46** | `46-viewmodel-init-order-crash.md` | ✅ `505133c` | **Absturz beim Öffnen des Editors:** `EditorViewModel` liest `projectRoot` im `init`, bevor es initialisiert ist. Fix = Felder vor `init`, Regressionstest `EditorViewModelInitTest`, Durchsicht aller ViewModels/Singletons (`docs/viewmodel-init-order-report.md`) |
| **47** | `47-settings-editor-welcome-project-keys.md` | ⬜ offen | Fünf Teile: 47.1 Zurück-Pfeil/Navigation in den Einstellungen · 47.2 Editor-Einstellungen vollständig und wirksam (Theme-Dropdown, Schrift, Snippets, Dateikopf je Dateityp) · 47.3 Willkommensbildschirm neu gestalten · 47.4 Projekteinstellungen (neues Modul: Minify, Shrink, PNG-Crunch, Signierung) · 47.5 Schlüsselverwaltung: Zuweisung Projekt↔Schlüssel, bequemer Import/Export. 47.5 vor 47.4 |
| **48** | `48-openssh-prefix-gate.md` | ⬜ offen | **openssh fehlt im Repo:** `com.termux` in `var/service/ssh-agent/run` (Kommentar) → Prefix-Check verwirft das Paket (aarch64+arm), Lauf bleibt grün. Fix im Rezept + verworfene Pakete sichtbar machen (Annotation, `failed-packages.txt`, `fail_on_skipped`) |
| **99** | `99-docs-mermaid-dokka.md` | ⬜ offen (**Dauer-Task**) | Dokumentation: Anwendungsdoku mit Mermaid (`docs/app/`, generierte Blöcke), API-Doku mit Dokka (`:libs:plugin-api`, `:core:domain`-Schnittstellen), `./gradlew docsGenerate`/`docsCheck`, Fakten-Drift-Prüfung, CI-Wochenlauf. Wird nach größeren Task-Blöcken **wiederholt** (Runbook 99.7) |

---

## 🧭 Geplante Tasks 35–47: Abhängigkeiten und Reihenfolge

Alle dreizehn Dateien liegen im Repo, **keiner ist umgesetzt**. Die Reihenfolge ist eine Empfehlung, keine Vorgabe.

| Gruppe | Tasks | Abhängigkeiten / Hinweise |
|---|---|---|
| Sofort startbar, unabhängig | 35, 36, 39, 40, 41, 42 | 36 und 40 bauen auf Task 28 auf (erledigt). 39 beginnt mit einer Bestandsaufnahme. 41 hat keine harte Abhängigkeit. 42 darf Task 17 (`scripts/find_hardcoded_strings.py`) nicht widersprechen |
| LSP | 43, 44 | Unabhängig voneinander umsetzbar; 44 vor oder zusammen mit 43, sonst startet jeder Tab-Wechsel zwischen Sprachen den Server neu (heute: genau eine Sitzung, `LspSessionController`/`LspClientRepositoryImpl`). 44 ändert `LspClientRepository`, das auch Plugins nutzen: quellkompatibel bleiben |
| Einstellungen/UI | 47 | Unabhängig von 35–46 außer: 47.5 vor 47.4; 47.2 (Dateikopf) berührt `FileTreeViewModel`, 47.4/47.5 berühren Build und Schlüsselverwaltung (`:feature:build`, `:feature:signing`). 47.1 ist klein und kann sofort einzeln committet werden |
| Absturz-Fix | 46 | **Zuerst**: ohne ihn öffnet kein Projekt im Editor. Unabhängig von allen anderen Tasks; ein fertiges Patch (`editor-viewmodel-init-order.patch`) existiert |
| Bootstrap/APT | 45 | Unabhängig von den übrigen. Betrifft Erstinstallation und Setup (`codeforge-env`); sinnvoll **vor** weiteren Gerätetests. Ändert Workflow `build-bootstrap-repo.yml` und `build_codeforge_repo.sh` |
| Debugger | 37a → 37b → 37c | 37a zuerst (Spike belegt Machbarkeit). 37b und 37c erst bei positivem Spike. 37c nutzt Task 29/29.5 |
| KI | 38 | Unabhängig. Inhalte verlassen das Gerät: Hinweis- und Schlüsselregeln im Task sind verbindlich |

---

## 🔍 Offene Arbeitsliste

### Task 34 – Signatur-Verwaltung und Release-Signierung (✅ erledigt)
1. **Sicherheitsarchitektur & Passwortspeicher (34.1)**: Keystore-Dateien strikt in `filesDir/keystores/`; `SecretStorage` & `SecretStorageImpl` mit AES-256-GCM über Android Keystore (`KeystoreSecretBox`) und `SharedPreferencesStore`. Passwörter niemals in Argumentlisten oder Logs (nur über Child-Process-Env `CF_STORE_PASS`, `CF_KEY_PASS`).
2. **Domain-Modelle & Parser (34.2)**: `KeyAlgorithm` (RSA 2048/4096, EC secp256r1/secp384r1), `DistinguishedName`, `CreateKeystoreRequest`, `KeystoreInfo`, `KeystoreEntryInfo`, `SignApkRequest`, `ApkSignResult`, `ApkVerificationResult`, `PasswordStrengthEvaluator`. Reiner Kotlin-Parser `KeytoolOutputParser` (unterstützt English & Deutsch, Multi-Alias, Ketten) und `ApksignerOutputParser`.
3. **Repository & Prozessausführung (34.3)**: `SigningRepository` & `SigningRepositoryImpl` führen `keytool` und `apksigner` aus; Übergabe von Passwörtern ausschließlich via `-storepass:env` und `--ks-pass env:`. Export mit Bestätigungswarnung, Löschen mit Bereinigung des Tresors.
4. **Gradle-Build-Integration (34.4)**: `GradleBuildRepository` erweitert um `setExtraEnvironment`; `ProcessGradleBuildRepository` injiziert `CODEFORGE_KEYSTORE_*` in Termux `./gradlew`-Builds. UI bietet kopierfertigen `signingConfigs`-Snippet für `build.gradle.kts`.
5. **UI-Modul `:feature:signing` (34.5)**: Eigenständiges Modul mit MVI/Hilt, 3 Tabs (Keystores, APK signieren, Gradle-Konfiguration), Dialoge für Keystore-Erstellung (mit Passwortstärkemeter), Import, Details (kopierbare SHA-1/SHA-256 Fingerabdrücke), Export (mit Verlust-Warnung), Löschen und APK-Signieren (Schema-Wahl v1–v4 mit automatischer Verifikation). Verlinkung in `SettingsHubScreen`.
6. **Tests & Lokalisierung (34.6)**: 12 Tests in `:feature:signing` (`SigningProcessSecurityTest`, `SigningRepositoryImplTest`, `SigningViewModelTest`), Tests in `:core:domain`, `:core:data`, `:libs:gradle-tooling-bridge`; 37 zweisprachige Strings (1085 gesamt, 0 Fehler); Bericht in `docs/signing-keystore-manager-report.md`.

### Task 33 – Code-Snippets und Live-Templates (✅ erledigt)
1. **Domain-Modelle & Parser (33.1)**: `Snippet`, `SnippetValidationResult`, `SnippetParser` (reiner Kotlin-Parser für Tabstopps, Placeholders, Choices, Escapes und Variablen mit Zeilen-/Spaltenangabe ohne Android-Abhängigkeiten), `SnippetVariableResolver`, `VsCodeSnippetJsonSerializer` für VS-Code-Snippet-JSON.
2. **Repository & Datenhaltung (33.2)**: `SnippetRepository` mit `observeSnippets()`, `saveSnippet()`, `deleteSnippet()`, `resetToDefaults()`, `importSnippets()`, `exportSnippets()`. `SnippetRepositoryImpl` lädt Built-ins aus Assets (`builtin_snippets.json` für Kotlin, Compose, Java, XML) mit Fallback und speichert User-Snippets in `snippets_user.json` reentrancy-sicher.
3. **Editor-Integration (33.3)**: `LspCompletionProvider` liefert Snippets für die aktive Sprache; `LspAwareLanguage` integriert `SimpleSnippetCompletionItem` mit `SnippetDescription`; `SoraCodeEditor` konfiguriert `fileVariableResolver` & `workspaceVariableResolver` und fängt `Tab`, `Shift+Tab` und `Esc` ab, wenn `snippetController.isInSnippet()` aktiv ist.
4. **Settings-UI (33.4)**: Einstellungs-UI unter **Einstellungen > Editor > Snippets & Live-Templates** mit Sprachfilter, Suche, Erstellen/Bearbeiten mit Live-Validierung, Löschschutz für Built-ins, Reset auf Defaults, Import und Export von VS-Code-Snippet-JSON.
5. **Tests & Lokalisierung (33.5)**: Unit-Tests in `:core:domain`, `:core:data`, `:feature:settings`, instrumentierter Editor-Test in `:feature:editor`; 29 zweisprachige Strings (1011 Strings gesamt, 0 Fehler); `assembleDebug` verifiziert; Bericht in `docs/snippets-live-templates-report.md`.

### Task 32 – Einheitlicher Formatter (✅ erledigt)
1. **Zentraler Vertrag & Kette (32.1)**: `CodeFormatterRepository` in `:core:domain`, implementiert in `:core:data` (`CodeFormatterRepositoryImpl`). 3-Stufen-Strategie: LSP (`textDocument/formatting` bzw. `textDocument/rangeFormatting`), Termux-CLI-Prozesse (`ktlint`, `google-java-format`), syntaxtoleranter Built-in-Fallback (`CodeFormatter` aus `:libs:code-tools`).
2. **Prozessausführung & Sicherheit (32.1)**: `ProcessExecutor` mit 10s-Timeout und Suchpfad-Erkennung; Syntaxprüfung vor Ersetzung; Quellcode bleibt bei Fehlern garantiert unverändert.
3. **Cursor- & Scrollposition (32.2)**: `EditorController.applyTextChange` mit Präfix/Suffix-Differenz und `mapCursorOffset`; Cursor bleibt exakt erhalten; einzelner Undo-Schritt pro Formatierung.
4. **EditorConfig & Format-on-Save (32.3)**: `EditorConfigResolver` für Einrückung und Zeilenlänge; Proto-Felder `format_on_save_kotlin`, `_java`, `_xml`, `_json` in Settings und automatische Formatierung bei `SaveActiveFile`.
5. **UI & Bereinigung (32.4)**: Redundante Buttons entfernt; ein einziger einheitlicher `AutoFixHigh`-Aktionsbutton für Dokument und Auswahl; Hinweis „Vereinfachte Formatierung (integriert)“.
6. **Tests & Doku (32.5)**: 56 Tests in `:libs:code-tools`, 7 in `EditorConfigResolverTest`, 9 in `CodeFormatterRepositoryImplTest`, 5 in `EditorControllerCursorTest`, 4 in `EditorViewModelFormatTest`; 981 zweisprachige Strings; Bericht in `docs/unified-formatter-report.md`.

### Task 31 – Code-Aktionen, Umbenennen und Struktur über LSP (✅ erledigt)
1. **Protokoll & Modelle (31.1)**: `LspCodeAction`, `LspWorkspaceEdit`, `LspTextEdit`, `LspDocumentSymbol`, `LspLocation`, `LspServerCapabilities`; Methoden `requestCodeActions`, `requestPrepareRename`, `requestRename`, `requestDocumentSymbols`, `requestDefinition`, `requestReferences`, `executeCommand` in `core:domain` & `libs:lsp-client`.
2. **Bidirektionales JSON-RPC & Capabilities (31.1)**: Beantwortung eingehender Server-Anfragen `workspace/applyEdit` mit `{ applied: true }`; Auslesen von `ServerCapabilities` aus `initializeResult` und dynamisches Ausblenden nicht unterstützter UI-Elemente.
3. **WorkspaceEdit anwenden (31.2)**: `LspWorkspaceEditApplier` mit Validierung, Puffer-Edits bei offenen Dateien, Dateisystem-Edits bei geschlossenen Dateien, Vorab-Sicherung und atomarem Rollback bei Fehlern; UTF-16 Code-Unit Offset-Umrechnung (validiert mit Emojis und Umlauten).
4. **UI im Editor (31.3)**: Glühbirnen-Symbol am Cursor/bei Diagnosen mit `QuickFixSheet`, Alt+Eingabe Shortcut; Rename-Dialog mit Namensvorschlag, Vorschau ("n Änderungen in m Dateien") und Undo-Snackbar; Struktur-Ansicht (Outline) im BottomSheet mit SymbolKind-Icons, Suchfilter und `SymbolOutlineExtractor`-Fallback für Kotlin/Java/Python/Rust/JSON; Definition & Referenzen Sheets.
5. **Tests & Doku (31.4)**: Fake JSON-RPC Server über Piped Streams (`LspFakeServerTest`), UTF-16- & Overlap-Tests (`LspJsonMapperTest`), atomare Rollback- & Undo-Tests (`LspWorkspaceEditApplierTest`), ViewModel-Tests (`EditorViewModelLspTest`); 965 Strings zweisprachig; Bericht in `docs/lsp-code-actions-rename-report.md`.

### Task 30 – Logcat-Ansicht (✅ erledigt)
1. **Spike & Quellen (30.0)**: Dokumentation der Vor-/Nachteile von `OWN_PROCESS`, `READ_LOGS_PERMISSION` und `ADB_LOOPBACK`. Permission `android.permission.READ_LOGS` im Manifest ergänzt. Transparenter Quell-Auswahldialog `LogcatSourceDialog` mit Terminal-Befehlen.
2. **Domain-Modelle & Parser (30.1)**: `LogLevel`, `LogEntry`, `StackFrame`, `LogcatFilter`, `LogcatSource`. Reiner Kotlin-Parser `LogcatParser` für `-v threadtime`, Fortsetzungszeilen, Crash-Gruppierung und Stacktrace-Regex. 4 Fixtures und Tests in `LogcatParserTest`.
3. **Ringpuffer & Repository (30.2)**: `LogcatRingBuffer` (50.000 Einträge), `LogcatRepository` & `LogcatRepositoryImpl` mit Prozess-Streaming auf `Dispatchers.IO`, Prozess-Zerstörung bei Flow-Abbruch, Fake-Tests in `LogcatRepositoryImplTest`.
4. **UI-Modul `:feature:build` (30.3)**: Neuer Tab `BuildTab.LOGCAT` neben Ausgabe & Probleme, roter Crash-Badge im Tab-Header; `LogcatFilterBar` (Paket, Level, Tag, Volltext, Nur-Abstürze), `LogcatActionBar` (Pause/Fortsetzen, Leeren, Quelle, Zähler, Kopieren, Teilen).
5. **Stacktrace-Navigation (30.4)**: Klickbare Stacktrace-Zeilen mit Sprung-Icon; automatische Dateiauflösung im Projektverzeichnis (`resolveStackFrameFile`); Sprung via `OpenFileRequestBridge.requestOpenAt(path, line, 1)` direkt in den Sora Editor.
6. **Verifikation & Tests (30.5)**: JVM-Unit-Tests in `:core:domain`, `:core:data`, `:feature:build`; 941 Strings zweisprachig verifiziert; Bericht in `docs/logcat-viewer-report.md`.

### Task 29 – APK installieren und starten (✅ erledigt)
1. **APK-Erkennung & Parser (29.1)**: `ApkMetadataParser` für `output-metadata.json` (ABI-Splits gegen `Build.SUPPORTED_ABIS`, `versionCode`, `versionName`, `outputFile`); Fallback-Scan nach `*.apk` unter `build/outputs/apk/`; Erkennung nicht signierter Release-APKs (`*-unsigned.apk`).
2. **Installation über PackageInstaller (29.2)**: `ApkInstallerRepository` & `ApkInstallerRepositoryImpl` mit `PackageInstaller`-Session (`MODE_FULL_INSTALL`), Streamen und `fsync()`; Weiterleitung des Systemdialogs bei `STATUS_PENDING_USER_ACTION`; `ApkInstallStatusMapper` mit vollständiger Statuscode- und Fehlerabbildung (Signaturkonflikt, Downgrade, Speicherplatz); `PackageManager.getPackageArchiveInfo` und Rechteprüfung (`ACTION_MANAGE_UNKNOWN_APP_SOURCES`).
3. **Starten & Selbstaktualisierungs-Schutz (29.3)**: Starten via `getLaunchIntentForPackage`; Einstellung für automatisches Starten nach Installation; Bestätigungsdialog bei Selbstaktualisierung (`packageName == context.packageName`).
4. **Ablauf-Integration in `:feature:build` (29.4)**: Neuer Button „Bauen & Starten“ (`StartBuildAndRun`); `@Immutable`-Zustände (`Idle`, `Building`, `ReadyToInstall`, `Installing`, `NeedsUserAction`, `NeedsPermission`, `Installed`, `Failed`); kein Installationsversuch bei Build-Fehlern; Dialog für Signaturkonflikte mit Option „Deinstallieren & neu installieren“.
5. **Wireless ADB Spike (29.5)**: Erreichbarkeit von `adb` in Termux (`android-tools 37.0.0p1-1`) und Serverstart verifiziert; Koppelungs- und Verbindungsablauf für Task 30 in `docs/install-and-run-apk-report.md` dokumentiert.
6. **Tests & Doku (29.6)**: JVM-Tests in `ApkMetadataParserTest`, `ApkInstallStatusMapperTest`, `BuildViewModelTest`; String-Check grün; Bericht in `docs/install-and-run-apk-report.md`.

### Task 28 – Gradle-Build-Ausgabe und Problemfenster (✅ erledigt)
1. **Entscheidung 28.0**: Option A (Termux-Prozess `./gradlew <task> --console=plain`) gewählt; bisherige Tooling-API-Bridge als `@Deprecated` markiert.
2. **Domain-Modelle & Schnittstelle (28.1)**: `BuildProblem`, `BuildSession`, `BuildStatus`, `BuildEvent.Problem` in `:core:domain`; `GradleBuildRepository` mit `currentSession` aktualisiert.
3. **Parser & Fixtures (28.2)**: `GradleOutputParser` mit ANSI-Stripping, Kotlin-, Java-javac-, AAPT2- und Gradle-Execution-Erkennung. 5 Fixtures unter `core/domain/src/test/resources/fixtures/` und JVM-Tests in `GradleOutputParserTest`.
4. **Prozessausführung & Ringpuffer (28.3)**: `CircularLineBuffer` (20.000 Zeilen Obergrenze), `ProcessGradleBuildRepository` mit Mehrfachstartschutz und Abbruch via `cancelBuild()`, `BuildForegroundService` für Hintergrundausführung mit Statusbar-Benachrichtigung und Abbruchaktion.
5. **UI-Modul `:feature:build` (28.4)**: `BuildViewModel`, `BuildContract`, `BuildScreen` / `BuildBottomSheet` mit Reiter „Ausgabe“ (Auto-Scroll, Monospace, Suche, Kopieren, Löschen, Syntax-Farben) und „Probleme“ (Dateigruppierung, Schweregrad-Filter, Navigation via `OpenFileRequestBridge.requestOpenAt`), `TaskSelectorDialog`.
6. **Integration in `:app` & Verifikation (28.5)**: Bauen-Button und Fehler-Snackbar in `ProjectWorkspaceRoute.kt`; alle Modul-Tests und `:app:assembleDebug` erfolgreich.

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

### Task 27b – Compose-Preview: Offscreen-Rendering (✅ erledigt)
1. **Reproduktion (27b.0)**: Analyse und instrumentierter Test belegen, dass die synchrone Ausführung von `measure/layout/draw` auf einer unangehängten `ComposeView` unmittelbar nach `setContent` ein leeres (0 % nicht-transparente Pixel) Bitmap lieferte, da der Recomposer noch nicht getaktet hatte.
2. **Aufnahme mit `rememberGraphicsLayer` (27b.1)**: Aufnahme innerhalb einer echten Komposition (`Modifier.drawWithContent { graphicsLayer.record { drawContent() } }`) und Konvertierung über `layer.toImageBitmap()`.
3. **Frame-Synchronisation & Timeout (27b.1)**: Warten auf den Frame via `withFrameNanos` im `LaunchedEffect` und Absicherung gegen Hängen durch 5-Sekunden-Timeout (`composepreview_zeitlimit_ueberschritten`).
4. **Fehlerisolation (27b.1)**: Eigene `ComposeView` entkoppelt die Vorschau von der Host-UI; `ReflectiveComposableHost` fängt `InvocationTargetException` ab, verhindert Abstürze der Activity und schaltet die UI in `PreviewRenderState.Failed(message)`.
5. **Signaturvalidierung (27b.1)**: Parameterlose Funktionen `(Composer, Int)` werden validiert; Funktionen mit Parametern werden gezielt abgewiesen (`composepreview_funktion_hat_parameter`).
6. **Dynamische Größe (27b.1)**: `ComposePreviewSizeResolver` nutzt Gerätemetriken, Seitenverhältnis und `MeasureSpec.AT_MOST` statt fixer 1080×1920.
7. **Tests & Report (27b.2)**: Instrumentierte Tests in `feature/composepreview/src/androidTest` (`ComposePreviewRenderTest.kt`, `Sample.kt`) kompilieren (`assembleDebugAndroidTest`); Bericht in `docs/compose-preview-render-report.md`.

### Außerhalb der Tasks
* **APT-Repo / Bootstrap (Fork `scto/terminal-packages-codeforge`):**
  * Lauf `H1-jdk17` vom 2026-10-19 (`build-all-stages.yml`, `mode=add`, `publish=true`) war formal erfolgreich, aber `openjdk-17` wurde auf `aarch64` und `arm` **nicht** gebaut. Ursache laut Log: Abhängigkeitszyklus `pulseaudio` ↔ `libsndfile` („Dependency "sndfile" not found“). Der Zyklus lief über `packages/libmpg123/build.sh` (`BUILD_DEPENDS`) **und** `packages/libmpg123/mpg123.subpackage.sh` (`SUBPKG_DEPENDS`); `openjdk-17`, `-21` und `-25` haben dieselben `DEPENDS`. Workaround in `build_codeforge_repo.sh` (Commits `8b03ab9`, `1d7b688`).
  * **Stand 2026-10-21 laut Paketliste der Repo-Seite (Screenshot des Inhabers):** `openjdk-17` 17.0.20 (91,6 MB), `openjdk-21` 21.0.12 (100,9 MB, „groß“ = Release-Asset) und `openjdk-25` 25.0.4 (111,3 MB, „groß“) sind für `aarch64` und `arm` gelistet, dazu `-x` (X11) und `-source`; außerdem u. a. `nodejs` 26.4.0-1, `npm` 11.20.0, `ninja`, `neocmakelsp`, `openssh` 10.5p1. Damit ist 24.7 aus `docs/task-24-report.md` (openjdk-17 fehlt) überholt. **Nicht geprüft:** die Lauf-Logs dazu, ob die Pakete auf dem Gerät installierbar sind (`pkg install openjdk-17`; `openjdk-21`/`-25` liegen als große Pakete im Release-Asset `large-packages` und gehen über den Pfad aus Task 25) und ob `jdtls`/Kotlin-Server damit starten.
  * **Abgleich 2026-10-21** (PDF `CodeForge_APT-Repo.pdf`, 447 Pakete, Stand 10:12 UTC, gegen die 71 Pakete aus `apt-stages.conf`): 69 vorhanden, **`shellcheck` (Stage 14-ide-tools) und `taplo` (Stage 12-lsp-misc) fehlen** (Gruppe D nicht vollständig; `taplo` betrifft Task 43). Ein Neubau der Gruppe D (`gopls,taplo,ruff,neocmakelsp,esbuild,pnpm,shellcheck`, `keep_going`) lief am 2026-10-21 an; Ergebnis nicht geprüft. Bei `openjdk-21`, `openjdk-25` und `libjansi` fand die Auswertung kein `arm` (Parser unsicher, große Pakete liegen im Release `large-packages`); **nicht geprüft**. Für `openssh` (Stage `06-dev-core`, Gruppe `B-leicht-dev`) wurde ein fehlgeschlagener Lauf gemeldet, obwohl das Paket im Repo liegt; Log steht aus. Die Auswertung prüft nur die Top-Level-Pakete, **nicht deren Abhängigkeiten** (siehe Task 45).
  * **Gerät des Inhabers (Pixel 9 Pro XL, 2026-10-21):** `codeforge-env setup` scheiterte bei `pkg install jq`/`openjdk-17`, weil `dpkg-perl 1.22.6-5` laut Repo-Paket `Depends: clang` hat, `clang` aber nicht installiert war; dadurch lehnte apt jede Installation ab („not going to be installed“). Behoben von Hand mit `dpkg --remove --force-depends dpkg-perl dpkg-scanpackages`; danach lief `pkg install jq`, und `openjdk-17` lud (ohne Fortschrittsanzeige im Setup). Dauerhafte Lösung: Task 45. Abhängigkeiten wie `clang`, `libllvm`, `oniguruma`, `libjpeg-turbo`, `littlecms` liegen im Repo.
  * Mehrere Gruppen in einem Lauf: `build-bootstrap-repo.yml` nimmt genau eine Etappe; mehrere Gruppen gehen über `build-all-stages.yml` (Feld `only`, kommagetrennt).
  * Nach einem Wechsel des GPG-Schlüssels gelten die Schritte aus `docs/BOOTSTRAP-WORKFLOW.md` (`mode=full`, neuer `release_tag`, `gradle.properties`, Auffüllen per `add`). Prüfen, ob das für den Schlüssel vom 2026-10-19 abgeschlossen ist.
* Artefakte `gradle` (APT-Repo) und Release `compiler-runtime-2025.09.00-k2.1.21` (`scto/codeforge-tools`) existieren laut Inhaber (2026-10-21). Nicht geprüft: SHA-256 der Runtime gegen `CompilerRuntimeProviderImpl`/`manifest.json`, Installation auf dem Gerät.
* Gerätetests: `docs/device-test-checklist.md` auf einem physischen Android-Gerät mit ADB ausführen; zusätzlich LSP-Start mit den nun verfügbaren JDKs (`jdtls`, Kotlin-Server).
