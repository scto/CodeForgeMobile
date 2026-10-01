# CodeForgeMobile - Editor Themes, Inline Color Preview & Mipmap Support Guide

## Overview
This document details the implementation of three major editor UX enhancements in **CodeForgeMobile**:
1. **Editor Themes Section with Visual Live Previews & Menu Integration**: Theme selection in `EditorSettingsScreen` featuring code preview boxes, plus theme switcher with visual badges in the editor top bar.
2. **Mipmap & Image File Preview**: Dedicated image file viewer in the editor for mipmaps, drawables, PNG, JPG, WEBP, GIF, ICO, and SVG files.
3. **Inline Visual Color Specification Previews**: Real-time visual color indicators rendered in code lines next to any color format (`#ffff00`, `#ff0`, `#ffff00ff`, `0xffff00`, `ffff00`, `ff0`, etc.).

---

## 1. Theme Section with Visual Previews (`EditorSettingsScreen.kt`)

### Implementation Details
In [EditorSettingsScreen.kt](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/feature/settings/src/main/kotlin/com/codeforge/feature/settings/editor/EditorSettingsScreen.kt):
- Added section **Editor Themes & Farbschemata**.
- Supported themes:
  - **Darcula** (Dark)
  - **Quiet Light** (Light)
  - **Ayu Dark** (Dark)
  - **Solarized Dark** (Dark)
  - **GitHub Light** (Light)
  - **VS Code Dark+** (Dark)
- Each theme is rendered as an `EditorThemePreviewCard` displaying:
  - Theme title and mode description (Dark / Light)
  - Live visual code preview box showing line numbers (`1`, `2`, `3`) and syntax-highlighted code (`fun main() { val theme = "..." }`) matching the exact theme colors.
- Selecting a theme updates `EditorConfig.textmateTheme` in DataStore.

### Editor Overflow Menu Integration (`EditorScreen.kt`)
In [EditorScreen.kt](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/feature/editor/src/main/kotlin/com/codeforge/feature/editor/EditorScreen.kt):
- Under **"Farbschema wechseln"**, a dialog presents all available themes alongside a visual color badge box.
- Clicking a theme applies it immediately to the active `CodeEditor` and persists the preference.

---

## 2. Mipmap & Image File Preview (`ImageFilePreview.kt`)

### Implementation Details
- **Component**: [ImageFilePreview.kt](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/feature/editor/src/main/kotlin/com/codeforge/feature/editor/ui/ImageFilePreview.kt)
- **Detection**: Files with extensions `.png`, `.jpg`, `.jpeg`, `.webp`, `.gif`, `.ico`, `.bmp`, `.svg` or located in `/mipmap-*` / `/drawable-*` directories (`isImageFilePath(path)`).
- **Behavior**:
  - `EditorViewModel` opens image files without attempting binary text parsing.
  - `EditorScreen` renders `ImageFilePreview` instead of text `CodeEditor`.
- **UI Components**:
  - Checkerboard/dark background container suitable for transparent PNG icons.
  - Image metadata info bar displaying file name, full path, pixel dimensions (`W × H px`), and formatted file size (`KB` / `MB`).

---

## 3. Inline Color Specification Previews (`EditorColorDetector.kt`)

### Implementation Details
- **Detector**: [EditorColorDetector.kt](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/feature/editor/src/main/kotlin/com/codeforge/feature/editor/utils/EditorColorDetector.kt)
- **Supported Color Formats**:
  - `#RGB` (e.g. `#FF0`, `#f00`)
  - `#ARGB` (e.g. `#FFF0`, `#f00f`)
  - `#RRGGBB` (e.g. `#FFFF00`)
  - `#AARRGGBB` (e.g. `#FFFF00FF`)
  - `0xRRGGBB` / `0xAARRGGBB` (e.g. `0xFFFF00`, `0xFFFFFF00`)
  - Raw hex strings (e.g. `ffff00`, `ff0`)
- **Integration**:
  In [SoraCodeEditor.kt](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/feature/editor/src/main/kotlin/com/codeforge/feature/editor/SoraCodeEditor.kt):
  ```kotlin
  private fun updateColorInlayHints(editor: CodeEditor, textStr: String) {
      runCatching {
          val container = InlayHintsContainer()
          EditorColorDetector.detectColorInlayHints(textStr).forEach { hint ->
              container.add(hint)
          }
          editor.setInlayHints(container)
      }
  }
  ```
  `updateColorInlayHints` is triggered upon initial `setText()` as well as inside `subscribeAlways<ContentChangeEvent>`.
- **Rendering**: Sora-Editor's `ColorInlayHintRenderer` draws a filled color box directly next to color values in the code in real time.

