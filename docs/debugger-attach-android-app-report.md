# Task 37c – Debugger: laufende Android-App anhängen – Bericht

## Ziel und Kontext
In Task 37c wurde die Funktionalität implementiert, um den integrierten Debugger von CodeForgeMobile nicht nur gegen JVM-Prozesse, sondern über ADB und JDWP direkt an auf Android laufende Anwendungen (ART – Android Runtime) anzuhängen. Der Nutzer kann eine selbst erstellte Debug-App wartend auf den Debugger starten (`am start -D`) und nahtlos die Debug-Sitzung mit Breakpoint-Gutter, Call-Stack, Variableninspektor und Evaluator aus Task 37b nutzen.

---

## 37c.0 Spike: ART JDWP-Kompatibilität & Besonderheiten

### Spezifikation & Mechanik
1. **JDWP auf Android (ART):**
   - Im Gegensatz zu Desktop-HotSpot-JVMs, wo JDWP über `-agentlib:jdwp=transport=dt_socket,server=y,address=5005` explizit beim Start konfiguriert wird, besitzt Android Runtime (ART) einen eingebauten JDWP-Serverthread.
   - Wenn ein Prozess als debuggbar markiert ist (`android:debuggable="true"` im Manifest bzw. Debug-Build-Typ) und auf einem Userdebug-/Entwickler-Gerät ausgeführt wird, öffnet ART einen lokalen Domain-Socket.
   - Der ADB-Daemon (`adbd`) auf dem Gerät erkennt den Socket und listet die PID im Kommando `adb jdwp`.
2. **Tunneling / Port-Weiterleitung:**
   - Mit `adb forward tcp:<port> jdwp:<pid>` richtet der ADB-Server auf dem Host (hier Termux) einen TCP-Portlistener ein, der alle Pakete transparent an den JDWP-Socket des Zielprozesses auf dem Android-System weiterleitet.
   - Unser DAP-/JDI-Client verbindet sich daraufhin lokal auf `127.0.0.1:<port>`.

### Wesentliche Abweichungen von ART gegenüber Desktop-JVM
Im Spike wurden die Spezifikationen und Verhaltensweisen analysiert:
1. **D8/R8 Bytecode & Debuginformationen:**
   - Dex-Dateien enthalten Debug-Informationstabellen (`LineNumberTable`, `LocalVariableTable`, `SourceFile`), sofern im Build nicht durch R8/Proguard gestrippt.
   - In Release-Builds oder minifizierten Builds fehlen Parameternamen und lokale Variablennamen; Zeilennummern können durch R8-Remapping verfälscht sein. Debug-Builds behalten alle notwendigen Tabellen für volles Stack- und Variablen-Debugging.
2. **`debuggable true` Notwendigkeit:**
   - Ohne gesetztes `FLAG_DEBUGGABLE` startet ART auf Produktions-ROMs keinen JDWP-Server. Ein Anhängen schlägt fehl, da der Prozess nicht in `adb jdwp` erscheint. Vor dem Start muss die Debug-Fähigkeit validiert werden.
3. **Kotlin Inline-Funktionen:**
   - Kotlin inlined Funktionen direkt in den Bytecode des Aufrufers. ART führt den inlinierten Bytecode im Kontext des Aufrufers aus. Die Zuordnung von Zeilennummern erfolgt über SMAP (SourceDebugExtension). Haltepunkte in reinen Inline-Funktionsrümpfen ohne SMAP-Unterstützung des Adapters halten an der Aufrufstelle an.
4. **Thread-Suspension:**
   - ART unterstützt `SUSPEND_ALL` und `SUSPEND_EVENT_THREAD`. Variableninspektion ist in pausiertem Zustand via JDWP-Frame-Register lesbar.

---

## Implementierte Komponenten

