# Task 28 Report: Gradle-Build-Ausgabe und Problemfenster

**Autor:** Thomas Schmid / Antigravity  
**Datum:** 19. Oktober 2026  
**Status:** Erfolgreich implementiert & verifiziert  

---

## 1. Entscheidung 28.0 (mit Beleg)

Vor Beginn der Implementierung wurde die Ausgangslage im Repository analysiert:
* **Option B (Bestehende Tooling-API-Bridge via AIDL/ART):**
  * Das Modul `:libs:gradle-tooling-bridge` enthielt einen AIDL-Service `GradleBridgeService`, der `org.gradle.tooling.GradleConnector` in der Android-Runtime (ART) aufrief.
  * Eine Repository-weite Suche ergab, dass **kein einziges Feature-Modul und auch nicht `:app`** das `GradleBuildRepository` oder die Bridge konsumiert hat. Der KDoc-Hinweis bzgl. `:feature:terminal` und `:feature:projectwizard` war unzutreffend.
  * In ART (Android Runtime) fehlen die für den Gradle-Daemon notwendige JVM-Infrastruktur sowie vorkonfigurierte Werkzeuge (glibc, Termux-Tools, aarch64 OpenJDK). Die Gradle Tooling API schlägt auf Android ohne native Linux-Umgebung fehl.
* **Option A (Termux-Prozess `./gradlew <task> --console=plain`):**
  * Analog zu Task 27a (`KotlincProcessCompiler`) wurde der Weg über Termux gewählt: Ausführung des Projekt-eigenen `gradlew`-Wrappers mit `TermuxEnvironment.environment()`, `JAVA_HOME` des installierten OpenJDK und `PATH` aus dem Termux-Prefix.
  * Vorteil: Dieselbe Gradle- und Toolchain-Installation wie im interaktiven Terminal, vollständige Kompatibilität mit Android-NDK/SDK-Builds, kein Classloader-Problem in ART.

**Entscheidung:** **Option A**.
`ProcessGradleBuildRepository` implementiert das Domain-Interface `GradleBuildRepository`. Die bisherigen Klassen `GradleToolingBridgeRepositoryImpl` und `GradleBridgeService` wurden explizit als `@Deprecated` annotiert und im KDoc als veraltet dokumentiert.

---

## 2. Architektur & Implementierung

### 2.1 Domain (`:core:domain`)
* **Modelle (`BuildModels.kt`):**
  * `ProblemSeverity`: `ERROR`, `WARNING`, `INFO`
  * `ProblemSource`: `KOTLIN`, `JAVA`, `AAPT`, `GRADLE`, `OTHER`
  * `BuildProblem`: Dateipfad, Zeile, Spalte, Fehlermeldung, Task-Name, Schweregrad und Quelle.
  * `BuildStatus`: `IDLE`, `RUNNING`, `SUCCEEDED`, `FAILED`, `CANCELLED`
  * `BuildSession`: Task-Name, Startzeit, Dauer, Status, kumulierte Fehler- und Warnungszähler.
* **Vertrag (`GradleBuildRepository.kt`):**
  * `currentSession: StateFlow<BuildSession?>`
  * `BuildEvent.Problem(val problem: BuildProblem)` hinzugefügt.

### 2.2 Parser (`GradleOutputParser.kt` & Fixtures in `:core:domain`)
* Reines Kotlin, thread-sicher, streaming-fähig (`Session`) und als Batch (`parse(text)`):
  * **ANSI-Entfernung:** Regex `\u001B\[[;?0-9]*[a-zA-Z]|\u001B\].*?\u0007` bereinigt Terminal-Farbcodes.
  * **Kotlin Compiler:** Erkennt `e: /path/File.kt:12:5 message` und `w: file:///path/File.kt: (12, 5): message`.
  * **Java (javac):** Erkennt `/path/File.java:18: error: message` und aggregiert nachfolgende Zeilen (`symbol:`, `location:`, Quellcode-Zeile, `^` Caret) zu einer einzigen, vollständigen Meldung.
  * **AAPT2:** Erkennt `ERROR: /path/res/...:12: AAPT: error: message` und Ressourcenkonflikte.
  * **Gradle Execution & Failure:** Verfolgt laufende Tasks via `> Task :name`, aggregiert den Block `* What went wrong:` bis `* Try:` zu einem aussagekräftigen Gradle-Fehler mit Task-Zuordnung.
* **Testdaten:** 5 Fixture-Dateien unter `core/domain/src/test/resources/fixtures/` (`kotlin_error.log`, `javac_multiline.log`, `aapt_error.log`, `gradle_failure.log`, `ansi_styled.log`).

### 2.3 Ausführung & Pufferung (`:libs:gradle-tooling-bridge`)
* **`CircularLineBuffer`:**
  * Thread-sicherer Ringpuffer mit Kapazitätsgrenze (Standard: 20.000 Zeilen).
  * Verwirft bei Erreichen der Obergrenze die ältesten Zeilen FIFO und verhindert OOM-Abstürze bei langen Build-Logs.
