# Task 30 – Logcat-Ansicht

**Ziel:** Der Nutzer sieht die Logausgabe seiner gestarteten App (und von CodeForge selbst) mit Filter nach Paket und Log-Level; Abstürze sind mit Stacktrace klickbar bis zur Quellzeile.
**Wichtige Einschränkung (aus dem Android-Verhalten, selbst prüfen und im Bericht belegen):** Eine normale App sieht per `logcat` nur die **eigenen** Logs. Fremde Apps lesen erfordert `READ_LOGS` (per ADB freigeben: `pm grant com.codeforge.app android.permission.READ_LOGS`) oder `adb logcat` über drahtloses Debugging (Task 29.5). Ohne eine dieser Möglichkeiten zeigt die Ansicht nur CodeForge-eigene Logs. Das muss das UI ehrlich sagen, kein stilles leeres Fenster.
**Ausgangslage:** Das Wort „logcat“ kommt nur im `:feature:sdkmanager` vor (Ausgabeanzeige des Installers); es gibt keine allgemeine Logcat-Ansicht.

**Gemeinsame Regeln (gelten für jeden Task):**
* Clean Architecture wie im Rest des Repos: Schnittstellen und Modelle in `:core:domain`, Implementierung in `:libs:*` bzw. im Feature-Modul, UI in `:feature:*` (MVVM/MVI mit Hilt, `@Immutable`-Daten-UI-States, `StateFlow`; Material 3; `modifier: Modifier = Modifier` als erster optionaler Parameter; keine Logik in Composables). Gradle Kotlin DSL, Versionen im Version Catalog (`gradle/libs.versions.toml`), Convention-Plugins aus `build-logic`.
* Alle UI-Texte über `:core:resources` (Englisch Standard, Deutsch in `values-de`); der String-Check (Task 17) muss grün bleiben.
* Nichts erfinden: Was du nicht selbst ausgeführt hast, steht im Bericht unter **nicht getestet** (insbesondere Gerätetests; Zielgerät des Inhabers: Pixel 9 Pro XL, Android 17). Kein „funktioniert“ ohne Beleg.
* Geheimnisse (Passwörter, Tokens) nie ins Log, nie in Dateien im Projektordner, nie in Berichte.
* Bericht in `docs/<name>-report.md` mit Abschnitten **getestet / nicht getestet / offene Punkte**; `agy-tasks/TASKS-STATUS.md` und `agy-tasks/README.md` um den Task ergänzen (Commit-Hash eintragen).
* Build muss grün sein: `./gradlew :app:assembleDebug` und die Tests der betroffenen Module (`./gradlew :<modul>:testDebugUnitTest`).

## 30.0 Spike mit Beleg
Prüfe auf dem Gerät (Pixel 9 Pro XL, Android 17), in dieser Reihenfolge, was jeweils sichtbar ist, und trage es als Tabelle in den Bericht ein:
1. `logcat -d` aus der App ohne weitere Rechte.
2. Mit `READ_LOGS` per ADB freigegeben (`pm grant`).
3. `adb logcat` aus Termux über drahtloses Debugging (`adb connect localhost:<port>`), wenn Task 29.5 positiv war.
Daraus ergibt sich, welche Quellen `LogcatSource` anbietet: `OwnProcess`, `ReadLogsPermission`, `AdbLoopback`. Nicht verfügbare Quellen im UI als solche erklären (mit Schritten zur Freigabe).

## 30.1 Domain und Parser
* `LogEntry(timestamp, pid, tid, level, tag, message)` in `:core:domain`; `LogcatParser` für das Format `-v threadtime` (`MM-DD HH:MM:SS.mmm  PID  TID L Tag: Meldung`), Mehrzeiler (Stacktraces) der vorherigen Meldung zuordnen. Fixtures aus echter Ausgabe.
* Absturzerkennung: `FATAL EXCEPTION` und Stacktrace-Zeilen `at pkg.Klasse.methode(Datei.kt:12)` → Position (Datei, Zeile). Zuordnung zur Projektdatei über den Klassen-/Paketnamen und den Index (`:libs:indexing-api`); kein Treffer = nur Text.

## 30.2 Streaming
* Prozess `logcat -v threadtime` (oder `adb logcat`) als `Flow<LogEntry>`; Beenden beim Verlassen des Bildschirms; Ringpuffer (Standard 50 000 Einträge), Bündelung der UI-Updates.
* Filter ohne Neustart des Stroms: nach Paket (über `--pid`, PID per `pidof`/`ps` des Pakets, wird bei App-Neustart neu aufgelöst), Level (Verbose bis Fatal), Tag, Freitext, „nur Abstürze“.

## 30.3 UI (`:feature:build` oder eigenes Modul `:feature:logcat`, begründen)
* Liste mit Level-Farben, Zeitstempel, Tag; Pause/Fortsetzen, Löschen, Kopieren (Auswahl und alles), Teilen als Textdatei.
* Absturzeintrag → klickbarer Stacktrace; Tipp auf eine Zeile mit Quellposition öffnet die Datei an dieser Stelle.
* Paketfilter mit Vorschlag „zuletzt gestartete App“ (aus Task 29, falls vorhanden).

## 30.4 Tests
* Parser: Fixtures (normale Zeilen, Mehrzeiler, Absturz, Zeilen ohne Tag).
* Filterlogik und Ringpuffer als reine Funktionen.
* Prozessstrom mit Fake-Prozess.
* Alles, was nur auf dem Gerät prüfbar ist (Rechte, echte Logs), in den Bericht unter nicht getestet, falls nicht ausgeführt.

## Abnahme
Build grün, Tests grün, Bericht mit der Tabelle aus 30.0 (oder dem Vermerk „nicht ausgeführt“), UI erklärt fehlende Rechte verständlich.
