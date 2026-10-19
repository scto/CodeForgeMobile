# Task 36 – Test-Läufer mit Ergebnisbaum

**Ziel:** Der Nutzer startet Unit-Tests (alle, eine Klasse, eine Methode) und sieht die Ergebnisse als Baum mit Status, Dauer und klickbarem Fehlerort.
**Abhängigkeit:** Task 28 (Build-Läufer). Ohne Task 28 nicht starten.
**Ausgangslage:** Keine Test-Oberfläche im Repo (kein Treffer für Testläufer-Klassen im Feature-Code, bitte selbst gegenprüfen). Tests laufen heute nur über das Terminal.

**Gemeinsame Regeln (gelten für jeden Task):**
* Clean Architecture wie im Rest des Repos: Schnittstellen und Modelle in `:core:domain`, Implementierung in `:libs:*` bzw. im Feature-Modul, UI in `:feature:*` (MVVM/MVI mit Hilt, `@Immutable`-Daten-UI-States, `StateFlow`; Material 3; `modifier: Modifier = Modifier` als erster optionaler Parameter; keine Logik in Composables). Gradle Kotlin DSL, Versionen im Version Catalog (`gradle/libs.versions.toml`), Convention-Plugins aus `build-logic`.
* Alle UI-Texte über `:core:resources` (Englisch Standard, Deutsch in `values-de`); der String-Check (Task 17) muss grün bleiben.
* Nichts erfinden: Was du nicht selbst ausgeführt hast, steht im Bericht unter **nicht getestet** (insbesondere Gerätetests; Zielgerät des Inhabers: Pixel 9 Pro XL, Android 17). Kein „funktioniert“ ohne Beleg.
* Geheimnisse (Passwörter, Tokens) nie ins Log, nie in Dateien im Projektordner, nie in Berichte.
* Bericht in `docs/<name>-report.md` mit Abschnitten **getestet / nicht getestet / offene Punkte**; `agy-tasks/TASKS-STATUS.md` und `agy-tasks/README.md` um den Task ergänzen (Commit-Hash eintragen).
* Build muss grün sein: `./gradlew :app:assembleDebug` und die Tests der betroffenen Module (`./gradlew :<modul>:testDebugUnitTest`).

## 36.1 Ergebnisse aus JUnit-XML statt aus der Konsolenausgabe
* Nach dem Lauf `build/test-results/<task>/TEST-*.xml` lesen (robust, strukturiert). Parser für `<testsuite>`/`<testcase>`/`<failure>`/`<error>`/`<skipped>`, `time`, `system-out`/`system-err` je Fall.
* Bei Build-Abbruch vor den Tests (Kompilierfehler) kein leerer Baum, sondern Verweis auf die Probleme-Liste (Task 28).
* Fixtures: echte XML-Dateien aus einem Lauf dieses Repos (z. B. `:core:domain`-Tests).

## 36.2 Starten
* Task je Modul/Variante (`:modul:testDebugUnitTest`), eingrenzen mit `--tests "pkg.Klasse"` bzw. `--tests "pkg.Klasse.methode"`; „Nur fehlgeschlagene wiederholen“.
* Modul- und Variantenauswahl aus dem geöffneten Projekt (bestehende Projektanalyse/Index nutzen).
* Aus dem Editor: Symbol neben Funktionen mit `@Test` und neben Testklassen (Erkennung über Tree-sitter-Abfrage oder Index, kein Regex auf Rohtext), Tipp startet genau diesen Test.

## 36.3 UI
* Baum Modul → Klasse → Methode mit Status (bestanden/fehlgeschlagen/übersprungen), Dauer, Zählern oben. Filter „nur Fehler“, Suche.
* Fehlerdetail: Meldung, Stacktrace mit klickbaren Projektzeilen (Datei:Zeile öffnet im Editor), erwartet/tatsächlich bei Vergleichsfehlern, Ausgabe des Tests (stdout/stderr) ausklappbar.
* Ergebnis des letzten Laufs bleibt nach Neustart der App sichtbar (Dateien neu einlesen, nicht separat speichern).

## 36.4 Tests
* XML-Parser: bestanden, fehlgeschlagen mit Stacktrace, Fehler, übersprungen, parametrisierte Tests, Mehrfachdatei.
* Auswahl der `--tests`-Argumente (Klasse/Methode, Sonderzeichen).
* Stacktrace → Position: Zuordnung zur Projektdatei, Treffer und Nicht-Treffer.
* ViewModel mit Fake-Läufer.

## Abnahme
Build grün, Tests grün, Bericht mit einem echten Lauf (oder „nicht ausgeführt“), `agy-tasks`-Übersicht aktualisiert.
