# CodeForge Mobile – APK-Dokumentation

> Anwender: [../user/Anwenderhandbuch.md](../user/Anwenderhandbuch.md) · Entwickler: [../dev/DeveloperGuide.md](../dev/DeveloperGuide.md)

## 1. Eckdaten

| Eigenschaft | Wert |
|---|---|
| Paket (`applicationId`) | `com.codeforge.app` |
| Version | `3.0.2` (`versionCode` 30002; Schema `MAJOR*10000 + MINOR*100 + PATCH`) |
| `minSdk` / `targetSdk` / `compileSdk` | 26 / 28 / 36 |
| Quelle der Version | `app/build.gradle.kts` |
| Lizenz | GPLv3 |

`targetSdk 28` ist Absicht: Nur damit darf die App heruntergeladene Termux-Binaries direkt ausführen (kein W^X-Verbot ab Android 10 für `targetSdk ≥ 29`).

## 2. Build-Varianten

| Variante | Befehl | Signatur | Besonderheiten |
|---|---|---|---|
| **Debug** | `./gradlew :app:assembleDebug` | Android-Debug-Schlüssel | debuggable, kein R8 |
| **Release** | `./gradlew :app:assembleRelease` | Release-Schlüssel (siehe 3.), sonst Debug-Schlüssel | R8 (Shrinking/Obfuskation) + Ressourcen-Shrinking, native Symbole (`SYMBOL_TABLE`) |

Ausgabe: `app/build/outputs/apk/<variante>/`. Beim Bau lädt Gradle den Terminal-Bootstrap aus dem Release von `scto/terminal-packages-codeforge` und prüft die SHA-256 aus `gradle.properties`.

### CI

- `build-debug-apk.yml`: Debug-APK als Workflow-Artefakt (bei Push auf `main`, manuell).
- `release-debug-apk.yml`: Debug-APK als **getaggtes Pre-release** (Tag `debug-*` pushen oder manuell starten), inkl. `SHA256SUMS.txt`.

## 3. Signierung

Release-Schlüssel kommen aus `keystore.properties` im Projektstamm (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`) oder den Umgebungsvariablen `CODEFORGE_STORE_FILE`, `CODEFORGE_STORE_PASSWORD`, `CODEFORGE_KEY_ALIAS`, `CODEFORGE_KEY_PASSWORD`. Fehlt beides, wird mit dem Debug-Schlüssel signiert (installierbar, **nicht für den Store**). Schlüssel und Passwörter nie einchecken. Wechselt die Signatur, muss die App vor der Neuinstallation deinstalliert werden (die Build-Ansicht bietet „Deinstallieren und neu installieren“).

## 4. Berechtigungen

| Berechtigung | Zweck |
|---|---|
| `INTERNET`, `ACCESS_NETWORK_STATE` | Pakete, Git, KI-Anfragen, Store |
| `READ/WRITE_EXTERNAL_STORAGE` | Projekte unter `/storage/emulated/0/CodeForgeMobileProjects/` |
| `FOREGROUND_SERVICE` (+ `_SPECIAL_USE`, `_DATA_SYNC`), `POST_NOTIFICATIONS`, `WAKE_LOCK` | Terminal-/Build-Dienst im Hintergrund |
| `REQUEST_INSTALL_PACKAGES`, `REQUEST_DELETE_PACKAGES` | gebaute APKs installieren/deinstallieren |
| `READ_LOGS` | Logcat anderer Apps (nur nach `adb pm grant`) |
| `RECEIVE_BOOT_COMPLETED`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, `VIBRATE` | Termux-Komponenten |
| `SYSTEM_ALERT_WINDOW`, `WRITE_SECURE_SETTINGS`, `DUMP` | optionale Termux-/System-Funktionen; werden nur bei Nutzung vom System angefragt |

Die Liste folgt den Manifesten von `:app` und den Bibliotheksmodulen (zusammengeführtes Manifest: `app/build/intermediates/merged_manifests/`).

## 5. Installation

1. APK aus dem Release (oder `app/build/outputs/apk/…`) laden, `SHA256SUMS.txt` prüfen: `sha256sum -c SHA256SUMS.txt`.
2. Auf dem Gerät „Installation aus unbekannten Quellen“ für den Browser/Dateimanager erlauben.
3. Öffnen, Onboarding durchlaufen (Bootstrap und `codeforge-env` laden zusätzliche Daten nach; WLAN empfohlen).

Per ADB: `adb install -r CodeForgeMobile-<tag>.apk`.

## 6. Größe und Inhalt

Die APK enthält die App (Kotlin/Compose), native Bibliotheken (Termux-Emulator, Tree-sitter) und den Terminal-Bootstrap. Die genaue Größe hängt von Variante und ABI ab und ist im Release angegeben. Inhalt prüfen: *APK-Analyzer* der App oder `unzip -l` / `apkanalyzer`.

## 7. Veröffentlichungs-Prozess

1. Version in `app/build.gradle.kts` und `CHANGELOG.md` anheben.
2. Tests: `./gradlew testDebugUnitTest`.
3. Tag pushen (`debug-3.0.2-1`) → Release-Workflow baut und veröffentlicht.
4. Signierter Release-Build: Schlüssel als CI-Secrets hinterlegen (siehe [Release-Signatur](../dev/Signing.md)), Tag `vX.Y.Z` pushen → Workflow `release-release-apk.yml` baut `assembleRelease`, prüft die Signatur und veröffentlicht den Release.

Weitere Berichte: [../apk-analyzer-report.md](../apk-analyzer-report.md), [../install-and-run-apk-report.md](../install-and-run-apk-report.md).
