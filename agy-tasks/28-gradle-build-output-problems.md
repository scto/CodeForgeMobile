# Task 28 – Gradle-Build-Ausgabe und Problemfenster

**Ziel:** Der Nutzer startet einen Gradle-Build in der App und sieht (1) die Ausgabe live und (2) alle Fehler und Warnungen als klickbare Liste („Probleme“). Ein Tipp auf einen Eintrag öffnet die Datei an der richtigen Zeile und Spalte im Editor.
**Ausgangslage (aus dem Code, nicht auf dem Gerät bestätigt):**
* `core/domain/.../GradleBuildRepository.kt` definiert `BuildEvent` (`Output`, `Progress`, `TaskStarted`, `TaskFinished`, `BuildFinished`, `BuildFailed`) und `runTask(taskName): Flow<BuildEvent>`. Implementiert ist es in `:libs:gradle-tooling-bridge` (AIDL-Service `GradleBridgeService` mit der Gradle Tooling API).
* **Kein Feature-Modul verwendet `GradleBuildRepository`** (Suche im Repo ohne Treffer). Der KDoc behauptet, `:feature:terminal` und `:feature:projectwizard` würden es konsumieren; das stimmt nicht.
* Der Service läuft in der App-JVM (ART), nicht in Termux. Ob die Tooling API dort überhaupt funktioniert (Gradle-Distribution, JDK, `gradleUserHome`), ist unbelegt.
* Diagnostics aus LSP gibt es bereits (`LspDiagnosticsBridge`, `DiagnosticsContainer` im Editor); Build-Probleme sind eine zweite, getrennte Quelle.

**Gemeinsame Regeln (gelten für jeden Task):**
* Clean Architecture wie im Rest des Repos: Schnittstellen und Modelle in `:core:domain`, Implementierung in `:libs:*` bzw. im Feature-Modul, UI in `:feature:*` (MVVM/MVI mit Hilt, `@Immutable`-Daten-UI-States, `StateFlow`; Material 3; `modifier: Modifier = Modifier` als erster optionaler Parameter; keine Logik in Composables). Gradle Kotlin DSL, Versionen im Version Catalog (`gradle/libs.versions.toml`), Convention-Plugins aus `build-logic`.
* Alle UI-Texte über `:core:resources` (Englisch Standard, Deutsch in `values-de`); der String-Check (Task 17) muss grün bleiben.
* Nichts erfinden: Was du nicht selbst ausgeführt hast, steht im Bericht unter **nicht getestet** (insbesondere Gerätetests; Zielgerät des Inhabers: Pixel 9 Pro XL, Android 17). Kein „funktioniert“ ohne Beleg.
* Geheimnisse (Passwörter, Tokens) nie ins Log, nie in Dateien im Projektordner, nie in Berichte.
* Bericht in `docs/<name>-report.md` mit Abschnitten **getestet / nicht getestet / offene Punkte**; `agy-tasks/TASKS-STATUS.md` und `agy-tasks/README.md` um den Task ergänzen (Commit-Hash eintragen).
* Build muss grün sein: `./gradlew :app:assembleDebug` und die Tests der betroffenen Module (`./gradlew :<modul>:testDebugUnitTest`).

## 28.0 Entscheidung mit Beleg (zuerst, vor jedem Umbau)
Prüfe auf einem Gerät oder, wenn keines erreichbar ist, nur durch Lesen und Rechnen, welcher Weg trägt, und halte die Entscheidung mit Begründung im Bericht fest:
* **A) Prozess in Termux:** `./gradlew <task> --console=plain` mit `TermuxEnvironment.environment()` und `JAVA_HOME` des installierten JDK, Ausgabe zeilenweise lesen (gleiches Muster wie `KotlincProcessCompiler` aus Task 27a). Vorteil: dieselbe Gradle-Installation wie im Terminal, kein ART-Problem.
* **B) Bestehende Tooling-API-Bridge** weiter verwenden. Nur wählen, wenn ein echter Lauf (`GradleBridgeService.connect` + `runTask`) auf dem Gerät nachweislich funktioniert.
Empfehlung ohne Gegenbeleg: **A**, die Schnittstelle `GradleBuildRepository` bleibt als Vertrag bestehen (`ProcessGradleBuildRepository` als neue Implementierung); die Bridge wird dann entweder entfernt oder ausdrücklich als „nicht verwendet“ dokumentiert, kein toter Code ohne Hinweis.

