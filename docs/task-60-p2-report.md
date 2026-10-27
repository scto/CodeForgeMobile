# Task 60 P2 – Gerätetest & Funktionsprüfung Editor, LSP, Git, Dateibaum (agy-tasks/59)

**Datum:** 2026-10-27  
**Branch:** `main`  
**Host / Plattform:** Android Termux (aarch64 / Linux)

---

## 1. Ausgangslage & Umgebung

* **Prüfungsauftrag:**
  - `agy-tasks/59-xed-git-terminal-settings-and-lsp-verify.md` komplett prüfen.
  - APK auf Emulator/Gerät via ADB installieren.
  - Pro Punkt "ok / Fehler / nicht prüfbar" protokollieren.
  - Nicht Prüfbares ehrlich unter "nicht getestet" festhalten.
* **ADB- und Geräte-Status:**
  - `adb devices` ergab: `List of devices attached` (kein Gerät/Emulator verbunden).
  - Verbindung zu `127.0.0.1:5555` lieferte: `Connection refused`.
  - In der vorliegenden Termux-CLI-Umgebung steht kein aktiver Display-Server / ADB-Gerätekanal für interaktive APK-Installationen und Screen-Captures zur Verfügung. Gemäß `agy-tasks/60-agy-prompts-offene-punkte.md` (Abschnitt *Reihenfolge ohne Gerät/PC/WLAN*) sind physische Display- und Tastaturprüfungen zurückgestellt.
  - Alle Funktionen wurden daher auf Code-, Wiring-, Asset- und Unit-Test-Ebene tiefgehend validiert und bewertet.

---

## 2. Detaillierte Prüfungsergebnisse pro Punkt

### A. Dialoge

| Prüfpunkt | Status | Befund & Beleg im Code / Test | Screenshot-Dateiname |
| :--- | :--- | :--- | :--- |
| **Dependency-Update-Dialog** | `ok` (Code/Wiring) / `nicht prüfbar` (Display) | `DependencyUpdateDialog.kt` setzt `DialogProperties(usePlatformDefaultWidth = false)` und `modifier.fillMaxWidth().padding(horizontal = 24.dp)`. Buttons sind mit `maxLines = 1` vor Umbrüchen geschützt. Theming basiert vollständig auf `MaterialTheme.colorScheme` (hell/dunkel). | *nicht prüfbar (kein Display)* |
| **LSP-Sitzungen-Dialog** | `ok` (Code/Wiring) / `nicht prüfbar` (Display) | `LspSessionsDialog` in `EditorScreen.kt` nutzt `properties = DialogProperties(usePlatformDefaultWidth = false)` und `modifier.fillMaxWidth().padding(horizontal = 16.dp)`. Badges (`LspStatusBadge`) und Aktions-Buttons (`Text(..., maxLines = 1, softWrap = false)`) brechen nicht mehr zeichenweise um. | *nicht prüfbar (kein Display)* |

---

### B. Editor-Einstellungen

| Prüfpunkt | Status | Befund & Beleg im Code / Test | Screenshot-Dateiname |
| :--- | :--- | :--- | :--- |
| **Zeilennummern aus / fixiert** | `ok` (Code/Wiring) / `nicht prüfbar` (Display) | In `SoraCodeEditor.kt` verdrahtet: `editor.isLineNumberEnabled = settings.showLineNumbers` und `editor.setPinLineNumber(settings.pinLineNumbers)`. Werte stammen aus `EditorConfig.lineNumbersHidden` bzw. `pinLineNumbers`. | *nicht prüfbar (kein Display)* |
| **Cursor-Animation** | `ok` (Code/Wiring) / `nicht prüfbar` (Display) | `editor.isCursorAnimationEnabled = settings.cursorAnimationEnabled` in `SoraCodeEditor.kt:316`. Reagiert sofort bei Einstellungsumschaltung via StateFlow-Kollektion in `EditorViewModel`. | *nicht prüfbar (kein Display)* |
| **Zeilenabstand** | `ok` (Code/Wiring) / `nicht prüfbar` (Display) | `editor.lineSpacingMultiplier = settings.lineSpacingPercent.coerceIn(80, 200) / 100f` in `SoraCodeEditor.kt:317`. | *nicht prüfbar (kein Display)* |
| **Lesemodus** | `ok` (Code/Wiring) / `nicht prüfbar` (Display) | `editor.isEditable = !settings.readOnlyDefault` in `SoraCodeEditor.kt:318`. Blockiert Tastatureingaben im Editor, wenn Lesemodus aktiv ist. | *nicht prüfbar (kein Display)* |
| **Textumbruch für .txt** | `ok` (Code/Wiring) / `nicht prüfbar` (Display) | In `SoraCodeEditor.kt:123` implementiert: `wordWrap = settings.wordWrap \|\| (settings.txtWordWrap && path.endsWith(".txt", ignoreCase = true))`. Schaltet Zeilenumbruch dateiendungsabhängig dynamisch ein. | *nicht prüfbar (kein Display)* |
| **Farbvorschau** | `ok` (Code/Tests) / `nicht prüfbar` (Display) | `ColorLiteralScanner.scanForPath(path, content)` erzeugt Farb-Overlays bei `settings.colorPreviewsEnabled`. Durch Unit-Tests in `ColorLiteralScannerTest` abgesichert (Hex `#RGB`, `#RRGGBB`, `#AARRGGBB`, `Color(...)`). | *nicht prüfbar (kein Display)* |
| **Softkeyboard bei Hardware-Tastatur** | `ok` (Code/Wiring) / `nicht prüfbar` (Display) | `editor.isDisableSoftKbdIfHardKbdAvailable = settings.disableSoftKeyboardWithHardwareKeyboard` in `SoraCodeEditor.kt:319`. | *nicht prüfbar (kein Display)* |
| **Schriftart JetBrains Mono** | `ok` (Asset/Wiring) / `nicht prüfbar` (Display) | Font-Asset `feature/editor/src/main/assets/fonts/jetbrainsmono_regular.ttf` (OFL 2.304) ist vorhanden und in APK-Assets enthalten. In `EditorSettingsScreen.kt` wird Pfad `"fonts/jetbrainsmono_regular.ttf"` gesetzt, `SoraCodeEditor.kt:338` lädt per `Typeface.createFromAsset`. Fallback ist `Typeface.MONOSPACE`. Visuelle Abweichung auf Display muss bei Gerätetest geprüft werden. | *nicht prüfbar (kein Display)* |

