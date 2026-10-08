# Bonsai, Sora Editor & App-Workspace — Integrationsstand

Dieses Dokument beschreibt den aktuellen Integrationsstand von drei
Kern-Subsystemen von CodeForgeMobile: dem Bonsai-Dateibaum, dem Sora-Editor
und der Workspace-Orchestrierung im `:app`-Modul. Für jedes Thema: beteiligte
Module/Dateien, Architektur, bekannte offene Punkte.

---

## 1. Bonsai — Dateibaum

**Beteiligtes Modul:** `:feature:filetree` (einziges Modul mit Bonsai-Dependency)

**Beteiligte Dateien:**

| Datei | Zweck |
|---|---|
| `gradle/libs.versions.toml` | Version `bonsai = "1.2.0"`, Aliase `bonsai-core`/`bonsai-file-system` (Gruppe `cafe.adriel.bonsai`, Maven Central) |
| `feature/filetree/build.gradle.kts` | Deklariert `implementation(libs.bonsai.core)` + `implementation(libs.bonsai.file.system)` |
| `feature/filetree/src/main/kotlin/com/codeforge/feature/filetree/FileTreeContract.kt` | UI-Contract (State/Event/Effect) |
| `feature/filetree/src/main/kotlin/com/codeforge/feature/filetree/FileTreeScreen.kt` | Compose-UI, eigentliche Bonsai-Verdrahtung (`FileTreeRoute`, `FileTreeScreen`, Kontextmenü-Dialoge) |
| `feature/filetree/src/main/kotlin/com/codeforge/feature/filetree/FileTreeViewModel.kt` | ViewModel/State-Management für Mutationen (Create/Rename/Delete) |

**Architektur:** Der Dateibaum nutzt `cafe.adriel.bonsai:bonsai-file-system`
(`FileSystemTree(rootPath: okio.Path, fileSystem: okio.FileSystem, selfInclude)`),
das auf Basis des Standard-File-API (nicht SAF/DocumentFile — `rootPath` ist ein
normaler lokaler Pfad, `okio.FileSystem.SYSTEM`) einen `Tree<okio.Path>` liefert.
Bonsai übernimmt Expand/Collapse und Lazy-Directory-Load selbst; `FileTreeContract`
deckt bewusst nur das ab, was Bonsai nicht kann: Dateisystem-Mutationen
(Create/Rename/Delete via Kontextmenü). Da Bonsai kein eigenes Refresh-API
besitzt, wird nach jeder Mutation ein `refreshToken` im `FileTreeUiState`
erhöht — Teil des `remember`-Keys für die `FileSystemTree`-Instanz, was einen
kompletten Tree-Rebuild erzwingt. Klicks auf Dateien lösen
`FileTreeUiEvent.FileOpened` aus, das über `OpenFileRequestBridge`
(`:core:navigation`) an `:feature:editor` weitergereicht wird, da Feature-Module
laut Architekturregel nicht direkt voneinander abhängen dürfen. Seit der
Umstellung "Editor als Hauptbildschirm" ist `FileTreeRoute` der Inhalt eines
`ModalNavigationDrawer` in `ProjectWorkspaceRoute.kt` (`:app`), keine
eigenständige Vollbild-Route mehr.

**Behoben in dieser Doku-Runde:** `feature/filetree/build.gradle.kts` verwies in
einem Kommentar auf eine Datei `BonsaiFileTree.kt`, die im aktuellen Repo-Stand
nicht existiert (vermutlich Rest einer früheren Umbenennung — die Verdrahtung
liegt tatsächlich direkt in `FileTreeScreen.kt`). Kommentar korrigiert.

---

## 2. Sora Editor — Code-Editor-Engine

**Beteiligtes Modul:** `:feature:editor` (einziges Modul mit Sora-Dependency)

**Gradle-Dependencies** (`feature/editor/build.gradle.kts`, als direkte
String-Koordinaten, nicht über den Version-Catalog):
- `io.github.Rosemoe.sora-editor:editor:0.23.4`
- `io.github.Rosemoe.sora-editor:language-textmate:0.23.4`
- `io.github.Rosemoe.sora-editor:language-treesitter:0.23.4`
- zusätzlich `libs.androidx.core.ktx` (FileProvider), `project(":libs:lsp-client")`

**Beteiligte Dateien** (`feature/editor/src/main/kotlin/com/codeforge/feature/editor/`):

