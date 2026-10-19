# Task 35 – APK-Analyse

**Ziel:** Der Nutzer öffnet eine APK und sieht Größe nach Bereichen, Berechtigungen, Mindest- und Ziel-SDK, Signatur und kann zwei APKs vergleichen (Größenänderung).
**Ausgangslage:** Keine APK-Analyse im Repo (bitte selbst gegenprüfen). `aapt` ist im APT-Plan (Etappe `05-android-tools`), `apksigner` in `04-jvm-tools`; beide sind optional zu nutzen, die Größenanalyse muss ohne sie auskommen.

**Gemeinsame Regeln (gelten für jeden Task):**
* Clean Architecture wie im Rest des Repos: Schnittstellen und Modelle in `:core:domain`, Implementierung in `:libs:*` bzw. im Feature-Modul, UI in `:feature:*` (MVVM/MVI mit Hilt, `@Immutable`-Daten-UI-States, `StateFlow`; Material 3; `modifier: Modifier = Modifier` als erster optionaler Parameter; keine Logik in Composables). Gradle Kotlin DSL, Versionen im Version Catalog (`gradle/libs.versions.toml`), Convention-Plugins aus `build-logic`.
* Alle UI-Texte über `:core:resources` (Englisch Standard, Deutsch in `values-de`); der String-Check (Task 17) muss grün bleiben.
* Nichts erfinden: Was du nicht selbst ausgeführt hast, steht im Bericht unter **nicht getestet** (insbesondere Gerätetests; Zielgerät des Inhabers: Pixel 9 Pro XL, Android 17). Kein „funktioniert“ ohne Beleg.
* Geheimnisse (Passwörter, Tokens) nie ins Log, nie in Dateien im Projektordner, nie in Berichte.
* Bericht in `docs/<name>-report.md` mit Abschnitten **getestet / nicht getestet / offene Punkte**; `agy-tasks/TASKS-STATUS.md` und `agy-tasks/README.md` um den Task ergänzen (Commit-Hash eintragen).
* Build muss grün sein: `./gradlew :app:assembleDebug` und die Tests der betroffenen Module (`./gradlew :<modul>:testDebugUnitTest`).

## 35.1 Analyse ohne Zusatzwerkzeuge (reines Kotlin/JVM, testbar)
* Per `ZipFile`: je Eintrag komprimierte und unkomprimierte Größe, gruppiert nach Bereichen: `classes*.dex`, `res/` (nach Ordner), `resources.arsc`, `lib/<abi>/`, `assets/`, `META-INF/`, `AndroidManifest.xml`, Sonstiges. Top-N größte Dateien. Anteil an Gesamtgröße.
* Pro-ABI-Aufschlüsselung der nativen Bibliotheken; Hinweis, wenn mehrere ABIs enthalten sind.
* Zip-Slip und beschädigte Archive abfangen.

## 35.2 Metadaten (optional über `aapt`, sonst `PackageManager.getPackageArchiveInfo`)
* Paketname, `versionName`/`versionCode`, `minSdk`/`targetSdk`/`compileSdk`, Berechtigungen (gefährliche hervorheben), Aktivitäten/Dienste/Empfänger (Anzahl, exportiert ja/nein).
* Signatur (`apksigner verify --print-certs`, falls vorhanden): Schema v1–v4, Zertifikat-Fingerabdruck. Fehlt `apksigner`, Abschnitt mit Hinweis ausblenden.
* Debuggable-Flag und Backup-Erlaubnis als Warnhinweise.

## 35.3 Vergleich
Zwei APKs wählen (z. B. letzte und aktuelle Build-Ausgabe): Delta pro Bereich und pro Datei (neu, entfernt, größer, kleiner), Gesamtdifferenz in Bytes und Prozent.

## 35.4 UI
Neuer Bildschirm (in `:feature:build`, wenn Task 28 vorhanden, sonst eigenes Modul): Zusammenfassung oben, Balkenübersicht je Bereich, ausklappbare Dateiliste mit Sortierung, Reiter für Berechtigungen, Vergleichsmodus. „Als Markdown teilen“. Aufruf aus dem Dateibaum (Kontextmenü auf `.apk`) und aus der Build-Ausgabe.

## 35.5 Tests
* Mit programmgesteuert erzeugten Test-ZIPs (kein echtes APK nötig): Gruppierung, Top-N, Differenz.
* Beschädigte und präparierte Archive (Zip-Slip) werden abgewiesen.
* Berechtigungs-Parser mit einer echten `aapt dump permissions`-Ausgabe als Fixture.

## Abnahme
Build grün, Tests grün, Bericht nennt, welche Teile ohne `aapt`/`apksigner` laufen und welche nicht.
