# Layout-Designer (`:feature:layoutdesigner`)

Stand: Oktober 2026 (Task 41 abgeschlossen). Visueller Editor für **Android-View-Layout-XML** (`res/layout*/*.xml`) mit Gestensteuerung, Drag & Drop, Zoom/Pan, echter ConstraintLayout- und RelativeLayout-Darstellung, Ressourcen-Auflösung, Linter mit Schnellkorrekturen und Zwei-Wege-Synchronisation mit dem Code-Editor.

---

## 1. Bedienung & Benutzeroberfläche

### Navigation & Einstieg
- **Drawer → Bereich „Layouts“:** Listet alle Layout-Dateien des Projekts gruppiert nach Varianten.
- **`+` (Neue Layout-Datei anlegen):** Dialog mit Namensprüfung (`a-z0-9_`), Zielordner-Auswahl (`res/layout*`) und 5 sofort einsatzbereiten, lint-sauberen Vorlagen (*Leeres ConstraintLayout*, *Leeres LinearLayout*, *Listen-Eintrag*, *Formular*, *Scrollbare Detailseite*).
- **Routen-Integration:** `Routes.layoutDesigner(path)` öffnet den visuellen Designer.

### Ansichten & Tabs
- **Tabs im Compact-Modus (< 600 dp):**
  - **Vorschau:** Interaktive Arbeitsfläche mit Canvas, Gesten, Größengriffen und Constraint-Ankern.
  - **Struktur:** Hierarchischer DOM-Baum mit Einrückung, Typ-Icons, IDs, Linter-Badges und Reorder-Handles.
  - **Eigenschaften:** Gruppiertes Attribut-Panel (*Häufig*, *Layout/Maße*, *Text/Typografie*, *Erscheinungsbild*, *Barrierefreiheit*, *Weitere Attribute*).
  - **Probleme (Lint):** Echtzeit-Codeanalyse mit Schnellkorrekturen (*QuickFix*) und Filter-Chips (*Fehler*, *Warnungen*, *Hinweise*).
  - **XML-Editor:** Direkte XML-Bearbeitung mit Syntax-Hervorhebung (Tags, Attribute, Werte, Kommentare), Tag-Range-Synchronisation und Vorab-Syntaxprüfung.
- **Two-Pane-Modus (≥ 600 dp / Tablets / DeX):** Leinwand links permanent sichtbar, rechte Spalte mit Tabs für Struktur, Eigenschaften, Probleme und XML.

### Gesten-Übersicht

| Geste | Zielbereich | Auswirkung / Aktion |
|---|---|---|
| **Antippen (Tap)** | Leinwand-Element / Baum | Wählt das Element primär aus. Zweites Antippen wählt das Elternelement. |
| **Langer Druck + Ziehen (Long Press Drag)** | Palette-Widget | Startet Drag & Drop eines neuen Widgets mit blauem Drop-Indikator auf der Leinwand. |
| **Langer Druck + Ziehen (Long Press Drag)** | Leinwand-Element | Verschiebt ein bestehendes Element auf der Leinwand oder in einen anderen Container. |
| **Ziehen am Reorder-Handle** | Strukturbaum | Ordnet Elemente hierarchisch um oder verschiebt sie in andere Container. |
| **Zwei-Finger-Pinch (Pinch to Zoom)** | Leinwand | Stufenloses Hinein- und Herauszoomen (0.25× bis 4.0×). |
| **Zwei-Finger-Verschieben (Pan)** | Leinwand | Verschiebt den sichtbaren Arbeitsbereich flüssig in alle Richtungen. |
| **Ziehen an Größengriffen (Resize)** | Leinwand-Auswahl | Ändert `layout_width` und `layout_height` des gewählten Elements. |
| **Ziehen an Kreis-Ankern (Constraints)** | Constraint-Auswahl | Erstellt oder verbindet Constraints magnetisch mit Nachbarelementen oder Elternrändern. |

### Tastatur-Kürzel (Hardware-Tastatur / DeX)
- `Strg + Z` / `Strg + Y`: Rückgängig / Wiederholen (Undo-Stack mit 100 Schritten und Coalescing aufeinanderfolgender Textänderungen).
- `Strg + C` / `Strg + X` / `Strg + V`: Kopieren, Ausschneiden und Einfügen (mit automatischer Kollisionsbereinigung bei `@+id/`).
- `Strg + D`: Gewähltes Element duplizieren.
- `Entf` / `Backspace`: Gewählte Elemente löschen (inklusive Bereinigung abhängiger Constraints).
- `Pfeiltasten`: Verschieben um 1 dp (`Shift + Pfeiltasten`: 8 dp Rasterschritt).

---

## 2. Architektur & Paketaufbau

