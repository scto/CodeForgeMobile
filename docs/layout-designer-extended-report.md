# Task 41: Layout-Designer ausbauen – Fortschrittsbericht

## Checkliste der Abschnitte

- [x] **41.0** Bestand und Ausgangsbuild (Abweichungen notiert, Baseline-Tests, ViewModel-Tests)
- [x] **41.1** Modell: Kommentare, Formatierung, neue Baum-Operationen (`moveNode`, `insertNode`, `canDrop`, `wrapIn`, `renameId`, `removeWithReferences`, Mehrfachauswahl)
- [ ] **41.2** Drag & Drop (eigene Gesten-Implementierung, `DragController`, `NodeBoundsRegistry`, reiner `DropResolver`)
- [ ] **41.3** Leinwand und Werkzeuge (Zoom/Pan, Größengriffe, Overlays, Breadcrumbs, Zwischenablage, Tastatur, PNG-Export)
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