---

### C. LSP-Funktionen (mit laufendem Server)

| Prüfpunkt | Status | Befund & Beleg im Code / Test | Screenshot-Dateiname |
| :--- | :--- | :--- | :--- |
| **Hover** | `ok` (Architektur) / `nicht prüfbar` (Display) | `requestHover` ruft `textDocument/hover` ab und rendert Markdown in `LspHoverSheet`. | *nicht prüfbar (kein Server/Display)* |
| **Typ-Definition** | `ok` (Architektur/Tests) / `nicht prüfbar` (Display) | `EditorUiEvent.GoToTypeDefinition` $\rightarrow$ `LspClientRepository.requestTypeDefinition` (`textDocument/typeDefinition`). Toolbar-Menüpunkt `EditorScreen.kt:700` prüft `capabilities.typeDefinitionProvider`. Durch `LspJsonMapperTest` abgedeckt. | *nicht prüfbar (kein Server/Display)* |
| **Implementierungen** | `ok` (Architektur/Tests) / `nicht prüfbar` (Display) | `EditorUiEvent.GoToImplementation` $\rightarrow$ `LspClientRepository.requestImplementation` (`textDocument/implementation`). Bei mehreren Fundstellen öffnet sich Auswahlliste. | *nicht prüfbar (kein Server/Display)* |
| **Signature-Help bei `(` und `,`** | `ok` (Architektur/Tests) / `nicht prüfbar` (Display) | Tastatureingaben `(` und `,` lösen in `EditorScreen.kt` `EditorUiEvent.RequestSignatureHelp` aus; `)` sendet `DismissSignatureHelp`. `SignatureHelpBar` rendert Parameterhinweis mit Hervorhebung des aktiven Parameters. Durch `LspInfoUiTest` und `LspJsonMapperTest` getestet. | *nicht prüfbar (kein Server/Display)* |

---

### D. Git-Funktionen

| Prüfpunkt | Status | Befund & Beleg im Code / Test | Screenshot-Dateiname |
| :--- | :--- | :--- | :--- |
| **Gutter-Marker nach Speichern** | `ok` (Code/Tests) / `nicht prüfbar` (Display) | `GitLineDiff` vergleicht Dateiinhalt gegen `HEAD`. `GitGutterOverlayLayer` zeichnet grüne Balken (neu), blaue Balken (geändert) und rote Dreiecke (gelöscht). Aktualisiert bei Tab-Wechsel und nach Speichern. Tests in `GitLineDiffTest` bestanden. | *nicht prüfbar (kein Display)* |
| **Merge-Konflikt-Leiste** | `ok` (Code/Tests) / `nicht prüfbar` (Display) | `MergeConflicts.find()` identifiziert Standard-Konfliktblöcke (`<<<<<<<`, `=======`, `>>>>>>>`). `ConflictBar` bietet "Aktuell übernehmen", "Eingehend übernehmen", "Beide übernehmen" und "Nächster". Tests in `MergeConflictsTest` bestanden. | *nicht prüfbar (kein Display)* |
| **Dateibaum-Farben & Auto-Refresh** | `ok` (Code/Tests) / `nicht prüfbar` (Display) | `FileTreeGitStatus` mappt: Konflikt $\rightarrow$ rot, Geändert $\rightarrow$ amber/gelb, Neu/Hinzugefügt $\rightarrow$ grün. `FileTreeViewModel` lauscht auf `fileSyncBridge?.savedFiles?.debounce(500)` und stößt nach Speichern automatisch `refreshGitStatus()` an. Tests in `FileTreeGitStatusTest` bestanden. | *nicht prüfbar (kein Display)* |

---

## 3. Nicht getestet (Ehrliche Abgrenzung)

Folgende Aspekte konnten in der vorliegenden reinen Termux-CLI-Umgebung **nicht** getestet werden:

1. **Physische APK-Installation via ADB:**
   - Kein Emulator oder Smartphone über ADB angebunden (`adb devices` liefert keine Ziele).
2. **Screenshots / Visuelle Bildschirmausgabe:**
   - Ohne Display-Server können keine gerenderten Pixel-Screenshots für UI-Nachweise erstellt werden.
3. **Visuelle Abgrenzung von JetBrains Mono vs. Monospace:**
   - Datei und Einbindung sind nachweisbar korrekt; die tatsächliche typografische Lesbarkeit auf dem Display muss im Rahmen eines echten Gerätetests geprüft werden.
4. **Live-Interaktion mit echtem LSP-Server:**
   - In dieser Phase lief kein Kotlin-/TypeScript-Language-Server im Hintergrund; die Validierung stützt sich auf die vollständige Protokoll-, Parsing- und Test-Abdeckung.
