# Task 34 – Signatur-Verwaltung (Keystore) und Release-Signierung

**Ziel:** Der Nutzer legt einen Schlüsselspeicher an oder importiert einen, verwaltet Alias und Passwörter sicher und signiert Release-APKs, ohne Passwörter in Projektdateien zu schreiben.
**Ausgangslage:** `SecretStorage` (`core/data/.../security`) existiert für Geheimnisse; `DefaultGitIgnore` schließt `*.jks`/`*.keystore` aus. Eine Oberfläche für Keystores habe ich nicht gefunden (bitte selbst prüfen). Werkzeuge im APT-Plan: `apksigner` (Etappe `04-jvm-tools`), `openjdk-17` (liefert `keytool`, noch nicht im Repo gebaut).

**Gemeinsame Regeln (gelten für jeden Task):**
* Clean Architecture wie im Rest des Repos: Schnittstellen und Modelle in `:core:domain`, Implementierung in `:libs:*` bzw. im Feature-Modul, UI in `:feature:*` (MVVM/MVI mit Hilt, `@Immutable`-Daten-UI-States, `StateFlow`; Material 3; `modifier: Modifier = Modifier` als erster optionaler Parameter; keine Logik in Composables). Gradle Kotlin DSL, Versionen im Version Catalog (`gradle/libs.versions.toml`), Convention-Plugins aus `build-logic`.
* Alle UI-Texte über `:core:resources` (Englisch Standard, Deutsch in `values-de`); der String-Check (Task 17) muss grün bleiben.
* Nichts erfinden: Was du nicht selbst ausgeführt hast, steht im Bericht unter **nicht getestet** (insbesondere Gerätetests; Zielgerät des Inhabers: Pixel 9 Pro XL, Android 17). Kein „funktioniert“ ohne Beleg.
* Geheimnisse (Passwörter, Tokens) nie ins Log, nie in Dateien im Projektordner, nie in Berichte.
* Bericht in `docs/<name>-report.md` mit Abschnitten **getestet / nicht getestet / offene Punkte**; `agy-tasks/TASKS-STATUS.md` und `agy-tasks/README.md` um den Task ergänzen (Commit-Hash eintragen).
* Build muss grün sein: `./gradlew :app:assembleDebug` und die Tests der betroffenen Module (`./gradlew :<modul>:testDebugUnitTest`).

## 34.1 Sicherheitsvorgaben (verbindlich)
* Keystore-Dateien nur im privaten App-Speicher (`filesDir/keystores/`), Passwörter nur in `SecretStorage` (verschlüsselt). Nie in `gradle.properties`, `local.properties`, Berichte, Logs oder Prozess-Argumentlisten (sichtbar in `ps`), sondern über Umgebungsvariablen des Kindprozesses oder `stdin`.
* Export des Keystores nur nach ausdrücklicher Bestätigung und mit Warnung (Verlust = App nie wieder aktualisierbar).
* Warnung bei Debug-Schlüssel für Release.

## 34.2 Funktionen
1. **Anlegen:** Formular (Alias, Gültigkeit, Name/Organisation/Land, Algorithmus RSA 4096 oder EC, Passwörter mit Stärkeanzeige). Erzeugung per `keytool -genkeypair` aus dem installierten JDK (Termux) oder, wenn kein JDK vorhanden, klare Meldung mit Verweis auf die JDK-Installation. Format PKCS12.
2. **Importieren:** Datei wählen, Passwort prüfen (`keytool -list`), Aliasse anzeigen.
3. **Anzeigen:** SHA-1/SHA-256-Fingerabdrücke, Gültigkeit, Algorithmus (für Google-Dienste kopierbar).
4. **Signieren:** Auswahl einer unsignierten oder neu zu signierenden APK, Signieren mit `apksigner sign` (v1–v4 gemäß minSdk), anschließend `apksigner verify --print-certs` und Ergebnis anzeigen. Ausgabe `*-signed.apk` neben der Quelle.
5. **Gradle-Anbindung:** Option „Release mit diesem Schlüssel bauen“: Variablen `CODEFORGE_KEYSTORE_PATH`, `CODEFORGE_KEYSTORE_ALIAS`, `CODEFORGE_KEYSTORE_PASSWORD`, `CODEFORGE_KEY_PASSWORD` nur für den Build-Prozess setzen (Task 28) und einen kopierfertigen `signingConfigs`-Block für `build.gradle.kts` anzeigen, der diese Variablen liest.

## 34.3 Modul
Neues Modul `:feature:signing` (UI) mit Domain-Schnittstelle `SigningRepository` in `:core:domain` und Implementierung, die Prozesse über `TermuxEnvironment` startet (Muster wie `KotlincProcessCompiler`).

## 34.4 Tests
* Ausgabe-Parser für `keytool -list -v` (Fingerabdrücke, Alias, Gültigkeit) mit echter Beispielausgabe.
* Passwort-Weitergabe: Test, dass kein Passwort in der Argumentliste steht (Fake-Prozess prüft Argumente).
* Zustandsautomat für Anlegen/Signieren.
* Echte `keytool`/`apksigner`-Läufe: nur mit installiertem JDK möglich → Bericht „nicht ausgeführt“ oder belegen.

## Abnahme
Build grün, Tests grün, Bericht mit Sicherheitsprüfung (wo liegen Passwörter, was wird geloggt), ehrlich zu nicht ausgeführten Prozessläufen.
