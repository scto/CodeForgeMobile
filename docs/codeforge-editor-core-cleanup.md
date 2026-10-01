# CodeForge Mobile - Editor & Core Architecture Cleanup Report

## Overview
This report details the bug fixes (Schritt 1), the audit and consolidation of `:feature:editor` (Schritt 2), and the analysis of `core/*` modules (Schritt 3).

---

## 1. Schritt 1: Bug Fixes (Editor Syntax Highlighting, Switches, FileTree & Text Colors)

### 1.1 Root Cause for Editor Code Coloring Failure
- **Issue:** Code opened in `SoraCodeEditor` lacked syntax highlighting and appeared uncolored.
- **Root Cause Identified:** `SoraLanguageProvider.getLanguage(...)` was wrapping language objects in a custom `CodeForgeLanguage` wrapper. `sora-editor` relies internally on `instanceof TextMateLanguage` and `instanceof MonarchLanguage` checks to attach grammar analyzers and apply `TextMateColorScheme`. Because of the `CodeForgeLanguage` wrapper, `instanceof` checks failed, preventing tokenization.
- **Fix:** `SoraLanguageProvider.kt` now directly returns `TextMateLanguage`, `MonarchLanguage`, or `BuiltinJavaLanguage` instances.

### 1.2 Switch & Text Contrast Fixes
- **Files:** `EditorSettingsScreen.kt`, `FileTreeSettingsScreen.kt`, `ThemeBuilderScreen.kt`
- **Fix:** Explicitly applied `color = MaterialTheme.colorScheme.onSurface` to titles and section headers in `SettingSwitchRow` so text remains high-contrast in all themes.
- **FileTreeToolbar:** Guaranteed all icons explicitly use `MaterialTheme.colorScheme.onSurface` or `primary`.

---

## 2. Schritt 2: Analyse & Consolidations in `:feature:editor`

### 2.1 Active Core Files (Kept & Optimized)
- `SoraCodeEditor.kt` - Main Compose wrapper around Sora CodeEditor.
- `EditorScreen.kt` - Screen layout with tabs, search panel, and top app bar.
- `EditorViewModel.kt` - ViewModel managing open files, tabs, LSP, and DataStore settings.
- `EditorContract.kt` - `OpenFile` state and UI events.
- `SoraLanguageProvider.kt` - Central registry for TextMate, Monarch, and TreeSitter grammars and themes.
- `SoraEditorAppearance.kt` - Applies DataStore preferences to Sora Editor.
- `EditorRoute.kt` - Compose navigation entry point.
- `SoraEditorTextActionWindow.kt` - Custom text selection menu.
- `ImageFilePreview.kt` - Image file viewer.

### 2.2 Redundant / Legacy Files Identified
- `CodeForgeLanguage.kt`: Wrapper class that broke `instanceof` checks. Removed in favor of direct language delegation.
- `SoraEditorHost.kt`: Redundant host composable superseded by `EditorScreen.kt`.
- `EditorSearchPanel.kt`: Standalone search panel superseded by integrated search bar in `EditorScreen.kt`.
- `EditorAssetRegistry.kt`: Superseded by `AssetsFileResolver` in `SoraLanguageProvider.kt`.
- `EditorDiagnosticsAdapter.kt`: Legacy diagnostic adapter superseded by direct LSP diagnostic state flow in `EditorViewModel.kt`.

---

## 3. Schritt 3: Analyse der `core/*` Module

### 3.1 `:core:datastore`
- Centralized DataStore settings repository (`SettingsRepository.kt`, `settings.proto`).
- **Cleanup:** Standardized default workspace directory fallback to `/storage/emulated/0/CodeForgeMobileProjects`.

### 3.2 `:core:designsystem`
- Holds standard Material 3 theme (`Theme.kt`), color palettes (`ThemePresets.kt`), and icons (`Icons.kt`).
- Clean separation from feature modules.

### 3.3 `:core:ui`
- UI helper components (`CenteredLoadingIndicator.kt`).

### 3.4 `:core:domain` & `:core:data`
- Clean Architecture design separating domain contracts (`FileSystemRepository`, `GitRepository`, `ComposeSourceAnalyzer`, `TemplateEngineRepository`, `RecentProjectsRepository`, `SdkRepository`) from Data implementations (`core/data/repository/*Impl.kt`).

---

## 4. Verification
- **Gradle Command:** `./gradlew assembleDebug`
- **Output:** `app/build/outputs/apk/debug/app-debug.apk`
