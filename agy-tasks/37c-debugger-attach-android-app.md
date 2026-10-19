# Task 37c – Debugger: laufende Android-App anhängen

**Abhängigkeit:** Task 37a und 37b (positiv), Task 29.5 (drahtloses ADB aus Termux erreichbar, `adb` aus `android-tools`) und Task 29 (APK installieren und starten).
**Ziel:** Der Nutzer startet die selbst gebaute Debug-App wartend auf den Debugger und hängt die Debug-Sitzung an, mit denselben Breakpoints und Ansichten wie in 37b.
**Wichtige Unsicherheit:** JDI gegen die JDWP-Implementierung der Android-Laufzeit (ART) funktioniert nicht zwingend so wie gegen eine Desktop-JVM. Das ist unbelegt und muss im Spike geklärt werden; bei negativem Ergebnis Bericht schreiben und stoppen.

**Gemeinsame Regeln (gelten für jeden Task):**
* Clean Architecture wie im Rest des Repos: Schnittstellen und Modelle in `:core:domain`, Implementierung in `:libs:*` bzw. im Feature-Modul, UI in `:feature:*` (MVVM/MVI mit Hilt, `@Immutable`-Daten-UI-States, `StateFlow`; Material 3; `modifier: Modifier = Modifier` als erster optionaler Parameter; keine Logik in Composables). Gradle Kotlin DSL, Versionen im Version Catalog (`gradle/libs.versions.toml`), Convention-Plugins aus `build-logic`.
* Alle UI-Texte über `:core:resources` (Englisch Standard, Deutsch in `values-de`); der String-Check (Task 17) muss grün bleiben.
* Nichts erfinden: Was du nicht selbst ausgeführt hast, steht im Bericht unter **nicht getestet** (insbesondere Gerätetests; Zielgerät des Inhabers: Pixel 9 Pro XL, Android 17). Kein „funktioniert“ ohne Beleg. Aussagen dieses Tasks über den Bestand stammen aus Stichwortsuchen; prüfe sie vor dem Start selbst und korrigiere sie im Bericht.
* Geheimnisse (Passwörter, Tokens, API-Schlüssel) nie ins Log, nie in Dateien im Projektordner, nie in Berichte.
* Bericht in `docs/<name>-report.md` mit Abschnitten **getestet / nicht getestet / offene Punkte**; `agy-tasks/TASKS-STATUS.md` und `agy-tasks/README.md` um den Task ergänzen (Commit-Hash eintragen).
* Build muss grün sein: `./gradlew :app:assembleDebug` und die Tests der betroffenen Module (`./gradlew :<modul>:testDebugUnitTest`).

## 37c.0 Spike
1. `adb jdwp` zeigt die PID einer debuggbaren App? `adb forward tcp:<port> jdwp:<pid>` anlegen, danach mit dem JDI-Client aus 37a attachen. Nachweis: Breakpoint setzen, Anhalten, Stack und Variablen lesen. Auf dem Pixel 9 Pro XL (Android 17) messen.
2. Bekannte Abweichungen von ART gegenüber der JVM (Klassen-/Zeileninformationen aus D8/R8-Ausgabe, lokale Variablen nur mit Debuginformation, Inline-Funktionen von Kotlin) ausprobieren und im Bericht auflisten.
3. Alternative bei Scheitern: Debuggen nur von JVM-Programmen (37b) dokumentieren.

## 37c.1 Start „auf Debugger warten“
`adb shell am start -D -n <paket>/<activity>` (Wartedialog) oder in der Build-Konfiguration der Zielapp `debuggable true` voraussetzen; Prüfung des `debuggable`-Flags vor dem Start (aus der APK-Analyse, Task 35, oder `PackageManager`), sonst klare Meldung. App-PID automatisch ermitteln und nach Neustart der App neu verbinden.

## 37c.2 Quellzuordnung
Breakpoint-Zeile → Klasse/Zeile der Laufzeit: Zuordnung von Quelldatei zu Klassennamen über Paket-Deklaration und Index (`:libs:indexing-api`); Kotlin-Dateien mit mehreren Klassen und `@file:JvmName` beachten. Nicht zuordenbare Breakpoints als „nicht verifiziert“ markieren statt zu raten.

## 37c.3 Aufräumen
Beim Beenden der Sitzung `adb forward --remove`, Adapter-Prozess beenden, optional die App beenden (Einstellung). Nichts hängen lassen, wenn die App abstürzt oder die Verbindung abbricht.

## 37c.4 Tests
* Port-Weiterleitung und PID-Ermittlung gegen Fake-`adb`-Ausgaben (echte Beispiele als Fixtures).
* Quellzuordnung: Beispielprojekte mit Kotlin-Besonderheiten.
* Echter Lauf auf dem Gerät: im Bericht belegen oder „nicht ausgeführt“.

## Abnahme
Bericht mit Spike-Ergebnis auf dem Gerät. Bei positivem Ergebnis: Durchlauf „Bauen → Installieren → Starten wartend → Anhängen → Breakpoint → Variablen“.
