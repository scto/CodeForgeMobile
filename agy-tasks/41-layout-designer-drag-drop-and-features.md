# Task 41 – Layout-Designer ausbauen: Drag & Drop, echte Darstellung, fehlende Features

**Ziel:** `:feature:layoutdesigner` wird von einem Palette-und-Pfeiltasten-Editor zu einem vollwertigen visuellen Layout-Editor auf dem Smartphone: Widgets per **Drag & Drop** aus der Palette in die Vorschau und den Strukturbaum ziehen, vorhandene Elemente per Drag verschieben und umhängen, Größen an Griffen ziehen, ConstraintLayout/RelativeLayout **echt** darstellen und bearbeiten, Ressourcen (`@string`, `@color`, `@dimen`, `@drawable`, Styles) auflösen, `include`/`merge` anzeigen, und beim Speichern **Kommentare und Formatierung erhalten**.

**Abhängigkeit:** keine harte. Task 15 (Verifikation, `42924e2`) ist erledigt, der Designer baut und die 14 JVM-Tests sind grün.

**Ausgangslage (aus dem Repo gelesen, vor dem Start selbst prüfen):**

| Datei (unter `feature/layoutdesigner/src/main/kotlin/com/codeforge/feature/layoutdesigner/`) | Inhalt heute |
|---|---|
| `model/LayoutNode.kt` | `LayoutNode(uid, tag, attributes, children)`, `LayoutDocument(root, nextUid)` – keine Kommentare, kein Text-Inhalt |
| `model/LayoutOps.kt` | `find`, `parentOf`, `flatten`, `insertTarget`, `freshId`, `addWidget` (immer **ans Ende** des Containers), `remove`, `duplicate`, `moveWithinParent(delta)`, `indent`, `outdent`, `setAttribute`, `changeTag` (ohne UI) |
| `model/WidgetCatalog.kt` | 18 Widgets, `AttrKind { TEXT, NUMBER, DIMENSION, SIZE, COLOR, ENUM }`, Eltern-abhängige Attribute nur für `LinearLayout`/`FrameLayout`, Palette mit 14 Einträgen |
| `model/Units.kt` | `parseDimension`, `parseColorArgb`, `parseSize` |
| `xml/LayoutXml.kt` | DOM ohne Namespaces, DOCTYPE gesperrt; `write` normalisiert (Einrückung 4, `id/width/height` zuerst), **Kommentare gehen verloren** |
| `preview/LayoutPreview.kt` | Compose-Näherung in `verticalScroll`+`horizontalScroll`, Auswahl per `clickable`; Constraint/Relative/unbekannte Container werden gestapelt (`isApproximatedTag`) |
| `LayoutDesignerContract.kt` / `LayoutDesignerViewModel.kt` / `LayoutDesignerScreen.kt` | MVI, Undo/Redo (100, Coalescing per `lastEditKey`), XML-Draft/Apply, atomares Speichern, `DevicePreset` (4 feste Größen), Compact = Tabs, ab 600 dp Split |
| `ui/DesignerPanels.kt` | `PalettePanel` (Bottom-Sheet), `SelectionBar`, `TreePanel`, `PropertiesPanel` (Editor je `AttrKind`), `XmlPanel` (TextField), `AddAttributeDialog` |
| `files/*` | `LayoutScanner`, Drawer-Bereich „Layouts“ |
| Tests | `LayoutModelTest` (11), `LayoutScannerTest` (3); **keine ViewModel-Tests** |

Offene Punkte laut `docs/layout-designer.md` („Grenzen“) und `STATUS.md` Punkt 5: kein Drag & Drop, Constraint-/Relative-Näherung, keine Ressourcen-Auflösung, kein `include`/`merge`, Speichern verliert Kommentare, Material-Komponenten nur als Platzhalter, ViewModel ungetestet.

**Gemeinsame Regeln (gelten für jeden Task):**
* Clean Architecture wie im Rest des Repos: Schnittstellen und Modelle in `:core:domain` nur, wenn andere Module sie brauchen; sonst bleibt alles in `:feature:layoutdesigner`. **Reine Logik (Modell, Drop-Berechnung, Ressourcen, Lint, XML) in Paketen ohne Android-/Compose-Abhängigkeit**, damit sie auf der JVM testbar ist. UI in MVI mit Hilt, `@Immutable`-UI-States, `StateFlow`; Material 3; `modifier: Modifier = Modifier` als erster optionaler Parameter; keine Logik in Composables. Gradle Kotlin DSL, neue Versionen nur im Version Catalog (`gradle/libs.versions.toml`); die harte Abhängigkeit `material-icons-extended:1.7.3` in `feature/layoutdesigner/build.gradle.kts` dabei in den Katalog ziehen.
* Feature-Module kennen sich nicht: Verbindung zum Editor nur über die `:core:navigation`-Bridges (`OpenFileRequestBridge`, `FileSyncBridge`).
* Alle UI-Texte über `:core:resources` (Englisch in `values/`, Deutsch in `values-de/`, Präfix `layout_`); der String-Check (Task 17) muss grün bleiben.
* Nichts erfinden: Was du nicht selbst ausgeführt hast, steht im Bericht unter **nicht getestet** (insbesondere Gerätetests; Zielgerät des Inhabers: Pixel 9 Pro XL, Android 17). Kein „funktioniert“ ohne Beleg. API-Signaturen (Compose Foundation, constraintlayout-compose) gegen die **tatsächlich aufgelöste** Version prüfen (`./gradlew :feature:layoutdesigner:dependencies --configuration debugCompileClasspath`), nicht aus dem Gedächtnis.
* Geheimnisse nie ins Log, in Dateien oder Berichte.
* Bericht in `docs/layout-designer-extended-report.md` mit Abschnitten **getestet / nicht getestet / offene Punkte**; `docs/layout-designer.md` auf den neuen Stand bringen (Abschnitt „Grenzen“ ehrlich aktualisieren); `STATUS.md` Punkt 5, `agy-tasks/TASKS-STATUS.md` und `agy-tasks/README.md` um Task 41 ergänzen (Commit-Hash eintragen).
* Build muss grün sein: `./gradlew :app:assembleDebug` und `./gradlew :feature:layoutdesigner:testDebugUnitTest`.

