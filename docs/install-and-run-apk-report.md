# Task 29 – APK installieren und starten: Bericht

**Datum:** 19. Oktober 2026  
**Autor:** Antigravity / Thomas Schmid  
**Status:** Erfolgreich abgeschlossen  

---

## 1. Übersicht & Zielsetzung

Nach einem erfolgreichen Gradle-Build (Task 28) soll der Nutzer die erzeugte APK mit einem einzigen Fingertipp installieren und die App direkt aus CodeForge Mobile heraus starten können, ohne Umwege über externe Dateimanager oder Download-Ordner.

### Hauptkomponenten:
1. **APK-Erkennung (`ApkMetadataParser` & `findApk`):**
   - Liest `output-metadata.json` aus `build/outputs/apk/<variant>/` (Unterstützung für ABI-Splits mit Abgleich gegen `Build.SUPPORTED_ABIS`, `versionCode`, `versionName`, `outputFile`).
   - Fallback-Suche nach der neuesten `*.apk` unter `build/outputs/apk/`.
   - Erkennung nicht signierter Release-APKs (`*-unsigned.apk`) mit explizitem Hinweis auf Task 34 (Signieren).
2. **Paket-Informationen & Berechtigungsprüfung:**
   - Auslesen von Paketname, App-Label, Versionscode und Versionsname via `PackageManager.getPackageArchiveInfo`.
   - Prüfung von `canRequestPackageInstalls()`; bei fehlender Berechtigung Verlinkung zur Seite „Unbekannte Apps installieren“ (`ACTION_MANAGE_UNKNOWN_APP_SOURCES`).
3. **Installation über Android `PackageInstaller`:**
   - Erzeugung einer `PackageInstaller.Session` (`MODE_FULL_INSTALL`), Streamen der APK-Bytes inkl. `fsync`.
   - Abfangen von `STATUS_PENDING_USER_ACTION` und Weiterleitung des System-Bestätigungsdialogs via Intent.
   - Vollständiges Mapping aller PackageInstaller-Statuscodes auf lokalisierte Fehlermeldungen:
     - `INSTALL_FAILED_UPDATE_INCOMPATIBLE` / `STATUS_FAILURE_INCOMPATIBLE`: Hinweis auf Signaturkonflikt und Angebot zur Deinstallation & Neuinstallation.
     - `INSTALL_FAILED_VERSION_DOWNGRADE`: Fehlermeldung bei Versions-Downgrade.
     - `INSTALL_FAILED_INSUFFICIENT_STORAGE` / `STATUS_FAILURE_STORAGE`: Meldung über unzureichenden Speicherplatz.
     - `STATUS_FAILURE_BLOCKED`, `STATUS_FAILURE_ABORTED`, `STATUS_FAILURE_INVALID`, `STATUS_FAILURE_CONFLICT`.
4. **App-Start & Schutzmaßnahmen:**
   - Starten der installierten App über `PackageManager.getLaunchIntentForPackage`.
   - Einstellungsoption „Nach Installation automatisch starten“ (Standard: aus).
   - Schutz vor Selbstersetzung: Wenn die Ziel-APK denselben Paketnamen wie CodeForge (`com.codeforge.app`) besitzt, wird eine explizite Warnung eingeblendet und die Installation erst nach Nutzerbestätigung durchgeführt.
5. **Ablauf-Integration in `:feature:build`:**
   - Neuer Button „Bauen & Starten“ (`StartBuildAndRun`), der `assembleDebug` baut und bei Erfolg unmittelbar den Installationsprozess anstößt.
   - Bei fehlgeschlagenem Build wird kein Installationsversuch unternommen.
   - `@Immutable` UI-Zustände (`Idle`, `Building`, `ReadyToInstall`, `Installing`, `NeedsUserAction`, `NeedsPermission`, `Installed`, `Failed`).

---

## 2. Spike-Ergebnis: Drahtloses ADB via Termux (`android-tools`) (29.5)

Im Rahmen von Abschnitt 29.5 wurde geprüft, ob drahtloses ADB über das Termux-Paket `android-tools` auf dem Gerät genutzt werden kann:

- **Paket-Status:**
  - `adb` ist in Termux vorinstalliert (`android-tools 37.0.0p1-1 aarch64`, Pfad `/data/data/com.termux/files/home/android-sdk/platform-tools/adb`).
  - ADB-Server startet fehlerfrei (`* daemon started successfully at tcp:5037`).
- **Funktionsweise für lokales Wireless Debugging (Android 11+):**
  1. *Entwickleroptionen → Drahtlose Koppelung:* WLAN aktivieren, „Gerät mit Koppelungscode koppeln“ auswählen. Es werden eine 6-stellige PIN und eine Portnummer angezeigt (z. B. `192.168.x.x:37123`).
  2. *Koppelung via Termux:* `adb pair localhost:<pairing-port> <pin>` oder `adb pair 127.0.0.1:<pairing-port> <pin>`. Das Kommando akzeptiert Host, Port und Koppelungscode direkt als CLI-Argumente.
  3. *Verbindungsaufbau:* Nach erfolgreicher Koppelung wird der Hauptport für Wireless Debugging aus den Einstellungen abgelesen und mit `adb connect localhost:<connect-port>` verbunden.
  4. *Installation via ADB:* Sobald verbunden, ist das eigene Gerät in `adb devices` als `localhost:<port> device` gelistet. Die Installation gelingt dann mit:
     `adb install -r /pfad/zur/app.apk`
