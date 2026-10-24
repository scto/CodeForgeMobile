# Task 41: Layout-Designer ausbauen – Fortschrittsbericht

## Checkliste der Abschnitte

- [x] **41.0** Bestand und Ausgangsbuild (Abweichungen notiert, Baseline-Tests, ViewModel-Tests)
- [x] **41.1** Modell: Kommentare, Formatierung, neue Baum-Operationen (`moveNode`, `insertNode`, `canDrop`, `wrapIn`, `renameId`, `removeWithReferences`, Mehrfachauswahl)
- [x] **41.2** Drag & Drop (eigene Gesten-Implementierung, `DragController`, `NodeBoundsRegistry`, reiner `DropResolver`)
- [x] **41.3** Leinwand und Werkzeuge (Zoom/Pan, Größengriffe, Overlays, Breadcrumbs, Zwischenablage, Tastatur, PNG-Export)
- [ ] **41.4** ConstraintLayout und RelativeLayout echt darstellen und bearbeiten (`ConstraintMapper`, Anker-Editor, topologischer RelativeLayout-Solver)
- [ ] **41.5** Ressourcen auflösen (`ResourceRepository`, Qualifizierer, Themes/Styles, Drawables/VectorDrawables, „In Ressource auslagern“)
- [ ] **41.6** `include`, `merge`, `ViewStub`, Fragmente, eigene Views
- [ ] **41.7** Widget-Katalog und Eigenschaften-Panel (Erweiterung Widgets/Layouts, SDK `attrs.xml` Fallback, Gruppen, Filter)
- [ ] **41.8** Prüfungen (Lint) mit Schnellkorrektur (`LayoutLinter`, Regeln, UI-Panel)
- [ ] **41.9** Zusammenspiel mit dem Code-Editor und Dateien (`FileSyncBridge`, Vorlagen, Varianten, `SavedStateHandle`)
- [ ] **41.10** Leistung und Robustheit (500 Knoten Benchmark, Fehlerisolation)
- [ ] **41.11** (optional) View-XML → Jetpack-Compose-Code
- [ ] **41.12** Tests, Doku, Abnahme (JVM-Tests, Checklist, Doku-Update)

---

## 41.0 Bestand und Ausgangsbuild

### Prüfung der Bestands-Tabelle
| Datei | Befund vor Task 41 |
|---|---|
| `model/LayoutNode.kt` | Bestätigt: `LayoutNode(uid, tag, attributes, children)` ohne Kommentare und Text-Inhalte. |
| `model/LayoutOps.kt` | Bestätigt: `find`, `parentOf`, `flatten`, `insertTarget`, `freshId`, `addWidget` (hängt ans Ende), `remove`, `duplicate`, `moveWithinParent`, `indent`, `outdent`, `setAttribute`. |
| `model/WidgetCatalog.kt` | Bestätigt: 18 Widgets, `AttrKind`, Elternattribute nur für Linear/Frame. |
| `model/Units.kt` | Bestätigt: `parseDimension`, `parseColorArgb`, `parseSize`. |
| `xml/LayoutXml.kt` | Bestätigt: DOM ohne Namespace-Erhalt, normalisierter `write`, Kommentare werden verworfen. |
| `preview/LayoutPreview.kt` | Bestätigt: Näherungsdarstellung mit `verticalScroll`/`horizontalScroll`, Constraint/Relative gestapelt (`isApproximatedTag`). |
| `LayoutDesignerContract/ViewModel/Screen.kt` | Bestätigt: MVI, Undo/Redo (100), `DevicePreset`, Split ab 600 dp. |
| Tests | Bestätigt: 14 Tests (`LayoutModelTest`: 11, `LayoutScannerTest`: 3), keine ViewModel-Tests vorhanden. |
| Bibliotheken | `material-icons-extended:1.7.3` hart in `feature/layoutdesigner/build.gradle.kts`. In `gradle/libs.versions.toml` als `libs.androidx.compose.material.icons.extended` überführt. |

### Baseline-Build
- `./gradlew :feature:layoutdesigner:testDebugUnitTest`: 14 Tests bestanden (Ausgangszustand).
- `./gradlew :app:assembleDebug`: BUILD SUCCESSFUL.

