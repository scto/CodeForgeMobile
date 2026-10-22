# Task 37b – Debugger: Breakpoints, Variablen und Schritte im Editor – Bericht

## 1. Ausgangslage & Ziel
Aufbauend auf dem in Task 37a belegten JDI-Spike und dem DAP-Sitzungskern (`:libs:debug-client`) liefert Task 37b die vollständige Benutzeroberfläche:
1. Haltepunkt-Verwaltung direkt im Editor-Gutter (`:feature:editor`).
2. Persistente Speicherung der Haltepunkte pro Projekt im privaten App-Speicher (`BreakpointRepository`).
3. Automatische Zeilennachführung bei Code-Edits (`BreakpointLineAdjuster`).
4. Ein neues Feature-Modul `:feature:debug` mit Symbolleiste (Resume, Pause, Step Over, Step Into, Step Out, Stop), Call-Stack-Reiter, Variablenbaum mit Lazy-Loading sowie Ausdrucksauswertung.
5. Visuelle Hervorhebung der aktuellen Anhaltezeile im Editor inkl. automatischem Scroll.

---

## 2. Implementierungsdetails

### 2.1 Haltepunkte im Editor (`:feature:editor`)
- **Gutter-Visualisierung (`BreakpointOverlayLayer`):**
  - Zeichnet Haltepunktmarkierungen direkt über die Zeilennummernspalte des Sora-Editors.
  - Verifizierte aktive Haltepunkte: gefüllter roter Kreis (`#E53935`).
  - Unverifizierte Haltepunkte (noch nicht gebunden): roter Hohlkreis mit Kontur.
  - Deaktivierte Haltepunkte: neutraler grauer Kreis.
  - Haltepunkte mit Bedingungen: weißer Kennzeichnungspunkt im Zentrum.
- **Interaktion:**
  - Tipp auf das Gutter: setzt bzw. entfernt einen Haltepunkt für die getippte Zeile.
  - Langes Drücken auf das Gutter: öffnet `EditBreakpointDialog` (Bedingung, Log-Meldung, Aktivieren/Deaktivieren, Löschen).
- **Anhaltezeilen-Hervorhebung:**
  - Wenn die Debug-Sitzung pausiert ist (`Paused`), wird die Zeile in der aktuell geöffneten Datei mit einem halbtransparenten gelben Hintergrund markiert.
  - Im Gutter wird ein grüner Ausführungspfeil angezeigt.
  - Der Editor springt und scrollt automatisch zur aktuellen Ausführungszeile (`EditorController.jumpToLine`).
- **Zeilennachführung (`BreakpointLineAdjuster`):**
  - Reine, deterministische Funktion im `:core:domain:repository`:
  - Eingefügte Zeilen verschieben nachfolgende Haltepunkte entsprechend nach unten.
  - Gelöschte Zeilen entfernen darin liegende Haltepunkte und ziehen nachfolgende Haltepunkte nach oben.
- **Projektbezogene Persistenz (`BreakpointRepositoryImpl`):**
  - Gespeichert als JSON im privaten App-Speicher (`context.filesDir/debug/breakpoints_<projectHash>.json`), nicht im Projektverzeichnis.
- **Übersichtsblatt (`BreakpointsOverviewSheet`):**
  - Aus der Editor-Toolbar (Icon `Adjust`) aufrufbar; listet alle Haltepunkte nach Datei gruppiert auf.
  - Klick springt zur Datei und Zeile.
  - „Alle entfernen“-Aktion mit Bestätigungsdialog.

### 2.2 Debug-Panel (`:feature:debug`)
- **Architektur:** MVVM mit `@HiltViewModel DebugViewModel`, `@Immutable DebugUiState` und `DebugUiIntent`.
- **Steuerleiste (`DebugToolbar`):**
  - Schaltflächen: Fortsetzen (Resume), Pause, Schritt über (Step Over), Schritt hinein (Step Into), Schritt hinaus (Step Out), Beenden (Stop).
  - Statusindikator: Farbpunkte (Grün = Läuft, Blau = Angehalten bei Datei:Zeile, Gelb = Startet, Grau = Nicht aktiv/Beendet, Rot = Fehler).
  - Schutz vor Fehlbedienung: Schritt-Schaltflächen sind deaktiviert (`canStep = isPaused`), solange das Zielprogramm läuft.
