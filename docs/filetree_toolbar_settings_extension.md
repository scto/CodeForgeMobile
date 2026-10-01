# Erweiterung der FileTreeToolbar um Schnell-Einstellungen

## Übersicht
Dieses Dokument beschreibt die Erweiterung der Komponente `FileTreeToolbar` im Modul `:feature:filetree`, um direkte Einstellungs- und Filter-Optionen für den Dateibaum bereitzustellen.

---

## Implementierte Optionen & Funktionen

Die Optionen im `DropdownMenu` der `FileTreeToolbar` ([FileTreeToolbar.kt](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/feature/filetree/src/main/kotlin/com/codeforge/feature/filetree/FileTreeToolbar.kt)) wurden um folgende Punkte erweitert:

1. **Versteckte Dateien (`showHiddenFiles`)**:
   - Checkbox zum Umschalten der Anzeige von Punkt-Dateien (`.gitignore`, `.idea`, `.gradle` etc.).
2. **Kompakte Ansicht (`isCompactMode`)**:
   - Checkbox zum Ein- und Ausschalten der verdichteten Ordneransicht.
3. **Ansichtsmodus / Project View (`viewMode`)**:
   - **Modul-Ansicht (`VIEW_MODE_MODULE`)**: Gruppiert nach Modulen, Quellcodes und Ressourcen.
   - **Projekt-Ansicht (`VIEW_MODE_PROJECT`)**: Vollständige Dateisystem-Hierarchie inklusive versteckter Konfigurationsordner.
   - **Datei-Ansicht (`VIEW_MODE_FILE`)**: Reine Dateiübersicht ohne Build-Verzeichnisse.
4. **Sortier-Reihenfolge / Sortmode (`sortOrder`)**:
   - `SORT_ORDER_ASCENDING`: Von A bis Z (aufsteigend).
   - `SORT_ORDER_DESCENDING`: Von Z bis A (absteigend).
5. **Sortieren nach / Sortby (`sortBy`)**:
   - `SORT_BY_NAME`: Alphabetische Sortierung nach Dateiname.
   - `SORT_BY_TYPE`: Sortierung nach Dateiendung/Typ.
   - `SORT_BY_SIZE`: Sortierung nach Dateigröße.
   - `SORT_BY_DATE`: Sortierung nach letztem Änderungsdatum.
6. **Suchoptionen**:
   - **Regex Suche**: Aktiviert/Deaktiviert reguläre Ausdrücke in der Schnellsuche.
   - **Groß-/Kleinschreibung**: Sensitivität bei der Dateisuche.

---

## State Binding & Integration

In [FileTreeDrawer.kt](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/feature/filetree/src/main/kotlin/com/codeforge/feature/filetree/FileTreeDrawer.kt) verwaltet `activeFileTreeConfig` den aktuellen Zustand. Ändert der Nutzer eine Option in der Toolbar, wird `activeFileTreeConfig` aktualisiert und die Baumkomponente (`CompactFileSystemTree`) rendert den Ordnerinhalt unverzüglich neu.
