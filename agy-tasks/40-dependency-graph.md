# Task 40 – Abhängigkeitsgraph (Baum, „Warum ist das drin?“, Konflikte)

**Ziel:** Der Nutzer sieht die aufgelösten Abhängigkeiten seines Projekts als durchsuchbaren Baum, erfährt zu jeder Bibliothek, **warum** sie enthalten ist, und erkennt Versionskonflikte und Duplikate.
**Abhängigkeit:** Task 28 (Gradle-Lauf als Prozess). Ohne diesen Task nur den Parser bauen und mit gespeicherter Beispielausgabe testen.
**Ausgangslage:** Es gibt `:libs:dependency-updater-api/impl` (Versionen im Katalog, `DependencyUpdate`, `LibraryCoordinate`, Maven-Metadaten) und `:feature:dependencyupdates` (Update-Dialog). Der Updater arbeitet auf den **deklarierten** Versionen (Katalog/Build-Dateien), nicht auf dem **aufgelösten** Graphen. Einen Graphen gibt es nicht (Stichwortsuche ohne Treffer, prüfen).

**Gemeinsame Regeln (gelten für jeden Task):**
* Clean Architecture wie im Rest des Repos: Schnittstellen und Modelle in `:core:domain`, Implementierung in `:libs:*` bzw. im Feature-Modul, UI in `:feature:*` (MVVM/MVI mit Hilt, `@Immutable`-Daten-UI-States, `StateFlow`; Material 3; `modifier: Modifier = Modifier` als erster optionaler Parameter; keine Logik in Composables). Gradle Kotlin DSL, Versionen im Version Catalog (`gradle/libs.versions.toml`), Convention-Plugins aus `build-logic`.
* Alle UI-Texte über `:core:resources` (Englisch Standard, Deutsch in `values-de`); der String-Check (Task 17) muss grün bleiben.
* Nichts erfinden: Was du nicht selbst ausgeführt hast, steht im Bericht unter **nicht getestet** (insbesondere Gerätetests; Zielgerät des Inhabers: Pixel 9 Pro XL, Android 17). Kein „funktioniert“ ohne Beleg. Aussagen dieses Tasks über den Bestand stammen aus Stichwortsuchen; prüfe sie vor dem Start selbst und korrigiere sie im Bericht.
* Geheimnisse (Passwörter, Tokens, API-Schlüssel) nie ins Log, nie in Dateien im Projektordner, nie in Berichte.
* Bericht in `docs/<name>-report.md` mit Abschnitten **getestet / nicht getestet / offene Punkte**; `agy-tasks/TASKS-STATUS.md` und `agy-tasks/README.md` um den Task ergänzen (Commit-Hash eintragen).
* Build muss grün sein: `./gradlew :app:assembleDebug` und die Tests der betroffenen Module (`./gradlew :<modul>:testDebugUnitTest`).

## 40.1 Datenquelle
`./gradlew :<modul>:dependencies --configuration <konfiguration> --console=plain` und `:<modul>:dependencyInsight --dependency <koordinate> --configuration <konfiguration>` über den Build-Läufer aus Task 28. Standard-Konfigurationen je Modul (`debugRuntimeClasspath`, `debugCompileClasspath`, `releaseRuntimeClasspath`, `debugUnitTestRuntimeClasspath`) wählbar; die Liste der Module/Konfigurationen aus dem Projekt ermitteln, nicht fest verdrahten. Läufe sind langsam: Ergebnis zwischenspeichern (je Modul/Konfiguration, mit Zeitstempel und Prüfsumme der Build-Dateien), „Neu laden“ als Aktion.

## 40.2 Parser (reines Kotlin, JVM-testbar)
Gradle-Textbaum (`+---`, `\---`, Einrückung `|    `) mit den Sonderzeichen: `(*)` = bereits oben aufgeführt, `(c)` = Constraint, `(n)` = nicht auflösbar/Hinweis, `FAILED`, Auflösungspfeil `1.0 -> 2.0` (Versionsänderung durch Konfliktlösung), `-> project :core:domain`, `project :x`, `{strictly …}`/`{require …}`-Bereiche. Modell: `DependencyNode(coordinate, requestedVersion, resolvedVersion, children, isProject, isConstraint, isRepeated, failed)`. **Fixtures aus echten Ausgaben dieses Repos** (mehrere Module und Konfigurationen); erfinde kein Format.
`dependencyInsight`-Ausgabe separat parsen: Auswahlgrund (`Selected by rule`, `By conflict resolution`, `By constraint`, `Forced`), Pfade bis zur Wurzel.

## 40.3 Auswertungen
* **Konflikte:** Bibliotheken, deren angefragte Version von der aufgelösten abweicht, mit Verursacher.
* **Duplikate:** gleiche Bibliothek in mehreren Versionen über Konfigurationen/Module; gleiche Funktion durch verschiedene Koordinaten (z. B. alte und neue Paketnamen) nur als Hinweis, nicht automatisch.
* **Größe/Tiefe:** Anzahl transitiver Abhängigkeiten, tiefste Pfade. Keine Größenangaben in Bytes ohne belegte Quelle.
* Verknüpfung mit dem Updater: Eintrag trägt Hinweis „Update verfügbar: x → y“, wenn `DependencyUpdateRepository` ein Update kennt; Tipp öffnet den bestehenden Update-Dialog.

## 40.4 UI (`:feature:dependencies`, neues Modul, oder Erweiterung von `:feature:dependencyupdates` mit Begründung)
* Baumansicht mit Aufklappen, Suche (Koordinate/Gruppe), Filter (nur Konflikte, nur Projekt-Module, nur direkte Abhängigkeiten), Auswahl von Modul und Konfiguration.
* Detailblatt zu einer Bibliothek: angefragte/aufgelöste Version, Auswahlgrund, **alle Pfade bis zur Wurzel** (aus `dependencyInsight`), Aktionen „In Katalog anzeigen“ (springt zur Zeile in `libs.versions.toml` über die Fundstellen des Updaters), „Kopieren“.
* Optional: einfache Graph-Zeichnung (Knoten und Kanten) für eine ausgewählte Bibliothek mit Nachbarn; nur wenn lesbar auf dem Smartphone, sonst bei Baum und Pfadliste bleiben.
* Export als Text/Markdown.

## 40.5 Tests
* Parser: einfache Bäume, `(*)`-Wiederholungen, Auflösungspfeile, Constraints, `FAILED`, Projektabhängigkeiten, Plugin-Konfigurationen, sehr tiefe Bäume (kein Stapelüberlauf: iterativ oder Tiefenlimit).
* Konflikt-/Duplikat-Auswertung auf Fixtures.
* Cache-Gültigkeit (Build-Datei geändert → ungültig).
* ViewModel mit Fake-Läufer.

## Abnahme
Build grün, Tests grün, Fixtures aus echten Läufen, Bericht mit Laufzeiten der Gradle-Aufrufe auf dem Gerät (oder „nicht gemessen“).