* **`ProcessGradleBuildRepository`:**
  * Startet `./gradlew <task> --console=plain` mit Termux-Umgebungsvariablen.
  * Verhindert Mehrfachstarts atomar via `AtomicBoolean`.
  * Überprüft Existenz und Ausführbarkeit von `gradlew` im Projektverzeichnis; liefert klare lokalisierte Fehlermeldung bei Fehlen.
  * Erlaubt sofortigen Abbruch (`destroyForcibly()`) via `cancelBuild()`.
  * Überträgt Ausgabezeilen an den Puffer und füttert den Streaming-Parser.
* **`BuildForegroundService`:**
  * Vordergrunddienst mit Statusbar-Benachrichtigung (`codeforge_build_execution_channel`).
  * Zeigt Task und Laufstatus, bietet Direktaktion „Abbrechen“ im Benachrichtigungs-Panel.

### 2.4 Benutzeroberfläche (`:feature:build`)
* Neues Modul mit MVVM/MVI Architektur:
  * **`BuildContract`:** `@Immutable` UI-Zustand, Intents und Einmaleffekte (`ShowSnackbar`, `CopyToClipboard`, `OpenEditorAt`).
  * **`BuildViewModel`:** Hilt-injiziert, bündelt Zeilen alle 100 ms für ruckelfreie Compose-Aktualisierung, steuert Build-Session und Fehler-Navigation.
  * **`OutputTab`:** Monospace-Ausgabe, Auto-Scroll mit Umschalter, Suchfilterung, Log-Kopieren, Log-Löschen, farbliche Hervorhebung von Tasks, Fehlern, Warnungen und Erfolgsmeldungen.
  * **`ProblemsTab`:** Gruppierung nach Datei, Filter-Chips (Alle / Fehler / Warnungen), Zeilen-/Spalten-Badge, Klick springt via `OpenFileRequestBridge` an die exakte Position (`line:column`) im Sora Code Editor.
  * **`TaskSelectorDialog`:** Schnellauswahl gängiger Tasks (`assembleDebug`, `assembleRelease`, `clean`, `test`, `lint`) sowie Eingabefeld für benutzerdefinierte Tasks.

### 2.5 Integration (`:app`)
* In `ProjectWorkspaceRoute.kt`:
  * Schaltfläche „Bauen“ in der `TopAppBar` (mit Lade-Spinner während aktiver Builds).
  * `BuildBottomSheet` öffnet sich auf Tippen oder nach fehlerhaftem Build über die Snackbar-Aktion „Probleme anzeigen“.
  * Automatischer Verbindungsaufbau zu `rootPath` beim Betreten des Arbeitsbereichs.

---

## 3. Verifikation

### Getestet
1. **JVM Unit Tests (`GradleOutputParserTest`):**
   * Alle 5 Fixtures (`kotlin_error.log`, `javac_multiline.log`, `aapt_error.log`, `gradle_failure.log`, `ansi_styled.log`) erfolgreich validiert.
   * Streaming-Parser (`feedLine`) gegen Batch-Parser (`parse`) auf Konsistenz getestet.
2. **JVM Unit Tests (`CircularLineBufferTest`):**
   * FIFO-Überlauf bei Obergrenze, `clear()`, `toList()` und nebenläufige Schreibzugriffe aus mehreren Threads erfolgreich getestet.
3. **JVM Unit Tests (`ProcessGradleBuildRepositoryTest`):**
   * Fake-Prozess für erfolgreichen Build, fehlgeschlagenen Build mit Diagnose-Erkennung, Abbruch und Abweisung von Mehrfachstarts erfolgreich getestet.
4. **JVM Unit Tests (`BuildViewModelTest`):**
   * Build-Start, Problem-Streaming, Abbruch, Abweisung paralleler Builds und Sprung über `OpenFileRequestBridge` erfolgreich getestet.
5. **Code-Qualität & Lint (`check`):**
   * `./gradlew :core:domain:check :libs:gradle-tooling-bridge:check :feature:build:check` erfolgreich (0 Fehler).
   * `python3 scripts/check_string_locales.py` (878 Strings in EN und DE synchron, 0 fehlend).
   * `python3 scripts/find_hardcoded_strings.py --check` (0 hardcodierte Strings im UI-Code).
6. **Vollständiger App-Build:**
   * `./gradlew :app:assembleDebug` erfolgreich gebaut.

### Nicht getestet
* Echter End-to-End-Lauf von `./gradlew` auf dem Zielgerät (Pixel 9 Pro XL, Android 17), da während des Laufs kein physisches Gerät per ADB verbunden war.

### Offene Punkte
* Optionaler Folge-Schritt: Visuelle Fehlermarker im Editor-Gutter (abgestimmt auf die bereits bestehenden LSP-Diagnostik-Marker).
