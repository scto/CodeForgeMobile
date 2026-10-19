# Task 37b – Debugger: Breakpoints, Variablen und Schritte im Editor

**Abhängigkeit:** Task 37a mit positivem Spike. Ohne diese Voraussetzung nicht starten.
**Ziel:** Der Nutzer setzt Breakpoints im Editor, startet die Debug-Sitzung, sieht Anhaltestelle, Aufrufliste und Variablen und steuert Fortsetzen und Schritte.

**Gemeinsame Regeln (gelten für jeden Task):**
* Clean Architecture wie im Rest des Repos: Schnittstellen und Modelle in `:core:domain`, Implementierung in `:libs:*` bzw. im Feature-Modul, UI in `:feature:*` (MVVM/MVI mit Hilt, `@Immutable`-Daten-UI-States, `StateFlow`; Material 3; `modifier: Modifier = Modifier` als erster optionaler Parameter; keine Logik in Composables). Gradle Kotlin DSL, Versionen im Version Catalog (`gradle/libs.versions.toml`), Convention-Plugins aus `build-logic`.
* Alle UI-Texte über `:core:resources` (Englisch Standard, Deutsch in `values-de`); der String-Check (Task 17) muss grün bleiben.
* Nichts erfinden: Was du nicht selbst ausgeführt hast, steht im Bericht unter **nicht getestet** (insbesondere Gerätetests; Zielgerät des Inhabers: Pixel 9 Pro XL, Android 17). Kein „funktioniert“ ohne Beleg. Aussagen dieses Tasks über den Bestand stammen aus Stichwortsuchen; prüfe sie vor dem Start selbst und korrigiere sie im Bericht.
* Geheimnisse (Passwörter, Tokens, API-Schlüssel) nie ins Log, nie in Dateien im Projektordner, nie in Berichte.
* Bericht in `docs/<name>-report.md` mit Abschnitten **getestet / nicht getestet / offene Punkte**; `agy-tasks/TASKS-STATUS.md` und `agy-tasks/README.md` um den Task ergänzen (Commit-Hash eintragen).
* Build muss grün sein: `./gradlew :app:assembleDebug` und die Tests der betroffenen Module (`./gradlew :<modul>:testDebugUnitTest`).

## 37b.1 Breakpoints im Editor (`:feature:editor`)
* Tipp auf die Zeilennummernspalte setzt/entfernt einen Breakpoint (roter Punkt); lange Drücken: Bedingung oder Log-Meldung bearbeiten; deaktivieren ohne Löschen.
* Breakpoints bleiben bei Zeilenänderungen an der richtigen Stelle (Positionen mit dem Editor-Text mitführen) und werden pro Projekt gespeichert (Datei im App-Speicher, nicht im Projektordner).
* Übersichtsliste aller Breakpoints (Datei, Zeile, Bedingung), Tippen springt hin; „Alle entfernen“ mit Rückfrage.
* Hinweis, wenn der Adapter einen Breakpoint nicht binden kann (nicht verifiziert, z. B. Zeile ohne Code).

## 37b.2 Debug-Panel (`:feature:debug`, neues Modul)
* Symbolleiste: Starten/Beenden, Fortsetzen, Pause, Schritt über, Schritt hinein, Schritt hinaus. Zustandsanzeige (läuft, angehalten bei Datei:Zeile, beendet mit Exit-Code).
* Reiter **Aufrufliste** (Rahmen, Tipp springt in die Quelle und setzt die Anzeige im Editor) und **Variablen** (Baum, nachladbar über `variablesReference`, Typ und Wert, lange Werte gekürzt mit „Mehr“, Kopieren). Reiter **Auswerten** (Ausdruck eingeben, Ergebnis anzeigen) nur, wenn der Adapter es kann.
* Anhaltezeile im Editor hervorgehoben, der Editor scrollt dorthin. Bei Wechsel der Datei im Editor die Hervorhebung korrekt führen.
* Während „läuft“ Schaltflächen für Schritte deaktivieren (kein Fehlverhalten durch schnelle Mehrfachtipps).

## 37b.3 Verhalten bei Fehlern
Adapter stirbt, Zeitüberschreitung, Programm beendet sich: UI zeigt eine verständliche Meldung und fällt in `Idle` zurück, Breakpoints bleiben erhalten. Kein Absturz der App, keine hängenden Prozesse (Prozess im `finally` beenden).

## 37b.4 Tests
* ViewModel mit Fake-`DebugRepository`: alle Zustandsübergänge, Mehrfachtipps, Fehlerfälle.
* Breakpoint-Positionsführung bei Einfügen/Löschen von Zeilen (reine Funktion).
* Variablenbaum: Nachladen, zyklische Referenzen, große Listen (Paginierung/Kürzung).
* `assembleDebugAndroidTest` kompiliert; echter Lauf auf Gerät mit dem Testprogramm aus 37a im Bericht belegen oder „nicht ausgeführt“.

## Abnahme
Build grün, Tests grün, ein dokumentierter Durchlauf (Breakpoint → Anhalten → Variable → Schritt → Ende) oder ehrlicher Vermerk, dass er nicht auf dem Gerät lief.
