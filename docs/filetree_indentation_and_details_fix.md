# FileTree Hierarchie-Einrückung & Dateidetails Korrektur

## 1. Übersicht
In dieser Korrektur wurden zwei wesentliche Darstellungsprobleme im Dateibaum (`FileTreeDrawer` / `CompactFileSystemTree`) behoben:
1. **Einrückungslinien (`|||` Stacking-Problem)**: Die vertikalen Verbindungslinien wurden durch eine zu schmale Einrückungsbreite (14.dp) eng gegeneinander gedrückt und wirkten wie aufeinander gestapelte Striche (`|||`). Zudem fehlte bei ausgeschalteten Einrückungslinien die Einrückung für tiefere Hierarchieebenen.
2. **Fehlende Dateidetails (Größe & Änderungsdatum)**: Durch uninitialisierte Protobuf-Standardwerte in `FileTreeConfig` startete `showFileDetails` standardmäßig als `false`, wodurch weder Dateigröße noch Änderungsdatum unter den Datei-/Ordnernamen angezeigt wurden.

---

## 2. Technische Umsetzung & Änderungen

### A. Hierarchie-Einrückung (`CompactFileSystemTree.kt`)
- **Einrückungsbreite**: Erhöhung der Einrückungsbreite von `14.dp * uiScale` auf eine zentrierte, professionelle IDE-Standardbreite von `20.dp * uiScale`.
- **Hierarchie-Abstand**: Die Einrückungs-`Box` wird nun für jede Hierarchieebene (`level > 0`) gerendert, unabhängig davon, ob `showIndentLines` aktiv ist. Dadurch bleiben Unterordner und Dateien stets sauber eingerückt.
- **Linienstärke & Transparenz**:
  - `strokeWidth = 1.dp.toPx()` (vorher 1.5dp)
  - `alpha = 0.35f` für ein dezentes, unaufdringliches Erscheinungsbild, das sich an das Material Theme anpasst.

```kotlin
// In CompactFileSystemTree.kt:
if (level > 0) {
    val indentWidth = 20.dp * uiScale
    for (i in 0 until level) {
        Box(
            modifier = Modifier
                .width(indentWidth)
                .fillMaxHeight()
                .drawBehind {
                    if (showIndentLines) {
                        val lineX = size.width / 2f
                        drawLine(
                            color = lineColor,
                            start = Offset(lineX, 0f),
                            end = Offset(lineX, size.height),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                }
        )
    }
}
```

---

### B. Anzeige von Dateidetails & Zeilenhöhe (`CompactFileSystemTree.kt` & `FileTreeDrawer.kt`)
- **Standardzustand**: `showFileDetails` und `showIndentLines` werden in `FileTreeDrawer.kt` standardmäßig auf `true` gesetzt, wenn kein gespeichertes Protobuf-Config vorliegt.
- **Formatisierung & Fallbacks**:
  - Für Dateien: Anzeigestruktur `<Größe> • <Änderungsdatum>` (z.B. `4.2 KB • 25.09.26 20:28`).
  - Für Ordner: Anzeigestruktur `Ordner • <Änderungsdatum>` (z.B. `Ordner • 25.09.26 20:28`).
  - Nutzt `java.io.File` als Fallback für `length()` und `lastModified()`, falls Okio-Metadaten `null` zurückgeben.
- **Dynamische Zeilenhöhe**:
  - `nodeIconSize = if (showFileDetails) 38.dp * uiScale else 24.dp * uiScale`
  - Verhindert Textabschneidungen und stellt zweizeilige Node-Einträge ohne Cropping dar.

```kotlin
// In CompactFileSystemTree.kt (nameComposable):
if (showFileDetails) {
    val javaFile = remember(path.toString()) { java.io.File(path.toString()) }
    val sizeText = if (!metadata.isDirectory) {
        val size = metadata.size ?: javaFile.length()
        formatFileSize(size)
    } else {
        "Ordner"
    }
    val lastMod = metadata.lastModifiedAtMillis ?: javaFile.lastModified()
    val dateText = formatLastModified(lastMod)
    val details = listOfNotNull(
        sizeText.takeIf { it.isNotBlank() },
        dateText.takeIf { it.isNotBlank() }
    ).joinToString(" • ")
    if (details.isNotBlank()) {
        Text(
            text = details,
            fontSize = 9.5.sp * uiScale,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
```

---

## 3. Ergebnis & Verifizierung
- Die Hierarchie-Linien im Dateibaum verlaufen nun sauber zentriert mit 20dp Abstand pro Ebene.
- Dateien und Ordner zeigen die Metadaten-Zeile (Größe und Datum) übersichtlich unterhalb des Titels mit Schriftgröße 9.5sp an.
- Die Zeilenhöhe passt sich dynamisch an, sodass kein Text im Dateibaum abgeschnitten wird.