**Arbeitsweise (wichtig, der Task ist groß):**
* Abschnitte in der Reihenfolge **41.0 → 41.12** abarbeiten. Nach **jedem** Abschnitt: Build + Tests grün, dann ein eigener Commit `feat(layoutdesigner): 41.x <Kurztitel>`. So bleibt jeder Zwischenstand lauffähig, und ein abgebrochener Lauf kann mit `agy -c` fortgesetzt werden.
* Am Anfang `docs/layout-designer-extended-report.md` mit einer Checkliste aller Abschnitte anlegen und nach jedem Commit abhaken; beim Fortsetzen zuerst diese Liste lesen.
* Abschnitte mit **(optional)** nur angehen, wenn alle Pflichtabschnitte fertig sind.

---

## 41.0 Bestand und Ausgangsbuild
1. Die Tabelle oben gegen den Code prüfen und Abweichungen im Bericht festhalten.
2. `./gradlew :feature:layoutdesigner:testDebugUnitTest :app:assembleDebug` – Ausgangszustand (Dauer, Ergebnis) notieren.
3. **ViewModel-Tests zuerst** (Sicherheitsnetz für alles Weitere), `LayoutDesignerViewModelTest` mit Fake-Bridges und `kotlinx-coroutines-test`: Laden (Erfolg, Datei fehlt, ungültiges XML), Undo-Coalescing (mehrere Eingaben in dasselbe Feld = ein Schritt, Feldwechsel = neuer Schritt), Undo-Limit 100, Draft/Apply/Revert, `dirty` nach Undo zurück auf gespeicherten Stand = `false`, Speichern (Erfolg, Schreibfehler → Snackbar, `.tmp` wird aufgeräumt), Verwerfen-Dialog. Dateizugriff dafür hinter eine kleine Schnittstelle `LayoutFileStore` (lesen/atomar schreiben) ziehen und per Hilt binden.

## 41.1 Modell: Kommentare, Formatierung, neue Baum-Operationen
**Format erhaltendes Lesen/Schreiben:**
* Kommentare (`<!-- … -->`) vor/zwischen/nach Elementen erhalten: `LayoutNode.leadingComments: List<String>` (Kommentare direkt vor dem Element) und `trailingComments` (Kommentare nach dem letzten Kind, vor dem schließenden Tag); Kommentare vor dem Wurzelelement im `LayoutDocument`. Text-Inhalt in Elementen (selten, z. B. `<requestFocus/>` hat keinen) wird erkannt und bei Bedarf als Warnung gemeldet, nicht stillschweigend gelöscht.
* Attributreihenfolge der Quelle bleibt (ist heute schon so in der Map), `write` stellt `id/width/height` **nur bei neu angelegten Knoten** nach vorn. Einrückung der Quelle erkennen (2/4 Leerzeichen, Tabs) und beibehalten. Namespaces der Wurzel (auch eigene Präfixe, z. B. `xmlns:custom`) übernehmen statt nur `android/app/tools` zu schreiben.
* Neue Einstellung in den Designer-Optionen: „Beim Speichern neu formatieren“ (Standard **aus** = Formatierung erhalten). Persistenz über das vorhandene DataStore-Muster (`:core:datastore`) oder, falls das zu viel Kopplung erzeugt, `SavedStateHandle` + Begründung im Bericht.
* Testfall „Laden → Speichern ohne Änderung“ muss für eine Datei mit Kommentaren, 2er-Einrückung und eigenem Namespace **byte-gleich** (bis auf abschließenden Zeilenumbruch) sein.

**Neue reine Operationen in `LayoutOps.kt`** (alle liefern ein neues Dokument, ungültige Aufrufe liefern das unveränderte):
* `moveNode(uid, newParentUid, index)` – Grundlage für Drag & Drop; verboten: Wurzel verschieben, in sich selbst oder einen eigenen Nachfahren, in einen Nicht-Container.
* `insertNode(node, parentUid, index)` / `addWidgetAt(entry, parentUid, index)` – Einfügen an Position (Palette-Drop).
* `canDrop(dragged: DragPayload, parentUid): DropVerdict` mit Gründen (z. B. `ScrollView`/`NestedScrollView`/`HorizontalScrollView` erlauben **ein** Kind, `include`/`ViewStub`/Blätter erlauben keine Kinder, `merge` nur als Wurzel, `TableRow` nur in `TableLayout` sinnvoll → Warnung statt Verbot).
* `wrapIn(uids, containerTag)` (Auswahl in einen neuen Container packen, Eltern-abhängige Attribute wie `layout_weight` an den Container übertragen bzw. entfernen), `unwrap(uid)` (Container auflösen, Kinder an seine Stelle).
* `changeTag` mit Attribut-Bereinigung: Eltern-abhängige Attribute der **Kinder** prüfen, die mit dem neuen Container-Typ ungültig werden (z. B. `layout_weight` beim Wechsel Linear → Frame) → als Liste zurückgeben, UI fragt „entfernen/behalten“.
* `renameId(uid, newId)` – benennt die Id um **und** passt alle Verweise im Dokument an (`@id/x`, `@+id/x` in `layout_below`, `layout_toEndOf`, `app:layout_constraint*`, `android:labelFor`, `accessibilityTraversalBefore/After`, `app:constraint_referenced_ids` (kommagetrennt), `nextFocus*`). Kollisionen ablehnen.
* `removeWithReferences(uid)` – beim Löschen hängende Verweise auf die gelöschten Ids melden (UI: „3 Verweise werden ungültig – trotzdem löschen / Verweise entfernen“).
* Mehrfachauswahl: `selectedUids: Set<Int>` im State (Langdruck im Baum = hinzufügen); Löschen, Duplizieren, Verschieben, `wrapIn` für mehrere Knoten. Primärauswahl (zuletzt gewählt) steuert das Eigenschaften-Panel; bei Mehrfachauswahl zeigt es nur gemeinsame Attribute (gemischte Werte als „—“).

