# Task 41: Layout-Designer ausbauen – Fortschrittsbericht

## Checkliste der Abschnitte

- [x] **41.0** Bestand und Ausgangsbuild (Abweichungen notiert, Baseline-Tests, ViewModel-Tests)
- [x] **41.1** Modell: Kommentare, Formatierung, neue Baum-Operationen (`moveNode`, `insertNode`, `canDrop`, `wrapIn`, `renameId`, `removeWithReferences`, Mehrfachauswahl)
- [x] **41.2** Drag & Drop (eigene Gesten-Implementierung, `DragController`, `NodeBoundsRegistry`, reiner `DropResolver`)
- [x] **41.3** Leinwand und Werkzeuge (Zoom/Pan, Größengriffe, Overlays, Breadcrumbs, Zwischenablage, Tastatur, PNG-Export)
- [x] **41.4** ConstraintLayout und RelativeLayout echt darstellen und bearbeiten (`ConstraintMapper`, Anker-Editor, topologischer RelativeLayout-Solver)
- [x] **41.5** Ressourcen auflösen (`ResourceRepository`, Qualifizierer, Themes/Styles, Drawables/VectorDrawables, „In Ressource auslagern“)
- [x] **41.6** `include`, `merge`, `ViewStub`, Fragmente, eigene Views
- [x] **41.7** Widget-Katalog und Eigenschaften-Panel (Erweiterung Widgets/Layouts, SDK `attrs.xml` Fallback, Gruppen, Filter)
- [x] **41.8** Prüfungen (Lint) mit Schnellkorrektur (`LayoutLinter`, Regeln, UI-Panel)
- [x] **41.9** Zusammenspiel mit dem Code-Editor und Dateien (`FileSyncBridge`, Vorlagen, Varianten, `SavedStateHandle`)
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

---

## 41.4 ConstraintLayout und RelativeLayout echt darstellen und bearbeiten

### Bibliotheken & Auflösung
- Gewählte Version: `androidx.constraintlayout:constraintlayout-compose:1.1.1` in `gradle/libs.versions.toml` als `libs.androidx.constraintlayout.compose` hinterlegt.
- Erfolgreich verifiziert gegen Compose-BOM `2025.09.00` mittels `./gradlew :feature:layoutdesigner:dependencies --configuration debugCompileClasspath` (1.1.1 aufgelöst).

### Reines Kotlin-Modell & Mapper (JVM-testbar)
- **`ConstraintModel.kt`:**
  - `ConstraintAnchor` (START, END, TOP, BOTTOM, BASELINE, LEFT, RIGHT).
  - `ConstraintTarget` (`Parent` oder `Sibling(id)`).
  - `DimensionSpec` (`Wrap`, `MatchParent`, `Fixed(dp)`, `MatchConstraints(0dp)` mit Ratio und Percent).
  - `GuidelineSpec`, `BarrierSpec`, `GroupSpec`.
  - Typisierte Warnungen `ConstraintWarning`: `MissingHorizontalConstraint`, `MissingVerticalConstraint`, `UnknownTargetId`, `CircularConstraint`.
- **`ConstraintMapper.kt`:**
  - Reine JVM-Logik zur Extraktion und Zuordnung aller XML-Constraint-Attribute.
  - Erkennt Margins und GoneMargins (`layout_goneMargin*`).
  - Erkennt Ketten (`layout_constraintHorizontal_chainStyle`, `layout_constraintVertical_chainStyle`, `weight`).
  - Zyklenerkennung über gerichtete Graphen-Tiefensuche.
  - Erkennt Verweise auf nicht existente Geschwister-IDs.
- **`RelativeSolver.kt`:**
  - Reine JVM-Logik zur Auflösung von `RelativeLayout`-Abhängigkeiten.
  - Topologische Sortierung via Kahn's Algorithmus.
  - Erkennt Zyklen und meldet betroffene Knoten.
  - Wertet Eltern-Ausrichtungen (`alignParent*`, `centerInParent`, `centerHorizontal/Vertical`) und Geschwister-Bezüge (`below`, `above`, `toStartOf`, `toEndOf`, `align*`) aus.