### Implementierung 41.0
- Abstraktion `LayoutFileStore` (`DefaultLayoutFileStore`) für `readText` und `writeAtomically` erstellt. Beseitigt direkte `File`-Operationen aus dem ViewModel und garantiert `.tmp`-Dateibereinigung.
- Hilt-Binding via `LayoutFileStoreModule` (`@Binds @Singleton`).
- `androidx.compose.material:material-icons-extended` in `gradle/libs.versions.toml` eingepflegt und harte Abhängigkeit ersetzt.
- `kotlinx-coroutines-test` in `:feature:layoutdesigner` eingebunden.
- 15 ViewModel-Tests in `LayoutDesignerViewModelTest` mit Fake-Bridges implementiert:
  1. `load_success_populatesDocumentAndXml`
  2. `load_missingFile_setsLoadError`
  3. `load_invalidXml_setsLoadError`
  4. `undoCoalescing_sameField_mergesIntoSingleStep`
  5. `undoCoalescing_differentField_createsSeparateSteps`
  6. `undoLimit_capsAt100Steps`
  7. `dirty_returnsToFalse_whenUndoneToSavedXml`
  8. `xmlDraft_apply_updatesDocumentAndEnablesUndo`
  9. `xmlDraft_apply_invalidXml_setsError`
  10. `xmlDraft_revert_discardsDraft`
  11. `save_success_writesFileAndNotifiesBridge`
  12. `save_failure_emitsErrorSnackbarAndRemainsDirty`
  13. `backRequested_cleanState_navigatesBack`
  14. `backRequested_dirtyState_promptsConfirmation`
  15. `openInEditor_savesAndRequestsOpen`
- 4 Tests in `LayoutFileStoreTest`:
  1. `readText_existingFile_returnsContent`
  2. `readText_missingFile_throwsIOException`
  3. `writeAtomically_writesContentAndCleansTmp`
  4. `writeAtomically_overwritesExistingFile`
- Alle 33 Unit-Tests in `:feature:layoutdesigner` sind grün.
- `./gradlew :app:assembleDebug` erfolgreich.

---

## 41.1 Modell: Kommentare, Formatierung, neue Baum-Operationen

### Format-erhaltendes XML
- `LayoutNode` erweitert um:
  - `leadingComments: List<String>` (Kommentare vor dem Knoten)
  - `trailingComments: List<String>` (Kommentare nach dem letzten Kind im Container)
  - `textContent: String?` (Text-Inhalt)
  - `isNew: Boolean` (neu hinzugefügte Knoten)
  - `firstAttrInline: Boolean` (erstes Attribut inline vs. neue Zeile)
- `LayoutDocument` erweitert um:
  - `leadingComments: List<String>` (Dokument-Kopfkommentare vor dem Wurzelelement)
  - `rootNamespaces: Map<String, String>` (alle Namespaces wie `xmlns:android`, `xmlns:custom` etc.)
  - `indentation: String` (automatische Einrückungserkennung, z. B. 2/4 Leerzeichen oder Tab)
  - `reformatOnSave: Boolean` (Standard `false` = Formatierung erhalten)
- Parser `LayoutXml`:
  - Tokenisiert Quell-Attribute für exakte Quellreihenfolge.
  - Erkennt Einrückung und Inline-Attribute.
  - Bewahrt alle Kommentare und Namespaces.
  - Byte-identischer Round-Trip-Test (`byteIdenticalRoundTripWithCommentsAndCustomNamespaces`) erfolgreich bestanden.

