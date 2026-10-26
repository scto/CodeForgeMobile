# Task 58 — Gradle-Verifikation: x2c (52.12), Build-Sheet, KI-Verbindung, NDK-Pfad

Kontext: In der Cloud-Umgebung scheitert `:app` an Maven-Central-Rate-Limits (HTTP 429). Verifiziert dort:
`:feature:build`, `:feature:x2c`, `:libs:ai-client`, `:core:data` (Unit-Tests grün). **Nicht** verifiziert: `:app`.

## Auftrag (im Projekt-Root, Termux)
1. `./gradlew :app:assembleDebug` — muss ohne Fehler laufen. Betroffen: `app/.../ProjectWorkspaceRoute.kt`
   (`rememberBuildTextActions`, Effekte `CopyToClipboard`/`ShareText`/`SaveText`, X2C-Dialoge 52.11).
2. `./gradlew :feature:x2c:testDebugUnitTest :feature:build:testDebugUnitTest :libs:ai-client:testDebugUnitTest :core:data:testDebugUnitTest`
3. NDK: `codeforge-env` erneut ausführen, danach `ls $ANDROID_HOME/ndk` (Version vorhanden) und
   prüfen, dass **kein** `$ANDROID_HOME/ndk-bundle` existiert; `:app:assembleDebug` meldet keinen NDK-Pfadfehler.
4. Gerätetest (siehe `docs/device-test-checklist.md`):
   - Build-Sheet: Tabs Ausgabe/Probleme/Logcat/Tests — Kopieren, Teilen, Speichern (Datei wählbar), Text markierbar;
     Scrollen der Logs bewegt das Sheet nicht; Schließen-Taste und Zurück schließen es.
   - KI-Assistent: Key speichern, App komplett beenden, neu starten → Chat funktioniert ohne erneute Key-Eingabe;
     bei Fehler zeigt die Meldung die konkrete Ursache (DNS/Timeout/TLS/URL/HTTP-Status).
   - X2C (52.12): Fortschritt/Ergebnis-Dialog, Foreground-Service, Gradle-Check.
5. Ergebnisse in `docs/x2c-report.md` (Abschnitt 52.12) und `TASKS-STATUS.md` eintragen; Fehler beheben, committen.

## Akzeptanz
`:app:assembleDebug` grün, alle genannten Tests grün, Gerätecheckliste abgehakt oder Abweichungen dokumentiert.