### Echte Darstellung in Compose
- **`ConstraintLayoutPreview.kt`:**
  - Nutzt `androidx.constraintlayout.compose.ConstraintLayout` mit `ConstraintSet`.
  - Guidelines werden über `createGuidelineFrom*` gerendert.
  - Barriers werden über `createStartBarrier` / `createEndBarrier` etc. erstellt.
  - Kindelemente werden dynamisch verknüpft (`linkTo`) inklusive Margins, GoneMargins und Biases.
  - Warnungs-Badge in der Vorschau bei fehlerhaften Constraints.
- **`RelativeLayoutPreview.kt`:**
  - Eigener Compose-`Layout`-Container mit Mess- und Platzierungslogik.
  - Berechnet Kind-Positionen `(x, y)` in Abhängigkeit von den topologisch sortierten Geschwistern.
  - Zyklus-Warnanzeige bei Ring-Abhängigkeiten.
- **`isApproximatedTag`:**
  - Drastisch verkleinert: `ConstraintLayout` und `RelativeLayout` werden nicht mehr als Näherung eingestuft. Der Näherungs-Hinweis entfällt für diese Container.

### Constraint-Editor UI
- **`ConstraintCard.kt`:**
  - Interaktives Mini-Diagramm im Eigenschaften-Panel für das selektierte Element.
  - Anker-Toggles für Top, Bottom, Start und End (Verbinden mit Elternteil bzw. Entfernen).
  - Bias-Schieberegler für horizontalen und vertikalen Bias.
  - Schnellaktionen: „Horizontal zentrieren“, „Vertikal zentrieren“, „Alle Constraints entfernen“, „Guideline hinzufügen (vertikal)“.
- **Events in Contract & ViewModel:**
  - `SetConstraint`, `RemoveConstraint`, `CenterHorizontally`, `CenterVertically`, `ClearConstraints`, `AddGuideline`, `SetHorizontalBias`, `SetVerticalBias`.
  - Vollständige Undo/Redo-Unterstützung.
- **`WidgetCatalog.attrsFor`:**
  - Ergänzt um alle Eltern-spezifischen Attribute für `ConstraintLayout`, `RelativeLayout`, `GridLayout`, `TableRow` und `CoordinatorLayout`.

### Testabdeckung
- 4 neue Tests in `ConstraintMapperTest`:
  1. `mapContainer_linksAndMarginsCorrect`
  2. `mapContainer_detectsMissingConstraints`
  3. `mapContainer_detectsUnknownTargetId`
  4. `mapContainer_detectsCycles`
- 3 neue Tests in `RelativeSolverTest`:
  1. `solve_topologicalOrder_placesDependenciesFirst`
  2. `solve_detectsCycle`
  3. `solve_missingTarget_reportedInMissingList`
- 3 neue Tests in `LayoutDesignerViewModelTest`:
  1. `setAndRemoveConstraint_updatesAttributesAndUndo`
  2. `centerHorizontallyAndVertically_setsConstraintsAndBiases`
  3. `addGuideline_insertsGuidelineChildAndEnablesUndo`
- Insgesamt **70 Unit-Tests** in `:feature:layoutdesigner` bestanden (100 %).
- `./gradlew :app:assembleDebug` erfolgreich.

---

## 41.5 Ressourcen auflösen & auslagern

