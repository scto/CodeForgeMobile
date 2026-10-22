# Task 37a – Debugger: Machbarkeit belegen und Sitzungskern bauen – Bericht

## 1. Ausgangslage & Ziel
Bislang besaß CodeForge Mobile keine Debugger-Unterstützung (weder JDWP- noch DAP-Anbindung). Task 37a belegt die technische Machbarkeit auf Android/Termux und liefert den UI-freien Sitzungskern auf Basis des Debug Adapter Protocols (DAP) und JDI.

---

## 2. Spike (37a.0) – Messungen & Belege

### 2.1 Verfügbarkeit von JDI und JDWP in Termux (OpenJDK 17)
Messung auf Termux:
```bash
$ java --list-modules | grep -E "jdk.jdi|jdk.jdwp.agent"
jdk.jdi@17.0.20
jdk.jdwp.agent@17.0.20

$ java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=127.0.0.1:5005 -version
Listening for transport dt_socket at address: 5005
openjdk version "17.0.20" 2026-01-20
OpenJDK Runtime Environment (build 17.0.20+0)
OpenJDK 64-Bit Server VM (build 17.0.20+0, mixed mode)
```
**Ergebnis:** Sowohl `jdk.jdwp.agent` als auch `jdk.jdi` sind in der Termux-OpenJDK-Installation nativ vorhanden und funktionsfähig.

### 2.2 Minimaler JDI Attach- und Breakpoint-Spike
Unter `scripts/jdi_spike/` wurden ein Zielprogramm (`TargetApp.java`), ein JDI-Attach-Client (`JdiAttachTest.java`) und ein Ausführungsskript (`run_spike.sh`) implementiert.

Ausgabe des Testlaufs (`bash scripts/jdi_spike/run_spike.sh`):
```
[Spike] Kompiliere TargetApp und JdiAttachTest...
[Spike] Starte TargetApp mit JDWP suspend=y auf Port 5005...
Listening for transport dt_socket at address: 5005
[Spike] Führe JdiAttachTest aus...
Attaching to localhost:5005...
Attached successfully: OpenJDK 64-Bit Server VM version 17.0.20
Found target class: TargetApp
Setting breakpoint at TargetApp:11...
Breakpoint set: TargetApp:11
Resuming VM to hit breakpoint...
Event received: com.sun.jdi.event.BreakpointEvent@TargetApp:11 in thread main
Thread suspended at breakpoint!
Top stack frame: TargetApp.main(java.lang.String[]) catch: -1
Visible variables in frame:
  args = (java.lang.String[0])
  x = 10
  y = 20
Resuming VM to let TargetApp finish...
TargetApp finished with exit code 0.
[Spike] JDI-Test erfolgreich abgeschlossen!
```
**Ergebnis:** JDI kann sich problemlos über TCP-Sockets auf `localhost:5005` mit `suspend=y` verbinden, Haltepunkte setzen, Stack-Frames inspizieren und lokale Variablenwerte (`x = 10`, `y = 20`) auslesen.

### 2.3 Vergleich möglicher DAP-Adapter
1. **`microsoft/java-debug`**:
   - Lizenz: EPL-1.0.
   - Voraussetzung: Benötigt Eclipse JDT Language Server / OSGi Runtime (~50 MB+ Speicherbedarf, langsame Kaltstartzeit auf Mobilgeräten).
   - Kotlin-Support: Kein nativer Kotlin-Support (nur Java).
2. **`fwcd/kotlin-debug-adapter`**:
   - Lizenz: MIT.
   - Voraussetzung: Basiert auf JDI und den Kotlin-Compiler-Internals; benötigt ~40 MB JAR-Abhängigkeiten.
   - Startzeit: Auf Termux ca. 2,5–4 Sekunden Kaltstart.
3. **Architektur-Entscheidung**:
   - Implementierung eines universellen, standardkonformen DAP-Clients (`:libs:debug-client`), der über Sockets oder Standard-I/O-Pipes mit jedem standardisierten Debug-Adapter (z. B. `kotlin-debug-adapter` oder einem leichtgewichtigen Termux-JDI-Adapter) kommuniziert.
   - Dadurch bleibt die Core-App schlank, modular und frei von schwergewichtigen internen JVM-Abhängigkeiten.

---

## 3. Implementierung des DAP-Sitzungskerns