## 41.2 Drag & Drop
**Entscheidung dokumentieren:** Eigene Gesten-Implementierung (`Modifier.pointerInput` + `detectDragGesturesAfterLongPress`) **oder** Compose `Modifier.dragAndDropSource/dragAndDropTarget`. Empfehlung: eigene Implementierung, weil Drag nur innerhalb des Designers stattfindet, kein `ClipData` nötig ist, Auto-Scroll, Zoom und Drop-Vorschau voll kontrollierbar sind und die Signaturen von `dragAndDrop*` zwischen Foundation-Versionen gewechselt haben. Wähle anders nur mit Begründung im Bericht.

**Architektur:**
* `DragController` (State-Holder in der UI-Schicht, `@Stable`): aktiver `DragPayload` (`NewWidget(PaletteEntry)` | `ExistingNodes(uids)`), Zeigerposition, aktuelles `DropTarget`.
* `NodeBoundsRegistry`: jeder gezeichnete Knoten meldet seine Grenzen per `onGloballyPositioned` (im Koordinatensystem der Leinwand, Zoom berücksichtigen); entfernt sich bei `onDispose`.
* **`DropResolver` – reines Kotlin, JVM-getestet:** Eingabe = Baum, Grenzen je uid (`Rect` als eigene Daten-Klasse, nicht Compose), Zeigerposition, Payload. Ausgabe = `DropTarget(parentUid, index, indicator, verdict)` bzw. `null`.
  * Tiefster Container unter dem Zeiger gewinnt; über einem **Blatt** entscheidet die Position: oberes/unteres Drittel (vertikaler Linear-Elternteil) bzw. linkes/rechtes Drittel (horizontal) = davor/dahinter; Mitte eines Containers = hinein (ans Ende).
  * `LinearLayout`: Index aus Orientierung + Mittelpunkten der Kinder; Indikator = Linie zwischen Kindern.
  * `FrameLayout`/`CoordinatorLayout`: Ablage ans Ende; 3×3-Zonen setzen beim Ablegen `android:layout_gravity` (z. B. `bottom|end`) – nur bei neuem Widget automatisch, bei verschobenem nur, wenn die Option „Gravity beim Ablegen setzen“ an ist.
  * `ConstraintLayout`: Ablage ans Ende; neues Widget bekommt `app:layout_constraintStart_toStartOf="parent"`, `…Top_toTopOf="parent"` und Margins aus der Ablageposition (dp, auf 8-dp-Raster gerundet) – damit es nicht nach links oben springt (Details 41.4).
  * `RelativeLayout`: Ablage ans Ende + `layout_alignParentStart/Top` mit Margins aus der Position.
  * Ungültige Ziele (`canDrop` = verboten) liefern `verdict = Rejected(reason)`.
* **Drag-Quellen:** Palette-Einträge (im Bottom-Sheet: Sheet beim Drag-Start einklappen, damit die Leinwand frei ist; im Split-Layout ab 600 dp eine dauerhaft sichtbare Palette-Spalte), Knoten in der Vorschau (Langdruck), Zeilen im Strukturbaum (Langdruck auf Griff-Symbol).
* **Drop-Ziele:** Vorschau-Leinwand **und** Strukturbaum. Im Baum: obere/untere Zeilenhälfte = davor/dahinter, Mitte bei Containern = hinein; horizontaler Versatz des Fingers wählt die Ebene (wie in Dateibäumen); eingeklappte Container klappen nach 600 ms Verweilen auf.
* **Rückmeldung:** halbtransparenter Schatten des Widgets am Finger (Name + Symbol), Einfüge-Linie bzw. hervorgehobener Ziel-Container in `primary`, ungültig in `error` mit Grund als Text-Chip, haptisches Feedback beim Start und beim Wechsel des Ziels (`LocalHapticFeedback`), Auto-Scroll/Pan am Rand von Leinwand und Baum.
* **Abbruch:** Ablegen außerhalb, Zurück-Taste oder ungültiges Ziel = keine Änderung. Jeder erfolgreiche Drop = **genau ein** Undo-Schritt; neues Widget wird danach ausgewählt.
* Die bisherigen Wege (Palette antippen, Pfeil-Leiste) bleiben als barrierefreie Alternative erhalten. TalkBack: Knoten bekommen `customActions` „Nach oben/unten verschieben, Einrücken, Ausrücken“.
* Gesten-Konflikt: Die heutige `verticalScroll`/`horizontalScroll`-Leinwand wird durch Zoom/Pan aus 41.3 ersetzt; Drag startet nur nach Langdruck, Ein-Finger-Wischen ohne Langdruck pannt.