### Architektur & Res-Ordner-Erkennung
- **Reiner JVM-Parser (`ResourceRepository.kt`, `ResourceModels.kt`):**
  - Erkennt Quellverzeichnisse automatisch: Durchsucht von der Layout-Datei ausgehend `src/*/res/` im Modul.
  - Parst XML-Ressourcen in `values*`-Ordnern: `<string>`, `<color>`, `<dimen>`, `<integer>`, `<bool>`, `<plurals>`.
  - Qualifier-Matching mit Best-Match-Bewertung für:
    - Nachtmodus (`night` vs default)
    - Sprache / Region (`de`, `en`, `es`, etc.)
    - Orientierung (`land` vs `port`)
    - Bildschirmbreite (`sw600dp`, `w400dp`)
    - Plattform-API-Level (`v21`, `v31`, etc.)
  - Referenzketten-Auflösung (`@color/primary -> @color/purple_500 -> #6200EE`) mit Zyklenerkennung und Tiefenlimit (10).
  - Built-in Material 3 Attribut-Tabelle für Theme-Referenzen (`?attr/colorPrimary`, `?attr/colorSurface`, etc.).
  - Robuste Dimensionen-Konvertierung (`sp`, `dp`, `pt`, `in`, `mm`, `px`) in Compose-Dichte.

### VectorDrawables & Vorschau
- **`VectorDrawableParser.kt`:**
  - Parst `<vector>`-XML-Dateien aus `res/drawable/`.
  - Konvertiert `<path android:pathData="..." android:fillColor="..." ...>` in Compose `ImageVector` zur direkten Darstellung in der Vorschau.

### UI-Integration & In-Place-Auflösung
- **Vorschau-Canvas (`LayoutPreview.kt`):**
  - Reagiert auf Konfigurations-Änderungen (`ResourceConfig` mit Nachtmodus, Sprache, Orientierung, Bildschirmbreite).
  - Löst Attribute wie `android:text`, `tools:text`, `android:textColor`, `android:background`, `tools:background`, `tools:visibility` über das `ResourceRepository` auf.
  - `tools:`-Präzedenz aktiv; Toolbar-Toggle zum Ein-/Ausblenden von Tools-Attributen.
- **Eigenschaften-Panel (`DesignerPanels.kt`):**
  - Zeigt unterhalb von Referenzwerten (`@string/...`, `@color/...`) den aktuell aufgelösten Wert in Grau an (`= ...`).
  - Integrierte Farb-Vorschau in Attributfeldern.
  - Quick-Action „Ressource auslagern“ (`BookmarkAdd`-Icon) für ungebundene Texte, Farben und Dimensionen.
  - `ExtractResourceDialog`: Schlägt Namen vor, schreibt strukturiert in `values/strings.xml`, `colors.xml` oder `dimens.xml`, aktualisiert das XML-Attribut auf `@type/name` und benachrichtigt `FileSyncBridge`.
  - Vollständige Undo/Redo-Fähigkeit für Auslagerungsoperationen.

### Testabdeckung
- 6 neue Tests in `ResourceRepositoryTest`:
  1. `resolveString_withQualifierPreference`
  2. `resolveColor_withReferenceChain`
  3. `resolveDimen_withUnits`
  4. `resolvePlurals_selectsOther`
  5. `resolveThemeAttr_usesMaterial3Fallback`
  6. `extractToResource_appendsAndReloads`
- 2 neue ViewModel-Tests in `LayoutDesignerViewModelTest`:
  1. `setShowToolsAttributes_togglesFlag`
  2. `extractToResource_replacesAttributeAndEnablesUndo`
- Insgesamt **78 Unit-Tests** in `:feature:layoutdesigner` bestanden (100 %).
- `./gradlew :app:assembleDebug` erfolgreich.

---

## 41.6 `include`, `merge`, `ViewStub`, Fragmente, eigene Views

