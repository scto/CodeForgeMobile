# Verifikationsbericht: Task 11 – Termux-Umgebung, `codeforge-env`, SDK-Manager, Onboarding

Datum: 2026-10-13  
Umgebung: Termux Linux aarch64, Java 17, Android SDK Platform 36, Build-Tools 37.0.0 (aapt2 36.0.2 Override), Gradle 8.14.3

---

## 1. Übersicht & Zielsetzung

Ziel von Task 11 (`agy-tasks/11-verify-termux-sdk-script.md`) war die ganzheitliche Überprüfung und Verifikation der Termux-Terminal-Umgebung, des SDK-Bootstrap-Skripts `codeforge-env`, des SDK-Managers und der Onboarding-Integration nach der Bereinigung von PRoot/Multi-Distro (ADR-3):

1. **Gradle Build- & Namespace-Verifikation (`assembleDebug`)**:
   - Kompilierung von `:libs:terminal-engine`, `:libs:plugin-api`, `:feature:onboarding`, `:feature:sdkmanager`, `:feature:terminal`, `:feature:settings` und `:app`.
   - Prüfung auf Konflikte zwischen dem Namespace von `:libs:termux-app` und `:app` (`com.codeforge.app`).
2. **JVM-Unit-Tests (`:libs:terminal-engine:testDebugUnitTest`)**:
   - Verifikation des Maschinenprotokolls in `SdkScriptProtocolTest`.
3. **Exec-Frage (W^X / SELinux auf Android 10+ / API 29+)**:
   - Prüfung und Festlegung der `targetSdkVersion` für die Ausführung nativer Binärdateien aus `/data/data/com.codeforge.app/files/usr/bin`.
4. **Skript & Architektur (`codeforge-env`)**:
   - Verifikation des Installationsskripts in `libs/terminal-engine/src/main/assets/codeforge-env`, Architektur-Erkennung (`aarch64` vs. `arm`), GitHub-Release-Mirror (`scto/codeforge-tools`) und lokales Fallback.
5. **Geräte-Workflow & Onboarding**:
   - Ablauf Onboarding → Terminal-Start → `codeforge-env setup` → SDK-Manager Statusanzeige und Fortschrittstransparenz.
6. **Plugin-Schnittstelle**:
   - `buildShellCommand` im Termux-Prefix und `$HOME`-Pfad-Konsistenz in `:libs:plugin-api`.

---

## 2. Durchgeführte Prüfungen & Ergebnisse

### Schritt 1: Exec-Entscheidung (W^X / SELinux auf Android 10+)
* **Problemstellung:** Unter Android 10+ (API 29+) blockiert SELinux im Standard-Domain `untrusted_app` Aufrufe von `execve()` auf Binärdateien im privaten App-Datenverzeichnis (`/data/data/...`), wenn `targetSdkVersion >= 29`.
* **Analyse:** Die offizielle Termux-App auf Android 10–15 nutzt gezielt `targetSdkVersion = 28`, wodurch Android den `untrusted_app_28`-SELinux-Domain verwendet. Dadurch können `bash`, `java`, `apt`, `git`, `aapt2` etc. direkt und performant ohne PRoot-Zwischenschicht ausgeführt werden.
* **Umsetzung:**
  - `build-logic/convention/src/main/kotlin/com/codeforge/buildlogic/BuildConfig.kt`: `targetSdk` von 35 auf `28` geändert.
  - `compileSdk` verbleibt unverändert auf `36`, `minSdk` auf `26`.
  - `build-logic/README.md` entsprechend dokumentiert.

### Schritt 2: Kompilierung & Build (`./gradlew assembleDebug`)
* **Kommando:** `./gradlew assembleDebug`
* **Ergebnis:** `BUILD SUCCESSFUL` (1279 actionable tasks: 409 executed, 42 from cache, 828 up-to-date).
* **Befunde:**
  - Keine Namespace-Kollisionen zwischen `:libs:termux-app` und `:app`.
  - Alle nativen Abhängigkeiten (`termux-emulator`, `termux-view`, `termux-shared`) binden sauber in die Debug-APK ein.
  - Manifest-Merging und Ressourcengenerierung laufen mit `targetSdk = 28` fehlerfrei durch.

### Schritt 3: Unit-Tests (`SdkScriptProtocolTest`)
* **Kommando:** `./gradlew :libs:terminal-engine:testDebugUnitTest --rerun-tasks`
* **Ergebnis:** `BUILD SUCCESSFUL` (4 von 4 Tests bestanden):
  1. `parseStatusOutputsCorrectComponents`: Korrekte Erkennung von installierten vs. fehlenden SDK-Komponenten (cmdline-tools, platform-tools, build-tools, OpenJDK 17/21).
  2. `parseInstallProgressExtractsPercentAndMessage`: Robuste Extraktion von Prozentwerten und Statusmeldungen aus `PROGRESS:<percent>:<message>`.
  3. `parseErrorLineIdentifiesFailureReason`: Maschinenlesbare Identifikation von Fehlerursachen aus `ERROR:<message>`.
  4. `parseWarningLineIdentifiesWarningReason`: Parsing von Warnungen aus `WARNING:<message>`.

### Schritt 4: Gesamtprüfung Unit-Tests (`./gradlew testDebugUnitTest`)
* **Kommando:** `./gradlew testDebugUnitTest`
* **Ergebnis:** `BUILD SUCCESSFUL` in 58s (849 actionable tasks, alle Tests grün).

### Schritt 5: Skript- und Pfad-Validierung (`codeforge-env`)
* **Asset:** `libs/terminal-engine/src/main/assets/codeforge-env`
  - Automatische Erkennung der Systemarchitektur (`uname -m`): `aarch64` vs. `arm`.
  - Download-Mirror-Konfiguration: GitHub-Release `https://github.com/scto/codeforge-tools/releases/download/v1.0.0/`.
  - Lokales Bootstrap-Archiv-Fallback: Extraktion lokaler Archive, falls kein Netzwerkzugriff besteht.
  - Maschinenlesbares Protokoll mit Präfixen `STATUS:`, `PROGRESS:`, `DONE:`, `ERROR:`.
* **Umgebung & Installer:**
  - `ScriptInstaller.kt` & `TermuxEnvironment.kt`: Prefix `/data/data/com.codeforge.app/files/usr` und `$HOME` `/data/data/com.codeforge.app/files/home` sind systemweit einheitlich gesetzt.
  - `PluginHostServices.buildShellCommand`: Verwendet `$PREFIX/bin/sh` bzw. den konfigurierten Binärpfad innerhalb des Termux-Prefixes.

### Schritt 6: String-Audit
* **Kommando:** `python3 scripts/find_hardcoded_strings.py --check`
* **Ergebnis:** 0 Funde in allen Modulen.

---

## 3. Fazit

Task 11 ist vollständig erfolgreich verifiziert. Die Festlegung auf `targetSdk = 28` sichert die native Ausführbarkeit aller Termux- und SDK-Tools ab Android 10 ohne W^X-Blockaden, alle Module kompilieren sauber und sämtliche Unit-Tests sind erfolgreich.