## 41.3 Leinwand und Werkzeuge
* **Zoom & Pan:** Zwei-Finger-Zoom (25–400 %), Doppeltippen = Einpassen/100 %, Zoom-Anzeige mit Schaltflächen; Zustand pro Datei während der Sitzung.
* **Gerät/Konfiguration:** bisherige `DevicePreset` + eigene Größe (Breite/Höhe dp), **Hoch-/Querformat**, **Hell/Dunkel**, **Schriftskalierung** (1.0/1.3/2.0), **Sprache** (wählt `values-<lang>` in 41.5), **Layout-Direction RTL**; Auswahl der passenden Layout-Variante aus `layout-land`, `layout-sw600dp` usw., wenn vorhanden (Hinweis „Diese Konfiguration nutzt `layout-land/x.xml`“ mit Sprung dorthin).
* **Größengriffe:** am ausgewählten Element vier Griffe; Ziehen setzt `layout_width`/`layout_height` in dp (8-dp-Raster, Raster abschaltbar), Doppeltippen auf Griff wechselt `wrap_content ↔ match_parent` (in ConstraintLayout `0dp` = match constraints). Live-Anzeige der Maße.
* **Overlays (umschaltbar):** Layout-Grenzen aller Views, Margins (orange) und Paddings (grün) des ausgewählten Elements, Blueprint-Modus (nur Umrisse + Ids), Abstandsmessung zum Elternteil/Nachbarn beim Ziehen.
* **Breadcrumb** über der Leinwand (Wurzel › … › Auswahl) zum schnellen Wechsel auf Eltern-Elemente; Tippen auf bereits gewähltes Element wählt das Elternteil („Durchklicken“).
* **Kontextmenü** (Langdruck ohne Ziehen bzw. Rechtsklick): Ausschneiden, Kopieren, Einfügen, Duplizieren, Löschen, In Container packen…, Container auflösen, Tag ändern…, Id umbenennen…, Im XML zeigen.
* **Zwischenablage:** Kopieren legt XML-Schnipsel in die System-Zwischenablage (`ClipboardManager`, Text) – dadurch auch zwischen zwei Layout-Dateien und in den Code-Editor nutzbar; Einfügen parst Schnipsel (ein oder mehrere Elemente), vergibt bei Kollision frische Ids und fügt in den gewählten Container ein.
* **Tastatur** (Hardware-Tastatur/DeX/Tablet): Strg+Z/Strg+Umschalt+Z/Strg+Y, Strg+S, Strg+C/X/V, Strg+D, Entf, Pfeile = Auswahl im Baum, Alt+Pfeil = verschieben. Über `onPreviewKeyEvent` auf Screen-Ebene.
* **Vorschau als PNG exportieren** (Teilen-Sheet, Datei in den Cache, kein Speicherrecht nötig) – über `rememberGraphicsLayer` wie in Task 27b.

## 41.4 ConstraintLayout und RelativeLayout echt darstellen und bearbeiten
* **ConstraintLayout:** Darstellung über `androidx.constraintlayout:constraintlayout-compose`. Version im Katalog ergänzen; vorher auf Maven Google prüfen, welche Version zur Compose-BOM `2025.09.00` passt, und im Bericht belegen. Reiner Mapper `ConstraintMapper` (JVM-testbar, erzeugt ein eigenes Zwischenmodell, das die Compose-Schicht in `ConstraintSet` übersetzt): `layout_constraint{Start,End,Left,Right,Top,Bottom,Baseline}_to{…}Of` (Ziel `parent` oder Id), Margins inkl. `layout_goneMargin*`, `layout_constraint{Horizontal,Vertical}_bias`, `0dp` = `Dimension.fillToConstraints`, `layout_constraintWidth_percent`, `layout_constraintDimensionRatio`, Ketten (`…_chainStyle` spread/spread_inside/packed, `…_weight`), `Guideline` (`layout_constraintGuide_begin/end/percent`), `Barrier` (`barrierDirection`, `constraint_referenced_ids`), `Group` (Sichtbarkeit). Nicht Unterstütztes (z. B. `Flow`, `Layer`, `MotionLayout`) → gestapelte Näherung **nur für dieses Kind** + Hinweis, nicht für das ganze Layout.
* **Constraint-Editor:** Am ausgewählten Kind einer ConstraintLayout erscheinen Anker (Kreise an den vier Seiten, Baseline-Griff bei Text). Anker auf Anker eines Geschwisters, der Guideline oder des Elternrands ziehen = Constraint setzen (ein Undo-Schritt); Tippen auf gesetzten Anker = Constraint entfernen. Pfeile zwischen verbundenen Ankern zeichnen. Eigenschaften-Panel bekommt eine Constraint-Karte (Mini-Diagramm mit Margins je Seite, Bias-Schieberegler, Breite/Höhe-Modus fixed/wrap/match constraints). Schaltflächen: „Horizontal zentrieren“, „Vertikal zentrieren“, „Alle Constraints entfernen“, „Guideline hinzufügen (horizontal/vertikal)“.
* **Warnungen** (gehen in 41.8 ein): Kind ohne horizontalen/vertikalen Constraint („springt zur Laufzeit nach 0,0“), Verweis auf nicht vorhandene Id, zirkuläre Constraints.
* **RelativeLayout:** eigener `Layout`-Composable mit Mess-Logik für `layout_alignParent*`, `layout_centerInParent/Horizontal/Vertical`, `layout_below/above/toStartOf/toEndOf/toLeftOf/toRightOf`, `layout_align{Start,End,Top,Bottom,Baseline}`; Auflösungsreihenfolge per topologischer Sortierung in reinem Kotlin (JVM-Test), Zyklen → Hinweis + Näherung. Bearbeitung über Eigenschaften-Panel mit Id-Auswahlliste der Geschwister.
* `isApproximatedTag` entsprechend verkleinern; der Hinweis „Näherungsdarstellung“ erscheint nur noch, wo wirklich genähert wird.
* Eltern-abhängige Attribute in `WidgetCatalog.attrsFor` für ConstraintLayout, RelativeLayout, `GridLayout` (`layout_row/column/rowSpan/columnSpan/columnWeight`), `TableRow` (`layout_column/span`), `CoordinatorLayout` (`layout_anchor`, `layout_anchorGravity`, `layout_behavior`) ergänzen.

