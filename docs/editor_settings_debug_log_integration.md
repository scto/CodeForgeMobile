# Integration of Debug Log Editor Settings in CodeForge Mobile

## Overview
This document describes the mapping and integration of the 27 editor configuration settings extracted from `codeforge_debug.log` into the Sora `CodeEditor` through [SoraEditorAppearance.kt](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/feature/editor/src/main/kotlin/com/codeforge/feature/editor/SoraEditorAppearance.kt).

## Implemented Editor Settings

The following 27 settings from `codeforge_debug.log` are now dynamically mapped from `EditorConfig` proto to `CodeEditor`:

1. **Hover Info (`hoverInfoEnabled`)**: Enables hover tooltips and LSP info.
2. **Inlay Hints (`inlayHintsEnabled`)**: Enables inline parameter and type hints in the editor.
3. **Magnifier / Lupe (`magnifierEnabled`)**: Magnifier component state (`editor.getComponent(Magnifier::class.java)?.isEnabled`).
4. **Schriftart-Ligaturen (`ligatureEnabled`)**: Enables font ligatures (`editor.isLigatureEnabled`).
5. **Cursor-Animation (`cursorAnimation`)**: Smooth cursor movement animation (`editor.isCursorAnimationEnabled`).
6. **Signatur-Hilfe (`signatureHelpEnabled`)**: Triggers LSP signature help popups.
7. **Symbol-Leiste (`symbolBarVisible`)**: Toggles the horizontal quick symbol bar above the soft keyboard.
8. **Zeilenumbruch / Word Wrap (`wordWrap`)**: Automatic soft wrapping of long lines (`editor.isWordwrap`).
9. **Vervollständigungs-Animation (`completionAnimEnabled`)**: Smooth auto-completion popup animation (`editor.getComponent(EditorAutoCompletion::class.java)?.setEnabledAnimation`).
10. **Zeilennummern anzeigen (`showLineNumbers`)**: Shows line numbers in the margin (`editor.isLineNumberEnabled`).
11. **Zeilennummern anheften (`pinLineNumbers`)**: Keeps line numbers fixed when scrolling horizontally (`editor.setPinLineNumber`).
12. **Minimap anzeigen (`showMinimap`)**: Code overview minimap display.
13. **Aktuelle Zeile hervorheben (`highlightCurrentLineEnabled`)**: Highlights the active line background (`editor.isHighlightCurrentLine`).
14. **Aktuellen Block hervorheben (`highlightCurrentBlockEnabled`)**: Highlights active code block scopes (`editor.setBlockLineEnabled`).
15. **Sticky Textauswahl (`stickyTextSelectionEnabled`)**: Sticky text selection mode (`editor.props.stickyTextSelection`).
16. **Scrollbalken anzeigen (`scrollbarEnabled`)**: Toggles horizontal and vertical scrollbars (`editor.isVerticalScrollBarEnabled`, `editor.isHorizontalScrollBarEnabled`).
17. **Erste Zeilennummer immer sichtbar (`firstLineNumberAlwaysVisible`)**: Keeps line 1 visible even when scrolled down (`editor.isFirstLineNumberAlwaysVisible`).
18. **Bidi-Richtungsindikator (`bidiIndicatorEnabled`)**: Enables bidirectional text direction indicators (`editor.props.bidiDirectionIndicator`).
19. **Abgerundeter Text-Hintergrund (`roundTextBackgroundEnabled`)**: Draws text background selections with rounded corners (`editor.props.enableRoundTextBackground`).
20. **Side Block Line (`sideBlockLineEnabled`)**: Draws vertical scope guide lines (`editor.props.drawSideBlockLine`).
21. **Sticky Scroll (`stickyScroll`)**: Pins the current enclosing code function/class header to top of editor (`editor.props.stickyScroll`).
22. **Scroll Fling (`scrollFlingEnabled`)**: Enables smooth fling scrolling momentum (`editor.isScrollFlingEnabled`).
23. **Overscroll Effekt (`overscrollEnabled`)**: Enables elastic overscroll at boundary limits (`editor.isOverScrollEnabled`).
24. **Auto Indent (`autoIndentEnabled`)**: Auto indents new lines based on context (`editor.props.autoIndent`).
25. **Mehrfache Leerzeichen löschen (`deleteMultiSpacesEnabled`)**: Deletes full tab stops on backspace (`editor.props.deleteMultiSpaces`).
26. **Klammern-Hervorhebung (`bracketHighlightEnabled`)**: Highlights matching bracket pairs (`editor.props.highlightMatchingDelimiters`).
27. **Fette Klammern-Hervorhebung (`boldMatchingBracketsEnabled`)**: Bold formatting for matching delimiters (`editor.props.boldMatchingDelimiters`).

## Architecture & Code Changes
- Updates applied in `SoraEditorAppearance.kt` safely handle all properties using `runCatching` blocks to prevent crashes across different versions of the underlying Sora Editor library.
- Settings are persisted in proto DataStore via `EditorConfig` and re-applied immediately whenever configuration state updates.