### Reine JVM-Logik (`IncludeResolver.kt`, `CustomViewScanner.kt`)
- **`IncludeResolver`:**
  - Löst Layout-Referenzen (`@layout/...`) in den `res/layout*`-Verzeichnissen des Moduls auf.
  - Überschreibt Attribute des eingebundenen Wurzelknotens mit den im `<include>`-Tag definierten Attributen (`android:id` und alle `layout_*`-Attribute wie Maße, Ränder, Gravity und Constraints).
  - Robuste Zyklenerkennung über kanonische Pfade im Rekursionsstapel (`IncludedLayoutResult.CycleDetected`).
  - Rekursionstiefen-Begrenzung auf maximal 8 Ebenen (`IncludedLayoutResult.DepthLimitExceeded`).
  - Behandelt nicht gefundene oder fehlerhafte Layouts graceful als `NotFound` bzw. `ParseError`.
- **`CustomViewScanner`:**
  - Durchsucht das Projekt- bzw. Modul-Quellverzeichnis (`src/`) nach Kotlin- und Java-Klassen, die von `View`, `ViewGroup` oder bekannten AndroidX/Material-View-Basisklassen erben.
  - Erkennt automatisch `isViewGroup` anhand der Vererbungshierarchie.
  - Liefert `CustomViewInfo` mit qualifiziertem Klassennamen, einfachem Namen und Quellpfad.

### Compose-Vorschau (`LayoutPreview.kt`)
- **`<include>`-Darstellung:**
  - Schreibgeschützte Darstellung mit gestricheltem Rahmen, Titelchip `<include @layout/...>` und Schaltfläche „Eingebundenes Layout öffnen“ (`OpenInNew`).
  - Zeigt bei Zyklen ein deutliches Fehler-Badge („Zirkulärer include erkannt“).
- **`<merge>` als Wurzel:**
  - Wertet `tools:parentTag` aus (Standard: `FrameLayout`).
  - Zeigt Banner `merge (Elternteil: <parentTag>)` und rendert Kindelemente im entsprechenden Container (`LinearLayout`, `ConstraintLayout`, `RelativeLayout`, `FrameLayout`).
- **`ViewStub`:**
  - Vorschau als gestrichelter Platzhalter mit `android:layout`-Referenz.
  - Interaktive Schaltfläche zum Auf- und Zuklappen („ViewStub aufklappen“ / „ViewStub zuklappen“).
  - Im aufgeklappten Zustand wird das referenzierte Layout eingebettet dargestellt.
- **`<fragment>` & `FragmentContainerView`:**
  - Wertet `tools:layout` aus und bettet bei Angabe das referenzierte Layout ein.
  - Andernfalls stilisierter Platzhalter mit Klassenname bzw. Fragment-Bezeichnung.
- **Eigene Views & Nicht-Widget-Tags:**
  - Benutzerdefinierte Projekt-Views werden mit Klassenname und Paketbeschriftung dargestellt (als Container, falls `isViewGroup`).
  - `<requestFocus/>` und `<tag>` werden syntaktisch erhalten, aber nicht als sichtbare Widgets gerendert.

### Palette & ViewModel-Integration
- **Palette:**
  - `include` in der Kategorie „Layouts“.
  - Gefundene eigene Views aus dem Quellverzeichnis erscheinen dynamisch unter der Kategorie „Projekt“.
- **Events & Effekte:**
  - `ToggleInflateViewStub(uid)` zur Steuerung des Aufklapp-Zustands.
  - `OpenIncludedLayout(layoutRef)` emittiert `LayoutDesignerUiEffect.OpenLayoutFile(path)` zum Wechsel in das eingebundene Layout.

### Testabdeckung
- 4 neue Tests in `IncludeResolverTest`:
  1. `resolve_normalInclude_mergesAttributesCorrectly`
  2. `resolve_cycleDetected_returnsCycleResult`
  3. `resolve_depthLimitExceeded_returnsDepthLimitResult`
  4. `resolve_notFound_returnsNotFoundResult`
- 1 neuer Test in `CustomViewScannerTest`:
  1. `scanDirectory_detectsKotlinAndJavaViews`
- 2 neue ViewModel-Tests in `LayoutDesignerViewModelTest`:
  1. `toggleInflateViewStub_togglesUidInState`
  2. `openIncludedLayout_emitsOpenLayoutFileEffect`