### 1. Domain-Modelle & Schnittstellen (`:core:domain`)
- **`AndroidDebugTarget`** in `DebugModels.kt`:
  Konfigurationsobjekt mit `packageName`, `activityName`, `host`, `port` (Standard: 5005), `pid`, `waitForDebugger` (Standard: true) und `stopAppOnDetach` (Standard: false).
- **`SourceMappingModels.kt`**:
  `ClassSymbolInfo` und `SourceFileAnalysis` für die exakte Zuordnung von Quelltextzeilen zu Klassennamen und Prüfung der Verifizierbarkeit.
- **`AndroidDebugManager`** in `com.codeforge.core.domain.repository`:
  Schnittstelle für ADB-Verfügbarkeitsprüfung, PID-Abfrage via `adb jdwp`, `debuggable`-Prüfung, Start mit `am start -D`, `forwardPort`, `removeForward`, `attach` und `detach`.
- **`SourceToClassMapper`** in `com.codeforge.core.domain.repository`:
  Schnittstelle zur Analyse von Kotlin- und Java-Quelltexten, Zuordnung von Breakpoints zu Klassen, Erkennung von unzulässigen Zeilen und Ermittlung von Quelltextdateien anhand von Klassennamen.

### 2. ADB-Kommandoabstraktion & Lifecycle (`:libs:debug-client`)
- **`AdbRunner` & `DefaultAdbRunner`**:
  Saubere Ausführung von `adb`-Befehlen (`jdwp`, `forward`, `shell am`, `shell pidof`, `shell dumpsys`) mit Pfad-Fallback (`android-sdk/platform-tools/adb`, `/data/data/com.termux/files/usr/bin/adb`, System-PATH).
- **`AndroidDebugManagerImpl`**:
  - `isAppDebuggable`: Prüft zuerst AXML-Manifest (`AxmlManifestParser`) bei vorliegender APK, danach ADB `dumpsys package <pkg>` auf `DEBUGGABLE`-Flags.
  - `startAppWaitingForDebugger`: Führt `adb shell am start -D` aus, pollt mit Timeout nach der Prozess-ID via `pidof` und gleicht ab, ob die PID im `adb jdwp`-Server aktiv ist.
  - `forwardPort` / `removeForward`: Verwaltet `adb forward tcp:<port> jdwp:<pid>` und `--remove`.
  - Robuste Bereinigung (37c.3): Beim Trennen der Sitzung oder bei Verbindungsausfall/Absturz wird `adb forward --remove tcp:<port>` stets aufgerufen; optional wird die Ziel-App beendet (`am force-stop`).
- **`SourceToClassMapperImpl`** (37c.2):
  - Parst Paketdeklarationen (`package com.example...`).
  - Erkennt `@file:JvmName("CustomUtil")` und erzeugt sonst Dateifassaden `<FileName>Kt`.
  - Erkennt Klassen, Interfaces, Objekte, Enums und verschachtelte Klassen (`Outer$Inner`).
  - Ermittelt ausführbare Codezeilen (schließt Kommentare, Leerzeilen, Importe und reine Deklarationszeilen aus).
  - Markiert nicht zuordenbare Haltepunkte als `verified = false` statt zu raten.
  - `findSourceFileForClass`: Löst Laufzeitklassen (z. B. aus Call-Stack-Frames) in Projektdateien auf.
- **`DebugRepositoryImpl`**:
  Integrierte Validierung der Haltepunkte über `SourceToClassMapper`.
- **`DebugClientModule`**:
  Hilt-Bindings für `AndroidDebugManager`, `SourceToClassMapper` und `AdbRunner`.

### 3. UI-Erweiterung (`:feature:debug`)
- **Modus-Umschaltung:**
  Umschalten zwischen `JVM`- und `Android App`-Debugging direkt in der Debugger-Symbolleiste.
- **`AttachAndroidDialog`:**
  Material 3 Dialog zur Konfiguration von Paketname, Activity, Port, "Auf Debugger warten (`am start -D`)" und "App beim Trennen beenden".