| Datei | Zweck |
|---|---|
| `EditorContract.kt` | UI-State (Tabs, Suchleiste-Status), Events, Effects |
| `EditorController.kt` | Imperativer Wrapper um eine lebende `CodeEditor`-Instanz (Undo/Redo, Clipboard, Navigation, Suche/Ersetzen, Wordwrap-Toggle, Textgröße) |
| `EditorLanguageFactory.kt` | Wählt pro Datei TreeSitter oder TextMate als `Language`-Backend, verdrahtet `LspAwareLanguage` |
| `EditorScreen.kt` | Haupt-Compose-UI (`EditorRoute`), Tab-Leiste, Toolbar |
| `EditorSearchBar.kt` | Such-/Ersetzen-Leiste (Compose), delegiert an `EditorController` |
| `EditorViewModel.kt` | ViewModel: Tabs, LSP-Anbindung (didOpen/didChange/didClose, Diagnostics, Format), Compose-Preview-Bridge |
| `SoraCodeEditor.kt` | Compose-`AndroidView`-Wrapper um `CodeEditor` — zentrale Sora-Konfiguration |
| `fileprovider/EditorFileProvider.kt` | `FileProvider`-Kapselung für Share/"Öffnen mit"-Intents |
| `lsp/LspAwareLanguage.kt` | Decorator: LSP-Completions zusätzlich zu dokumentinternen Vorschlägen |
| `lsp/LspCompletionProvider.kt` | Brücke zwischen Sora `requireAutoComplete` und `LspClientRepository` |
| `lsp/LspDiagnosticsBridge.kt` | Übersetzt LSP-Diagnostics (Zeile/Spalte) in Sora `DiagnosticsContainer` (Zeichen-Offsets) |
| `textmate/TextMateAssetLoader.kt` | Asset-Loader für TextMate-Grammars/Themes — Details in `ASSETS.md` |
| `treesitter/TreeSitterLanguageSupport.kt` | Verfügbarkeits-Check/Erstellung von TreeSitter-`Language`-Instanzen — Details in `TREESITTER.md` |

**Architektur:** `SoraCodeEditor.kt` bindet `CodeEditor` per
`AndroidView(factory=..., update=...)` in Compose ein. Im `factory`-Block läuft
die komplette Sora-Konfiguration: `setEditorLanguage` (via
`EditorLanguageFactory`), `isBlockLineEnabled` (Code-Block-Indicators),
`isHighlightBracketPair`, `isWordwrap`, `nonPrintablePaintingFlags`,
`Magnifier`-Komponente, `EditorAutoCompletion`, sowie ein defensiver
`runCatching`-Reflection-Zugriff auf `EditorStickyScroll` (da diese Komponente
in älteren sora-editor-Artefakten ggf. fehlt). Tab-Größe, Schriftgröße/-art und
TextMate-Theme werden ebenfalls dort gesetzt; `diagnostics = diagnosticsContainer`
koppelt die Diagnostics-Anzeige. Inhaltsänderungen werden über
`subscribeEvent<ContentChangeEvent>` gemeldet; der `update`-Block hält Text,
Wordwrap, Tab-Breite, Textgröße, Theme und Diagnostics mit dem Compose-State
synchron. `EditorController` kapselt alle imperativen Operationen (Undo/Redo auf
Soras internem Content-Undo-Stack, Suche/Ersetzen via `EditorSearcher`).
LSP-Anbindung: `EditorViewModel` sendet `didOpen`/`didChange`/`didClose`/
`requestFormat` an `LspClientRepository` (`:libs:lsp-client`) mit
Dokumentversionierung je Pfad; Completions laufen über `LspCompletionProvider`
→ `LspAwareLanguage` (Decorator um das TextMate/TreeSitter-Backend);
Diagnostics über `LspDiagnosticsBridge`. Datei-Öffnen-Anfragen aus dem
Bonsai-Dateibaum kommen über `OpenFileRequestBridge` (`:core:navigation`) an,
nicht direkt vom Feature-Modul.

**Bekannte offene Punkte** (Details siehe `TREESITTER.md`, `ASSETS.md`,
`agy-tasks/01-...md`, `agy-tasks/02-...md`):
- TreeSitter-Core-Library (`com.itsaky.androidide.treesitter:android-tree-sitter`)
  noch nicht als Dependency ergänzt — ohne sie schlägt jeder
  `System.loadLibrary`-Aufruf für eine Pro-Sprache-`.so` fehl.
- `TreeSitterGrammar.fromPath` erkennt `CMakeLists.txt` (ohne Dateiendung)
  aktuell nicht — **korrigiert in dieser Doku-Runde:** der Code-Kommentar
  behauptete fälschlich, `EditorLanguageFactory.kt` hätte dafür bereits einen
  TODO-Kommentar/Sonderbehandlung; das ist nicht der Fall, jetzt als echtes
  `TODO(Thomas)-OFFEN` markiert.
- TextMate-Grammars/Themes/Query-Dateien für die meisten Sprachen fehlen noch
  (siehe `agy-tasks/01-textmate-grammars-themes.md`).

---

## 3. App-Modul / Workspace-Integration

**Beteiligtes Modul:** `:app` (Orchestrator) — abhängig von praktisch allen
`:feature:*`- und `:libs:*`-Modulen sowie `:core:designsystem`, `:core:ui`,
`:core:navigation`, `:core:datastore`, `:core:data`.

