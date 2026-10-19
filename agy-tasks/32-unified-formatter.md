# Task 32 – Einheitlicher „Formatieren“-Befehl

**Ziel:** Ein Befehl „Datei formatieren“ (und optional „beim Speichern“) für Kotlin, Java, XML und JSON, mit echtem Formatierer statt Näherung, wo möglich.
**Ausgangslage:** `libs/code-tools/.../format/CodeFormatter.kt` formatiert mit einem eigenen klammerbasierten Verfahren (`BraceFormatter`), das ist kein ktlint und kein google-java-format. `ktlint` und `google-java-format` tauchen nur in der Build-Konfiguration auf (`QualityConventionPlugin.kt`). `LspClientRepository.requestFormat(path, content)` existiert. Es ist nicht geprüft, wie die Formatierung heute im Editor verdrahtet ist.

**Gemeinsame Regeln (gelten für jeden Task):**
* Clean Architecture wie im Rest des Repos: Schnittstellen und Modelle in `:core:domain`, Implementierung in `:libs:*` bzw. im Feature-Modul, UI in `:feature:*` (MVVM/MVI mit Hilt, `@Immutable`-Daten-UI-States, `StateFlow`; Material 3; `modifier: Modifier = Modifier` als erster optionaler Parameter; keine Logik in Composables). Gradle Kotlin DSL, Versionen im Version Catalog (`gradle/libs.versions.toml`), Convention-Plugins aus `build-logic`.
* Alle UI-Texte über `:core:resources` (Englisch Standard, Deutsch in `values-de`); der String-Check (Task 17) muss grün bleiben.
* Nichts erfinden: Was du nicht selbst ausgeführt hast, steht im Bericht unter **nicht getestet** (insbesondere Gerätetests; Zielgerät des Inhabers: Pixel 9 Pro XL, Android 17). Kein „funktioniert“ ohne Beleg.
* Geheimnisse (Passwörter, Tokens) nie ins Log, nie in Dateien im Projektordner, nie in Berichte.
* Bericht in `docs/<name>-report.md` mit Abschnitten **getestet / nicht getestet / offene Punkte**; `agy-tasks/TASKS-STATUS.md` und `agy-tasks/README.md` um den Task ergänzen (Commit-Hash eintragen).
* Build muss grün sein: `./gradlew :app:assembleDebug` und die Tests der betroffenen Module (`./gradlew :<modul>:testDebugUnitTest`).

## 32.1 Bestandsaufnahme
Lies, wo `CodeFormatter` und `requestFormat` verwendet werden, und dokumentiere im Bericht die tatsächliche Kette (Editor → Formatierer). Keine Doppelwege hinterlassen.

## 32.2 Strategie je Sprache (Reihenfolge der Quellen)
1. LSP-Formatierung, wenn der Server sie anbietet (`documentFormatting`/`rangeFormatting`).
2. Sonst Termux-Prozess: Kotlin → `ktlint --format` (Paket oder Release-Asset prüfen und dem APT-Plan, `apt-stages.conf`, nur nach Absprache hinzufügen; Eintrag im Bericht vorschlagen, nicht eigenmächtig bauen), Java → `google-java-format` (JAR, Download mit SHA-256 wie im Extensions-Manager).
3. Sonst der eingebaute `CodeFormatter` mit sichtbarem Hinweis „vereinfachte Formatierung“.
Die verwendete Quelle im UI kurz anzeigen (Toast/Statuszeile).

## 32.3 Verhalten
* „Datei formatieren“ und „Auswahl formatieren“; Cursor und Scrollposition bleiben erhalten (Differenz-Anwendung statt kompletter Ersatz des Puffers); ein Undo-Schritt.
* Fehler im Quelltext (Syntaxfehler) lassen die Datei unverändert und zeigen die Meldung.
* Einstellungen: „Beim Speichern formatieren“ je Sprache (Standard aus), Einrückung und Zeilenlänge aus `.editorconfig` des Projekts, falls vorhanden.
* Zeitlimit für externe Prozesse (z. B. 10 s) mit Abbruchmeldung.

## 32.4 Tests
* Eingebauter Formatierer: bestehende Tests erhalten, Randfälle (Raw Strings, Kommentare) ergänzen.
* Strategie-Auswahl: Fake-Quellen, richtige Reihenfolge, Fallback bei Fehler.
* Cursor-Erhalt: Test mit bekannter Änderung vor und nach dem Cursor.

## Abnahme
Build grün, Tests grün, Bericht beschreibt genau, welche Formatierer auf dem Gerät tatsächlich laufen und welche nur vorgesehen sind.