- Insgesamt **85 Unit-Tests** in `:feature:layoutdesigner` bestanden (100 %).
- `./gradlew :app:assembleDebug` erfolgreich.

---

## 41.7 Widget-Katalog und Eigenschaften-Panel

### Erweiterung des Widget-Katalogs (`WidgetCatalog.kt`)
- **Neue Attribut-Typen (`AttrKind`):**
  - `DIMENSION`, `COLOR`, `STRING`, `BOOLEAN`, `ENUM`, `FLAG`, `REFERENCE`, `FLOAT`, `INTEGER`, `ID`.
- **Katalog-Erweiterung Layouts & Container:**
  - `NestedScrollView`, `GridLayout`, `TableLayout`, `TableRow`, `RadioGroup`, `CoordinatorLayout`, `AppBarLayout`, `CollapsingToolbarLayout`, `CardView`, `MaterialCardView`, `ChipGroup`, `FlexboxLayout`.
- **Katalog-Erweiterung Widgets & Steuerelemente:**
  - `MaterialButton`, `TextInputLayout`, `TextInputEditText`, `Chip`, `FloatingActionButton`, `ExtendedFloatingActionButton`, `MaterialToolbar`, `Toolbar`, `BottomNavigationView`, `NavigationRailView`, `TabLayout`, `ViewPager2`, `RecyclerView`, `ListView`, `GridView`, `Spinner`, `SeekBar`, `Slider`, `RatingBar`, `ToggleButton`, `MaterialSwitch`, `MaterialDivider`, `ShapeableImageView`, `LinearProgressIndicator`, `CircularProgressIndicator`, `WebView`, `VideoView`, `SurfaceView`, `TextureView`, `AutoCompleteTextView`, `ComposeView`.
- **Eltern-spezifische Layout-Attribute:**
  - Dynamische Ergänzung von Layout-Attributen je nach Containertyp (`GridLayout`, `TableLayout`, `CoordinatorLayout`, `FlexboxLayout`, `ConstraintLayout`, `RelativeLayout`, `LinearLayout`, `FrameLayout`).

### Android SDK `attrs.xml` Parser (`SdkAttrsParser.kt`)
- Reine JVM-Implementierung ohne Android-Framework-Abhängigkeiten.
- Liest `declare-styleable`, `attr`, `enum` und `flag` Definitionen direkt aus SDK-Pfad oder InputStreams.
- Unterstützt Caching für performante Wiederverwendung.
- Liefert Attribute, Typen (`AttrKind`) und Optionen (Werte für Enums und Flags) als Fallback für nicht fest im Katalog verdrahtete Attribute.

### Eigenschaften-Panel & Palette (`DesignerPanels.kt`)
- **PalettePanel:**
  - Schnellsuchfeld zur Filterung nach Tag- oder Anzeigenamen.
  - Kategorie-Filter-Chips: *Alle*, *Layouts*, *Text*, *Buttons*, *Eingabe*, *Medien*, *Sonstige*, *Projekt*.
  - Einheitliches Drag-and-Drop- und Klick-Verhalten.
- **PropertiesPanel:**
  - Suchfeld für Attributnamen.
  - Filter-Chip „Nur gesetzte“ (`onlySetFilter`) zur schnellen Übersicht über existierende Attribute.
  - Gruppierung nach logischen Kategorien: *Layout*, *Ränder*, *Text*, *Erscheinungsbild*, *Verhalten*, *Constraints*, *Sonstige* mit Ein-/Ausklapp-Zustand.
  - Spezialisierte Editoren je Attribut-Typ:
    - `BOOLEAN`: Auswahlschalter / Segmentierte Chips (`true`, `false`).
    - `FLAG`: Mehrfachauswahl-Chips in `FlowRow`.
    - `ENUM`: Dropdown / Optionsauswahl.
    - `DIMENSION`: Textfeld mit schnellen `+/- 4dp` Tasten.
    - Zurücksetzen-Schaltfläche (Mülleimer-Symbol) zum gezielten Entfernen eines gesetzten Attributs.
  - Verknüpfung mit „In Ressource auslagern“ (`onExtractToResource`) für String-, Color- und Dimen-Werte.

