# SoraEditor Code-Formatierung & Mipmap/VectorDrawable Renderer Korrektur

## 1. Übersicht
In diesem Update wurden zwei wesentliche Probleme im Editor (`:feature:editor`) behoben:
1. **Code-Formatierung im SoraEditor**: Wenn kein aktiver LSP (Language Server Protocol) verbunden war oder das Formformatieren fehlschlug, passierte beim Auslösen der Formatierungsaktion bisher nichts. Nun greift eine automatische Fallback-Steuerung auf das integrierte `CodeFormatter`-Modul.
2. **Mipmap- & VectorDrawable-Vorschau**: Mipmap-Dateien (`ic_launcher.xml`, `ic_launcher_round.xml`, Adaptive Icons) und Android VectorDrawables (`<vector>`) in `res/mipmap-*` und `res/drawable-*` wurden vorher teilweise von der Vorschau ausgeschlossen oder lieferten aufgrund fehlender XML-Attribute Parsing-Fehler.

---

## 2. Technische Umsetzung & Änderungen

### A. Code-Formatierung Fallback (`EditorViewModel.kt`)
- **Problem**: `formatViaLsp()` verließ sich ausschließlich auf `lspClient.requestFormat(...)`. Ohne laufenden Sprachserver oder bei Fehlern lieferte die Methode kein Ergebnis zurück.
- **Lösung**:
  - `formatViaLsp()` prüft nun zunächst das Ergebnis von LSP Format.
  - Wenn kein LSP verfügbar ist oder der Inhalt unverändert bleibt, wird `CodeFormatter.format(active.content, ext)` aufgerufen.
  - `CodeFormatter` verarbeitet JSON, XML, HTML sowie Einrückungen für Kotlin, Java, Python, C++, JS, TS etc.

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

### B. Mipmap & XML VectorDrawable Inflation (`ImageFilePreview.kt`)
- **Problem 1**: Layout- und Manifest-XMLs wurden fälschlicherweise als Bilddateien erkannt und ersetzten den SoraCodeEditor.
- **Problem 2**: Adaptive Icons (`<adaptive-icon>`) und VectorDrawables (`<vector>`) in `mipmap`- bzw. `drawable`-Ordnern konnten über Standard-`Drawable.createFromXml` nicht direkt von Plaintext-XML-Dateien geladen werden.
- **Lösung**:
  - `isImageFilePath` filtert XML-Dateien nun strikt: Nur XMLs mit `<vector` oder `<adaptive-icon` in `mipmap`- oder `drawable`-Ordnern lösen die Bild-Vorschau aus. Alle anderen XMLs (Layouts, Strings, Styles, Manifests) öffnen wie gewohnt im `SoraCodeEditor`.
  - `loadXmlDrawable`: Lädt `<vector>` XMLs unter Nutzung von `VectorDrawableCompat.createFromXmlInner(...)` und `Xml.asAttributeSet(...)`.
  - `loadAdaptiveIcon`: Extrahiert `<background>` und `<foreground>` Referenzen aus `<adaptive-icon>`, sucht die zugehörigen Vektor- oder Bitmap-Dateien in den übergeordneten `res/`-Ordnern und führt sie in einem `LayerDrawable` zusammen.

```kotlin
// In ImageFilePreview.kt:
private fun loadXmlDrawable(file: File, context: Context): Drawable? {
    val text = file.readText()
    if (text.contains("<adaptive-icon")) {
        loadAdaptiveIcon(file, text, context)
    } else {
        file.inputStream().use { stream ->
            val parser = Xml.newPullParser()
            parser.setInput(stream, "UTF-8")
            // Start tag navigation & AttributeSet conversion...
            VectorDrawableCompat.createFromXmlInner(context.resources, parser, attrs, context.theme)
        }
    }
}
```

---

## 3. Ergebnis & Verifizierung
- **Formatierung**: Die Formatierungsfunktion funktioniert nun in jedem Projekt und für jede unterstützte Datei (JSON, XML, HTML, Kotlin, Java etc.) auch ohne gestarteten LSP-Server.
- **Mipmap & Drawables**: Adaptive App-Icons (`ic_launcher.xml`) sowie Vektor-Drawables werden mit vollem Layer-Blending und Dimensionen im Editor dargestellt, während reine Quellcode-XMLs regulär im Editor editiert werden können.