**`app/build.gradle.kts`:** alle Core-Module (s. o.), alle Feature-Module
(`onboarding`, `welcome`, `projectwizard`, `editor`, `composepreview`,
`filetree`, `terminal`, `sdkmanager`, `layoutdesigner`, `themebuilder`, `git`,
`plugins`, `settings`), Libs (`terminal-engine`, `template-engine`,
`gradle-tooling-bridge`, `lsp-client`, `plugin-api`), Compose BOM/UI/Material3,
Navigation-Compose, Activity-Compose, Material-Icons-Extended, Hilt
(`hilt-android` + KSP-Compiler), `coreLibraryDesugaring`.

**Beteiligte Dateien** (`app/src/main/kotlin/com/codeforge/app/`):

| Datei | Zweck |
|---|---|
| `CodeForgeApplication.kt` | `@HiltAndroidApp`-Application-Klasse (minimal) |
| `MainActivity.kt` | `@AndroidEntryPoint`, liest `SettingsRepository.appSettings` (Theme, Onboarding-Status), setzt `CodeForgeTheme` + `CodeForgeNavHost` |
| `CodeForgeNavHost.kt` | Zentraler `NavHost` mit allen Routen (Onboarding, Welcome, ProjectWizard, Import, Clone, Settings-Hub + Unterseiten, Terminal, Git, `workspace/{rootPath}`) |
| `ProjectWorkspaceRoute.kt` | Haupt-Workspace-Screen: `ModalNavigationDrawer` mit `FileTreeRoute` als Drawer-Inhalt, `EditorWithPreviewHost` als Hauptinhalt |
| `EditorWithPreviewHost.kt` | Tab-Host für Editor/Compose-Preview (kombiniert `:feature:editor` und `:feature:composepreview`, die sich laut Architekturregel nicht direkt kennen dürfen) |
| `ProjectImportRoute.kt` | SAF-Picker-Screen (`ActivityResultContracts.OpenDocumentTree`), Fortschrittsanzeige |
| `ProjectImportViewModel.kt` | Verdrahtet `ProjectImportRepository`/`RecentProjectsRepository` mit dem SAF-Ergebnis |
| `ComposablePreviewBridgeViewModel.kt` | Adapter auf `ActiveComposablePreviewBridge` (`:core:navigation`) |

**Workspace-Öffnung — zwei Wege:**
1. **SAF-Picker:** `ProjectImportRoute` nutzt `OpenDocumentTree`,
   `ProjectImportRepositoryImpl` (`:core:data`) liest den gewählten Baum per
   `DocumentFile.fromTreeUri`, kopiert ihn rekursiv in ein app-privates
   Zielverzeichnis (lokaler Pfad) und meldet Fortschritt als
   `Flow<ImportProgress>`. Danach läuft alles über normale `java.io.File`-Pfade
   weiter — SAF wird nur für den initialen Import verwendet, nicht für
   laufenden Dateizugriff.
2. **ProjectWizard / Git-Clone:** erzeugen ebenfalls einen lokalen `rootPath`,
   der direkt an `workspace/{rootPath}` navigiert wird.

**Modul-Verdrahtung:** `:app` orchestriert, da direkte
`:feature:*`→`:feature:*`-Abhängigkeiten verboten sind.
`ProjectWorkspaceRoute` (in `:app`) komponiert `:feature:filetree` (Drawer) und
`:feature:editor`/`:feature:composepreview` (Hauptbereich) nebeneinander.
Kommunikation zwischen Features läuft ausschließlich über
`:core:navigation`-Bridges: `OpenFileRequestBridge` (Filetree → Editor,
`SharedFlow<String>`, da jeder Klick ein einmaliges Ereignis ist) und
`ActiveComposablePreviewBridge` (Editor → Preview-Tab-Sichtbarkeit in `:app`,
`StateFlow`). `:feature:projectwizard` wird direkt von `CodeForgeNavHost`
aufgerufen und liefert ebenfalls einen `rootPath`.

**Hilt-Module:** Es gibt **keine** `@Module`-Deklaration direkt im
`:app`-Modul selbst — die relevanten Module liegen in den Core-Modulen und
werden transitiv eingebunden:
- `core/data/src/main/kotlin/com/codeforge/core/data/di/DataModule.kt` —
  `DataStoreProviderModule` (liefert `DataStore<Preferences>`) und `DataModule`
  (bindet `RecentProjectsRepository`, `FileSystemRepository`, `GitRepository`,
  `ComposeSourceAnalyzer`, `ProjectImportRepository` an ihre Impl-Klassen)
- `core/navigation/src/main/kotlin/com/codeforge/core/navigation/NavigationBridgeModule.kt`
  — bindet `ActiveComposablePreviewBridge` und `OpenFileRequestBridge` an ihre
  Singleton-Impl-Klassen

**Bekannte offene Punkte:** Keine TODO/FIXME-Kommentare im `:app`-Modul
gefunden — der Workspace-Orchestrierungscode selbst ist vollständig, offene
Punkte liegen ausschließlich in den Subsystemen, die er komponiert (Bonsai: s.
Abschnitt 1; Sora/TreeSitter/TextMate: s. Abschnitt 2 und
`agy-tasks/01`–`02`; Termux/PRoot: s. `docs/sub/TERMUX-PORTING.md`).
