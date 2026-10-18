# Changelog

Alle wesentlichen Änderungen an diesem Projekt werden in dieser Datei dokumentiert.
Das Format basiert auf [Keep a Changelog](https://keepachangelog.com/de/1.1.0/),
dieses Projekt folgt [Semantic Versioning](https://semver.org/lang/de/).

> **Versionierung:** `versionName` und `versionCode` stehen in `app/build.gradle.kts`.
> Schema für den Code: `MAJOR*10000 + MINOR*100 + PATCH` (3.0.0 → `30000`).
> **Alle Einträge seit 3.0.0 sind ungebaut** (kein Gradle-/Android-Lauf); geprüft wurde nur die
> reine JVM-Logik (Git, Code-Tools, Layout-Modell, XML) und die Kotlin-Syntax. Siehe `STATUS.md`.

## [Unreleased]

### 🛠️ Onboarding-Farben, Rechte, Launcher-Icon, volle Termux-UI & sichtbare Setup-Fehler (Task 24)

* **Onboarding-Farben, Surfaces & Previews (`24.1`)**:
  * `OnboardingRoute` und `GitCloneScreen` in `Surface(color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground)` eingebettet, sodass Texte und Icons im Dark-Mode nicht mehr auf Schwarz zurückfallen.
  * Intro-Seiten-Icon auf `64.dp` und `tint = MaterialTheme.colorScheme.primary` gesetzt; Light- und Dark-`@Preview`-Funktionen für `IntroPagerScreen`, `PermissionScreen` und `SetupScreen` ergänzt.
* **Berechtigungen im Onboarding (`24.2`)**:
  * `MANAGE_EXTERNAL_STORAGE` (API 30+ via `ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION`, API 26–29 `READ/WRITE_EXTERNAL_STORAGE` mit `maxSdkVersion="29"`), `REQUEST_INSTALL_PACKAGES` (`ACTION_MANAGE_UNKNOWN_APP_SOURCES`) und `REQUEST_DELETE_PACKAGES` (Info-Zeile) ergänzt.
  * Automatische Neuauswertung aller Berechtigungen bei `Lifecycle.Event.ON_RESUME` nach Rückkehr aus den Systemeinstellungen; ganze Karten klickbar (sofern noch nicht erteilt).
* **Original-Launcher-Icon & Vektor-Logo (`24.3`)**:
  * `ic_launcher_background.xml`, `ic_launcher_foreground.xml` und `icon.xml` aus Commit `de2caf0` wiederhergestellt (`@author Thomas Schmid`) und in `IntroPagerScreen` sowie `WelcomeScreen` eingebunden.
* **Volle Termux-UI als Standard (`24.4` / Task 06)**:
  * `:libs:termux-app` (`TermuxActivity`, `TermuxService`, `HelpActivity`) in `:app` integriert und `TermuxApplication.initTermuxApplication(this)` in `CodeForgeApplication.onCreate()` aufgerufen.
  * `TermuxActivity.EXTRA_INITIAL_COMMAND` für die Übergabe des `codeforge-env setup ...`-Befehls nach dem Start der ersten Session implementiert.
  * Einstellung „Terminal-Ansicht: Termux-UI (Standard) / Kompakt“ (`use_compact_terminal` in `settings.proto`) in `TerminalSettingsScreen` ergänzt.
* **Sichtbare Fehler in `codeforge-env` (`24.5`)**:
  * `pkg_update_once()` und `pkg_install()` protokollieren in `$HOME/.codeforge/logs/setup-<datum>.log`, geben bei Fehlern die letzten 20 Zeilen sowie `apt-cache policy` auf der Konsole aus und melden fehlende APT-Pakete („Unable to locate package“) mit Repository-URL aus `sources.list`.
  * Lesbare Komponenten-Namen (`JDK 17` statt `jdk;17`) und Wiederholungshinweis bei Fehler-Exit.
* **Bootstrap Second-Stage Zeile 290 & regulärer Aufruf (`24.6`)**:
  * `fix_bootstrap_second_stage()` in `build_codeforge_repo.sh` und `scripts/fix-fork.sh` ergänzt (Quoting von `"${TERMUX_PACKAGE_MANAGER}"` in Zeile 290, Vorbelegung `TERMUX_PACKAGE_MANAGER=apt` und Übergabe von `$TERMUX_ARCH` in `build-bootstraps.sh`).
  * `TermuxInstaller.java` repariert bestehende Second-Stage-Skripte beim Entpacken in-place und führt `termux-bootstrap-second-stage.sh` direkt nach dem Entpacken aus, sodass der `profile.d`-Fallback beim ersten Shell-Start entfällt.
  * Bericht in [`docs/task-24-report.md`](file:///data/data/com.termux/files/home/CodeForgeMobile/docs/task-24-report.md).

### 🧩 Extensions-Manager Reparatur & LSP-Client-Anbindung (Task 23)

* **Katalog-Korrektur & Verifikation (`LspExtensionPackage.kt`)**:
  * Alle SHA-256-Prüfsummen anhand realer Original-Downloads neu berechnet und validiert (`kotlin-language-server`, `jdtls`, `pyright`, `vscode-langservers-extracted`).
  * npm-Pakete zusätzlich gegen Registry-`dist.integrity` (SHA-512) gegengeprüft.
  * Korrektur von Eclipse JDT.LS auf den funktionierenden Milestone 1.45.0.
  * Umstellung von `clangd` und `rust-analyzer` auf native Termux-Pakete (`ExtensionSource.TermuxPackage`), da Upstream-Binaries (x86_64/glibc) auf Android/Bionic inkompatibel sind.
  * Ergänzung von Laufzeitvoraussetzungen (`requires`) mit automatischer Erkennung und zweisprachigen Hinweisen in der UI.
* **Sicheres Archiv-Entpacken (`ArchiveUtils.kt`)**:
  * Unterstützung für `TAR_GZ` (`.tar.gz`/`.tgz`) mittels `GZIPInputStream` und integriertem ustar/GNU-Tar-Reader ohne externe Abhängigkeiten.
  * Unterstützung für `GZ`-Einzeldateien.
  * Durchgängiger Schutz vor Zip-Slip, Pfad-Traversal und Symlink-Ausbrüchen.
  * Erhalt der Ausführungsrechte (Tar-Modusbits und Heuristik für `.sh`/`bin`).
  * Atomares Aufräumen bei Download- oder Extraktionsfehlern; automatisches Löschen des Quellarchivs nach erfolgreicher Installation.
  * `HttpFileDownloader` mit Unterstützung für Redirects (bis zu 5 Hops über Protokollgrenzen) und Prüfung der `Content-Length`.
* **Architektur-konforme Anbindung an den Editor**:
  * Saubere Schichtentrennung ohne Feature-zu-Feature-Abhängigkeiten: `LspServerResolver` in `:core:domain`, implementiert in `:feature:settings` (`LspServerResolverImpl`), gebunden in `:app` (`LspModule`).
  * Lebenszyklus-Steuerung im Editor über `LspSessionController`: genau ein Server pro Workspace und Sprache, automatischer Wechsel und Beenden.
  * Visuelle Anzeige des Serverzustands (`LspStatusBadge`) in der Editor-Toolbar.
  * Veraltete PRoot-TODOs in der KDoc von `LspClientRepositoryImpl` auf die native Termux-Umgebung aktualisiert.
* **Testabdeckung & Skripte**:
  * Neue Testsuite `LspExtensionCatalogTest` zur Offline-Integritätsprüfung aller Katalogeinträge.
  * Erweiterte Tests in `ArchiveUtilsTest` und `ExtensionsManagerTest` für alle Archivtypen und Schutzmechanismen.
  * `LspSessionControllerTest` in `:feature:editor`.
  * Verifikationsskript `scripts/verify-extension-catalog.sh` (ShellCheck-geprüft).
* **Repository-Bereinigung**:
  * Duplikat `plan-apt-build.sh` im Root entfernt, Verweise auf `.github/scripts/` aktualisiert.
  * `mirror-integration.patch` aus Git entfernt.
  * Veraltete `values-en`-Doku-Referenzen bereinigt.
  * Stringanzahl in allen Dokumenten auf den aktuellen Stand von 834 Strings je Sprache synchronisiert.

### 🔍 Folgepunkte aus den Verifikationsberichten (Task 22)

* **Projekt-Wizard Speicherort-Auswahl (`ProjectDirectoryPickerDialog`)**:
  * Technische Klärung des SAF- vs. Termux-Dateisystemmodells: Native Linux-Syscalls (`open()`, `execve()`) und Android `noexec`-Mounts auf externem Speicher verbieten das direkte Arbeiten auf externen SAF-URIs.
  * Ergänzung eines Ordner-Auswahl-Buttons (`trailingIcon`) in `ProjectWizardScreen.kt` und Implementierung von `ProjectDirectoryPickerDialog.kt` zum Navigieren und Anlegen von Verzeichnissen im app-privaten Bereich (`<filesDir>/projects`).
  * 7 neue Strings zweisprachig in `core/resources` ergänzt (`pw_choose_directory`, `pw_select_directory_title`, `pw_select_this_directory`, `pw_new_folder`, etc.).
* **Material 3 Expressive Stabilität**:
  * Analyse der Verfügbarkeit: Expressive APIs (`MaterialExpressiveTheme`, `MotionScheme.expressive()`) existieren erst in experimentellen Alpha-Versionen (`1.4.0-alpha+`).
  * Beibehaltung des stabilen `MaterialTheme` aus Compose BOM 2025.09.00 (`material3 1.3.2`) gemäß Projekt-Stabilitätsrichtlinien.
* **Gradle 9 & AGP-Vorbereitung**:
  * Analyse von `./gradlew help --warning-mode all`: 0 Gradle-Deprecation-Warnungen in eigenem Code/build-logic.
* **ktlint Code-Style Check**:
  * `./gradlew ktlintCheck` ausgeführt (225 Tasks) und Befunde (Package-Namen-Unterstriche in `template_engine`, Template-Funktionsnamen) rein berichtend in `docs/verification-followups-report.md` erfasst.
* **Manuelle Gerätetest-Checkliste**:
  * [`docs/device-test-checklist.md`](file:///data/data/com.termux/files/home/CodeForgeMobile/docs/device-test-checklist.md) mit 28 detaillierten Testfällen in 6 Kategorien (Bootstrap/W^X, Adaptive Layouts, IME/Input, Themes, Sora Editor, Filetree & Wizard) erstellt; Play-Store-Einschränkung bei `targetSdk = 28` dokumentiert.


### 📦 APT-Repo-Pipeline: Verifikation der Etappen & Paketliste (Task 21)

* **Skript-Bereinigung & Linter-Härtung**:
  * `build_codeforge_repo.sh`, `plan-apt-build.sh`, `.github/scripts/plan-apt-build.sh` und Workflow `.github/workflows/build-bootstrap-repo.yml` mit `shellcheck` und `actionlint` gehärtet und fehlerfrei gestellt (Exit Code 0).
  * Trennung von `GNUPGHOME` Deklaration und Assignment (SC2155), Ersetzung von `ls` durch `find` für Subpackage- und Deb-Prüfungen (SC2012).
* **Vollständiger Paketabgleich**:
  * Abgleich aller 12 Etappen in `apt-stages.conf` gegen den Fork `terminal-packages-codeforge` (100% Deckung der Elternpakete).
  * Traceability für Subpackages (`clang`, `aapt2`, `aidl`, `protobuf`/`protoc`, `xz`, `curl`) und Provides (`7zip` -> `p7zip`) validiert.
* **Lückenanalyse & Standalone-Entwürfe**:
  * 5 fehlende Termux-Rezepte (`flutter`, `typescript`, `jdtls`, `lemminx`, `kotlin-lsp`) im Stil von `packages/kotlin` (`TERMUX_PKG_PLATFORM_INDEPENDENT=true`) entworfen und im Fork unter `agy-output/packages/` abgelegt.
* **Speicher- & Laufzeitanalyse**:
  * Bestandsaufnahme von `gh-pages`: 420 Pakete mit 181,6 MB (17,7% des 1 GB Pages-Limits).
  * Risikobetrachtung für Langläufer (`libllvm`, `openjdk`, `dart`) und Ausführungsempfehlung dokumentiert in `docs/apt-stages-verification.md`.


### ⌨️ Editor: Wiederherstellung verlorener Editor-Funktionen (Task 19)

* **Symbolleiste (`EditorSymbolBar`)**:
  * Vollwertiges Compose-Composable in `:feature:editor/ui` mit Presets für Kotlin, Java, Python, HTML/XML, Bash und allgemeine Programmiersymbole.
  * Dynamische Einblendung in `EditorScreen` über der Bildschirmtastatur (`WindowInsets.isImeVisible`) bei geöffneter Textdatei.
  * Undo-fähiger Text-Einschub über `EditorController.insertText` und MVI-Event `EditorUiEvent.InsertText`.
  * Zweisprachig mit `editor_symbol_bar` und `editor_symbol_tab` in `values/strings.xml` und `values-de/strings.xml`.
* **Sora-Aktionsleiste & Suchen/Ersetzen**:
  * `SoraEditorTextActionWindow` durch natives Sora 0.23.x `EditorTextActionWindow` und Compose `EditorToolbar` abgedeckt.
  * `EditorSearchPanel` durch vollwertige `EditorSearchBar` (Regex, Groups, Case/Word) abgedeckt.
  * Dokumentation in `docs/restore-lost-editor-features.md`.

### 🌐 Internationalisierung: Englisch als Standard, vollständige deutsche Übersetzung (Task 18)

* **Standardsprache Englisch (`values/strings.xml`)**:
  * Vollständige Übersetzung aller 816 Strings der App in natürliches, professionelles technisches Englisch.
  * Sentence case für UI-Titel/Labels, Imperativ für Aktionen/Buttons.
  * Exakte Beibehaltung aller Format-Platzhalter (`%1$s`, `%2$d`, `%%`), Escapes (`\n`, `\'`, `\"`) und Gruppierungskommentare.
* **Deutsche Übersetzung (`values-de/strings.xml`)**:
  * 100 % Parität mit dem deutschen Original-Stand (Baseline `321c81707d363995f29360d4a88d13143744c637`).
  * Redundantes Teil-Verzeichnis `values-en/` entfernt.
* **Locale-Validierung & Tests**:
  * Validierungsskript `scripts/check_string_locales.py` verifiziert XML-Wohlgeformtheit, Duplikatfreiheit, Parität der Schlüsselmengen und Identität aller Platzhalter/Escapes (Exit 0).
  * `find_hardcoded_strings.py --check` meldet 0 hartcodierte Strings (Exit 0).
  * `TestRes.kt` in `:core:testing` auf englische Standardauflösung umgestellt mit optionalem `TestRes.install("de")`.
  * Alle Unit-Tests und Android-Builds (`testDebugUnitTest`, `:core:resources:assembleDebug`, `assembleDebug`) erfolgreich.

* **Hintergrundbefehle & Service-Architektur (Item 5)**:
  * Analyse und Verifikation der Hintergrundausführung: `TermuxService` (`:libs:termux-app`) deckt als vollwertiger Foreground-Service mit Notification, Wakelock und Wifilock alle langen Terminal- und Compiler-Sessions ab.
  * WorkManager-basierter `RunCommandWorker` als redundantes Altredundanz-Konstrukt identifiziert und entfallen. Dokumentiert in `docs/fluent2-and-background-services-decision.md`.
* **Fluent 2 Design-Tokens (`:core:designsystem`, Item 6)**:
  * Eigenständiges, leichtgewichtiges Token-Set `FluentTokens` nach Microsoft Fluent 2 Spezifikation (Brand-Palette, Neutrals, CornerRadii, Elevation Levels 0–4, Strokes, `FluentColorScheme`) ohne fehleranfällige Alpha-Bibliotheken.
  * Vollständig typisiert und verifiziert durch 4 Unit-Tests in `FluentTokensTest`.

### 🧩 Extensions-Manager: LSP-Server-Pakete, SHA-256-Prüfung & Navigation (Task 16 Item 4)

* **Extensions-Manager (`:feature:settings`)**:
  * `ExtensionsManager` zur Verwaltung von Language-Server-Paketen unter dem Termux-Prefix (`/data/data/com.codeforge.app/files/usr/opt/extensions`) bzw. Anwendungs-`filesDir`.
  * Unterstützung für Download, SHA-256-Prüfsummen-Verifizierung und sicheres Entpacken (Zip mit Schutz vor Zip-Slip, Jar, Binaries mit automatischer `chmod +x` Ausführbarkeit).
  * Katalog mit 6 vorkonfigurierten LSP-Servern: Kotlin Language Server, Eclipse JDT LS (Java), Clangd (C/C++), Pyright (Python), Rust Analyzer, HTML/CSS/JSON Language Server.
* **UI & Navigation (`ExtensionsScreen`)**:
  * `ExtensionsScreen` mit Sprachserver-Karten, Statusanzeige (Installiert, Nicht installiert, Fehler), Live-Download-Fortschrittsbalken und Aktionen (Installieren, Deinstallieren).
  * Route `Routes.EXTENSIONS` in `CodeForgeNavHost` und Verknüpfung im `SettingsHubScreen`.
* **Lokalisierung & Tests**:
  * Vollständig lokalisierte String-Ressourcen (de / en); 0 hartcodierte Strings.
  * 10 Unit-Tests in `Sha256UtilsTest`, `ArchiveUtilsTest` und `ExtensionsManagerTest` für Hash-Prüfung, Zip-Slip-Schutz und Installations-Lebenszyklus.

### 🎨 Theme Studio: 15 Presets, horizontale Live-Vorschau & RGB-Colorpicker (Task 16 Item 3)

* **15 Theme-Presets**: Erweiterung von 5 auf 15 vordefinierte Farbschemata in `ThemePresets` (Monokai, Darcula, Quiet Light, Ayu Dark, Solarized Dark, GitHub Light, VS Code Dark+, Notepad++, Eclipse, Cyberpunk Neon, Forest, Ocean, Sunset, Monochrome, Violet) mit Labels als `@StringRes`.
* **Horizontale Live-Vorschau (`LazyRow`)**: Wischbare Preset-Karten mit Farbpalette (Primär, Sekundär, Tertiär), Kontraststreifen und interaktiver Auswahlmarkierung; dynamische Vorschau im `ThemePreviewCard`.
* **RGB-Colorpicker-Dialog**: Modal mit Live-Farbfläche, RGB-Schiebereglern (0–255), Hex-Eingabefeld und 10 Schnell-Auswahlfarben (Material Swatches) zur feinen Justierung von Primär-, Sekundär- und Tertiärfarben.
* **Lokalisierung & Tests**: Vollständig lokalisierte String-Ressourcen (de / en); 0 hartcodierte Strings; 6 Unit-Tests in `RgbColorUtilsTest` erfolgreich ausgeführt.

### 📂 Dateibaum-Verbesserungen & Eigenschaften-Dialog (Task 16 Item 2)

* **Theme-adaptive durchgehende Einrückungslinien**: Kontinuierliche vertikale Führungslinien (`drawBehind`) für jede Hierarchiestufe, dynamisch adaptiert an das aktive Theme (`MaterialTheme.colorScheme.outlineVariant`).
* **2-zeilige Dateiinfo**: Zeile 1 für den Datei- bzw. Verzeichnisnamen; Zeile 2 für Dateigröße (B / KB / MB / GB) bzw. Eintragsanzahl und das Datum der letzten Änderung (`FileTreeFormatters.formatSubtitle`).
* **Voll-Zeilen-Tap-Target**: Vollflächiger Klick- und Long-Click-Bereich über die gesamte Zeilenbreite (`fillMaxWidth()`, `combinedClickable`) zur komfortablen Bedienung auf Mobilgeräten; separater Chevron-Button zur Ordner-Expansion.
* **Eigenschaften-Dialog (`FileTreePropertiesDialog`)**: Detaillierte Dateiinformationen im Kontextmenü (Name, Pfad, Typ, Byte-/Formatgröße, Änderungszeitstempel, Lese-/Schreib-/Ausführungsrechte).
* **Lösch-Bestätigungsdialog (`FileTreeDeleteConfirmDialog`)**: Sicherheitsabfrage mit Bestätigung (`Löschen` / `Abbrechen`) vor der Dateisystem-Löschung.
* **Lokalisierung & Tests**: Vollständig lokalisierte String-Ressourcen (de / en); 0 hartcodierte Strings; 7 Unit-Tests in `FileTreeFormattersTest` erfolgreich ausgeführt.

### 📝 Editor Ultimate Setup, Bildvorschau & Desktop-Navigation (Task 16 Item 1)

* **Erweiterte Editor-Funktionen**:
  * **Minimap**: Optionale Miniatur-Codeübersicht am rechten Bildschirmrand via sichere Reflection (`EditorMinimap`).
  * **Rainbow Brackets**: Farbliches Hervorheben und Fetten zusammengehöriger Klammerpaare (`isHighlightBracketPair`, `boldMatchingDelimiters`, `BracketPairs`).
  * **Pinch-to-Zoom**: Stufenloses Skalieren der Schriftgröße per Zwei-Finger-Geste (`setScaleTextSizes(8f * density, 40f * density)`).
  * **Desktop-Shortcuts & PC-Navigation**: Aktivierung des PC-Navigationsmodus (`navigationMode = 1`) für Tastatur-Navigation und Shortcuts.
  * **Diagnose-UI**: Aktivierung von `EditorDiagnosticTooltipWindow` für Tooltip-Anzeige bei LSP-Diagnosen.
* **Bild- und Vektor-Vorschau (`ImageFilePreview`)**:
  * Unterstützung für Raster- und Vektor-Formate: PNG, JPG, JPEG, WEBP, GIF, SVG, BMP sowie Android XML `<vector>` und `<adaptive-icon>`.
  * Zoom & Pan via `pointerInput(detectTransformGestures)`, Detail-Header mit Dateigröße und Pixel-Dimensionen, Vektor-Strukturanzeige bei XML-Dateien.
  * Umschaltung im `EditorScreen` bei Bilddateien unter Deaktivierung nicht anwendbarer Aktionen (Undo, Redo, Cut, Copy, Formatting).
* **Einstellungen & Proto-Schema**:
  * `core/datastore/src/main/proto/settings.proto` um 4 Felder erweitert (`minimap_enabled = 12`, `rainbow_brackets_enabled = 13`, `pinch_to_zoom_enabled = 14`, `desktop_shortcuts_enabled = 15`), standardmäßig aktiv in `AppSettingsSerializer`.
  * Toggles in `EditorSettingsContract`, `EditorSettingsViewModel` und `EditorSettingsScreen` integriert.
* **Lokalisierung & Tests**:
  * Vollständige englische und deutsche Ressourcen in `values/` und `values-en/`; 0 hartcodierte Strings (`python3 scripts/find_hardcoded_strings.py --check`).
  * 4 Unit-Tests in `ImageFilePreviewTest` erfolgreich; `:feature:editor:testDebugUnitTest`, `:feature:settings:testDebugUnitTest` und `:app:assembleDebug` erfolgreich ausgeführt.


### 🌳 Tree-sitter Core-Library, native Sprachbibliotheken & Query-Assets (Task 02)

* **Core-Library & Maven-Integration**: Tree-sitter Core-Runtime (`com.itsaky.androidide.treesitter:android-tree-sitter:4.3.1`) und native Parser-AARs für Kotlin, Java, JSON, XML, C++ sowie C in `gradle/libs.versions.toml` und `feature/editor/build.gradle.kts` eingebunden.
* **Query-Assets**: Vollständige `highlights.scm`-Query-Dateien aus den offiziellen Upstream-Repositories für alle 10 Ziel-Sprachen (Kotlin, Java, JSON, XML, C++, C, Bash, CMake, TOML, YAML) unter `feature/editor/src/main/assets/treesitter/<sprache>/highlights.scm` bereitgestellt und im erzeugten Debug-AAR verifiziert.
* **Resiliente Ladelogik**: `TreeSitterGrammar` um Sprachbezeichner `langName` erweitert; `TreeSitterLanguageSupport` instanziiert vorkompilierte Grammatiken vorrangig über deren typisierte Wrapper-Klasse (`TSLanguage<Lang>.getInstance()`) und fällt bei dynamischen Bibliotheken transparent auf `TSLanguage.loadLanguage(context, langName)` bzw. TextMate/Plaintext zurück.
* **Verifikation**: Alle 9 Unit-Tests in `:feature:editor` erfolgreich ausgeführt; `:feature:editor:assembleDebug` fehlerfrei gebaut; 0 hartcodierte Strings.

### 🎨 TextMate-Grammatiken, Konfigurationen & Themes (Task 01)

* **Vollständige Grammatiken & Sprachkonfigurationen**: Offizielle MIT-lizenzierte TextMate-Grammatiken und Sprachkonfigurationen (Bracket-Pairs, Auto-Closing, Kommentare) für Kotlin (`fwcd/vscode-kotlin`), Java, XML und JSON (`microsoft/vscode`) in `feature/editor/src/main/assets/textmate/` hinterlegt.
* **Autarke Themes**: Vollständige `dark_plus.json` und `light_plus.json` durch Zusammenführung der VS-Code-Basisfarben (`dark_vs`/`light_vs`) und Plus-Tokens generiert; sora-editor und tm4e laden die Themes ohne unaufgelöste `include`-Referenzen autark.
* **JSON-Validierung**: Alle 10 Dateien als valides Standard-JSON ohne JSONC-Kommentare oder trailing commas verifiziert.
* **Lizenz & Dokumentation**: `feature/editor/src/main/assets/textmate/docs/sub/NOTICE.md` angelegt und `feature/editor/ASSETS.md` aktualisiert.

### 🔍 TreeSitter & CMakeLists.txt-Erkennung (Task 07)

* **Dateinamen-Erkennung**: `TreeSitterGrammar.fromPath` erkennt `CMakeLists.txt` (auch in Unterverzeichnissen, Windows-Pfaden und unabhängig von Groß-/Kleinschreibung) als `CMAKE`-Grammatik, obwohl die Datei keine klassische Dateiendung besitzt.
* **Unit-Tests erweitert**: `TreeSitterGrammarTest` in `:feature:editor` hinzugefügt (3 Tests für `CMakeLists.txt`, Standard-Endungen und nicht unterstützte Dateien). Alle 8 Tests in `:feature:editor` grün.
* **Build & Doku**: `:feature:editor:assembleDebug` erfolgreich gebaut; TODO in `TreeSitterLanguageSupport.kt` auf gelöst aktualisiert und Verifikationsbericht hinterlegt.

### 🛠️ Editor-Werkzeuge, Git-Panel & Drawer (Task 10)

* **Unit-Tests vollständig grün**: Alle 118 Tests in `:libs:code-tools` (53 Tests: Formatierung für Kotlin/Java/C/Gradle/JSON/XML, Zeilenbefehle, Regex-Suche/Ersetzen), `:core:domain` (24 Tests: Git-Modelle, Hunk-Patching, Eingabevalidierung) und `:core:data` (41 Tests gegen JGit: Stash inkl. untracked, Tags, Rebase, Cherry-Pick, Revert, Reset, Amend, Hunk-Staging mit `DirCacheEditor`, Blame, Verlauf, 3-Wege-Konflikt-Editor) erfolgreich ausgeführt.
* **sora-editor 0.23.4 API**: `EditorController` vollständig verifiziert (`Content.replace`, `indexer.getCharPosition`, `cursor.left/right`, `setSelectionRegion`). Batch-Edits fassen Ersetzen und Formatieren atomar in `beginBatchEdit()`/`endBatchEdit()` zusammen, sodass Undo exakt einen Schritt zurücknimmt.
* **Workspace-Drawer & Navigation**: `WorkspaceDrawer` mit adaptiver Breite `min(92%, 420 dp)` und horizontal wischbarer `LazyRow` für Tabs (Files, Suche, Git, Module, Terminal, Settings).
* **Editor- und Dateisynchronisation**: `FileSyncBridge` speichert ungespeicherte Puffer vor Git-Aktionen (Checkout, Pull, Stash) und Projektersetzungen atomar ab und lädt betroffene Tabs nach externen Dateiänderungen neu.
* **Dokumentation**: Risikosektion in `docs/editor-tools-git-drawer.md` aktualisiert und Verifikationsbericht erstellt.

### 🧙 Project Wizard & Template Engine (Task 09)

* **Wrapper-Harmonisierung & Sofort-Build**: Template-Wrapper von `gradle-9.0.0` auf `gradle-8.14.3-bin.zip` vereinheitlicht; `RootBuildGradle.kt` verwendet nun `rootProject.layout.buildDirectory` (Groovy & KTS). Ein erzeugtes Android-Projekt (`SampleApp`, EmptyActivity, Kotlin, KTS) wurde im realen Dateisystem mit `./gradlew assembleDebug` in 3m 48s erfolgreich und fehlerfrei gebaut.
* **Schlanker Versionskatalog**: `libs/template-engine/src/main/assets/gradle/libs.versions.toml` von 228 auf 35 relevante Zeilen bereinigt (AGP 8.13.0, Kotlin 2.1.0, Vorlagen-Bibliotheken). Verhindert irrelevante Warnungen im Dependency-Updater.
* **Unit-Tests vollständig grün**: Alle 7 Tests in `ProjectGeneratorTest` (alle 9 Vorlagen × Kotlin/Java × KTS/Groovy, Quelltext-Endungen, Layout- und ViewBinding-Auflösung, XML-Escaping, String-Referenzen) und alle 6 Tests in `ProjectInputValidatorTest` erfolgreich.
* **Formular & Persistenz**: Vorlagenwahl mit 9er-Raster; automatische Package-Vorschläge; Compose erzwingt Kotlin; Benutzereinstellungen (Speicherort, Sprache, minSdk, DSL) werden in `WizardConfig` (`settings.proto`) gespeichert; automatischer Eintrag in `RecentProjectsRepository` und Navigation in den Editor.
* **Audit-Notizen**: SAF-Ordnerauswahl für den Speicherort und Flutter-Wizard (aus ZIP nicht portiert) planmäßig als offen dokumentiert.

### 📦 Indexierung, Dependency-Updater & Editor-Overlays (Task 08)

* **Unit-Tests vollständig grün**: Alle 34 Tests in `:libs:indexing-impl` (8 Tests: Gradle-Repositories, Settings, Index-Builder), `:libs:dependency-updater-impl` (21 Tests: Gradle-Build-Parser, Repository-Flow, TOML-Katalog-Parser, Update-Flow, Versionsvergleich) und `:feature:editor` (5 Tests: Hex-/Kotlin-Farbliteral-Scanner) erfolgreich ausgeführt.
* **Builds & Integration verifiziert**: `:feature:dependencyupdates:assembleDebug`, `:feature:editor:assembleDebug` und `:app:assembleDebug` erfolgreich kompiliert und assembliert.
* **Editor-Overlays (sora-editor 0.23.4)**: `EditorOverlayGeometry` korrigiert den sora-Versatz (`getCharOffsetY` liefert Unterkante; `rowHeight` wird subtrahiert) für zeilenbündige Platzierung; Farbkästchen mit 2x2-Schachbrett für Alphatransparenz; reaktives Re-Layout über `overlayTick` bei Scroll- und Content-Events.
* **Update-Flow & FileSyncBridge**: Update-Chips in `libs.versions.toml` mit Dialog (Update / Update All); atomares Speichern und automatischer Tab-Reload via `FileSyncBridge.requestFlush()` und `notifyExternalChange()`.
* **Projekt-Öffnen & Dialog-Optionen**: `DependencyUpdatesHost` prüft Abhängigkeiten im Hintergrund; persistentes `Dismiss` (überlebt Neustarts via `DismissalStore`), sitzungsweises `Ask later` (`snooze`) und direkte Aktualisierung verifiziert.
* **Sprachanalyse**: Prüfung der `.toml`-Erkennung dokumentiert (derzeit `PLAIN`; Erweiterung für Task 01/02 empfohlen).

### 💻 Termux-Umgebung, SDK-Setup & W^X Exec (Task 11)

* **Exec-Entscheidung & W^X-Lösung**: `targetSdk` in `build-logic/convention/.../BuildConfig.kt` und Dokumentation auf `28` festgelegt. Dies weist Android 10+ (API 29+) an, die App im `untrusted_app_28`-SELinux-Domain ohne W^X-Restriktionen auszuführen, sodass `bash`, `java`, `apt`, `aapt2` etc. direkt aus `/data/data/com.codeforge.app/files/usr/bin` gestartet werden können (identisch zur offiziellen Termux-App).
* **Build & Namespace-Kollisionsprüfung**: `./gradlew assembleDebug` fehlerfrei gebaut (1279 Tasks). Namespace-Koexistenz von `:libs:termux-app` und `:app` (`com.codeforge.app`) ohne Konflikte bestätigt.
* **Unit-Tests vollständig grün**: Alle 4 Tests in `SdkScriptProtocolTest` (`:libs:terminal-engine`) erfolgreich (Status-Parsing, Fortschrittsextraktion `PROGRESS:`, Fehleranalyse `ERROR:` und Warnungen). Alle 849 Unit-Test-Tasks im Gesamtlauf (`testDebugUnitTest`) fehlerfrei.
* **codeforge-env & Architektur**: Installationsskript in `libs/terminal-engine/src/main/assets/codeforge-env` verifiziert (Multi-Arch `aarch64`/`arm`, GitHub-Mirror-Fallback `scto/codeforge-tools`, Extraktion und Maschinenprotokoll).
* **Plugin-API**: `PluginHostServices.buildShellCommand` und `$HOME`-Pfadkonsistenz im Termux-Prefix verifiziert.

### 🎨 Layout-Designer (`:feature:layoutdesigner`) (Task 15)

* **Unit-Tests vollständig grün**: Alle 14 Tests (`LayoutModelTest`: 11, `LayoutScannerTest`: 3) erfolgreich ausgeführt (Roundtrip-XML, Widget-Palette, Attribut-Filterung nach Eltern-Typ, Duplizieren/Verschieben/Einrücken, XXE-Schutz, Einheiten/Farben, Datei-Scanner).
* **Compose-UI & Material 3 verifiziert**: `ModalBottomSheet`, `VerticalDivider`, `HorizontalDivider`, `CircularProgressIndicator` und spezifische Icons (`SpaceBar`, `TouchApp`, `HourglassEmpty`, `AutoMirrored.*`) fehlerfrei im Debug-Build kompiliert.
* **Workflow & Näherungsdarstellung**: Palette-Einfügen mit freier ID-Generierung, 100 Schritte Undo/Redo mit Eingabe-Coalescing, XML-Draft/Apply/Revert mit Fehlerprüfung, atomares Speichern (.tmp) und Benachrichtigung via `FileSyncBridge`. Erkennung von `ConstraintLayout`/`RelativeLayout` mit Hinweistext `layout_approx_note`.
* **Adaptives Layout**: Split-Panel ab 600 dp (Vorschau links, Eigenschaften/Struktur/XML rechts); Tab-Navigation auf kompakten Bildschirmen.

### 📱 Adaptive Layouts, Edge-to-Edge & Material 3 (Task 14)

* **Toolchain & Bibliotheken verifiziert**: Compose BOM `2025.09.00` (Material 3 `1.3.2`) und `material3-adaptive:1.1.0` auf Google Maven verifiziert. Veralteten Eintrag `composeCompiler = "1.5.15"` aus `libs.versions.toml` bereinigt.
* **WelcomeScreen (Adaptive 1.1.0)**: `NavigableListDetailPaneScaffold`, `AnimatedPane` und `rememberListDetailPaneScaffoldNavigator` verifiziert; `navigator.navigateTo` korrekt als Suspend-Funktion in Coroutine-Scope gebunden.
* **Edge-to-Edge & Insets**: `WindowSizeUtils` Breakpoints (Compact <600dp, Medium <840dp, Expanded >=840dp), permanenter Workspace-Drawer ab 840dp, `consumeWindowInsets(padding)` gegen doppeltes Scaffold-Padding und `imePadding()` für Tastatur im Editor/Terminal verifiziert.
* **Statusleisten-Synchronisation**: `MainActivity` schaltet Status- und Navigationsleisten dynamisch per `SystemBarStyle.auto(...) { isDark }` passend zum App-Theme (auch bei invertiertem System-Modus lesbar).
* **AutoMirrored-Icons**: Deprecated Icons in `ProjectImportRoute`, `EditorScreen`, `ChangesTab`, `CommitViews`, `DiffViewer` und `TerminalScreen` auf `Icons.AutoMirrored.Filled.*` (`ArrowBack`, `Undo`, `Redo`, `Send`, `CallSplit`, `FormatAlignLeft`, `Comment`, `FormatIndentIncrease`, `FormatIndentDecrease`) migriert.

### 🌐 Lokalisierung & Ressourcen (`:core:resources`) (Task 13)

* **Zentrale Strings verifiziert**: AAPT2-Validierung, R-Codegenerierung und `TestRes`-Reflection erfolgreich auf Gradle/Termux ausgeführt.
* **Harte UI-Strings migriert**: 25 verbliebene sichtbare UI-Literale in `feature/` (`EditorScreen`, `BranchesTab`, `ChangesTab`, `CommitViews`, `ConflictEditor`, `DiffViewer`, `GitCloneScreen`, `GitPanel`, `LayoutXml`, `ModuleMakerScreen`, `PluginsScreen`, `EditorSettingsScreen`, `ThemeBuilderScreen`) nach `core:resources` ausgelagert. `find_hardcoded_strings.py --check` ist sauber (Exit 0).
* **Multi-Prozess-Unterstützung**: `Res.init(this)` in `GradleBridgeService.onCreate()` integriert, sodass auch im isolierten `:gradletooling`-Prozess String-Ressourcen korrekt aufgelöst werden.
* **JVM-Unit-Tests**: Type-Mismatch-Warnung in `TestRes.locateStringsXml()` und `parentFile`-Warnungen in Git-Tests behoben; alle Test-Tasks im Gesamtlauf grün.

### ➕ Tooling

* `scripts/find_hardcoded_strings.py` (Suche nach harten UI-Texten, `--check`/`--json`/Allowlist) und `agy-tasks/17-externalize-hardcoded-strings.md` (wiederkehrende Auslagerung nach `:core:resources`).

### 📦 Terminal-Bootstrap (`bootstrap-2026.10.11`) (Task 04)

* **Neuer Bootstrap-Release eingebunden**: Release `bootstrap-2026.10.11` (`scto/terminal-packages-codeforge`) mit Präfix `/data/data/com.codeforge.app/files/usr` für `aarch64` und `arm` verifiziert und in `gradle.properties` konfiguriert.
* **Architektur-Fokus**: `TerminalBootstrapPackagesPlugin` auf `aarch64` und `arm` angepasst (Standard für Pixel 9 Pro XL / ARM-Zielgeräte), defekten x86_64-Release `2026.10.08` vollständig entfernt.
* **Native Einbettung verifiziert**: Task `:libs:termux-app:embedTerminalBootstrap` lädt die Zips und generiert `termux-bootstrap-zip.S` mit `.incbin`. `:app:assembleDebug` baut erfolgreich mit `libtermux-bootstrap.so` (128 MB für aarch64, 120 MB für arm).

### 🐛 Fixes nach dem ersten echten On-Device-Build (`error2.log`)

* **`:app:processDebugResources` (AAPT „resource … not found")**: Die vendorten Termux-Module hatten keine `res/values`. Ergänzt:
  `:libs:termux-shared` → `strings.xml` (+`values-de`), `colors.xml` (`black`, `red_400`, `background_markdown_code_block/_inline`),
  `dimens.xml` (`content_padding`, `content_padding_half`), `styles.xml` (`TextAppearance.Widget.BaseToolbar.Title/Subtitle`),
  `attrs.xml` (`termuxActivityDrawerBackground`, `extraKeys*`), Drawables `ic_share`, `ic_copy`;
  `:libs:termux-app` → `strings.xml` (+`values-de`).
* **Theme**: `Theme.CodeForge` setzt die Termux-Attribute (`termuxActivityDrawerBackground`, `extraKeys*`).
* **Doppelter Namespace**: `:libs:termux-app` hat jetzt `com.codeforge.termux` (vorher wie `:app` `com.codeforge.app` → doppelte `R`/`BuildConfig`).
  Java-Imports `com.codeforge.R` (existierte nicht) → `com.codeforge.termux.R`; Manifest-Komponentennamen voll qualifiziert.
* `BuildConfig.TERMUX_PACKAGE_VARIANT` liegt jetzt in `:app` (Reflection von `:libs:termux-shared` liest `com.codeforge.app.BuildConfig`).
* `R.raw.bell` und `R.style.Theme_AppCompat_Light_Dialog` über die richtigen (nicht-transitiven) R-Klassen angesprochen.
* Bewusste Ausnahme: Termux-Strings stehen in den jeweiligen Libs, nicht in `:core:resources` (Java-Code greift per eigener R darauf zu).

## [3.0.0] - 2026-10-08

### 🏗️ Termux statt Rootfs, zentrale Strings, Build-Logic, Adaptive/Expressive, Layout-Designer

Breaking: Die Rootfs-/PRoot-/Multi-Distro-Umgebung ist entfernt; die Shell läuft im Termux-Prefix
(`/data/data/com.codeforge.app/files/usr`). Die Version springt von der 2.x-Linie auf 3.0.0
(vorher im Repository noch `0.1.0`/`versionCode 1`).

#### Added
- **Termux-Vendoring** (GPLv3): `:libs:termux-emulator`, `:libs:termux-view`, `:libs:termux-shared`, `:libs:termux-app` (aus `scto/AndroidIDE`, auf `com.codeforge` umgeschrieben, JNI-Symbole angepasst). `TerminalSessionRepositoryImpl` startet echte Termux-Shells; Lizenzfolge in `docs/sub/NOTICE.md`.
- **`codeforge-env`** (`:libs:terminal-engine`, Asset): einziges Setup-Skript für JDK 17/21, Android-SDK (`$PREFIX/opt/android-sdk`), cmdline-tools, platform-tools, CMake, NDK; `--machine`-Protokoll `CFSDK|…` für den SDK-Manager (GUI). Onboarding: Bootstrap → Skript → Terminal mit Autostart-Kommando. `ScriptInstaller` als Fallback.
- **`:core:resources`**: ~720 Texte zentral in `strings.xml`; Zugriff `Res.string(…)` (Nicht-UI) / `stringRes(…)` (Compose), Enum-/Listen-Labels als `@StringRes`; `TestRes` für JVM-Tests in `:core:testing`. Doku `docs/resources-and-strings.md`.
- **build-logic** mit Convention-Plugins (`codeforge.android.application|library|library.compose|hilt`, `codeforge.kotlin.library`, `codeforge.quality`, `codeforge.terminal.bootstrap`), Version-Catalog, Gradle 8.14.3 / AGP 8.13.2. Doku `docs/build-logic.md`.
- **Modul-Pflichtdateien** für alle 43 Module (`.gitignore`, `consumer-rules.pro`, `proguard-rules.pro`, Manifeste) sowie `:app`-Ressourcen (Theme, Farben, Launcher-Icons, Backup-/Extraktionsregeln). Doku `docs/module-files-and-app-resources.md`.
- **Adaptive Layouts, Edge-to-Edge, Material 3 Expressive**: `WidthClass` (`:core:ui`), dauerhafter Drawer ab 840 dp, `NavigableListDetailPaneScaffold` im Welcome-Screen, `enableEdgeToEdge` mit themenabhängigen Systemleisten, Inset-Behandlung, `MaterialExpressiveTheme` + `MotionScheme.expressive()`. Doku `docs/adaptive-edge-to-edge-expressive.md`.
- **Layout-Designer** (`:feature:layoutdesigner`): visueller Editor für Android-Layout-XML (Palette, Compose-Vorschau, Baum, Eigenschaften, XML-Tab, Undo/Redo, Speichern), Drawer-Bereich „Layouts“. Doku `docs/layout-designer.md`.
- **Git-Panel** (`:feature:git`, JGit): Diff, Merge, Commit, Push/Pull, Graph, Stash, Tags, Rebase, Cherry-Pick, Revert, Reset, Amend, Blame, Hunk-weises Stage, Konflikt-Editor; Git-Einstellungen. Doku `docs/editor-tools-git-drawer.md`.
- **Suche & Ersetzen** (`:feature:search`), **Submodule Maker** (`:feature:modulemaker`), `:libs:code-tools` (reine JVM-Logik, 53 Tests).
- **Indexierung und Dependency-Updater** (`:libs:indexing-*`, `:libs:dependency-updater-*`, `:feature:dependencyupdates`) mit Editor-Overlays (Versions-Hinweis im TOML, Farb-Kästchen). Doku `docs/indexing-and-dependency-updater.md`.
- **Projekt-Wizard und Template-Engine** komplett neu (9 Vorlagen, kein Freemarker-Einsatz mehr). Doku `docs/project-wizard-and-template-engine.md`.
- **Dokumentation/Betrieb:** `docs/architecture-decisions.md` (ADRs), `agy-tasks/01`–`16` (Prüf- und Umsetzungsaufträge für agy), `build_codeforge_repo.sh` (Bootstrap- und APT-Repo-Build).

#### Changed
- Toolchain: Kotlin 2.1.21, KSP 2.1.21-2.0.1, Hilt 2.56.2, Compose BOM 2025.09.00, material3-adaptive 1.1.0, activity-compose 1.10.1 (**nicht gegen Maven/Google verifiziert**).
- `versionName` `0.1.0` → `3.0.0`, `versionCode` `1` → `30000`.
- Termux-/Lizenzdokumente liegen unter `docs/sub/` (`TERMUX-PORTING.md`, `NOTICE.md`, `LICENSE.termux`); Referenzen im Code angepasst.
- `README.md`/`README_DE.md`, `STATUS.md` und dieses Changelog auf den tatsächlichen Repository-Stand gebracht (vorher teils Beschreibungen des lokalen Experimentzweigs).

#### Removed
- PRoot, Rootfs-Downloader, `DistroBootstrapRepository`, Multi-Distro-Auswahl, `setup-ide`/`xmltool.sh`/`tools/cfmide`/`codeforge-tools`.

#### Fixed
- Verschachtelte-Kommentar-Syntaxfehler in KDoc (`ComposePreviewRendererImpl.kt`, `TextMateAssetLoader.kt`).
- `$PREFIX` wurde im SDK-Manager-Text als Kotlin-Template interpretiert.
- Klartext-GPG-Passphrase in `build_codeforge_repo.sh`/`.md` durch Umgebungsvariable `CODEFORGE_GPG_PASSPHRASE` ersetzt (**die Passphrase gilt als kompromittiert, falls das Repository öffentlich war – rotieren**).

#### Known Issues
- Nichts davon wurde mit Gradle/Android gebaut. Offen: W^X-/Exec-Frage für Binaries unter `files/usr` (targetSdk 35), echter Bootstrap-Release für `com.codeforge.app`, Verifikation der angehobenen Versionen. Siehe `STATUS.md` und `agy-tasks/README.md`.
- Features aus dem lokalen Experimentzweig (u. a. Minimap, Rainbow Brackets, Extensions-Manager, 15 Theme-Presets, Datei-Eigenschaften im Dateibaum) sind in diesem Stand **nicht** enthalten – siehe `agy-tasks/16-port-local-experiment-features.md`.

---

> Die folgenden Einträge stammen aus der **lokalen Experimentlinie** (Termix/Nyamux-Terminal, Rootfs) und beschreiben
> Code, der in diesem Repository-Stand nicht vorhanden ist.

## [2.2.0] - 2026-09-24

### 🚀 FileTree, Theme Studio, Editor & Extensions Overhaul

#### Added
- **FileTree Features (`:feature:filetree`)**:
  - Dynamische, kontrastreiche & theme-adaptive Einrückungslinien (`start = Offset(lineX, 0f)`, `end = Offset(lineX, size.height)`), die sich zeilenübergreifend nahtlos verbinden.
  - 2-zeilige Datei-Informationen (Dateigröße & Änderungsdatum) unterhalb des Dateinamens in reduzierter Schriftgröße (`10.sp * uiScale`).
  - Ganzzeiliges Tap-Target (`fillMaxWidth()`) für alle Datei- und Ordnerzeilen im Drawer.
  - Kontextmenü-Erweiterung um **Eigenschaften (Properties)** (Name, Pfad, Typ, Größe, Änderungsdatum, Berechtigungen) und Bestätigungsdialog für **Löschen**.
- **Theme Studio Presets (`:feature:themebuilder`)**:
  - 15 integrierte Themes (Monokai, Darcula, Quiet Light, Ayu Dark, Solarized Dark, GitHub Light, VS Code Dark+, Notepad++, Eclipse, Cyberpunk Neon, Forest, Ocean, Sunset, Monochrome, Violet).
  - Horizontale scrollbare `LazyRow`-Vorschau mit Live-Farbkarten.
  - Interaktiver RGB-Colorpicker (`ColorPickerDialog`) mit 10 Custom Presets.
- **Erweiterungen (Extensions) Settings Screen (`:feature:settings`)**:
  - Neuer Screen `ExtensionsScreen.kt` & `ExtensionsManager.kt` unter `com.codeforge.feature.settings.extensions`.
  - Download, SHA-256 Checksummen-Verifizierung, Zip/Jar-Entpackung & Verwendungsprüfung für LSP Language Server.
  - Navigation über `Routes.EXTENSIONS` in `CodeForgeNavHost.kt`.

#### Fixed
- **SoraEditor Scrollbars (`:feature:editor`)**:
  - `editor.isVerticalScrollBarEnabled` und `editor.isHorizontalScrollBarEnabled` werden in `SoraEditorAppearance.kt` nun strikt über `config.scrollbarEnabled` geschaltet.

---

## [2.0.0] - 2026-09-03

### 🎯 Migration abgeschlossen: Clean Architecture Refactor (Termix → CodeForgeMobile)

Vollständige Migration der Codebasis auf ein Multi-Module Clean-Architecture-Setup
mit Jetpack Compose. Verifiziert und sign-off-erteilt gemäß strikter Audit-Prüfung
(siehe [`08_final_audit.md`](.agent/docs/migration-reports/08_final_audit.md)).

#### Added
- Neue Multi-Module-Struktur: `:app`, `:core`, `:feature`, `:libs`
- Terminal-Feature vollständig in Jetpack Compose (`TerminalScreen.kt`) mit
  Unidirectional Data Flow via `StateFlow`
- `TerminalForegroundService` mit `systemExempt`-Konfiguration in
  `AndroidManifest.xml` (`core/data`)
- `RunCommandWorker` für Hintergrund-Kommando-Ausführung

#### Changed
- Terminal-Session-Handling refaktoriert: `TerminalSessionRepositoryImpl.kt`
  ruft nun korrekt `finishIfRunning()` zum sauberen nativen Session-Abbau auf
- Settings-Feature auf neue Modulstruktur migriert
  (siehe `03_settings_feature_migration_report.md`)

#### Removed
- Legacy-Package `com.termix` vollständig aus allen produktiven Modulen
  (`app`, `core`, `feature`, `libs`) entfernt

#### Known Issues
- Verzeichnis `Termix-main-extract/` (Legacy-Backup) enthält weiterhin
  altes `com.termix`-Package. **Kein Einfluss auf Build/Runtime**, da nicht im
  Gradle-Include-Pfad. Empfehlung: Ordner archivieren oder via `.gitignore`
  ausschließen (Cleanup-Task, kein Blocker).

#### Verification
- Vollständige Code-Inspektion aller migrierten Module durchgeführt
- Historische Migrationsreports (`01`–`07`) gegen aktuellen Codebestand
  gegengeprüft — ein dokumentierter Widerspruch (s. o.), keine Blocker
- **Sign-off erteilt:** ✅ `.agent/docs/migration-reports/08_final_audit.md`

---

## [1.x.x] - Vorherige Migrationsphasen
<!-- Optional: Verlinke ältere Phasen, falls gewünscht -->
- Phase 3 Verifizierung: `PHASE_3_VERIFICATION_REPORT.md`
- Phase 2 Implementierung: `PHASE_2_IMPLEMENTATION_REPORT.md`
- Terminal-Feature-Migration: `02_terminal_feature_migration_report.md`
- Initiales Repo-Audit: `01_full_repo_audit_report.md`