### 3.1 Domain-Modelle & Schnittstelle (`:core:domain`)
- Modelle in `com.codeforge.core.domain.model`:
  - `Breakpoint`: ID, Pfad, Zeile, Bedingung, Log-Nachricht, Aktiviert-/Verifiziert-Status.
  - `DebugSessionState`: `Idle`, `Starting`, `Running`, `Paused(threadId, reason, frames)`, `Terminated(exitCode)`, `Failed(message)`.
  - `DebugStackFrame`: ID, Name, Pfad, Zeile, Spalte.
  - `Scope`: Name, `variablesReference`, `expensive`.
  - `Variable`: Name, Typ, Wert, Kind-Referenz (`variablesReference`), `evaluateName`.
  - `DebugConfig`: Hauptklasse, Klassenpfade, Arbeitsverzeichnis, JVM-/Programmargumente, Host und Port, Attach-Flag.
  - `DebugEvent`: `Output`, `Stopped`, `Continued`, `BreakpointValidated`, `Terminated`, `Error`.
- Schnittstelle `DebugRepository`:
  - `start`, `stop`, `setBreakpoints`, `resume`, `pause`, `stepOver`, `stepInto`, `stepOut`, `stackTrace`, `scopes`, `variables`, `evaluate`.
  - Reaktive State- und Event-Flows (`state: StateFlow<DebugSessionState>`, `events: Flow<DebugEvent>`, `activeBreakpoints: StateFlow<Map<String, List<Breakpoint>>>`).

### 3.2 DAP-Client (`:libs:debug-client`)
- `DapFraming`: Robuster Parser für das DAP-Framing (`Content-Length: <n>\r\n\r\n<json>`), unempfindlich gegenüber Fragmentierung (1-Byte-Chunks) und Batch-Nachrichten.
- `DapConnection`: Asynchrone Anfragen-/Antwort-Korrelation (`CompletableDeferred`), Timeout-Handling und Event-Dispatching über Coroutinen auf `Dispatchers.IO`.
- `DapJsonMapper`: Mapping von DAP-Objekten auf Core-Domain-Modelle.
- `DebugRepositoryImpl`: Vollständige Verwaltung des Sitzungslebenszyklus, lazy Haltepunkt-Validierung (`verified`), Stack- und Variablenabfragen.
- Hilt-Modul `DebugClientModule`: Bindet `DebugRepositoryImpl` an `DebugRepository`.

---

## 4. Getestet
- **JDI Spike auf Termux:** Ziel-JVM mit JDWP gestartet, JDI-Client angehängt, Haltepunkt in Zeile 11 getroffen, Variablen `x = 10` und `y = 20` gelesen.
- **Unit-Tests `:libs:debug-client:testDebugUnitTest`:**
  - `DapFramingTest`: Request/Response/Event Roundtrip, fragmentierter Stream (Byte-für-Byte), Mehrfachnachrichten in einem Puffer, ungültige Header.
  - `DebugRepositoryImplTest`: Vollständiger Sitzungszyklus gegen Fake-DAP-Server über In-Memory-Pipes (`initialize` → `launch` → `setBreakpoints` → `configurationDone` → `stopped` → `stackTrace` → `scopes` → `variables` → `stepOver` → `resume` → `disconnect` / `terminated`). Verbindungsfehlerbehandlung getestet.
- **Vollständiger Build:** `./gradlew :app:assembleDebug` erfolgreich (`BUILD SUCCESSFUL in 1m 48s`).
- **String-Check:** `python3 scripts/find_hardcoded_strings.py` unverändert grün (keine neuen UI-Strings in Nicht-Ressourcendateien).

---

## 5. Nicht getestet
- Physischer Test der UI-Interaktion auf Pixel 9 Pro XL (Android 17) – UI folgt in Task 37b.
- Attach an laufende Android-Apps via Wireless ADB / JDWP-Forwarding – folgt in Task 37c.

---

## 6. Offene Punkte
- Task 37b: Editor-Gutter für Haltepunkte, Debug-Panel (Toolbar, Call-Stack, Variablenbaum, Watch/Evaluate).
- Task 37c: Wireless-ADB-Portweiterleitung (`adb forward tcp:<port> jdwp:<pid>`) und Quelltext-zu-Klasse-Mapping.
