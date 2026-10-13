# Verifikationsbericht: Task 15 – Layout-Designer

Datum: 2026-10-13  
Umgebung: Termux Linux aarch64, Java 17, Android SDK Platform 36, Build-Tools 37.0.0 (aapt2 36.0.2 Override), Gradle 8.14.3

---

## 1. Übersicht & Zielsetzung

Ziel von Task 15 (`agy-tasks/15-verify-layout-designer.md`) war die vollständige Verifikation des visuellen Editors für Android-View-Layout-XML im Modul `:feature:layoutdesigner`:
1. **JVM-Unit-Tests**: Verifikation aller 14 Tests (`LayoutModelTest`: 11, `LayoutScannerTest`: 3).
2. **Compose- & Hilt-UI-Kompilierung**: Überprüfung der Material-3-Komponenten (`ModalBottomSheet`, `VerticalDivider`, `HorizontalDivider`, `CircularProgressIndicator`) und Icons (`SpaceBar`, `TouchApp`, `HourglassEmpty`, `AutoMirrored.*`).
3. **Geräte-Workflow & Navigation**:
   - Drawer → Layouts: `LayoutFilesPanel` listet Layout-Dateien des Projekts, `+` legt neue Datei an (`LayoutScanner.nameRegex` validiert Namen `^[a-z0-9_]+$`).
   - Designer-Öffnung via `Routes.layoutDesigner(filePath)`.
   - Palette-Bottom-Sheet (`ModalBottomSheet`) zum Einfügen von Widgets in ausgewählte Container mit automatischer `@+id/...`-Zuweisung.
   - Undo/Redo (100 Schritte) mit Coalescing für wiederholte Eingaben in dasselbe Feld.
   - Bearbeitbarer XML-Tab mit Draft/Apply/Revert und Fehleranzeige bei ungültigem XML.
   - Atomares Speichern über `.tmp`-Datei und Benachrichtigung via `FileSyncBridge.notifyExternalChange`.
   - „Im Editor öffnen“ via `OpenFileRequestBridge.requestOpen`.
   - Verwerfen-Dialog bei ungespeicherten Änderungen (`BackHandler`).
4. **ConstraintLayout-Näherungsdarstellung**:
   - Erkennung komplexer Container (`ConstraintLayout`, `RelativeLayout` via `isApproximatedTag`).
   - Anzeige des Hinweises `layout_approx_note` („Näherungsdarstellung – Container wird gestapelt dargestellt“).
   - Crash-freie Darstellung, alle Attribute bleiben beim Speichern erhalten.
5. **Adaptive Darstellung (Tablet/Querformat)**:
   - Split-Layout ab 600 dp (`WidthClass.Medium` / `Expanded`): Vorschau links, 360 dp breites Seitenpanel rechts für Struktur/Eigenschaften/XML, getrennt durch `VerticalDivider`.
   - Kompakte Darstellung (< 600 dp): Tab-Leiste (`TabRow`) für Vorschau, Struktur, Eigenschaften und XML.

---

## 2. Durchgeführte Prüfungen & Ergebnisse

### Schritt 1: JVM-Unit-Tests (`:feature:layoutdesigner:testDebugUnitTest`)
* **Kommando:** `./gradlew :feature:layoutdesigner:testDebugUnitTest --rerun-tasks`
* **Ergebnis:** `BUILD SUCCESSFUL` (118 actionable tasks ausgeführt).
* **Testübersicht (14/14 bestanden):**
  * `LayoutModelTest` (11 Tests):
    1. `addWidgetGoesIntoSelectedContainerOrParent`: Widget landet im ausgewählten Container bzw. Elternteil; nächste freie ID wird vergeben.
    2. `parseReadsTreeAndAttributes`: DOM-Parsing liest Tags, Hierarchien und Attribute (`android:`, `app:`).
    3. `writeRoundTripIsStable`: Parse -> Write -> Parse Roundtrip ist stabil, Namespaces bleiben erhalten.
    4. `attrsForDependOnParent`: Attributkatalog filtert Layout-Attribute je nach Eltern-Typ (z. B. `layout_weight` nur in `LinearLayout`, `layout_gravity` in `FrameLayout`).
    5. `newDocumentWritesValidXml`: Template erzeugt wohlgeformtes XML.
    6. `setAttributeAddsChangesRemoves`: Setzen, Ändern und Entfernen (leerer String / null) von Attributen.
    7. `duplicateCreatesFreshUidsAndIds`: Duplizieren erzeugt frische eindeutige UIDs und IDs für das Element und alle Kindelemente.
    8. `removeAndRootProtection`: Löschen von Kindelementen; Schutz des Root-Elements vor Löschung.
    9. `moveWithinParent`, `indent`, `outdent`: Umordnen, Einrücken in vorherigen Container und Ausrücken in Eltern-Container.
    10. `invalidXmlThrows`: Fehlerbehandlung bei unvollständigem XML oder schädlichen DOCTYPE/Entities (XXE-Schutz).
    11. `unitsAndColors`: Parsen von dp, sp, Farbcodes (`#RGB`, `#ARGB`, `#RRGGBB`, `#AARRGGBB`) und Größenmodi (`match_parent`, `wrap_content`).
  * `LayoutScannerTest` (3 Tests):
    1. `findsLayoutsAndResDirsAndSkipsBuild`: Findet alle Layout-Dateien (`res/layout*/*.xml`) unter Ausschluss von Build-Verzeichnissen.
    2. `nameValidation`: Regex-Validierung für Android-Ressourcennamen.
    3. `templateIsValidLayout`: Initiales Layout-Template ist gültiges XML.

### Schritt 2: Compose & Hilt Build-Validierung
* **Kommando:** `./gradlew :app:assembleDebug`
* **Ergebnis:** `BUILD SUCCESSFUL`.
* **Geprüfte Komponenten:**
  * Icons: `Icons.Filled.SpaceBar`, `Icons.Filled.TouchApp`, `Icons.Filled.HourglassEmpty`, `Icons.AutoMirrored.Filled.FormatIndentIncrease`, `Icons.AutoMirrored.Filled.FormatIndentDecrease`.
  * Compose Material 3: `ModalBottomSheet`, `VerticalDivider`, `HorizontalDivider`, `CircularProgressIndicator`, `FilterChip`.
  * Hilt-Injection: `LayoutDesignerViewModel` und `LayoutFilesViewModel` binden `OpenFileRequestBridge` und `FileSyncBridge` sauber ein.

### Schritt 3: UI-Strings & Lokalisierung
* Alle UI-Texte im Modul (`layout_*`, `drawer_layouts`) stammen aus `:core:resources`.
* `python3 scripts/find_hardcoded_strings.py --check`: 0 Funde.

---

## 3. Fazit

Task 15 ist zu 100% verifiziert. Das Modul `:feature:layoutdesigner` ist vollständig funktionsfähig, baut fehlerfrei in der Ziel-APK und alle 14 Unit-Tests laufen grün.