## 41.5 Ressourcen auflösen
* **`ResourceRepository` (reines Kotlin, JVM-getestet)** für das Modul der geöffneten Datei: `res`-Ordner aus dem Dateipfad ableiten (`…/src/<sourceSet>/res/layout*/x.xml`), alle `res`-Ordner der Source-Sets dieses Moduls (`main` + `debug` usw.) einbeziehen; optional die Module aus `settings.gradle(.kts)`, von denen das Modul abhängt (nur `project(":…")`-Abhängigkeiten aus der Build-Datei, per einfacher Textsuche – keinen Gradle-Lauf starten).
  * Werte aus `values*/` (`string`, `plurals` (Form „other“), `string-array` (erstes Element für Spinner/ListView-Platzhalter), `color`, `dimen`, `integer`, `bool`, `style` mit `parent` (explizit und per Punkt-Notation), `attr`/`declare-styleable` für eigene Views).
  * Qualifizierer-Auswahl passend zur Konfiguration aus 41.3: `night`, Sprache/Region (`de`, `de-rCH`, `b+de+CH`), `land`, `sw<N>dp`, `w<N>dp`, `v<N>`; Auswahl nach den Android-Regeln vereinfacht (dokumentieren, was nicht unterstützt wird).
  * Verweisketten (`@color/a` → `@color/b`) mit Tiefenlimit und Zyklenerkennung.
  * Theme-Attribute `?attr/…`/`?android:attr/…`: Theme aus `tools:context` → Activity im `AndroidManifest.xml` (`android:theme`) → `application`-Theme ermitteln; Werte aus dem eigenen Theme und seinen Eltern lesen. Für Framework-/Material-Themes ohne Quelltext eine kleine eingebaute Tabelle (Material 3 hell/dunkel: `colorPrimary`, `colorOnPrimary`, `colorSurface`, `colorOnSurface`, `colorSecondary`, `colorError`, `textColorPrimary`, `colorControlNormal`, `selectableItemBackground` (= transparent)); alles andere bleibt sichtbar unaufgelöst.
* **Drawables:** PNG/JPG/WebP aus `drawable*/`/`mipmap*/` (Dichte passend wählen, `BitmapFactory` mit `inSampleSize`, Cache mit Größenlimit); `vector` → Compose `ImageVector` über eigenen Parser mit `PathParser` (`pathData`, `fillColor`, `strokeColor`, `strokeWidth`, `group` mit Transformationen, `viewportWidth/Height`, `tint`); `shape` (rectangle/oval, `solid`, `stroke`, `corners`, `gradient` linear) und `selector`/`layer-list` (erster passender bzw. alle Ebenen) vereinfacht; `@android:drawable/…` und Bibliotheks-Drawables → Platzhalter mit Name.
* **Anwenden in der Vorschau:** `android:text`, `hint`, `textColor`, `textSize`, `background` (Farbe oder Drawable), `src`/`srcCompat`, `tint`, Dimensionen, `style="@style/…"` (Style-Items als Basis, direkte Attribute überschreiben), `android:textAppearance`, `fontFamily` (nur `sans-serif`, `serif`, `monospace` + `res/font` TTF/OTF).
* **`tools:`-Attribute auswerten:** `tools:text`, `tools:src`, `tools:visibility`, `tools:background`, `tools:layout` (bei `fragment`/`FragmentContainerView`), `tools:listitem` + `tools:itemCount` (RecyclerView/ListView zeigen das Item-Layout n-mal), `tools:showIn`, `tools:parentTag` (für `merge`). Designer-Option „Tools-Attribute anzeigen“ (Standard an).
* **Aktualisierung:** Änderungen an Ressourcen-Dateien (über `FileSyncBridge` und beim Zurückkehren in den Designer per Datei-Zeitstempel) laden die betroffenen Teile neu; Laden im Hintergrund (`Dispatchers.IO`), die Vorschau zeigt bis dahin Rohwerte.
* **Eigenschaften-Panel:** Felder vom Typ Text/Farbe/Dimension/Drawable bekommen einen **Ressourcen-Auswähler** (Suche über vorhandene Ressourcen des passenden Typs, Vorschau des aufgelösten Werts, auch `?attr/…`) und die Aktion **„In Ressource auslagern“**: legt `strings.xml`/`colors.xml`/`dimens.xml` unter `values/` an bzw. ergänzt sie (Format erhaltend, alphabetisch oder ans Ende – Einstellung), fragt bei Strings optional nach der Übersetzung für vorhandene `values-<lang>/`-Ordner, ersetzt den Wert durch `@string/name`; Namensvorschlag aus Id + Attribut, Kollisionen prüfen. Ein Undo-Schritt im Layout; die Ressourcen-Datei wird atomar geschrieben und über `FileSyncBridge.notifyExternalChange` gemeldet.
* Aufgelöste Werte im Panel als grauer Hinweis neben dem Rohwert anzeigen (`@string/title` → „Willkommen“).