## 28.1 Domain
* Neues Modell `BuildProblem(severity: ERROR|WARNING|INFO, source: KOTLIN|JAVA|AAPT|GRADLE|OTHER, path: String?, line: Int?, column: Int?, message: String, taskName: String?)` in `:core:domain`; neues Ereignis `BuildEvent.Problem(problem)`.
* Einfaches `BuildSession`-Modell (Task, Startzeit, Dauer, Zustand `Running|Succeeded|Failed|Cancelled`).

## 28.2 Parser (reines Kotlin, JVM-testbar, eigenes Modul oder `:libs:code-tools`)
`GradleOutputParser` erkennt mindestens:
* Kotlin: `e: file:///pfad/Datei.kt:12:5 Meldung` und `w: …`
* Java (javac): `/pfad/Datei.java:12: error: Meldung` (mit der folgenden Quellzeile/Caret als Teil derselben Meldung zusammenfassen)
* AAPT2: `ERROR: /pfad/res/layout/x.xml:5: AAPT: error: …`
* Gradle: `FAILURE: Build failed with an exception.`, `* What went wrong:` (Folgezeilen sammeln), `Execution failed for task ':app:compileDebugKotlin'.`, `Could not resolve …`
ANSI-Sequenzen entfernen. **Testdaten aus echten Logs:** sammle Fixtures aus echten Gradle-Läufen (Repo-Build selbst, absichtlich kaputter Code) und lege sie unter `src/test/resources`. Erfinde keine Formate; bei Unsicherheit lieber `OTHER` ohne Position.

## 28.3 Ausführung
* Start, Abbruch (Prozess beenden, danach `--stop` nur auf Wunsch), Mehrfachstart verhindern.
* Läuft als Vordergrunddienst mit Benachrichtigung (Fortschritt, Abbrechen), damit Android den Build im Hintergrund nicht beendet.
* Ausgabe gepuffert an die UI (z. B. alle 100 ms bündeln) und in einem Ringpuffer begrenzt (Zeilenzahl konfigurierbar, Standard 20 000), damit lange Builds keinen Speicher sprengen.
* Zugrundeliegendes Projekt aus dem geöffneten Projekt; fehlt `gradlew`, klare Meldung statt Absturz.

## 28.4 UI (neues Modul `:feature:build`)
* Unteres Panel/BottomSheet im Editor mit zwei Reitern **Ausgabe** und **Probleme**. Statusleiste: Task, Laufzeit, Zustand, Abbrechen.
* **Ausgabe:** Monospace, Auto-Scroll mit Umschalter (stoppt beim Hochscrollen), Suche, Kopieren, Löschen, Task-Überschriften einklappbar, Fehler- und Warnzeilen farblich markiert.
* **Probleme:** gruppiert nach Datei, Zähler je Schwere, Filter (Fehler/Warnungen), Sortierung nach Reihenfolge des Auftretens. Tipp → Datei an `line:column` im Editor öffnen (vorhandene Navigation nutzen, kein Parallelmechanismus). Einträge ohne Position zeigen die Meldung ausklappbar.
* Aufruf: Schaltfläche „Bauen“ mit Task-Auswahl (`assembleDebug`, `assembleRelease`, `clean`, `test`, `lint`, frei wählbar); nach Ende Snackbar „Probleme anzeigen“ bei Fehlern.
* Optional (nur wenn der Aufwand klein bleibt): Build-Probleme als Markierungen in geöffneten Dateien, klar getrennt von LSP-Diagnostics (eigene Quelle, damit sie bei neuem Build ersetzt statt vermischt werden).

## 28.5 Tests
* Parser: JVM-Tests gegen die Fixtures (mindestens je ein Fall Kotlin, Java, AAPT, Gradle-Ausfall, Mehrzeiler, ANSI).
* Ringpuffer und Bündelung: Grenzfälle.
* ViewModel mit Fake-Repository: Laufend → Fehler → Probleme-Liste; Abbruch; Mehrfachstart abgewiesen.
* Prozess-Wrapper mit Fake-Prozess (kein echter Gradle-Lauf nötig). Ein echter Lauf auf dem Gerät gehört in den Bericht unter getestet oder nicht getestet.

## Abnahme
Build grün, Parser-Tests grün, Entscheidung 28.0 begründet im Bericht, `GradleBuildRepository` wird tatsächlich von einem Feature verwendet, kein verwaister Bridge-Code ohne Vermerk.