### Testabdeckung
- 2 neue Tests in `SdkAttrsParserTest`:
  1. `parseAttrs_extractsStyleablesAndAttributes`
  2. `parseAttrs_resolvesFlagsAndEnums`
- Insgesamt **87 Unit-Tests** in `:feature:layoutdesigner` bestanden (100 %).
- `./gradlew :app:assembleDebug` erfolgreich.

---

## 41.8 Prüfungen (Lint) mit Schnellkorrektur

### Reine JVM-Prüfengine (`LayoutLinter.kt`)
- **Regeln (vollständig implementiert):**
  1. `DuplicateId` (ERROR): Erkennt doppelt vergebene IDs im Layout.
  2. `MissingIdReference` (ERROR): Erkennt Verweise auf nicht existierende IDs (`layout_below`, `layout_toEndOf`, `layout_constraint*`, `labelFor`, `constraint_referenced_ids`).
  3. `MissingResourceReference` (WARNING): Prüft referenzierte Strings, Farben, Dimens, Drawables und Styles gegen `ResourceRepository`.
  4. `HardcodedText` (WARNING): Erkennt fest codierten Text in `text`, `hint`, `contentDescription`.
  5. `MissingContentDescription` (WARNING): Prüft ImageViews ohne Bildbeschreibung und ohne `importantForAccessibility="no"`.
  6. `SmallTouchTarget` (WARNING): Erkennt interaktive Elemente mit fester Klickfläche < 48×48 dp.
  7. `ScrollViewChildren` (ERROR): Stellt sicher, dass ScrollViews maximal ein Kind besitzen.
  8. `NestedWeights` (WARNING): Warnt vor verschachtelten LinearLayouts mit `layout_weight`.
  9. `InefficientWeight` (WARNING): Erkennt `layout_weight` mit `wrap_content` statt `0dp` in Gewichtsrichtung.
  10. `DeprecatedFillParent` (WARNING): Erkennt veraltetes `fill_parent`.
  11. `PxUsage` (WARNING): Warnt vor absoluten Pixelwerten `px`.
  12. `WrongDimensionUnit` (WARNING): Erkennt `sp` für Layoutmaße oder `dp` für `textSize`.
  13. `RtlAttributes` (WARNING): Empfiehlt `Start`/`End` statt `Left`/`Right`.
  14. `UnconstrainedChild` (ERROR): Erkennt unbeschränkte Kinder in `ConstraintLayout`.
  15. `CircularConstraints` (ERROR): Erkennt zirkuläre horizontale oder vertikale Constraints.
  16. `UselessParent` (INFO): Erkennt überflüssige Container mit nur einem Kind ohne Styling.
  17. `DeepNesting` (INFO): Warnt bei Schachtelungstiefe > 10 Ebenen.
- **Schnellkorrekturen (`QuickFix`):**
  - `SetAttribute`: Attribut direkt setzen (z. B. `0dp`, `match_parent`, `importantForAccessibility="no"`).
  - `ReplaceAttribute`: Attribut migrieren (z. B. `Left/Right` zu `Start/End`).
  - `RemoveAttribute`: Überflüssiges Attribut entfernen.
  - `UnwrapContainer`: Überflüssigen Container auflösen.
  - `ExtractResource`: Auslagern in `strings.xml`.

### UI-Integration
- **Probleme-Tab (`DesignerTab.PROBLEMS`, `ProblemsPanel`):**
  - Eigene Ansicht mit Filter-Chips (*Alle*, *Fehler*, *Warnungen*, *Hinweise*) und Zählern.
  - Aktions-Schaltflächen: „Zum Element springen“ und Schnellkorrektur ausführen.
  - Leerzustand mit Bestätigungssymbol („Keine Layout-Probleme festgestellt“).