## 41.6 `include`, `merge`, `ViewStub`, Fragmente, eigene Views
* `<include layout="@layout/x">`: eingebundenes Layout lesen und **schreibgeschützt** zeichnen (gestrichelter Rahmen, Name), überschriebene `layout_*`-Attribute und `android:id` des `include` anwenden; Zyklenerkennung und Tiefenlimit (z. B. 8); Aktion „Eingebundenes Layout öffnen“ (navigiert zur Designer-Route der Datei, vorher Ungespeichertes abfragen). Palette-Eintrag „Include…“ mit Layout-Auswahl.
* `<merge>` als Wurzel: Kinder in einem Container nach `tools:parentTag` (Standard `FrameLayout`) zeichnen, Hinweis anzeigen.
* `ViewStub` (Platzhalter mit `android:layout` + Option „aufgeklappt anzeigen“), `<fragment>`/`FragmentContainerView` (Platzhalter bzw. `tools:layout`), `<requestFocus/>` und `<tag>` werden gelesen, erhalten und nicht als Widgets gezählt.
* **Eigene Views:** Klassen im Projekt finden, die von `View`/`ViewGroup` (oder bekannten Unterklassen) erben – über die vorhandene Projekt-Indexierung (`:libs:indexing-api`, prüfen, was sie liefert), sonst per Textsuche nach `class X(…) : (View|ViewGroup|…)` in `src/**/*.kt|java`. In der Palette unter „Projekt“; Darstellung als beschrifteter Platzhalter (bzw. als Container, wenn `ViewGroup`), Attribute aus `declare-styleable` des gleichnamigen Styleables.

## 41.7 Widget-Katalog und Eigenschaften-Panel
* **Katalog erweitern** (jeweils mit sinnvollen Standardwerten, Attributen und Compose-Näherung in der Vorschau, Material-Optik über die vorhandenen Material-3-Composables):
  * Layouts: `NestedScrollView`, `GridLayout`, `TableLayout`/`TableRow`, `RadioGroup`, `CoordinatorLayout`, `AppBarLayout`, `CollapsingToolbarLayout` (statisch), `androidx.cardview.widget.CardView`, `MaterialCardView`, `ChipGroup`, `FlexboxLayout` (nur wenn im Projekt als Abhängigkeit gefunden, sonst Platzhalter).
  * Widgets: `MaterialButton` (filled/outlined/text/tonal über `style`), `TextInputLayout` + `TextInputEditText`, `Chip`, `FloatingActionButton`/`ExtendedFloatingActionButton`, `MaterialToolbar`/`Toolbar`, `BottomNavigationView`, `NavigationRailView`, `TabLayout`, `ViewPager2` (Platzhalter), `RecyclerView`/`ListView`/`GridView` (mit `tools:listitem`), `Spinner`, `SeekBar`, `Slider`, `RatingBar`, `ToggleButton`, `MaterialSwitch`, `MaterialDivider`, `ShapeableImageView`, `ProgressBar` horizontal/kreisförmig (`style`), `LinearProgressIndicator`/`CircularProgressIndicator`, `WebView`/`VideoView`/`SurfaceView`/`TextureView` (Platzhalter), `AutoCompleteTextView`, `ImageButton`, `ComposeView` (Platzhalter mit Hinweis).
* **Vollständige Attributliste aus dem SDK (empfohlen):** Wenn ein Android-SDK gefunden wird (`$ANDROID_HOME`/`$PREFIX/opt/android-sdk`, höchste `platforms/android-N`), `data/res/values/attrs.xml` und `attrs_manifest.xml` parsen (reines Kotlin, Ergebnis cachen): Attributnamen je Styleable, Formate (`dimension|reference|color|boolean|integer|float|string|enum|flag`) und **Enum-/Flag-Werte**. Für Material-Komponenten optional `values.xml` aus der Material-AAR im Gradle-Cache (`~/.gradle/caches/**/material-*.aar`). Ohne SDK fällt der Katalog auf die eingebauten Attribute zurück. Neue `AttrKind`-Werte: `BOOLEAN`, `FLAG` (Mehrfachauswahl, z. B. `gravity`, `inputType`, `textStyle`), `REFERENCE`, `FLOAT`, `INTEGER`, `ID` (Auswahl aus vorhandenen Ids).
* **Panel-Verbesserungen:** Suchfeld, Filter „nur gesetzte Attribute“, Gruppen (Layout, Abstände, Text, Aussehen, Verhalten, Barrierefreiheit, Constraints, Sonstiges) einklappbar; Dimension-Feld mit Einheiten-Auswahl (dp/sp/px) und +/−-Tasten; Margin/Padding als Box-Editor (alle/horizontal/vertikal/je Seite, `Start/End` statt `Left/Right` bevorzugt); Farbwähler (HSV + Alpha, Hex, zuletzt verwendet, Ressourcen); Flag-Editor mit Checkboxen; „Attribut zurücksetzen“; Tag ändern (Combobox mit Katalog, Bereinigung aus 41.1); Id-Umbenennen über `renameId`.
* **Palette:** Suche, Kategorien als Chips, „Zuletzt verwendet“ und Favoriten (pro Gerät gespeichert), Anzeige des vollen Klassennamens bei langem Druck.

