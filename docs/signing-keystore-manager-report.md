# Task 34 – Signatur-Verwaltung (Keystore) und Release-Signierung

**Datum:** 20. Oktober 2026  
**Autor:** Antigravity  
**Status:** ✅ Erledigt  
**Module:** `:core:domain`, `:core:data`, `:libs:gradle-tooling-bridge`, `:feature:signing`, `:core:resources`, `:app`

---

## 1. Ziel und Zusammenfassung

Ziel von Task 34 ist eine sichere und intuitive Keystore- und Signatur-Verwaltung direkt auf dem Android-Gerät in CodeForge Mobile. Der Nutzer kann Keystores anlegen oder importieren, Zertifikatsdetails und Fingerabdrücke (SHA-1, SHA-256) für Google Play oder Firebase einsehen, Release-APKs ohne Passwörter in Projektdateien signieren und die Schlüssel per Knopfdruck für Gradle-Builds bereitstellen.

---

## 2. Sicherheitsprüfung (Verbindliche Vorgaben 34.1)

| Sicherheitsaspekt | Vorgabe | Umsetzung |
|---|---|---|
| **Speicherort Keystores** | Nur im privaten App-Speicher (`filesDir/keystores/`) | Keystores werden ausschließlich unter `context.filesDir/keystores/` abgelegt. |
| **Speicherort Passwörter** | Nur in `SecretStorage` (verschlüsselt) | `SecretStorageImpl` (`:core:data`) nutzt AES-256-GCM (`KeystoreSecretBox`) mit einem im Android Keystore hardware-geschützten Schlüssel (`codeforge_secrets_key`). |
| **Passwort-Weitergabe** | Niemals in Befehlszeilen-Argumenten (sichtbar via `ps`), nur `stdin` oder Umgebungsvariablen | `keytool` erhält `-storepass:env CF_STORE_PASS` und `-keypass:env CF_KEY_PASS`. `apksigner` erhält `--ks-pass env:CF_STORE_PASS` und `--key-pass env:CF_KEY_PASS`. Die Passwörter werden ausschließlich in die `environment()`-Map des Kindprozesses injected und erscheinen in keinem Argument. |
| **Export-Schutz** | Nur nach Bestätigung mit Verlustwarnung | Warnungsdialog weist explizit darauf hin: *„Verlust des Schlüssels bedeutet, dass Updates für bestehende Apps im Play Store nicht mehr möglich sind!“* |
| **Debug-Schlüssel-Warnung** | Warnung bei Release mit Debug-Schlüssel | `isDebugKeystore` bzw. `isDebugCertificate` prüft Zertifikats-DN und Alias (`CN=Android Debug`, `androiddebugkey`) und blendet gut sichtbare Warnungen ein. |
| **Gradle-Sicherheit** | Keine Passwörter in `build.gradle.kts` oder `gradle.properties` | Kopierfertiger `signingConfigs`-Block liest Umgebungsvariablen (`CODEFORGE_KEYSTORE_*`), die nur für den konkreten Build-Prozess temporär gesetzt werden. |

---

## 3. Architektur und Komponenten

### 3.1 Domain-Schicht (`:core:domain`)
* **Modelle (`SigningModels.kt`)**:
  * `KeyAlgorithm`: RSA 4096, RSA 2048, EC 256 (`secp256r1`), EC 384 (`secp384r1`).
  * `DistinguishedName`: Formatierung der X.500-Attribute (CN, OU, O, L, ST, C) mit Escaping.
  * `CreateKeystoreRequest`, `KeystoreInfo`, `KeystoreEntryInfo`, `KeystoreCertificateInfo`.
  * `SignApkRequest`, `ApkSignResult`, `ApkVerificationResult`.
  * `PasswordStrengthEvaluator`: Pure Kotlin-Stärkeberechnung (Länge, Groß-/Kleinbuchstaben, Zahlen, Sonderzeichen).
  * `GradleSigningSnippet`: Vorlage für `signingConfigs` in `build.gradle.kts`.
* **Parser**:
  * `KeytoolOutputParser`: Reiner Kotlin-Parser für `keytool -list -v` mit Unterstützung für englische und deutsche Lokalisierung, Multi-Alias-Keystores und Zertifikatsketten.
  * `ApksignerOutputParser`: Reiner Kotlin-Parser für `apksigner verify --verbose --print-certs` (V1–V4 Schemas, Zertifikats-DNs, SHA-1/SHA-256-Fingerabdrücke).
* **Vertrag (`SigningRepository.kt`)**:
  * Vollständiges CRUD für Keystores, Import, Export, Signieren, Verifizieren und Gradle-Build-Konfiguration.
* **Geheimnisspeicher (`SecretStorage.kt`)**:
  * Allgemeines Domain-Interface für verschlüsselte Schlüssel-Wert-Ablage.

### 3.2 Data & Tooling (`:core:data`, `:libs:gradle-tooling-bridge`)
* `SecretStorageImpl`: AES-256-GCM-Verschlüsselung mit Android Keystore.
* `ProcessGradleBuildRepository`: Unterstützt `setExtraEnvironment(env)` und übergibt die `CODEFORGE_KEYSTORE_*`-Variablen an `./gradlew`-Prozesse.

