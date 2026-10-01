# CodeForge Mobile - Release & Entwickler-Dokumentation

## Übersicht aller durchgeführten Erweiterungen

In diesem Release wurden umfassende Überarbeitungen und Neuerungen in den Modulen **Dateibaum (`:feature:filetree`)**, **Theme-Studio (`:feature:themebuilder`)**, **Editor (`:feature:editor`)**, **Einstellungen (`:feature:settings`)** sowie **Erweiterungen (`:feature:settings:extensions`)** umgesetzt.

---

## 1. Dateibaum (FileTree) Optimierungen & Erweiterungen

### A. Dynamische Einrückungslinien (Indentation Lines)
- **Problemursache**: Vorherige Versionen benutzten zu schmale Einrückungsbreiten (14dp) und zeichneten Linien mit hoher Deckkraft (0.6f), was bei mehrstufigen Unterordnern wie gestapelte vertikale Striche (`|||`) eng am Icon wirkte. Bei ausgeschalteten Linien fehlte zudem die Einrückung.
- **Lösung**: 
  - Einrückungs-Spaltenbreite auf zentrierte `20.dp * uiScale` angepasst.
  - Die Einrückungs-Boxen werden für jede Ebene (`level > 0`) konsistent gerendert (so bleiben tiefere Ordner stets eingerückt), während die eigentliche Linie innerhalb von `drawBehind` gesteuert wird.
  - Dezent abgestimmte Linienstärke `1.dp.toPx()` mit Transparenz `alpha = 0.35f`.

### B. Dynamische Zeilenhöhe & 2-Zeilige Details-Darstellung
- **Problemursache**: Uninitialisierte Protobuf-Standardwerte führten dazu, dass `showFileDetails` beim ersten Start `false` war und keine Datei-Metadaten angezeigt wurden.
- **Lösung**:
  - Standardwert in `FileTreeDrawer.kt` auf `true` gesetzt, wenn kein abweichendes Protobuf-Config vorliegt.
  - `nodeIconSize` in `BonsaiStyle` wird dynamisch gesteuert: `if (showFileDetails) 38.dp * uiScale else 24.dp * uiScale`.
  - Dateidetails-Formatierung: Dateien zeigen `<Größe> • <Änderungsdatum>` (z. B. `4.2 KB • 25.09.26 20:28`), Ordner zeigen `Ordner • <Änderungsdatum>` (z. B. `Ordner • 25.09.26 20:28`) mit Fallback auf `java.io.File`, falls Okio Metadaten `null` sind.

### C. Ganzzeilige Klick- & Tap-Reaktion (Full Row Touch Target)
- Das innere Namenslayout nutzt `fillMaxWidth()`, wodurch die gesamte Zeilenbreite des Dateibaum-Drawers auf Klicks und Gesten reagiert.

### D. Kontextmenü-Aktionen & Dialoge
- **Eigenschaften (Properties)**: Zeigt einen Dialog mit detaillierten Dateiattributen:
  - Dateiname & absoluter Pfad
  - Typ (Datei / Ordner)
  - Formatierte Dateigröße
  - Zuletzt geändert am (Datum & Uhrzeit)
  - Berechtigungen (`R`/`W`/`X` sowie `[Versteckt]`)
- **Löschen**: Zeigt vor dem Ausführen einen Bestätigungsdialog ("Möchtest du '$name' wirklich unwiderruflich löschen?").
- **Ausschneiden / Kopieren / Einfügen / Umbenennen / Pfad kopieren**: Vollständig mit Zwischenablage-Indikator verknüpft.

---

## 2. Theme Studio & Theme Presets

- **15 Vordefinierte Themes**: Monokai, Darcula, Quiet Light, Ayu Dark, Solarized Dark, GitHub Light, VS Code Dark+, Notepad++, Eclipse, Cyberpunk Neon, Forest, Ocean, Sunset, Monochrome, Violet in `ThemePresets.kt`.
- **Visuelle Themen-Vorschau**: Horizontale scrollbare `LazyRow` mit Farbpaletten-Vorschau-Karten für schnelle Theme-Auswahl.
- **Farbauswahl & Custom Color Picker**: 10 vorgefertigte Farb-Presets sowie ein interaktiver RGB-Colorpicker (`ColorPickerDialog`) für individuelle Akzentfarben.

---

## 3. SoraEditor Scrollbalken-Verhalten

- **Scrollbar-Steuerung**: In `SoraEditorAppearance.kt` werden `editor.isVerticalScrollBarEnabled` und `editor.isHorizontalScrollBarEnabled` nun strikt über die Konfiguration `config.scrollbarEnabled` aktiviert bzw. deaktiviert.

---

## 4. Einstellungen-Struktur & Erweiterungen (Extensions) Screen

### A. Überarbeitete Reihenfolge der Einstellungen im SettingsHub
1. **Erscheinungsbild & Theme**: Farbschema, Dark/Light Mode & Theme Studio
2. **Editor**: Zeilenumbruch, Schriftart, Einrückung, Formatierung & Verhalten
3. **Dateibaum & Ansichten**: Sortierung, Versteckte Dateien, Module/Projekt/Datei-Ansichtsmodus
4. **Terminal**: Distro-Auswahl, Virtuelle Tasten & Farbschemas
5. **SDK Manager**: Build-Tools, NDK, CMake & Java 17
6. **Plugins & Erweiterungen (Extensions)**: Language Server, Tooling Bridge & Erweiterungen
7. **Debug & Diagnose**: Logging-Level, Tracing & Systemdiagnose
8. **Über CodeForge Mobile**: App-Version, Lizenzen & Entwicker-Infos

### B. Erweiterungen-Manager (`ExtensionsScreen.kt` & `ExtensionsManager.kt`)
- Verwaltet den Download, die SHA-256 Checksummen-Verifizierung, die Entpackung (ZIP/JAR) und das Deinstallieren von LSP-Sprachservern (z. B. Kotlin Language Server, Java JDT LS, Clangd).
- Navigationsroute: `Routes.EXTENSIONS` in `CodeForgeNavHost.kt`.

---

## 5. Build & Verifizierung

- **Gradle Build**: Erstellt über `bash gradlew assembleDebug`.
- **APK Pfad**: `app/build/outputs/apk/debug/app-debug.apk` (Größe: ca. 63 MB).