## 41.8 Prüfungen (Lint) mit Schnellkorrektur
Reines Kotlin `LayoutLinter` (JVM-getestet, jede Regel mit eigenem Test), Ergebnis als Liste `LayoutIssue(uid, severity, ruleId, message, quickFix?)`. Anzeige: Warn-Badge am Element in Vorschau und Baum, eigenes Panel/Tab „Probleme“ mit Sprung zum Element, Zähler in der Topbar. Regeln mindestens:
* Doppelte Ids; Verweis auf fehlende Id; Verweis auf fehlende Ressource (`@string/x` existiert nicht).
* Fest codierter Text in `text`/`hint`/`contentDescription` → Schnellkorrektur „In Ressource auslagern“ (41.5).
* `ImageView`/`ImageButton` ohne `contentDescription` (und ohne `importantForAccessibility="no"`).
* Klickbare Elemente kleiner als 48 × 48 dp (soweit aus festen Maßen bestimmbar).
* `ScrollView` mit mehr als einem Kind; verschachtelte `layout_weight` in verschachtelten LinearLayouts; `layout_weight` mit `wrap_content` statt `0dp` in Gewichtsrichtung (Schnellkorrektur `0dp`).
* `fill_parent` (Schnellkorrektur `match_parent`), `px`-Einheiten, `sp` für Nicht-Text-Maße bzw. `dp` für `textSize`.
* `Left/Right` statt `Start/End` (Schnellkorrektur, RTL).
* ConstraintLayout-Kinder ohne horizontalen/vertikalen Constraint; zirkuläre Constraints.
* Unnötiger Eltern-Container (ein Kind, keine Hintergrund-/Padding-Attribute) → Schnellkorrektur „Container auflösen“.
* Zu tiefe Verschachtelung (> 10 Ebenen) als Hinweis.
Die Prüfung läuft entprellt (z. B. 300 ms nach der letzten Änderung) im Hintergrund.

## 41.9 Zusammenspiel mit dem Code-Editor und Dateien
* **Externe Änderungen:** Ist die Datei im Code-Editor geöffnet und wird dort geändert/gespeichert (`FileSyncBridge`), lädt der Designer neu, wenn er selbst keine ungespeicherten Änderungen hat; sonst Dialog „Datei wurde extern geändert: neu laden / meine Version behalten / vergleichen (XML-Tab mit beiden Ständen)“. Beim Öffnen des Designers ungespeicherte Editor-Puffer der Datei vorher speichern lassen (`requestFlushDirectory` bzw. passende Bridge-Methode prüfen).
* **XML-Tab:** Auswahl synchronisieren – Element gewählt → zugehöriger XML-Bereich wird markiert und dorthin gescrollt; Cursor im XML (nach „Übernehmen“) wählt das Element. Dafür beim Parsen Zeilen-/Spaltenbereiche je Knoten mitführen (SAX/`XmlPullParser` statt DOM, falls DOM keine Positionen liefert – Entscheidung begründen; XXE-Schutz beibehalten). Einfaches Syntax-Highlighting (Tags, Attribute, Werte, Kommentare) per `AnnotatedString`; Fehlermeldungen mit Zeile/Spalte und Sprung dorthin.
* **Vorlagen für neue Layouts** im Drawer-Dialog „Neues Layout“: Leer (Constraint), Leer (Linear vertikal), Listeneintrag (Bild + zwei Zeilen), Formular (zwei Eingabefelder + Button), Scroll-Seite mit Toolbar. Vorlagen als Kotlin-Strings im Modul (keine Assets), alle mit Ressourcen-freiem, lint-sauberem XML (außer fest codierten Beispieltexten, die mit `tools:text` statt `android:text` arbeiten).
* **Layout-Varianten:** Aktion „Variante anlegen…“ (Auswahl aus gängigen Qualifizierern: `land`, `sw600dp`, `sw840dp`, `night`, `ldrtl`, `v<N>`, frei eingebbar mit Prüfung auf gültige Qualifizierer-Reihenfolge) kopiert die Datei in den passenden Ordner und öffnet sie; Umschalter zwischen vorhandenen Varianten in der Topbar.
* **Zustand bei Prozess-Tod:** Dateipfad, Tab, Gerät, Auswahl und – falls `dirty` – das aktuelle XML in `SavedStateHandle` sichern; beim Wiederherstellen Hinweis „Ungespeicherte Änderungen wiederhergestellt“. Undo-Verlauf darf verloren gehen (im Bericht vermerken).

## 41.10 Leistung und Robustheit
* Testlayout mit **500 Knoten** (generiert im Test) und eine echte große Layoutdatei: Parse/Write/Lint/DropResolver auf der JVM messen (Ziel jeweils < 50 ms auf dem Entwicklungsrechner, Messwerte in den Bericht; auf dem Gerät „nicht gemessen“, wenn nicht gemessen).
* Compose: `key(node.uid)` in allen Listen, stabile Lambdas, Vorschau-Knoten nur bei Änderung ihres Teilbaums neu zeichnen (Teilbäume sind unveränderlich – `remember(node)` reicht); Undo-Stapel speichert Dokumente (strukturelles Teilen der unveränderten Teilbäume ist bereits gegeben – nicht zusätzlich kopieren).
* Fehler in der Vorschau eines einzelnen Knotens (z. B. kaputtes Drawable) dürfen nie den Screen abstürzen lassen: Platzhalter mit Fehlermeldung am Knoten.
* XML mit unbekannten Tags, Entities (`&amp;`), Unicode, sehr langen Attributwerten und CRLF-Zeilenenden korrekt lesen und unverändert zurückschreiben (Tests).