### Neue reine Baum-Operationen (`LayoutOps.kt`)
- `moveNode(uid, newParentUid, index)`: Sicheres Verschieben mit Validierung (Wurzel, Selbstverschiebung, Nachfahren-Schutz, Container-Prüfung).
- `insertNode(node, parentUid, index)` und `addWidgetAt(entry, parentUid, index)`.
- `canDrop(dragged: DragPayload, parentUid): DropVerdict` (`Allowed`, `Warning`, `Rejected`).
- `wrapIn(uids, containerTag)`: Packt Auswahl in neuen Container, bereinigt `layout_weight`.
- `unwrap(uid)`: Löst Container auf und setzt Kinder an dessen Position.
- `changeTag(uid, newTag, removeIncompatibleAttrs)` mit `findIncompatibleChildAttributes`.
- `renameId(uid, newId)`: Kollisionssichere Umbenennung inklusive aller Verweise (`@id/`, `@+id/`, Constraints, `labelFor`, `nextFocus*`, `constraint_referenced_ids`).
- `removeWithReferences(uid, clearReferences)`: Erkennt und bereinigt hängende Referenzen.
- Mehrfachauswahl: `removeNodes(uids)`, `duplicateNodes(uids)`, `selectedUids: Set<Int>`, `SelectToggle`.

### Testabdeckung
- 47 Tests in `:feature:layoutdesigner` erfolgreich (100 % bestanden).
- `./gradlew :app:assembleDebug` erfolgreich.

---

## 41.2 Drag & Drop

### Architekturentscheidung Gesten-Implementierung
- **Entscheidung:** Eigene Gesten-Implementierung via `Modifier.pointerInput` und `detectDragGesturesAfterLongPress` (statt Compose `Modifier.dragAndDrop*`).
- **Begründung:** Drag & Drop findet ausschließlich innerhalb des Layout-Designers statt (kein systemweites `ClipData` erforderlich). Vollständige Kontrolle über Zoom, Pan, Auto-Scroll und Drop-Indikatoren ohne API-Bruch zwischen Compose-Foundation-Versionen.

### Komponenten & Logik
- **`DragController` & `NodeBoundsRegistry`:**
  - `@Stable` Controller verwaltet `DragPayload` (`NewWidget(PaletteEntry)` | `ExistingNodes(Set<Int>)`), Zeigerkoordinaten und aktuelles `DropTarget`.
  - Jeder gezeichnete Knoten registriert seine Leinwand-Grenzen (`DesignerRect`) per `onGloballyPositioned` und räumt sich bei `onDispose` auf.
- **Reiner `DropResolver` (JVM-getestet):**
  - Tiefster Container unter dem Zeiger gewinnt.
  - `LinearLayout`: Orientierung + Mittelpunkte der Kinder bestimmen Einfüge-Index und Indikator-Linie.
  - `FrameLayout` / `CoordinatorLayout`: 3×3-Zonen berechnen automatisches `android:layout_gravity` (z. B. `bottom|end`, `center`).
  - `ConstraintLayout` & `RelativeLayout`: Automatische Eltern-Constraints und 8-dp-gerasterte Margins (`android:layout_marginStart/Top`).
  - Über einem Blatt: Oberes/unteres Drittel bzw. linkes/rechtes Drittel platziert davor oder dahinter.
  - Ungültige Ziele liefern `DropVerdict.Rejected(reason)`.
- **Rückmeldung & Zustand:**
  - Halbtransparenter Schatten / Badge des Widgets am Finger.
  - Einfüge-Linie bzw. Container-Highlighting (`primary` bzw. `error` bei Verstoß).
  - Fehler-Chip am Finger mit detaillierter Begründung.
  - Jeder erfolgreiche Drop erzeugt genau einen Undo-Schritt und selektiert das neue/verschobene Element.

### Testabdeckung
- 6 neue Unit-Tests in `DropResolverTest`:
  1. `resolveDrop_linearLayout_vertical_calculatesIndexAndLine`
  2. `resolveDrop_linearLayout_horizontal_calculatesIndexByX`
  3. `resolveDrop_frameLayout_computesGravityZones`
  4. `resolveDrop_constraintLayout_setsParentConstraintsAndSnappedMargins`
  5. `resolveDrop_deepestContainerWins`
  6. `resolveDrop_outsideBounds_returnsNull`
- 2 neue ViewModel-Tests in `LayoutDesignerViewModelTest`:
  1. `dropWidget_insertsNewWidget_recordsSingleUndoStep`
  2. `dropWidget_movesExistingNode_recordsSingleUndoStep`