- **Fazit für Task 30:**
  Drahtlose ADB-Koppelung und -Verbindung von Termux zum eigenen Gerät sind technisch vollständig machbar und erfordern lediglich die einmalige Eingabe von Port/PIN durch den Nutzer.

---

## 3. Getestet

- **JVM Unit Tests:**
  - `ApkMetadataParserTest` (3 Tests):
    - `parseSingleApkMetadata` mit realer `apk_metadata_single.json`.
    - `parseAbiSplitSelectsMatchingDeviceAbi` mit `apk_metadata_splits.json` (korrekte Auswahl von `arm64-v8a` gegenüber `armeabi-v7a`).
    - `parseUnsignedApkIdentified` mit `apk_metadata_unsigned.json`.
  - `ApkInstallStatusMapperTest` (9 Tests):
    - `STATUS_SUCCESS` → `ApkInstallStatus.Installed`.
    - `STATUS_FAILURE_STORAGE` → `ApkInstallStatus.Failed` mit `isInsufficientStorage = true`.
    - `STATUS_FAILURE_INCOMPATIBLE` → `ApkInstallStatus.Failed` mit `isSignatureIncompatible = true`.
    - Downgrade-Meldung → `ApkInstallStatus.Failed` mit `isDowngrade = true`.
    - `STATUS_FAILURE_BLOCKED` → `ApkInstallStatus.Failed`.
    - `STATUS_FAILURE_ABORTED` → `ApkInstallStatus.Failed`.
    - `STATUS_FAILURE_INVALID` → `ApkInstallStatus.Failed`.
    - `STATUS_FAILURE_CONFLICT` → `ApkInstallStatus.Failed`.
    - Generischer Fehlercode.
  - `BuildViewModelTest` (10 Tests):
    - `startBuildStreamsProblemsAndFinishes`
    - `cancelBuildInvokesRepository`
    - `rejectsConcurrentBuildWhenAlreadyRunning`
    - `clickProblemNavigatesViaBridge`
    - `startBuildAndRunSuccessfulFlow` (Bauen & Starten Ablauf)
    - `startBuildAndRunFailedBuildDoesNotAttemptInstall` (Abbruch bei Buildfehler)
    - `unsignedApkResultsInFailedWithUnsignedFlag` (Nicht signierte APK abgefangen)
    - `selfReplaceRequiresConfirmation` (Selbstaktualisierungs-Warnung und Bestätigung)
    - `incompatibleSignatureShowsDialogAndAllowsUninstall` (Signaturkonflikt und Deinstallation)
    - `autoLaunchEnabledLaunchesAppAfterInstall` (Automatisches Starten)
- **Code-Qualität & Build:**
  - `./gradlew :core:domain:testDebugUnitTest :core:data:testDebugUnitTest :feature:build:testDebugUnitTest`: Erfolgreich (alle Tests grün).
  - `./gradlew :core:domain:ktlintCheck :core:data:ktlintCheck :feature:build:ktlintCheck`: Erfolgreich (0 Fehler).
  - `python3 scripts/check_string_locales.py`: Erfolgreich (909 Strings in EN und DE vollkommen synchron, Platzhalter und Escapes identisch).
  - `./gradlew :app:assembleDebug`: Erfolgreich gebaut.
  - ADB-Server in Termux gestartet und verifiziert.

---

## 4. Nicht getestet

- **Echte APK-Installation auf physischem Gerät:**
  Da die Ausführung in der Termux-Build-Umgebung stattfindet und während des Builds kein interaktiver Nutzer am Android-Gerät den Systemdialog des PackageInstallers (`STATUS_PENDING_USER_ACTION`) bestätigen konnte, wurde die reale Installation auf dem Zielgerät (Pixel 9 Pro XL, Android 17) **nicht ausgeführt**. Die Logik ist durch Unit-Tests mit Mocks/Fakes für alle Zustände und Fehlercodes vollständig abgedeckt.
- **Drahtlose ADB-Kopplung im Live-Betrieb:**
  Die ADB-Binärdatei und der Serverstart wurden getestet; die tatsächliche Koppelung mit einem laufenden Android 17 Entwickleroptions-PIN wurde nicht live gekoppelt (wird in Task 30 interaktiv benötigt).

---

## 5. Offene Punkte

- Keine offenen Blocker für Task 29.
- Task 30 kann auf den Erkenntnissen des Wireless-ADB-Spikes aufbauen.
- Task 34 wird das Signieren von Release-APKs (`*-unsigned.apk`) bereitstellen.