## 41.11 (optional) View-XML → Jetpack-Compose-Code
Aktion „Als Compose kopieren/exportieren“: Generator (reines Kotlin, JVM-getestet) erzeugt aus dem Baum eine `@Composable`-Funktion mit `@Preview`: `LinearLayout` → `Column`/`Row` (+ `Modifier.weight`), `FrameLayout` → `Box` (+ `align`), `ScrollView` → `Column(Modifier.verticalScroll(...))`, `ConstraintLayout` → `ConstraintLayout` aus `constraintlayout-compose` mit `createRefs`, `TextView` → `Text`, `Button`/`MaterialButton` → `Button`/`OutlinedButton`/`TextButton`, `EditText`/`TextInputLayout` → `OutlinedTextField` (mit `remember { mutableStateOf("") }`), `ImageView` → `Image(painterResource)`, `CheckBox`/`Switch`/`RadioButton`, `RecyclerView` → `LazyColumn`-Gerüst, `@string/x` → `stringResource(R.string.x)`, `@color/x` → `colorResource`, `@dimen/x` → `dimensionResource`, Margin → äußerer `padding`, Padding → innerer `padding` (Reihenfolge der Modifier korrekt), `visibility=gone` → weggelassen + Kommentar. Nicht Abbildbares als `// TODO: <tag> manuell umsetzen`. Ziel: Datei im passenden `src/main/kotlin`-Paket anlegen oder in die Zwischenablage; Ergebnis mit dem Compose-Preview-Feature (Task 26/27) prüfbar. Test: generierter Code für 3 Beispiel-Layouts als Golden-Files.

## 41.12 Tests, Doku, Abnahme
**JVM-Tests (Pflicht, je Regel/Fall ein Test):**
* `LayoutXml`: Round-trip byte-gleich (Kommentare, Einrückung 2/4/Tab, eigene Namespaces, CRLF, Entities), Positionen je Knoten, XXE weiterhin abgelehnt.
* `LayoutOps`: `moveNode` (gültig, in Nachfahren verboten, Wurzel verboten, Index-Grenzen), `addWidgetAt`, `canDrop`-Regeln, `wrapIn`/`unwrap`, `changeTag`-Bereinigung, `renameId` mit allen Verweisarten, `removeWithReferences`, Mehrfachauswahl-Operationen, Einfügen aus Zwischenablage mit Id-Kollision.
* `DropResolver`: vertikal/horizontal Linear (Anfang, Mitte, Ende, zwischen Kindern), Blatt-Drittel, verschachtelte Container (tiefster gewinnt), Frame-Zonen → `layout_gravity`, Constraint-Ablage → Constraints + gerundete Margins, Zoom-Faktor, ungültige Ziele.
* `ConstraintMapper`, RelativeLayout-Sortierung (inkl. Zyklus), `ResourceRepository` (Qualifizierer-Auswahl, Ketten, Zyklus, Styles mit Eltern, Theme-Attribute), Vector-Drawable-Parser, `LayoutLinter` (jede Regel + jede Schnellkorrektur), SDK-`attrs.xml`-Parser (mit kleiner Fixture, nicht der echten Datei), Compose-Generator (falls 41.11).
* `LayoutDesignerViewModelTest` (aus 41.0) erweitert: Drop = ein Undo-Schritt, Mehrfachauswahl, externe Änderung mit/ohne `dirty`, „In Ressource auslagern“ (Layout + Ressourcen-Datei), Wiederherstellung aus `SavedStateHandle`.

**Instrumentierte Tests (kompilieren müssen sie, `./gradlew :feature:layoutdesigner:assembleDebugAndroidTest`; Ausführung nur, wenn ein Gerät/ADB verfügbar ist, sonst „nicht ausgeführt“):** Palette-Drag in die Leinwand mit `performTouchInput { longClick(); moveTo(); up() }`, Baum-Drag, Constraint-Anker ziehen, Zoom-Geste.

**Doku:** `docs/layout-designer.md` vollständig aktualisieren (Bedienung inkl. Gesten-Übersicht, Aufbau mit neuen Paketen, Entscheidungen Drag-Implementierung/Parser, ehrliche Grenzen); `docs/device-test-checklist.md` um Layout-Designer-Fälle ergänzen (Drag aus Palette, Drag im Baum, Größengriffe, Constraint-Anker, Ressourcen-Auflösung, Auslagern, externe Änderung, Querformat/Tablet, Hardware-Tastatur, TalkBack).

**Abnahme:**
* `./gradlew :feature:layoutdesigner:testDebugUnitTest :feature:layoutdesigner:assembleDebugAndroidTest :app:assembleDebug` grün; String-Check grün; keine neuen ktlint/detekt-Fehler im Modul (Konvention aus `build-logic`).
* Eine echte Layout-Datei mit ConstraintLayout, Kommentaren und `@string`-Verweisen aus einem mit dem Projekt-Wizard erzeugten Projekt: laden → ohne Änderung speichern = unverändert; Widget per Drag einfügen und verschieben → XML enthält das Element an der erwarteten Stelle mit gültigen Constraints (Test oder dokumentierter Gerätelauf).
* Bericht `docs/layout-designer-extended-report.md` mit **getestet / nicht getestet / offene Punkte**, Messwerten aus 41.10, gewählten Bibliotheksversionen mit Beleg, Liste der übersprungenen optionalen Punkte; `STATUS.md`, `agy-tasks/TASKS-STATUS.md`, `agy-tasks/README.md` aktualisiert.

**Aufruf:**
```bash
cd ~/CodeForgeMobile
agy --mode plan -i "Lies agy-tasks/41-layout-designer-drag-drop-and-features.md und führe den Auftrag aus. Beginne mit 41.0, committe nach jedem Abschnitt."
# Fortsetzen nach Abbruch:
agy -c
```
