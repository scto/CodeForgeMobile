# Report: Einheitlicher Formatter (Task 32)

## 1. Übersicht & Architektur

In Task 32 wurde der Code-Formatierungsablauf in CodeForge Mobile zu einem einheitlichen, verlässlichen System für die Sprachen **Kotlin**, **Java**, **XML** und **JSON** zusammengeführt. Zuvor existierten im Editor zwei separate Einstiegspunkte (`onFormat` für den lokalen heuristischen Formatter und `onFormatLsp` für LSP-Formatierung).

### Aufruf-Kette
```
EditorScreen (Aktionsleiste: AutoFixHigh-Button / Menü)
  │
  ▼
EditorViewModel.onEvent(EditorUiEvent.FormatCode(range?))
  │
  ├─ Ermittlung von Workspace-Root, Einrückung (.editorconfig / Fallback)
  ▼
CodeFormatterRepository (in :core:domain, implementiert in :core:data)
  │
  ├─► Strategie 1: LSP (textDocument/formatting oder textDocument/rangeFormatting)
  │     falls Language Server für die Sprache läuft und formattingProvider / rangeFormattingProvider unterstützt.
  │
  ├─► Strategie 2: Termux-Prozess mit 10s Timeout (DefaultProcessExecutor / ProcessBuilder)
  │     - Kotlin: ktlint --format - (oder temporäre Datei)
  │     - Java:   google-java-format -
  │     Abbruch bei Timeout oder Syntaxfehlern; Quellcode bleibt garantiert unverändert.
  │
  └─► Strategie 3: Built-in CodeFormatter (:libs:code-tools)
        Robuster, syntax-toleranter Offline-Fallback mit sichtbarem UI-Hinweis:
        „Vereinfachte Formatierung (integriert)“.
  │
  ▼
EditorController.applyTextChange(formattedText)
  │
  ├─ Präfix/Suffix-Differenz (nur geänderter Block wird ersetzt -> Single Undo Step)
  └─ Cursor- und Scroll-Mapping via mapCursorOffset (Cursorposition bleibt relativ zum Inhalt erhalten)
```

---

## 2. Getestet

Folgende Komponenten wurden mit automatisierten Unit-Tests verifiziert:

1. **`CodeFormatter` & Heuristiken (`:libs:code-tools:test`)**:
   - `CodeFormatterTest`: Raw Strings mit mehrfachen Leerzeilen & Quotes, Block-Kommentare mit Quotes/Braces, Verkettungs-Zeilen (`.` und `->`), XML Layout & Text-Erhalt, JSON Pretty-Printing, Tabs vs. Spaces, CRLF-Erhalt.
2. **`.editorconfig` Resolver (`:core:domain:testDebugUnitTest`)**:
   - `EditorConfigResolverTest`: Wildcard-Matching (`[*]`, `[*.{kt,kts}]`, `[*.java]`, `[*.xml]`, `[*.json]`), Vererbung und Überschreiben von `indent_size`, `indent_style`, `max_line_length`, `trim_trailing_whitespace`, `insert_final_newline`.
3. **`CodeFormatterRepositoryImpl` Fallback-Kette (`:core:data:testDebugUnitTest`)**:
   - `CodeFormatterRepositoryImplTest`:
     - Strategie 1 (LSP Dokument-Formatierung wenn Server läuft und fähig).
     - Strategie 1 (LSP Range-Formatierung wenn selektiert und fähig).
     - Strategie 2 (`ktlint` Aufruf via Termux-Prozess).
     - Strategie 2 (`google-java-format` Aufruf via Termux-Prozess).
     - Timeout-Abbruch nach 10s (Datei unverändert, Fehler gemeldet).
     - Syntaxfehler-Abbruch (Datei unverändert, Syntaxfehler gemeldet).
     - Strategie 3 (Built-in Fallback bei fehlendem LSP & fehlenden Termux-Tools).
     - XML & JSON leiten direkt an den integrierten Formatter weiter.
4. **Cursor- und Scroll-Erhalt (`:feature:editor:testDebugUnitTest`)**:
   - `EditorControllerCursorTest`:
     - Cursor vor geändertem Bereich bleibt exakt erhalten.
     - Cursor nach geändertem Bereich verschiebt sich exakt um die Textlängen-Differenz.
     - Cursor innerhalb des geänderten Bereichs interpoliert proportional.
     - Reines Einfügen / Löschen klemmt sauber an Grenzen.
