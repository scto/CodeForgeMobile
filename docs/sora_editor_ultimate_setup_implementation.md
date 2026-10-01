# Sora-Editor: Master Setup & Ultimate Feature Integration Guide

## 1. Übersicht
Dieses Dokument beschreibt die vollständige Integration des **Sora-Editor Master/Ultimate Setups** in CodeForge Mobile (`:feature:editor`). Sämtliche Editor-Komponenten, Gestensteuerungen, Navigationsmodi sowie die erweiterte Code-Formatierung und Mipmap-/VectorDrawable-Vorschau sind zentral in `SoraEditorAppearance.kt`, `EditorViewModel.kt` und `ImageFilePreview.kt` integriert.

---

## 2. Umgesetzte Ultimate Setup Features & Komponenten

### A. Gestensteuerung & PC-Navigationsmodus (`SoraEditorAppearance.kt`)
- **Pinch-to-Zoom (`props.canScaleText = true`)**: Ermöglicht das stufenlose Vergrößern und Verkleinern des Editor-Textes mittels Zwei-Finger-Pinch-Geste.
- **PC Navigation Mode (`props.navigationMode = CodeEditor.NAVIGATION_MODE_PC`)**:
  - Unterstützt erweiterte PC-Tastatur-Shortcuts (Strg+C, Strg+V, Strg+Z, Strg+Shift+Z, Strg+F, Strg+A).
  - Aktiviert Multi-Cursor Block-Auswahl und Mausrad-Scrolling.

```kotlin
// In SoraEditorAppearance.kt:
editor.props.canScaleText = true
editor.props.navigationMode = CodeEditor.NAVIGATION_MODE_PC
```

---

### B. Modulare Editor-Komponenten (`getComponent`)
Alle Systemkomponenten der Sora-Editor-Engine wurden dynamisch konfiguriert:

1. **EditorMinimap (`io.github.rosemoe.sora.widget.component.EditorMinimap`)**:
   - `minimap.isEnabled = config.showMinimap` (Standardmäßig aktiv)
   - `minimap.minWidth = 80`
   - Rendert eine Miniatur-Codeübersicht am rechten Bildschirmrand.
2. **EditorAutoCompletion**:
   - `isEnabled = true`
   - `isHideWhenNoMatch = true` (Blendet das Fenster automatisch aus, wenn keine Treffer vorliegen)
   - Einstellbare Pop-up-Animationen.
3. **Sticky Scroll (`StickyScroll`)**:
   - `isEnabled = config.stickyScroll`
   - Hält Klassen- und Methodenkoepfe beim Scrollen am oberen Rand fest.
4. **Farbige Klammernpaare (`BracketPairs`)**:
   - `isEnabled = true`
   - Zuordnende Klammern (`()`, `{}`, `[]`) werden farblich hervorgehoben.
5. **Touch-Lupe (`Magnifier`)**:
   - `isEnabled = config.magnifierEnabled`
   - Bietet eine Vergrößerungslupe beim Präzisions-Cursorsetzen per Touch.
6. **Inlay Hints & Diagnostics (`InlayHintManager` & `DiagnosticManager`)**:
   - `isEnabled = true`
   - Rendert Inline-Typen- und Parameter-Hinweise sowie rote/gelbe Fehlerwellenlinien bei LSP-Meldungen.

```kotlin
// In SoraEditorAppearance.kt (Abschnitt 10):
runCatching {
    editor.getComponent(Magnifier::class.java)?.isEnabled = config.magnifierEnabled
    editor.getComponent(EditorAutoCompletion::class.java)?.apply {
        isEnabled = true
        isHideWhenNoMatch = true
        setEnabledAnimation(config.completionAnimEnabled)
    }
    runCatching {
        editor.getComponent(io.github.rosemoe.sora.widget.component.StickyScroll::class.java)?.isEnabled = config.stickyScroll
    }
    runCatching {
        editor.getComponent(io.github.rosemoe.sora.widget.component.BracketPairs::class.java)?.isEnabled = true
    }
    val minimap = editor.getComponent(io.github.rosemoe.sora.widget.component.EditorMinimap::class.java)
    if (minimap != null) {
        val isMinimapEnabled = if (config.fontSize == 0 && !config.showMinimap) true else config.showMinimap
        minimap.isEnabled = isMinimapEnabled
        minimap.minWidth = 80
    }
}
```

---

## 3. Code-Formatierung Fallback (`EditorViewModel.kt`)
Um sicherzustellen, dass die Formatierung (Button / Menü) auch ohne aktiven Language Server funktioniert:
- `formatViaLsp()` fordert zunächst ein LSP-Format an.
- Schlägt LSP fehl oder ist nicht verbunden, greift automatisch das integrierte `CodeFormatter`-Modul für JSON, XML, HTML, Kotlin, Java, Python, C++, JS, TS etc.

```kotlin
// In EditorViewModel.kt:
var formattedContent: String? = null
runCatching {
    val result = lspClient.requestFormat(active.path, active.content)
    if (result.isSuccess) {
        formattedContent = result.getOrNull()
    }
}

if (formattedContent.isNullOrBlank() || formattedContent == active.content) {
    formattedContent = CodeFormatter.format(active.content, ext)
}

if (formattedContent != active.content && !formattedContent.isNullOrBlank()) {
    updateBuffer(formattedContent)
    _effect.emit(EditorUiEffect.ShowSnackbar("Formatierung angewendet"))
}
```

---

## 4. Mipmap & VectorDrawable Renderer (`ImageFilePreview.kt`)
- **Strikte Dateierkennung (`isImageFilePath`)**: Nur Binärbilder (PNG, WEBP, JPG, GIF, SVG) und XMLs mit `<vector` oder `<adaptive-icon` in `mipmap`- bzw. `drawable`-Ordnern schalten in den Vorschau-Modus.
- **VectorDrawableInflation**: `<vector>` Plaintext-XMLs werden via `VectorDrawableCompat.createFromXmlInner(...)` und `Xml.asAttributeSet(...)` verarbeitet.
- **Adaptive Icons (`<adaptive-icon>`)**: Extrahiert Hintergrund- und Vordergrund-Layer aus XML-Definitionen und kombiniert sie zu einem `LayerDrawable`.

---

## 5. Zusammenfassung der aktivierten Features
| Feature | status | Beschreibung |
|---|---|---|
| **Pinch-to-Zoom** | ✅ Aktiv | Stufenlose Skalierung der Schriftgröße via Geste |
| **PC Navigation Mode** | ✅ Aktiv | Tastatur-Shortcuts, Mausrad-Scrolling & Multi-Cursor |
| **Editor Minimap** | ✅ Aktiv | Mini-Codeübersicht am rechten Rand (konfigurierbar) |
| **Sticky Scroll** | ✅ Aktiv | Klassen/Methoden-Köpfe oben anheften |
| **Rainbow Bracket Pairs**| ✅ Aktiv | Farbliche Markierung zusammengehöriger Klammern |
| **Lupe (Magnifier)** | ✅ Aktiv | Touch-Lupe zur exakten Positionierung des Cursors |
| **Formatierung Fallback** | ✅ Aktiv | LSP-Format mit automatischem CodeFormatter-Fallback |
| **Mipmap / Vector Preview** | ✅ Aktiv | Rendert Android Adaptive Icons & VectorDrawables |
