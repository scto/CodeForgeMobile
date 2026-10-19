# Task 29 – APK installieren und starten

**Ziel:** Nach einem erfolgreichen Build (Task 28) kann der Nutzer die erzeugte APK mit einem Tipp installieren und die App starten, ohne den Umweg über Dateimanager oder Download-Ordner.
**Abhängigkeit:** Task 28 (Build-Läufer und Build-Ende-Ereignis). Ohne Task 28 nur die Installationslogik bauen und per Datei-Auswahl testen.
**Ausgangslage:** `REQUEST_INSTALL_PACKAGES` und `REQUEST_DELETE_PACKAGES` stehen im Manifest und werden im Onboarding abgefragt (Task 24). Es gibt keine Installationslogik für Projekt-APKs (kein Treffer für `PackageInstaller` im Feature-Code, Suche beschränkt, bitte selbst prüfen).

**Gemeinsame Regeln (gelten für jeden Task):**
* Clean Architecture wie im Rest des Repos: Schnittstellen und Modelle in `:core:domain`, Implementierung in `:libs:*` bzw. im Feature-Modul, UI in `:feature:*` (MVVM/MVI mit Hilt, `@Immutable`-Daten-UI-States, `StateFlow`; Material 3; `modifier: Modifier = Modifier` als erster optionaler Parameter; keine Logik in Composables). Gradle Kotlin DSL, Versionen im Version Catalog (`gradle/libs.versions.toml`), Convention-Plugins aus `build-logic`.
* Alle UI-Texte über `:core:resources` (Englisch Standard, Deutsch in `values-de`); der String-Check (Task 17) muss grün bleiben.
* Nichts erfinden: Was du nicht selbst ausgeführt hast, steht im Bericht unter **nicht getestet** (insbesondere Gerätetests; Zielgerät des Inhabers: Pixel 9 Pro XL, Android 17). Kein „funktioniert“ ohne Beleg.
* Geheimnisse (Passwörter, Tokens) nie ins Log, nie in Dateien im Projektordner, nie in Berichte.
* Bericht in `docs/<name>-report.md` mit Abschnitten **getestet / nicht getestet / offene Punkte**; `agy-tasks/TASKS-STATUS.md` und `agy-tasks/README.md` um den Task ergänzen (Commit-Hash eintragen).
* Build muss grün sein: `./gradlew :app:assembleDebug` und die Tests der betroffenen Module (`./gradlew :<modul>:testDebugUnitTest`).

## 29.1 APK finden
* Pfad aus `build/outputs/apk/<variant>/output-metadata.json` lesen (Varianten-Name, ABI-Splits, `outputFile`); Fallback: neueste `*.apk` unter `build/outputs/apk/`. Bei mehreren Treffern (ABI-Splits) die zum Gerät passende (`Build.SUPPORTED_ABIS`) wählen oder fragen.
* Nicht signierte Release-APKs (`*-unsigned.apk`) erkennen und mit klarer Meldung auf Task 34 (Signieren) verweisen.

## 29.2 Installation
* Über `PackageInstaller` (Session, `commit`, Statusempfänger). `STATUS_PENDING_USER_ACTION` → System-Dialog starten. Alle Statuscodes auf verständliche, übersetzte Meldungen abbilden, vor allem `INSTALL_FAILED_UPDATE_INCOMPATIBLE` (anderer Signaturschlüssel: Angebot „Deinstallieren und neu installieren“ mit Hinweis auf Datenverlust), `INSTALL_FAILED_VERSION_DOWNGRADE`, `INSTALL_FAILED_INSUFFICIENT_STORAGE`.
* Paketname und Version vor der Installation mit `PackageManager.getPackageArchiveInfo` lesen und anzeigen („com.beispiel.app 1.2 (14)“). Fehlt das Recht „Unbekannte Apps installieren“, zur passenden Einstellungsseite führen (bestehendes Onboarding-Muster wiederverwenden).
* Deinstallieren über `ACTION_DELETE`/`PackageInstaller.uninstall` (Recht `REQUEST_DELETE_PACKAGES` ist vorhanden).

## 29.3 Starten
* Nach erfolgreicher Installation Schaltfläche „Starten“ (`getLaunchIntentForPackage`); automatisches Starten nur als Einstellung, Standard aus.
* Hinweis im UI, wenn die Zielapp CodeForge selbst ersetzt (gleicher Paketname): erst nach Bestätigung.

## 29.4 Ablauf-Integration
* Aktion „Bauen und starten“ in `:feature:build` (Task 28): `assembleDebug` → Installationsablauf → Start. Bei Build-Fehler kein Installationsversuch.
* Zustand als `@Immutable`-UI-State (`Idle`, `Building`, `ReadyToInstall`, `Installing`, `NeedsUserAction`, `Installed`, `Failed(reason)`).

## 29.5 Optional (nur als kleiner Spike, nicht ausbauen)
Prüfe, ob drahtloses ADB mit `android-tools` (Termux-Paket) von Termux aus erreichbar ist (`adb pair`, `adb connect localhost:<port>`) und ob damit `adb install` auf das eigene Gerät klappt. Ergebnis im Bericht (ja/nein, Schritte). Wird in Task 30 gebraucht.

## 29.6 Tests
* `output-metadata.json`-Parser: JVM-Tests mit echten Beispieldateien.
* Statusabbildung (Fake-Installer): jeder Statuscode → erwartete Meldung.
* Zustandsautomat im ViewModel.
* Echte Installation: nicht in JVM-Tests möglich → im Bericht ausdrücklich „nicht ausgeführt“ oder mit Gerät belegen.

## Abnahme
Build grün, Tests grün, Bericht mit getestet/nicht getestet, ehrlich zum Gerätelauf.