### 3.3 Feature-Modul (`:feature:signing`)
* `SigningRepositoryImpl`:
  * Erkennt `keytool` in Termux (`$JAVA_HOME/bin/keytool`, `$PREFIX/lib/jvm/java-*-openjdk/bin/keytool`, `$PREFIX/bin/keytool` oder PATH).
  * Erkennt `apksigner` in Android SDK (`$ANDROID_SDK_ROOT/build-tools/*/apksigner`, `$PREFIX/bin/apksigner` oder PATH).
  * Führt Befehle sicher ohne Argument-Leaks aus.
* `SigningViewModel`:
  * MVI-Zustandsautomat (`SigningUiState`, `SigningUiIntent`).
  * Live-Berechnung der Passwortstärke und Validierung.
* `SigningScreen`:
  * Material 3 UI mit Reiter-Struktur:
    1. **Keystores**: Übersicht, Neuer Keystore, Importieren, Details (Fingerabdrücke kopierbar), Exportieren, Löschen, Für Build aktivieren.
    2. **APK signieren**: Auswahl von Quell-APK und Schlüsselspeicher, Schemas V1–V4 wählbar, Verifikationsanzeige nach erfolgreicher Signierung.
    3. **Gradle-Konfiguration**: Copy-ready `signingConfigs`-Code für `build.gradle.kts`, Anzeige des aktuell für Builds aktiven Schlüssels.

---

## 4. Getestet

1. **Sicherheitsprüfung (`SigningProcessSecurityTest`)**:
   - `createKeystoreNeverExposesPasswordsInArguments`: Verifiziert, dass Store- und Key-Passwörter in keinem Argument von `keytool` auftauchen und stattdessen über `CF_STORE_PASS` / `CF_KEY_PASS` in der Prozessumgebung übergeben werden.
   - `importKeystoreNeverExposesPasswordsInArguments`: Verifiziert, dass Passwörter beim Import-Check nicht in den Argumenten stehen.
   - `signApkNeverExposesPasswordsInArguments`: Verifiziert, dass Passwörter bei `apksigner sign` nur über `env:CF_STORE_PASS` / `env:CF_KEY_PASS` übergeben werden.
2. **Repository-Funktionalität (`SigningRepositoryImplTest`)**:
   - Keystore-Erstellung und Inspektion (`createAndInspectKeystore`).
   - Export und sicheres Löschen (`exportAndDeleteKeystore`).
   - APK-Signierung und Verifikation (`signApkAndVerify`).
   - Gradle-Build-Konfiguration und Bereinigung (`configureBuildSigningSetsEnvironmentOnGradle`).
3. **Zustandsautomat & ViewModel (`SigningViewModelTest`)**:
   - Initialer Ladevorgang und Tab-Wechsel.
   - Passwortstärke-Berechnung im Erstellungsformular.
   - Keystore-Erstellung und automatische Aktualisierung der Liste.
   - APK-Signierung mit Ergebnisübernahme.
   - Aktivierung und Deaktivierung der Gradle-Build-Signierung.
4. **Ausgabe-Parser (`KeytoolOutputParserTest`, `ApksignerOutputParserTest`, `SigningModelsTest`)**:
   - Englische und deutsche `keytool -list -v` Ausgaben.
   - Mehrere Aliasse und Zertifikatsketten.
   - `apksigner verify --verbose --print-certs` mit Debug- und Produktionsschlüsseln, V1- bis V4-Schemas.
   - X.500 DistinguishedName-Formatierung und Standard-Pfadauflösung für `*-signed.apk`.
5. **Verschlüsselung (`SecretStorageImplTest`)**:
   - Sichere Ablage, Abruf und Löschen von Passwörtern.
6. **Gradle-Umgebung (`ProcessGradleBuildRepositoryTest`)**:
   - Weitergabe von `extraEnvironment` an den child process von `./gradlew`.
7. **Bilinguale Strings (`check_string_locales.py`)**:
   - 1085 Strings in `values/strings.xml` und `values-de/strings.xml` synchronisiert, 0 Fehler.
8. **Echte CLI-Werkzeuge auf dem Testgerät**:
   - `keytool`: `/data/data/com.termux/files/usr/lib/jvm/java-17-openjdk/bin/keytool` (OpenJDK 17) vorhanden und funktionsfähig (`-genkeypair`, `-list -v`, `-storepass:env`).
   - `apksigner`: `/data/data/com.termux/files/home/android-sdk/build-tools/37.0.0/apksigner` (Build-Tools 37.0.0) vorhanden und verifiziert an `app-debug.apk`.

---

## 5. Nicht getestet

- Manuelle Installation einer mit einem neu erzeugten Release-Schlüssel signierten APK auf einem physischen Google Pixel 9 Pro XL (Android 17) via Google Play Store oder Sideload.

---

## 6. Offene Punkte

- Keine. Task 34 ist vollständig umgesetzt und alle Kriterien sind erfüllt.
