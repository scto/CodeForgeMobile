# CodeForgeMobile – Gesamtdokumentation der Änderungen

Dieses Dokument fasst die Implementierungen, Einstellungen-Integrationen, Toolbar-Erweiterungen und Fehlerbehebungen im Projekt **CodeForgeMobile** zusammen.

---

## 1. Zentralisierte Workspace-Architektur

Basiert auf der Skelett-Struktur aus `assets/cfm/CodeForgeMobile.zip`.

### Modul `:feature:editor`
- **`com.codeforge.feature.editor.state.EditorSession`**: Datenklasse für geöffnete Dateien (`filePath`, `content`, `languageId`, `dirty`).
- **`com.codeforge.feature.editor.state.EditorSessionStore`**: In-Memory-Verwaltung mehrerer zeitgleich geöffneter Editor-Tabs (`linkedMapOf`).
- **`com.codeforge.feature.editor.language.EditorAssetRegistry`**: Pfadbereitstellung für TextMate- & Tree-Sitter-Assets.
- **`com.codeforge.feature.editor.diagnostics.EditorDiagnosticsAdapter`**: Adapter zur Steuerung von LSP-Diagnosen.
- **`com.codeforge.feature.editor.ui.SoraEditorHost`**: Composable-Wrapper für `SoraCodeEditor`, eingebunden in den Workspace.
- **`com.codeforge.feature.editor.ui.EditorSearchPanel`**: Such- & Ersetzungspanel für den Editor.

### Modul `:feature:filetree`
- **`com.codeforge.feature.filetree.model.FileTreeNode`**: Knoten-Struktur für den Dateibaum (`path`, `name`, `isDirectory`, `children`).
- **`com.codeforge.feature.filetree.ui.tree.BonsaiFileTree`**: High-Level Tree Component.
- **`com.codeforge.feature.filetree.ui.drawer.WorkspaceFileTreeDrawer`**: Kapselung des Dateibaum-Drawers mit Root-Verzeichnis und Ereignisbehandlung.
- **`com.codeforge.feature.filetree.FileTreeToolbar`**: Erweiterung der Toolbar um Schnell-Optionen:
  - **ShowHidden (Versteckte Dateien)**: Checkbox für Punkt-Dateien.
  - **Sortmode (Sortierung)**: Umschaltung `A-Z` (aufsteigend) / `Z-A` (absteigend).
  - **Sortby (Sortieren nach)**: Auswahl `Name`, `Typ`, `Größe`, `Datum`.
  - **Projectview / View Mode**: Umschaltung `Modul-Ansicht`, `Projekt-Ansicht`, `Datei-Ansicht`.

### Modul `:app`
- **`com.codeforge.app.workspace.WorkspaceUiState`**: Zustand des zentralen Workspaces.
- **`com.codeforge.app.workspace.WorkspaceViewModel`**: Hilt ViewModel zur Steuerung des Workspaces und Datei-Operationen.
- **`com.codeforge.app.workspace.WorkspaceScreen`**: Shell-Layout mit `ModalNavigationDrawer` und `SoraEditorHost`.
- **`com.codeforge.app.workspace.WorkspaceRoute`**: Navigationseinstiegspunkt für den Workspace.
- **`CodeForgeNavHost.kt`**: Einbindung der Route `workspace/{rootPath}`.

---

## 2. Integration der Einstellungen (`:feature:settings`)

### Dateibaum-Einstellungen (`FileTreeConfig`)
Die Einstellungen aus Proto DataStore werden nun dynamisch im Workspace-Drawer und in der Toolbar angewendet:
- **Sortierung & Reihenfolge**: A-Z (aufsteigend) vs. Z-A (absteigend).
- **Sortieren nach**: Name, Typ, Größe oder Änderungsdatum.
- **Versteckte Dateien**: An-/Ausblenden von Dateien mit führendem Punkt (`.gitignore`, `.idea` etc.).
- **FileTree View Mode**: Modul-Ansicht, Projekt-Ansicht und Datei-Ansicht.

### Editor-Einstellungen (`EditorConfig`)
Die Einstellungen aus Proto DataStore steuern das Verhalten des `SoraCodeEditor` im Workspace:
- **Erscheinungsbild**: TextMate-Theme, Schriftart (JetBrains Mono, Ubuntu Mono, Roboto Mono), Schriftgröße, Zeilennummern, Word Wrap, Minimap.
- **Verhalten**: Sticky Scroll, Auto Indent, Klammernhervorhebung, Symbolpaar-Vervollständigung, Soft-Keyboard-Steuerung, Lupe.
- **Symbol-Leiste**: Dynamisches Einblenden der `EditorSymbolBar` unter dem Editor bei `symbolBarVisible = true`.

---

## 3. Debug-Log Reparaturen (`codeforge_debug.log`)

1. **`IndexOutOfBoundsException` in `ScrollableTabRow` (Material3)**:
   - *Ursache*: Wettlaufbedingung beim Schließen von Tabs zwischen Listenaktualisierung und Tab-Indikator-Messung.
   - *Lösung*: `ScrollableTabRow` in [EditorScreen.kt](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/feature/editor/src/main/kotlin/com/codeforge/feature/editor/EditorScreen.kt) mit `key(uiState.openFiles.size)` geschützt.

2. **LSP Stream Closed Warnings**:
   - *Ursache*: Schreibversuche an beendete Serverprozesse erzeugten Fehler-Logs mit Stacktrace.
   - *Lösung*: In [LspRpcConnection.kt](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/libs/lsp-client/src/main/kotlin/com/codeforge/libs/lsp_client/LspRpcConnection.kt) abgefangen und als Warnung geloggt.

---

## 4. APK-Build & Dokumente
- **Build Status**: `BUILD SUCCESSFUL`
- **APK Pfad**: [app-debug.apk](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/app/build/outputs/apk/debug/app-debug.apk)
- **Dokumente**:
  - [filetree_toolbar_settings_extension.md](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/docs/filetree_toolbar_settings_extension.md)
  - [workspace_architecture_changes.md](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/docs/workspace_architecture_changes.md)
  - [debug_log_repairs.md](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/docs/debug_log_repairs.md)
  - [editor_tabs_and_tree_guide_lines_update.md](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/docs/editor_tabs_and_tree_guide_lines_update.md)
  - [editor_preview_statusbar_fix.md](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/docs/editor_preview_statusbar_fix.md)
  - [completion_preview_and_filetree_features.md](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/docs/completion_preview_and_filetree_features.md)