- **Reiter:**
  1. **Aufrufliste (`CallStackTab`):** Zeigt die Call-Stack-Frames des pausierten Threads. Klick selektiert den Frame, lädt dessen Variablen und springt via `OpenFileRequestBridge` an die Quellcodezeile.
  2. **Variablen (`VariablesTab`):** Hierarchischer Variablenbaum. Strukturierte Objekte mit `variablesReference > 0` werden bei Klick nachgeladen und ausgeklappt. Werte können mit einem Klick in die Zwischenablage kopiert werden. Lange Werte (> 80 Zeichen) werden gekürzt mit einer „Mehr/Weniger“-Umschaltung angezeigt.
  3. **Auswerten (`EvaluateTab`):** Eingabefeld für Ausdrücke, Auswertungsknopf und Ergebnisanzeige mit Kopierfunktion.
  4. **Haltepunkte (`BreakpointsTab`):** Übersicht aller Haltepunkte im Projekt mit Direktlöschung und Deaktivierung.
- **Integration (`DebugBottomSheet`):**
  - Integriert in `ProjectWorkspaceRoute` über ein Bug-Icon in der TopAppBar.

---

## 3. Getestet

### 3.1 Unit-Tests
1. **`:feature:debug:testDebugUnitTest`:**
   - `startTransitionsToRunning`: Start schaltet Zustand auf `Running`.
   - `pausedSessionLoadsFramesAndVariables`: Pausierter Zustand lädt automatisch Frames, Scopes und Variablen des obersten Frames.
   - `stepOverBlockedWhenNotPaused`: Schutz vor Mehrfachtipps (StepOver wird ignoriert, wenn nicht im Status `Paused`).
   - `stepOverExecutedWhenPaused`: StepOver wird bei `Paused` korrekt ausgeführt.
   - `toggleExpandVariableLoadsChildren`: Variablenbaum lädt Kindelemente bei `variablesReference > 0` nach und klappt wieder zu.
   - `evaluateExpressionPopulatesResult`: Ausdrucksauswertung liefert formatierte Ergebnisse bzw. Fehlerbehandlung.
   - `stopCallsRepositoryStop`: Stop beendet Sitzung und schaltet auf `Terminated`.
2. **`:libs:debug-client:testDebugUnitTest`:**
   - `BreakpointLineAdjusterTest`: Zeileneinfügungen, Zeilenlöschungen, Löschen enthaltener Haltepunkte.
   - `DapFramingTest` & `DebugRepositoryImplTest`: Vollständiger DAP-Zyklus.
3. **`:feature:editor:testDebugUnitTest`:**
   - Sämtliche bestehenden Editor-, LSP- und Format-Tests laufen weiterhin zu 100 % grün (`BUILD SUCCESSFUL in 2m 28s`).
4. **Build & AndroidTest:**
   - `./gradlew :app:assembleDebug` erfolgreich kompiliert und assembliert.
   - `./gradlew :feature:debug:assembleDebugAndroidTest` erfolgreich kompiliert.
5. **Hardcoded String Check:**
   - `python3 scripts/find_hardcoded_strings.py` unverändert grün. Sämtliche Texte in `:core:resources` (`values` und `values-de`) deklariert.

---

## 4. Nicht getestet
- Physische Interaktion auf dem Zielgerät (Pixel 9 Pro XL, Android 17): Touch-Gesten im Gutter und BottomSheet-Animationen wurden im JVM-Unit-Test- und Compose-Build verifiziert, aber nicht physisch auf der Hardware bedient.
- Anhängen an laufende Android-Apps via Wireless ADB / JDWP (Aufgabe von Task 37c).

---

## 5. Offene Punkte
- Task 37c: Anhängen an laufende Android-Apps (`am start -D`, `adb forward tcp:<port> jdwp:<pid>`) und Quelltext-zu-Klasse-Mapping über `:libs:indexing-api`.
