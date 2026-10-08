# Layout-Designer (`:feature:layoutdesigner`)

Stand: 2026-10-08. Visueller Editor für **Android-View-Layout-XML** (`res/layout*/*.xml`). Die reine Logik ist per JVM-Test geprüft (14 Tests), die Compose-UI und die Einbindung sind **nicht gebaut** (kein Gradle/Android); Prüfauftrag: `agy-tasks/14`-Folge → `agy-tasks/15-verify-layout-designer.md`.

## Bedienung
- Drawer → Bereich **Layouts**: listet alle Layout-Dateien des Projekts, `+` legt eine neue an (Name `a–z0–9_`, Ziel-`res`-Ordner wählbar), Tippen öffnet den Designer (eigene Route `layout_designer/{filePath}`).
- **Vorschau** (Gerätegrößen Phone/Phone groß/Tablet 7″/10″): Tippen wählt ein Element. Auswahl-Leiste: nach oben/unten, ein-/ausrücken (in vorherigen Container / heraus), duplizieren, löschen.
- **+ (Palette):** Linear vertikal/horizontal, Frame, Scroll, Constraint, TextView, Button, EditText, CheckBox, Switch, ImageView, ProgressBar, View, Space. Das Widget landet im ausgewählten Container (oder im Elternteil eines ausgewählten Blatts) und bekommt eine freie `@+id/…`.
- **Struktur** (Baum), **Eigenschaften** (Editor je Attributtyp: Text, Zahl, Dimension, Größe mit Schnellauswahl, Farbe mit Swatch, Enum; „Weitere Attribute“ für alles Übrige + Attribut hinzufügen), **XML** (bearbeitbar, wirkt erst nach „XML übernehmen“, Fehler werden angezeigt).
- Undo/Redo (100 Schritte; Eingaben in dasselbe Feld teilen sich einen Schritt), Speichern (atomar über `.tmp`, danach `FileSyncBridge.notifyExternalChange`), „Im Editor öffnen“, Verwerfen-Dialog bei ungespeicherten Änderungen.
- Layout: Compact = Tabs; ab 600 dp = Vorschau links, rechts Panel mit Struktur/Eigenschaften/XML.

## Aufbau
| Paket | Inhalt |
|---|---|
| `model` | `LayoutNode`/`LayoutDocument` (immutable), `WidgetCatalog` (Widgets, Attribute je Eltern-Typ, Palette), `LayoutOps` (add/remove/duplicate/move/indent/outdent/setAttribute), `Units` (dp, Farben, Größen) |
| `xml` | `LayoutXml.parse/write` (DOM ohne Namespace-Auflösung, DOCTYPE abgelehnt; Ausgabe mit `xmlns:android/app/tools`, `id`/`width`/`height` zuerst) |
| `preview` | `LayoutPreview`: Compose-Näherung (Linear mit weight/gravity, Frame mit layout_gravity, Widgets) |
| `ui`, Root | `LayoutDesignerContract` (MVI), `LayoutDesignerViewModel`, `LayoutDesignerScreen`, Panels |
| `files` | `LayoutScanner` (JVM), `LayoutFilesViewModel`/`Panel` für den Drawer |

Integration in `:app`: `Routes.LAYOUT_DESIGNER_PATTERN`/`Routes.layoutDesigner(path)`, `DrawerSection.LAYOUTS` in `WorkspaceDrawer`. Texte in `:core:resources` (`layout_*`, `drawer_layouts`).

## Grenzen (ehrlich)
- Die Vorschau ist eine **Näherung**, keine echte View-Inflation: ConstraintLayout/RelativeLayout und unbekannte Container werden gestapelt gezeichnet (Hinweis in der UI); Referenzen (`@string/…`, `@drawable/…`, `@color/…`) werden nicht aufgelöst; Styles/Themes/`include`/`merge` fehlen; Bilder sind Platzhalter.
- Kein Drag-and-Drop – Einfügen per Palette, Umordnen per Leiste.
- Speichern **normalisiert** die Formatierung der Datei (Einrückung, Attributreihenfolge `id/width/height` zuerst); Kommentare und Text zwischen Elementen gehen verloren.
- Nur ein Dokument pro Sitzung; `tools:`-Attribute werden erhalten, aber nicht ausgewertet.
- Material-Komponenten (`com.google.android.material.*`) erscheinen als Platzhalter-Kästen.
- ViewModels sind nicht getestet (nur Modell, XML, Scanner).
