# Test Runner Report (Task 36)

## Übersicht
Der Test-Läufer ermöglicht das Ausführen von Unit-Tests (alle Tests, einzelne Klassen, einzelne Methoden, fehlgeschlagene Tests wiederholen) direkt in CodeForge Mobile. Die Testergebnisse werden aus JUnit-XML-Dateien (`build/test-results/<task>/TEST-*.xml`) über einen puren Kotlin-DOM-Parser geparst (kein fehleranfälliges Console-Scraping). Fehlerhafte Tests werden im Ergebnisbaum übersichtlich dargestellt, Stack-Traces sind anklickbar und springen direkt an die Quellcodezeile im Editor. Zudem werden Gutter-Icons neben `@Test`-Methoden und Testklassen im Editor angezeigt.

---

## 1. Getestet

### Domain & Parser (`:core:domain`)
- **`JUnitXmlParserTest`**:
  - Parsen von erfolgreichen Tests (`junit_passed.xml`).
  - Parsen von fehlschlagenden Tests (`junit_failed.xml`) mit Extraktion von Assertion-Fehlern, `expected` und `actual` Werten für Diff-Vergleiche.
  - Parsen von Fehlern/Exceptions (`junit_error.xml`) und Stack-Trace-Zerlegung in strukturierte `StackFrame`-Objekte.
  - Parsen von übersprungenen/ignorierten Tests (`junit_skipped.xml`).
  - Parsen von parametrisierten Tests (`junit_parameterized.xml`, z.B. `testAddition[0]`).
  - Parsen und Zusammenführen mehrerer Suiten bzw. Test-XML-Dateien (`junit_multi_suites.xml`).
  - Schutz vor XML External Entity (XXE) Angriffen in `DocumentBuilderFactory`.
- **`TestCommandBuilderTest`**:
  - Generierung der korrekten Gradle `--tests`-Argumente für das gesamte Projekt (`testDebugUnitTest`).
  - Generierung für einzelne Testklassen (`--tests "com.example.MyTest"`).
  - Generierung für einzelne Testmethoden (`--tests "com.example.MyTest.myMethod"`).
  - Generierung für das Wiederholen fehlgeschlagener Tests (`--tests "..." --tests "..."`).
- **`ProjectStackFrameResolverTest`**:
  - Auflösung von Stacktrace-Zeilen zu Quellcodedateien im Modulpfad (`src/test/kotlin`, `src/test/java`, `src/main/kotlin`, `src/main/java`).
  - Fallback-Suche bei abweichenden Verzeichnisstrukturen.

### Editor-Integration (`:feature:editor`)
- **`TestTargetScannerTest`**:
  - Erkennung von `@Test`-annotierten Methoden in Kotlin- und Java-Dateien.
  - Erkennung von Testklassen (`class ...Test`, `class ...Tests`, `class ...Spec`).
  - Maskierung von Kommentaren (`// ...`, `/* ... */`) und String-Literalen, um Fehlalarme (False Positives) auszuschließen.
- **`EditorOverlay` & `SoraCodeEditor`**:
  - Anzeige von `TestRunChip`-Gutter-Overlays neben Testklassen und `@Test`-Methoden.
  - Auslösen von `TestRunRequest` über `TestRunRequestBridge`.

### Repository & UI-Logik (`:feature:build`)
- **`TestRunnerRepositoryImplTest`**:
  - Erkennung und Laden von `TEST-*.xml` aus Projektverzeichnissen.
  - Aggregation von Modul-, Klassen- und Methodenergebnissen.
  - Persistente Wiederherstellung der Ergebnisse beim Projekt-Connect/App-Start.
- **`BuildViewModelTest`**:
  - Ausführung von Gesamttests und gezielten Re-Runs.
  - Umschaltung der Filter (`ALL` vs. `FAILURES_ONLY`).
  - Suche nach Testnamen und Klassen.
  - Erkennung von Build-Fehlern vor Beginn der Testausführung (`didBuildFailBeforeTests`) und Anzeige des Hinweis-Banners mit Weiterleitung zum Problems-Tab (`BuildTab.PROBLEMS`).
  - Klick auf Stack-Trace-Frames im Detail-Sheet mit Absendung des `OpenFileRequestBridge`-Events zum Öffnen der Datei an exakter Zeilennummer im Editor.

---

## 2. Nicht getestet (Manuelle Verifikation auf echtem Gerät erforderlich)
- **Android-Instrumented-Tests (`connectedAndroidTest`)**:
  - Task 36 fokussiert gemäß Spezifikation auf Unit-Tests (`testDebugUnitTest`). Ausführung auf physischen Android-Geräten mit ADB/Instrumentation Runner wurde nicht auf Hardware getestet.
- **Extreme XML-Größen**:
  - Getestet mit typischen Test-Suites (Dutzende bis Hunderte Tests). Riesen-XMLs mit über 10.000 Testfällen wurden nicht auf Low-End-Termux-Speicherlimits getestet.
- **Sehr tiefe Editor-Scroll-Performance mit hunderten Test-Gutter-Chips**:
  - Virtuelle Zeilenanzeige im Sora-Editor funktioniert flüssig, extremes Scrolling in 5.000-Zeilen-Testdateien sollte auf echten Geräten nachgeprüft werden.

---

## 3. Offene Punkte / Zukünftige Erweiterungen
- **Direkte Inline-Testausführung**:
  - Anzeige von grünen/roten Inline-Statusmarkierungen direkt neben dem Methodenkopf im Editor (analog zu Android Studio / IntelliJ IDEA Gutter-Icons mit letztem Teststatus).
- **Gradle Continuous Testing (`--continuous`)**:
  - Automatisches erneutes Ausführen von Tests bei Dateiänderungen.
- **Code-Coverage-Visualisierung**:
  - Unterstützung für JaCoCo XML-Reports zur Hervorhebung abgedeckter/unabgedeckter Zeilen im Editor.
