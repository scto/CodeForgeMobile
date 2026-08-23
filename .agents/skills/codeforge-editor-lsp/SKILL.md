---
name: codeforge-editor-lsp
description: CodeForgeMobile Editor-Integration (Sora-Editor) und Language Server Protocol (LSP).
---
# CodeForgeMobile Editor & LSP

## Wichtige Dateien
- `:feature:editor/src/main/kotlin/com/codeforge/feature/editor/SoraCodeEditor.kt`: Der `AndroidView`-Wrapper für den Sora-Editor.
- `:feature:editor/src/main/kotlin/com/codeforge/feature/editor/EditorViewModel.kt`: Verwaltet offene Tabs und Dateizustände.
- `:libs:lsp-client/`: JSON-RPC Implementierung über Stdin/Stdout Streams.

## Architektur
- **Sora-Editor**: Verwendet TextMate-Grammatiken oder TreeSitter für Syntax-Highlighting. Anpassbar über `EditorSettings`.
- **LSP Bridge**: Der `LspClientRepositoryImpl` startet Language Server als Subprozess (meistens verpackt via PRoot). Kommunikation erfolgt via `LspRpcConnection` (Content-Length Header + JSON).
- **Compose Preview**: Der Editor meldet Composable-Funktionen über die `ActiveComposablePreviewBridge` an das `:feature:composepreview` Modul.