- Insgesamt 55 Unit-Tests in `:feature:layoutdesigner` bestanden (100 %).
- `./gradlew :app:assembleDebug` erfolgreich.

---

## 41.3 Leinwand und Werkzeuge

### Features & Implementierung
- **Zoom & Pan:**
  - Zwei-Finger-Gesten (Pinch-to-Zoom 25 % bis 400 %, Pan über Zwei-Finger-Verschiebung).
  - Schwebende `ZoomControls` (+, -, 100 %, Reset-Taste) am unteren rechten Rand.
- **Konfigurationsleiste (`CanvasConfigBar`):**
  - Querformat / Hochformat Umschalter mit automatischer Breiten-/Höhenumkehr (`effectiveWidthDp`, `effectiveHeightDp`).
  - Dunkel / Hell Vorschau-Modus.
  - Schriftskalierung (1.0x, 1.3x, 2.0x).
  - Overlays (Blueprint-Modus, Abstände / Margins & Paddings, Grenzen).
- **Größengriffe (`ResizeHandles`):**
  - Griffe rechts, unten und rechts-unten am selektierten Element mit Live-Anzeige der Dimensionen.
  - 8-dp-Raster-Snapping beim Ziehen.
  - Doppeltippen auf einen Griff schaltet zwischen `wrap_content` und `match_parent` (bzw. `0dp` bei ConstraintLayout).
- **Breadcrumbs & Navigation:**
  - `BreadcrumbBar` (Wurzel › … › Auswahl) mit direktem Klick zur Elternelement-Auswahl.
  - „Durchklicken“ auf der Leinwand: Erneutes Antippen eines bereits gewählten Elements wählt automatisch dessen Elternelement aus.
- **Zwischenablage (`LayoutClipboard`):**
  - Reine JVM-Logik: Erzeugt und parst XML-Schnipsel.
  - Beim Einfügen automatische Neuzuordnung von UIDs und IDs bei Kollisionen (`@+id/textView1`, `@+id/button2` usw.).
  - Vollständige Integration mit Undo/Redo und System-Zwischenablage über `LocalClipboardManager` und ViewModel-Effekt `CopyToClipboard`.
- **Tastatur-Kurzbefehle (`onPreviewKeyEvent`):**
  - Strg+Z / Strg+Y / Strg+Umschalt+Z (Undo / Redo)
  - Strg+S (Speichern)
  - Strg+C / Strg+X / Strg+V (Kopieren, Ausschneiden, Einfügen)
  - Strg+D (Duplizieren)
  - Entf / Backspace (Löschen)
  - Alt+Pfeiltasten (Verschieben)
  - Pfeiltasten Oben/Unten (Knotennavigation)
- **Kontextmenü & Dialoge:**
  - Modal-BottomSheet Kontextmenü mit Ausschneiden, Kopieren, Einfügen, Duplizieren, Löschen, „In Container packen…“, „Container auflösen“, „Tag ändern…“, „ID umbenennen…“, „Im XML zeigen“.
  - Dialoge für Container-Auswahl, Tag-Änderung und ID-Eingabe.
- **PNG-Export:**
  - Aufnahme der Vorschau über `rememberGraphicsLayer()` und `GraphicsLayer.record { drawContent() }`.
  - Bitmap-Konvertierung und Speichern in den App-Cache.
  - Eigener `LayoutDesignerFileProvider` zur Vermeidung von Manifest-Merger-Kollisionen und sicheres Teilen über das Android Share Sheet (`Intent.ACTION_SEND`).

### Testabdeckung
- 5 neue Tests in `LayoutDesignerViewModelTest` & `LayoutClipboardTest`:
  1. `createSnippet_and_pasteWithCollisionHandling`
  2. `zoomAndPan_updatesStateAndResetRestoresDefaults`
  3. `configurationToggles_updateState`
  4. `clipboard_copyCutPaste_worksWithUndo`
  5. `resizeAndToggleSizeMode_updatesAttributesAndRecordsUndo`
- Insgesamt **60 Unit-Tests** in `:feature:layoutdesigner` bestanden (100 %).
- `./gradlew :app:assembleDebug` erfolgreich.




