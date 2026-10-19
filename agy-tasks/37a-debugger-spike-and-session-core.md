# Task 37a – Debugger: Machbarkeit belegen und Sitzungskern bauen

**Ziel (Teil 1 von 3):** Ein Java-/Kotlin-Programm, das in Termux läuft, lässt sich mit Breakpoints, Schritten und Variablenanzeige debuggen. Dieser Teil liefert nur Spike, Architektur und den UI-freien Sitzungskern. UI folgt in 37b, das Anhängen an Android-Apps in 37c.
**Ausgangslage:** Im Repo gibt es keine Debugger-Unterstützung (kein Treffer für „jdwp“ oder „debugger“ im Code). Das ist das größte Funktionsloch einer IDE und zugleich das riskanteste Vorhaben.
**Grundidee (Hypothese, durch den Spike zu belegen oder zu verwerfen):** JDWP nicht selbst in der App implementieren. Stattdessen läuft in Termux eine JVM (`openjdk-17`), die ein **Debug Adapter Protocol (DAP)-Programm** auf Basis von JDI (`com.sun.jdi`) betreibt; die App spricht DAP über Standard-Ein-/Ausgabe oder `localhost` mit dem Adapter. Vorteil: Breakpoints, Schritte, Variablen, Auswertung kommen aus JDI. Nachteil: braucht ein JDK in Termux und ist nur so gut wie der Adapter.

**Gemeinsame Regeln (gelten für jeden Task):**
* Clean Architecture wie im Rest des Repos: Schnittstellen und Modelle in `:core:domain`, Implementierung in `:libs:*` bzw. im Feature-Modul, UI in `:feature:*` (MVVM/MVI mit Hilt, `@Immutable`-Daten-UI-States, `StateFlow`; Material 3; `modifier: Modifier = Modifier` als erster optionaler Parameter; keine Logik in Composables). Gradle Kotlin DSL, Versionen im Version Catalog (`gradle/libs.versions.toml`), Convention-Plugins aus `build-logic`.
* Alle UI-Texte über `:core:resources` (Englisch Standard, Deutsch in `values-de`); der String-Check (Task 17) muss grün bleiben.
* Nichts erfinden: Was du nicht selbst ausgeführt hast, steht im Bericht unter **nicht getestet** (insbesondere Gerätetests; Zielgerät des Inhabers: Pixel 9 Pro XL, Android 17). Kein „funktioniert“ ohne Beleg. Aussagen dieses Tasks über den Bestand stammen aus Stichwortsuchen; prüfe sie vor dem Start selbst und korrigiere sie im Bericht.
* Geheimnisse (Passwörter, Tokens, API-Schlüssel) nie ins Log, nie in Dateien im Projektordner, nie in Berichte.
* Bericht in `docs/<name>-report.md` mit Abschnitten **getestet / nicht getestet / offene Punkte**; `agy-tasks/TASKS-STATUS.md` und `agy-tasks/README.md` um den Task ergänzen (Commit-Hash eintragen).
* Build muss grün sein: `./gradlew :app:assembleDebug` und die Tests der betroffenen Module (`./gradlew :<modul>:testDebugUnitTest`).

## 37a.0 Spike (zuerst, ehrlich bewerten)
Beantworte mit Belegen auf dem Gerät (Befehle und Ausgaben in den Bericht):
1. **Bereits gemessen vom Inhaber am 2026-10-19 (Termux, nicht wiederholen):** `java --list-modules | grep -E "jdk.jdi|jdk.jdwp.agent"` zeigt `jdk.jdi@17.0.20` und `jdk.jdwp.agent@17.0.20`. Trage das als Beleg in den Bericht ein.
2. **Teilweise gemessen vom Inhaber am 2026-10-19:** `java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=127.0.0.1:5005 -version` gibt `Listening for transport dt_socket at address: 5005` aus (OpenJDK 17.0.20+0), der JDWP-Agent lädt also. **Noch zu prüfen:** kann ein minimaler JDI-Client darauf attachen (mit `suspend=y`), einen Breakpoint setzen, anhalten, Stack und lokale Variablen lesen? Minimales Kotlin-/Java-Testprogramm und Client als Skript unter `scripts/`.
3. Welche Adapter kommen in Frage? Prüfe mindestens `fwcd/kotlin-debug-adapter` und `microsoft/java-debug` (Lizenz, Aktualität, Größe, Start ohne IDE-Umgebung, Kotlin-Unterstützung) **anhand der Projektseiten**, nicht aus dem Gedächtnis. Alternative: eigener kleiner DAP-Adapter auf JDI (Umfang schätzen).
4. Messwerte: Startzeit des Adapters, RAM, Latenz Breakpoint-Treffer → UI-Ereignis.
Ergebnis: Entscheidungsvorlage (Adapter wählen oder eigener Adapter) mit Begründung. **Bei negativem Spike hier stoppen** und nur den Bericht liefern.

## 37a.1 Domain (`:core:domain`)
Modelle: `Breakpoint(id, path, line, condition?, logMessage?, enabled)`, `DebugSessionState` (`Idle`, `Starting`, `Running`, `Paused(threadId, reason, frames)`, `Terminated(exitCode)`, `Failed(message)`), `StackFrame(id, name, path, line, column)`, `Variable(name, type, value, variablesReference, evaluateName)`, `Scope`. Schnittstelle `DebugRepository`: `start(config)`, `setBreakpoints(path, list)`, `continue`, `pause`, `stepOver`, `stepInto`, `stepOut`, `stackTrace(threadId)`, `scopes(frameId)`, `variables(reference)`, `evaluate(frameId, expression)`, `stop`, `events: Flow<DebugEvent>`.

## 37a.2 DAP-Client (`:libs:debug-client`, neues Modul)
* JSON-RPC-ähnlicher DAP-Rahmen (`Content-Length`-Header), Anfragen/Antworten/Ereignisse, Sequenznummern, Zeitlimits, saubere Abbrüche. Kein Blockieren des Main-Threads.
* Adapter-Prozess über `TermuxEnvironment` (Muster `KotlincProcessCompiler`), stderr in die Build-Ausgabe/Logs, nicht verschlucken.
* Verzögerte Breakpoints (Klasse noch nicht geladen) müssen nach dem Laden greifen; Statusmeldungen `verified` an die UI weitergeben.

## 37a.3 Start-Konfiguration
`DebugConfig(mainClass, classpath, workingDir, args, vmArgs, jdkHome)`. Ermittlung von Klassenpfad und Hauptklasse aus dem Projekt über das Build-System (Task 28; bis dahin manuell eintragbar). Start über `java -agentlib:jdwp=…,suspend=y` plus Attach durch den Adapter.

## 37a.4 Tests
* DAP-Rahmen: Zerlegen/Zusammensetzen, fragmentierte Eingabe, große Nachrichten.
* Sitzungs-Zustandsautomat gegen einen Fake-Adapter (Pipes): Start → Breakpoint-Treffer → Schritt → Variablen → Ende; Abbruch und Absturz des Adapters.
* Der echte Lauf aus 37a.0 ist Teil des Berichts (getestet/nicht getestet).

## Abnahme
Spike-Bericht mit klarer Empfehlung; bei „machbar“ zusätzlich Kern und Tests grün. Kein UI in diesem Task.