| Paket | Klassen & Komponenten | Verantwortung |
|---|---|---|
| `model` | `LayoutNode`, `LayoutDocument`, `LayoutOps`, `WidgetCatalog`, `Units`, `LayoutTemplates`, `LayoutClipboard` | Unveränderliches DOM, Baum-Operationen, Widget-Definitionen, Vorlagen und Maßeinheiten. |
| `xml` | `LayoutXml`, `LayoutParseException` | DOM-Parser und Generator unter vollständigem Erhalt von Kommentaren, CRLF/LF-Zeilenenden, Einrückung, benutzerdefinierten Namespaces und XML-Entities. |
| `dnd` | `DragController`, `DropResolver`, `NodeBoundsRegistry`, `DropTarget`, `DropVerdict` | Plattformunabhängige Gestenerkennung, Koordinatentransformation und treffsichere Einfüge- und Verdrängungs-Berechnung. |
| `preview` | `LayoutPreview`, `ConstraintLayoutPreview`, `RelativeLayoutPreview`, `PreviewContext` | Echte Darstellung von ConstraintLayout (inkl. Guidelines/Barriers), RelativeLayout (topologischer Solver), LinearLayout, FrameLayout und ScrollViews. |
| `res` | `ResourceRepository`, `IncludeResolver`, `SdkAttrsParser`, `ResourceConfig` | Auflösen von `@string/`, `@color/`, `@dimen/`, Themes/Styles, VectorDrawables, `include`, `merge`, `ViewStub` und SDK-Attributen. |
| `lint` | `LayoutLinter`, `LayoutIssue`, `QuickFix`, `LintRules` | Statische Prüfung auf fehlende Constraints, RTL-Verletzungen, harte Texte, ungültige Einheiten, Zyklen und Barrierefreiheit. |
| `ui` | `DesignerPanels`, `XmlVisualTransformation` | Compose-Panels für Palette, Strukturbaum, Eigenschaften, Linter und formatierter XML-Codeeditor. |
| `files` | `LayoutScanner`, `LayoutFilesViewModel`, `LayoutFilesPanel`, `LayoutFileStore` | Scannen von Layoutdateien, Varianten-Handling (`layout-land`, `layout-sw600dp`) und atomare Datei-I/O (`.tmp` + rename). |
| Root | `LayoutDesignerScreen`, `LayoutDesignerViewModel`, `LayoutDesignerContract` | MVI-Architektur, Event-Handling, Undo/Redo, Zwei-Wege-Sync (`FileSyncBridge`) und Prozess-Tod-Wiederherstellung (`SavedStateHandle`). |

---

## 3. Technische Schlüsselentscheidungen

### Gestenerkennung & Drag & Drop
- **Eigene Geste (`DragController` & `NodeBoundsRegistry`):** Statt fehleranfälliger Drittanbieter-Bibliotheken verwendet der Layout-Designer einen reinen Kotlin-Gestencontroller in Kombination mit `Modifier.onGloballyPositioned` und `detectDragGesturesAfterLongPress`.
- **Reiner Drop-Resolver (`DropResolver`):** Pure JVM-Logik ohne Android-Abhängigkeiten berechnet Ankerzonen, Verdrängungspositionen in `LinearLayout`s (Drittel-Aufteilung), `layout_gravity` in `FrameLayout`s und relative Abstände in `ConstraintLayout`s.

### XML-Roundtrip & Formatierungserhalt
- **Erweiterter DOM-Parser (`LayoutXml`):**
  - Behält Kommentare vor dem Wurzelelement und zwischen Knoten byte-genau bei.
  - Erkennt Quell-Einrückung (2 Leerzeichen, 4 Leerzeichen, Tabs) und Zeilenenden (`\r\n` vs. `\n`).
  - Behandelt benutzerdefinierte Tags (`<com.example.MyCustomView>`), XML-Entities (`&amp;`, `&lt;`) und Unicode ohne Datenverlust.
  - Verhindert XXE-Angriffe durch Deaktivierung von Doctype-Deklarationen (`disallow-doctype-decl`).

### Echte Container-Darstellung
- **ConstraintLayout:** Nutzung von `androidx.constraintlayout.compose` mit dynamischem `ConstraintSet`. Guidelines (horizontal/vertikal, Prozent/dp) und Barrieren werden direkt berechnet.
- **RelativeLayout:** Eigener topologischer Sortierungs-Solver (`RelativeSolver`) ermittelt die korrekte Messreihenfolge abhängiger Knoten und fängt zyklische Abhängigkeiten ab.

---

## 4. Ehrliche Grenzen & Bekannte Einschränkungen

- **Keine native NDK/C++ Inflation:** Eigene Custom Views aus Drittbibliotheken, die auf NDK-Code oder systemspezifische Bytecode-Manipulation angewiesen sind, werden nicht im nativen Android-View-System instantiiert, sondern über den `CustomViewScanner` als informative, layout-konforme Platzhalter gerendert.
- **Komplexe Shader / Renderscript:** Erweiterte Render-Effekte (z. B. BlurMaskFilter, RenderEffect-Pipelines) werden in der Vorschau vereinfacht dargestellt.
- **Android Support-Library (Legacy):** Fokus liegt auf modernen AndroidX- und Material3-Komponenten. Veraltete Support-v4-Fragmente werden auf Standard-Fragment-Container gemappt.