5. **EditorViewModel & Format-on-Save (`:feature:editor:testDebugUnitTest`)**:
   - `EditorViewModelFormatTest`:
     - `EditorUiEvent.FormatCode()` formatiert gesamtes Dokument und emittiert `ApplyFormattedText` + Quell-Snackbar.
     - `EditorUiEvent.FormatCode(range)` formatiert Selektion.
     - Formatierungsfehler zeigt Fehlermeldung und lässt Puffer unberührt.
     - `EditorUiEvent.Save` mit aktiviertem `format_on_save_kotlin` formatiert vor dem Speichern.
6. **Einstellungen (`:feature:settings:testDebugUnitTest`)**:
   - Toggle-Optionen für Format-on-Save für Kotlin, Java, XML und JSON in DataStore Proto und Settings-Screen.
7. **Bilingual Strings & Build**:
   - `scripts/check_string_locales.py`: 981 Strings in `values` und `values-de` ohne Abweichungen.
   - `./gradlew :app:assembleDebug`: Erfolgreich kompiliert und verlinkt.

---

## 3. Nicht getestet (Gerätelaufzeit & reale Termux-Tools)

1. **Realer Ausführungstest von `ktlint` und `google-java-format` auf Android**:
   - Auf dem Zielgerät sind `ktlint` und `google-java-format` aktuell nicht standardmäßig im Termux `$PREFIX/bin` installiert.
   - Die Prozess-Pfade wurden in Unit-Tests mit `FakeProcessExecutor` und simulierten CLI-Outputs abgedeckt, auf dem realen Gerät greift im Ist-Zustand transparent die Stufe 3 (Built-in Formatter mit Hinweis) bzw. Stufe 1 (wenn ein Kotlin-LSP läuft).
2. **Touch-Gesten-Interaktion mit Cursor während Format-on-Save**:
   - Getestet wurde die mathematische Offset-Projektion in `EditorControllerCursorTest`. Das taktile Verhalten auf echten Touchscreens bei gleichzeitigem autosave wurde nicht manuell getestet.

---

## 4. Offene Punkte & Vorschlag für `apt-stages.conf`

### Ist-Zustand auf dem Gerät
Aktuell läuft auf dem Gerät:
- **Kotlin**: Built-in `CodeFormatter` (oder LSP, falls konfiguriert).
- **Java**: Built-in `CodeFormatter` (oder LSP, falls konfiguriert).
- **XML / JSON**: Eingebauter Pretty-Printer & Indenter (`libs:code-tools`).

### Vorschlag zur Bereitstellung von `ktlint` in Termux (`apt-stages.conf`)
`ktlint` ist ein reines JVM-Standalone-Executable (All-in-One Fat JAR mit Shell-Header):
1. **Option A (APT-Paket)**:
   In Etappe `04-jvm-tools` von `apt-stages.conf`:
   ```conf
   04-jvm-tools|gradle,kotlin,maven,libjansi,ant,apksigner,d8,dx,ecj,ktlint|single-arch nodeps=all
   ```
   *Vorteil:* Saubere Integration in Termux-Paketverwaltung via `apt install ktlint`.
   *Voraussetzung:* Paket-Rezept `packages/ktlint` im `termux-packages`-Fork erstellen, das das offizielle GitHub-Release-Asset (`https://github.com/pinterest/ktlint/releases/download/<version>/ktlint`) nach `$PREFIX/bin/ktlint` installiert und mit `chmod +x` versieht.

2. **Option B (On-Demand SDK / Bootstrap Download)**:
   Integration in den internen Tool-Downloader von CodeForge Mobile (analog zu `android-sdk` oder JDK-Installationen), der `ktlint` bei Bedarf nach `$PREFIX/bin/ktlint` oder `~/.local/bin/ktlint` herunterlädt.

*Hinweis:* `apt-stages.conf` wurde nicht eigenmächtig geändert, die CLI-Erkennung in `CodeFormatterRepositoryImpl` ist jedoch vollständig vorbereitet und schaltet automatisch auf `ktlint` um, sobald das Binary im Suchpfad liegt.