- **Topbar & Baum-Indikatoren:**
  - BadgedBox-Zähler mit Warnsymbol in der TopAppBar.
  - Farbige Fehler- und Warnsymbole direkt an betroffenen Knoten im Strukturbaum.
- **Entprellte Ausführung:**
  - Automatische, nicht-blockierende Prüfung bei allen Dokumentänderungen.

### Testabdeckung
- 15 neue Tests in `LayoutLinterTest` (jede Regel und Schnellkorrektur separat abgedeckt).
- 3 neue ViewModel-Tests in `LayoutDesignerViewModelTest` (`load_populatesLintIssues`, `applyQuickFix_updatesDocumentAndEnablesUndo`, `jumpToIssue_selectsNodeAndSwitchesTab`).
- Insgesamt **105 Unit-Tests** in `:feature:layoutdesigner` bestanden (100 %).

---

## 41.9 Zusammenspiel mit dem Code-Editor und Dateien

### Implementierung
- **Layout-Vorlagen (`LayoutTemplates.kt`):**
  - Fünf saubere Vorlagen nach Android Best-Practices und Lint-geprüft:
    1. Leeres `ConstraintLayout`
    2. Leeres `LinearLayout` (vertikal)
    3. Listen-Eintrag (`ConstraintLayout` mit ImageView, zwei TextViews, Divider)
    4. Formular / Eingabemaske (`ScrollView` mit LinearLayout, TextInputLayouts, Button)
    5. Scrollbare Detailseite (`NestedScrollView` mit vertikalem LinearLayout)
  - Unit-Test `LayoutTemplatesTest` verifiziert syntaktische Korrektheit und 0 Lint-Fehler bei allen Vorlagen.
  - Integration in `LayoutFilesPanel` mit Filter- und Vorlagenauswahl-Chips sowie Dateianlege-Dialog.
- **Zwei-Wege-Synchronisation mit Code-Editor (`FileSyncBridge`):**
  - Automatisches Neuladen sauberer Zustände bei externen Dateiänderungen.
  - Konfliktdialog bei ungespeicherten Änderungen (`showExternalChangeDialog`): Auswahl zwischen *Externen Stand übernehmen*, *Eigene Version behalten* und *Vergleichen*.
  - `flushRequests` vor Ladevorgängen und `notifyExternalChange` nach Speichern/Erstellen von Varianten.
- **XML-Syntax-Highlighting und Synchronisation (`XmlVisualTransformation`):**
  - XML-Editor mit Syntaxhervorhebung für Kommentare, XML-Tags, Attribute und Attributwerte.
  - Tag-Bereich-Tracking (`sourceRange: IntRange?`) an `LayoutNode` zur Synchronisation zwischen XML-Textauswahl und Leinwand/Baum.
- **Layout-Varianten (`availableVariants`, `currentVariant`):**
  - Automatische Erkennung vorhandener Varianten (z. B. `layout-land`, `layout-sw600dp`).
  - Schneller Wechsel zwischen Varianten (`SwitchVariant`) und Erstellung neuer Varianten (`CreateVariant`).
- **Prozess-Tod-Wiederherstellung (`SavedStateHandle`):**
  - Wiederherstellung von Pfad, Entwurf, aktivem Tab, Preset und Knotenauswahl bei Android-Prozesstod.

### Testabdeckung
- 5 neue Tests in `LayoutTemplatesTest`.
- 5 neue ViewModel-Tests in `LayoutDesignerViewModelTest` (`externalChange_cleanState_autoReloads`, `externalChange_dirtyState_showsConflictDialog`, `restoreFromSavedState_restoresDocumentAndState`, `createVariant_writesFileAndNotifiesBridge`, `sourceRange_nodeMapping`).
- Insgesamt **110 Unit-Tests** in `:feature:layoutdesigner` bestanden (100 %).







