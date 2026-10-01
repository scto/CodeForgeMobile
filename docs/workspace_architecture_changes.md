# Zentralisierte Workspace-Architektur in CodeForgeMobile

## Übersicht
Diese Dokumentation beschreibt die Implementierung und Integration des **zentralisierten Workspace-Ansatzes** in CodeForgeMobile, basierend auf der Skelett-Architektur aus `assets/cfm/CodeForgeMobile.zip`. 

Der zentralisierte Workspace bündelt das Projektmanagement, das Dateibaum-Drawer-Management und die Editor-Sitzungsverwaltung in einer kohärenten `Workspace`-Shell im `:app`-Modul und wahrt dabei strikte Modulgrenzen (Clean Architecture).

---

## Modulübersicht & Implementierte Komponenten

### 1. Modul `:feature:editor`
* **`com.codeforge.feature.editor.state.EditorSession`**:
  - Repräsentiert den Status einer einzelnen geöffneten Datei.
  - Felder: `filePath`, `content`, `languageId`, `dirty`.
* **`com.codeforge.feature.editor.state.EditorSessionStore`**:
  - Bietet In-Memory State-Management für mehrere parallele Editor-Tabs/Sitzungen (`linkedMapOf`).
* **`com.codeforge.feature.editor.language.EditorAssetRegistry`**:
  - Stellt Pfade für TextMate-Grammatiken (`textmate`) und Tree-Sitter-Queries (`tree-sitter-queries`) bereit.
* **`com.codeforge.feature.editor.diagnostics.EditorDiagnosticsAdapter`**:
  - Schnittstelle zur Verwaltung von LSP-Diagnose- und Fehler-Overlays.
* **`com.codeforge.feature.editor.ui.SoraEditorHost`**:
  - `@Composable`-Wrapper um den `SoraCodeEditor`, der als saubere Einbettungsschnittstelle im Workspace dient.
* **`com.codeforge.feature.editor.ui.EditorSearchPanel`**:
  - Such- und Ersetzungspanel für den Editor.

### 2. Modul `:feature:filetree`
* **`com.codeforge.feature.filetree.model.FileTreeNode`**:
  - Datenmodell für die Baumstruktur (`path`, `name`, `isDirectory`, `children`).
* **`com.codeforge.feature.filetree.ui.tree.BonsaiFileTree`**:
  - High-Level Composable für die Darstellung baumstrukturierter Dateisysteme.
* **`com.codeforge.feature.filetree.ui.drawer.WorkspaceFileTreeDrawer`**:
  - Kapselt die `FileTreeDrawer`-Komponente mit `workspaceRoot` und Rückruffunktionen (`onFileSelected`, `onCloseDrawer`).

### 3. Modul `:app`
* **`com.codeforge.app.workspace.WorkspaceUiState`**:
  - Datenklasse für den globalen Workspace-Zustand (`workspaceRoot`, `currentFilePath`, `currentContent`, `isDrawerOpen`, `isLoading`, `errorMessage`).
* **`com.codeforge.app.workspace.WorkspaceViewModel`**:
  - Hilt ViewModel zur Verwaltung von `WorkspaceUiState` und Datei-Lade-Operationen.
* **`com.codeforge.app.workspace.WorkspaceScreen`**:
  - Haupt-Screen für den zentralen Workspace. Verwendet `ModalNavigationDrawer` mit `WorkspaceFileTreeDrawer` im Drawer-Inhalt und `SoraEditorHost` im Hauptbereich.
* **`com.codeforge.app.workspace.WorkspaceRoute`**:
  - Jetpack Compose Route für den Workspace.

---

## Routing & Integration (`CodeForgeNavHost.kt`)
In `CodeForgeNavHost.kt` wurde die Route `workspace/{rootPath}` hinzugefügt:

```kotlin
const val WORKSPACE_PATTERN = "workspace/{rootPath}"
fun workspace(rootPath: String) = "workspace/${Uri.encode(rootPath)}"
```

Sowie der dazugehörige Navigations-Eintrag:
```kotlin
composable(
    route = Routes.WORKSPACE_PATTERN,
    arguments = listOf(navArgument("rootPath") { type = NavType.StringType }),
) {
    WorkspaceRoute()
}
```

---

## Zusammenfassung
Die Architektur ermöglicht nun eine nahtlose Entkopplung von Dateibrowser und Editor bei gleichzeitiger zentraler Kontrolle über den aktiven Workspace-Pfad. Strikte Modulgrenzen zwischen `:app`, `:feature:editor` und `:feature:filetree` bleiben vollständig erhalten.