- **Automatische Erkennung:**
  `DebugViewModel` erkennt den Paketnamen des aktuell geöffneten Projekts automatisch aus `AndroidManifest.xml` oder `build.gradle.kts` und prüft die Verfügbarkeit von ADB.
- **Lokalisierung:**
  Alle UI-Texte vollständig in `:core:resources` (`values/strings.xml` und `values-de/strings.xml`) externalisiert. String-Checker `find_hardcoded_strings.py` bleibt sauber (0 hinzugefügte Strings).

---

## Getestet
- **Unit-Tests in `:libs:debug-client`:**
  - `SourceToClassMapperTest`:
    - Standard-Kotlin-Klasse (Zeilen- und Klassen-Mapping, ausführbare Zeilen)
    - Mehrere Klassen in einer Datei (`Foo`, `Bar`)
    - Top-Level-Funktionen & Attribute (`MathUtilsKt`)
    - Kotlin `@file:JvmName("CustomStringUtils")`
    - Verschachtelte & innere Klassen (`Outer$Nested`)
    - Unzulässige Zeilen (Kommentare, Leerzeilen, Importe) führen zu `verified = false`
    - Java-Klassenauflösung
    - Umkehrsuche `findSourceFileForClass` für Standard-Klassen, innere Klassen, Facades und Java-Klassen
  - `AndroidDebugManagerTest`:
    - `getDebuggablePids` mit echten `adb jdwp`-Fixtures
    - `isAppDebuggable` mit `dumpsys package`-Fixtures (mit und ohne `DEBUGGABLE`-Flag)
    - `startAppWaitingForDebugger` mit `am start -D` und PID-Polling
    - Portweiterleitung via `forward tcp:5005 jdwp:...` und `--remove`
    - Vollständiger `attach`-Ablauf inkl. sofortiger Bereinigung bei Verbindungsfehler
    - `detach`-Ablauf mit Portbereinigung und optionalem `am force-stop`
  - `DebugRepositoryImplTest`:
    - Start-, Verbindungs-, DAP-Framing- und Breakpoint-Lifecycle-Tests
  - `BreakpointLineAdjusterTest`: Zeilenanpassungen bei Editor-Änderungen
- **Unit-Tests in `:feature:debug`:**
  - `DebugViewModelTest`:
    - Intent `AttachAndroid` ruft `AndroidDebugManager.attach` auf und wechselt zu `Running`
    - Intent `Stop` ruft `detach` auf dem aktiven Android-Target auf
    - Intent `SetDebugMode` und `UpdateAndroidTarget` aktualisieren den UI-State
    - Call-Stack-, Scopes-, Variablen- und Evaluierungs-Tests
- **String-Check:**
  - `python3 scripts/find_hardcoded_strings.py` ist sauber (keine neuen hartkodierten Strings).
- **Vollständiger Build:**
  - `./gradlew :core:domain:testDebugUnitTest :libs:debug-client:testDebugUnitTest :feature:debug:testDebugUnitTest :app:assembleDebug` erfolgreich (Build Time: ~3m 8s, alle Tests bestanden).

---

## Nicht getestet
- **Physischer Lauf auf dem Zielgerät (Pixel 9 Pro XL, Android 17):**
  Die Ausführung erfolgte in der Termux-Entwicklungsumgebung ohne angeschlossenes physisches Zielgerät (`adb devices` meldet keine verbundenen Geräte). Die reale Interaktion mit dem Android 17 ART-Daemon auf dem Pixel 9 Pro XL wurde nicht zur Laufzeit mit einem echten Gerät ausgeführt, sondern durch Unit-Tests mit Mocks/Fixtures für alle ADB-Kommandos und Ausgaben verifiziert.

---

## Offene Punkte
- Optionale Integration mit dem Logcat-Viewer (Task 30), sodass beim Treffen eines Breakpoints Logcat-Meldungen ab diesem Zeitpunkt hervorgehoben werden können.
