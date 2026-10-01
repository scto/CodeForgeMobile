# FileTree, Theme Presets, Editor Scrollbars & Extensions Settings Guide

## Overview

This document details the architecture, UI changes, and feature enhancements implemented across **CodeForge Mobile**:

1. **FileTree Settings Parity (`FileTreeSettingsScreen.kt`)**
   - Expanded settings controls mirroring `FileTreeToolbar.kt`: Font size slider with live preview, indent lines toggle, file details toggle, hidden files toggle, compact mode toggle, view mode selector, sort order & sort by options.

2. **Theme Presets & Color Picker (`ThemeBuilderScreen.kt`)**
   - Added 15 pre-configured UI/Editor/FileTree themes in `ThemePresets.kt` (Monokai, Darcula, Quiet Light, Ayu Dark, Solarized Dark, GitHub Light, VS Code Dark+, Notepad++, Eclipse, Cyberpunk Neon, Forest, Ocean, Sunset, Monochrome, Violet).
   - Horizontal scrollable `LazyRow` preview row for fast visual theme selection.
   - Interactive RGB `ColorPickerDialog` with 10 custom preset color swatches.

3. **FileTree Visual & Interaction Enhancements (`CompactFileSystemTree.kt` & `FileTreeDrawer.kt`)**
   - **High-contrast Theme Adaptive Indentation Lines**: Vertical guidelines rendered from `0f` to `size.height` in each node row, ensuring seamless continuous guidelines that adapt dynamically to `MaterialTheme.colorScheme.outline` without getting clipped by Bonsai node boundaries.
   - **2-Line File Info Formatting**: File size and last modified date rendered directly underneath the file/folder name in a smaller font size (`10.sp * uiScale`).
   - **Full Row Clickability**: Expanded node container width to `fillMaxWidth()` so the entire row responds to tap gestures.
   - **Context Menu Actions & Dialogs**:
     - **Properties (Eigenschaften)**: Displays file name, full path, type (file/folder), formatted size, last modified date, and permission flags (R/W/X/Hidden).
     - **Delete (Löschen)**: Prompts with a confirmation dialog before executing recursive deletion.

4. **Editor Scrollbars Enforcement (`SoraEditorAppearance.kt`)**
   - Explicitly bound `editor.isVerticalScrollBarEnabled` and `editor.isHorizontalScrollBarEnabled` to `config.scrollbarEnabled`.

5. **Extensions Settings Screen (`ExtensionsScreen.kt` & `ExtensionsManager.kt`)**
   - New settings category in `SettingsHubScreen.kt`: **Plugins & Erweiterungen (Extensions)**.
   - Download, verification (SHA-256), installation, and removal of LSP language servers and tooling packages.
   - Route `Routes.EXTENSIONS` integrated into `CodeForgeNavHost.kt`.
