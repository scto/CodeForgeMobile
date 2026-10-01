# CodeForgeMobile - FileTree, Indentation Lines, Mipmap & Auto-Bracket Guide

## Overview
This document describes the architectural changes and bug fixes implemented in **CodeForgeMobile**:
1. **XML Vector & Mipmap Image Preview in Editor**: Support for Android Mipmap resources, VectorDrawables (`.xml`), Adaptive Icons (`ic_launcher.xml`), PNGs, WEBPs, JPGs, GIFs, ICOs, and SVGs inside [ImageFilePreview.kt](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/feature/editor/src/main/kotlin/com/codeforge/feature/editor/ui/ImageFilePreview.kt). Includes `XmlPullParser` fallback for plain XML files on disk.
2. **Automatic Bracket & Symbol Pair Closing**: SoraEditor auto-closing brackets (`()`, `[]`, `{}`, `""`, `''`, `` ``) enabled by default in [SoraEditorAppearance.kt](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/feature/editor/src/main/kotlin/com/codeforge/feature/editor/SoraEditorAppearance.kt).
3. **Theme-Adaptive FileTree Indentation Lines**: Universal hierarchy guide lines drawn across parent levels (`1..level`) for both files (`Leaf`) and folders (`Branch`) in [CompactFileSystemTree.kt](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/feature/filetree/src/main/kotlin/com/codeforge/feature/filetree/CompactFileSystemTree.kt) using unclipped canvas `drawBehind` modifiers.
4. **Instant FileTree Refresh on Settings Change**: Reactive Compose `key(...)` scope binding around tree creation in [FileTreeDrawer.kt](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/feature/filetree/src/main/kotlin/com/codeforge/feature/filetree/FileTreeDrawer.kt), ensuring immediate tree re-composition whenever settings are changed in the settings dialog or toolbar.
5. **File Details in FileTree (Size & Last Modified Date)**: Real-time inline display of formatted file sizes (B, KB, MB, GB) and modification timestamps (`dd.MM.yy HH:mm`) next to filenames.
6. **Android Studio View Modes & 1-Tap Switcher**: Full parity with Android Studio view modes (**Modul-Ansicht**, **Projekt-Ansicht**, **Datei-Ansicht**).

---

## 1. Mipmap & XML Drawable Rendering (`ImageFilePreview.kt`)

### Problem
Standard `Drawable.createFromPath(path)` returns `null` for uncompiled plain-text Android XML resources on disk (such as `mipmap-anydpi-v26/ic_launcher.xml` or `drawable/ic_launcher_background.xml`).

### Solution
- **`XmlPullParser` Fallback**: Added `Xml.newPullParser()` to parse plain-text XML streams directly from disk into `Drawable.createFromXml(resources, parser)`.
- **Extended Path Matching**: `isImageFilePath` matches paths containing `mipmap`, `drawable`, `ic_launcher`, or `res/` for extensions `.xml`, `.png`, `.webp`, etc.

---

## 2. Automatic Bracket & Symbol Pair Closing (`SoraEditorAppearance.kt`)

### Problem
Proto defaults for `auto_close_brackets` and `symbol_pair_completion_enabled` evaluated to `false`, leaving SoraEditor's `symbolPairAutoCompletion` disabled on initial startup.

### Solution
- Enabled `editor.props.symbolPairAutoCompletion = true` and `editor.props.autoIndent = true` by default in [SoraEditorAppearance.kt](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/feature/editor/src/main/kotlin/com/codeforge/feature/editor/SoraEditorAppearance.kt).
- Automatically pairs `()`, `[]`, `{}`, `""`, `''`, `` `` as the user types.

---

## 3. Universal FileTree Indentation Guide Lines (`CompactFileSystemTree.kt`)

### Problem
- `BonsaiStyle.toggleIcon` is `null` for `Leaf` nodes (files), meaning file rows drew no guide lines.
- `Bonsai` layout `Row` clipped negative X drawing coordinates.

### Solution
- Applied `Modifier.drawBehind` onto a `Box` wrapper around `customIcon` for **both** `Leaf` (files) and `Branch` (folders).
- Exact mathematical formula for guide line offset:
  `lineX = (i - level) * indentPx - (indentPx / 2f)`
  where `1 <= i <= level`.

---

## 4. Reactive Tree Refresh on Settings Changes (`FileTreeDrawer.kt`)

### Problem
`CompactFileSystemTree(...)` was previously instantiated outside the Compose `key(...)` block, keeping the old `tree` instance cached when settings changed.

### Solution
- Wrapped `CompactFileSystemTree` and `Bonsai` inside `key(activeFileTreeConfig, refreshTrigger, uiScale, showIndentLines, showFileDetails, isCompactMode)`.
- Changing any setting disposes the old tree and creates a fresh tree with the updated parameters immediately.

---

## Summary of Modified Files
- `feature/editor/src/main/kotlin/com/codeforge/feature/editor/ui/ImageFilePreview.kt`: Added `XmlPullParser` fallback for XML mipmaps & drawables; expanded `isImageFilePath`.
- `feature/editor/src/main/kotlin/com/codeforge/feature/editor/SoraEditorAppearance.kt`: Set `symbolPairAutoCompletion = true` and `autoIndent = true`.
- `feature/filetree/src/main/kotlin/com/codeforge/feature/filetree/CompactFileSystemTree.kt`: Added `Box` icon wrappers with `drawBehind` guide lines for all nodes.
- `feature/filetree/src/main/kotlin/com/codeforge/feature/filetree/FileTreeDrawer.kt`: Wrapped tree creation inside reactive `key(...)` block for instant settings refresh.
- `docs/filetree_and_mipmap_features_guide.md`: Updated documentation.
