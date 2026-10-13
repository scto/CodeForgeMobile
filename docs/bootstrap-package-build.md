# Verifikationsbericht: Task 04 – Terminal Bootstrap Package Build

Datum: 2026-10-13  
Umgebung: Termux Linux aarch64, Java 17, Android SDK Platform 36, NDK 29.0.14206865, Gradle 8.14.3

---

## 1. Übersicht & Zielsetzung

Ziel von Task 04 (`agy-tasks/04-termux-bootstrap-package-build.md`) war die Einbindung des korrigierten, verifizierten Releases `bootstrap-2026.10.11` aus dem Repository `scto/terminal-packages-codeforge`:
- Ablösung des defekten Alt-Releases `2026.10.08`.
- Verifikation des Präfix `/data/data/com.codeforge.app/files/usr` (ADR-5).
- Anpassung der Build-Logik (`TerminalBootstrapPackagesPlugin`) und Konfiguration (`gradle.properties`) auf `aarch64` und `arm` (Fokus auf Pixel 9 Pro XL / ARM-Zielgeräte).
- Ausführung von `:libs:termux-app:embedTerminalBootstrap` und Verifikation der nativen Einbettung über `termux-bootstrap-zip.S` in `libtermux-bootstrap.so`.
- Erfolgreicher Build von `:app:assembleDebug`.

---

## 2. Durchgeführte Verifikationsschritte

### 1. Prüfsummen- und Release-Asset-Abgleich
Die Release-Assets von `bootstrap-2026.10.11` wurden live gegen GitHub abgeglichen:
* **aarch64:** `05bb4a96a74e955333cc96139f6d05df394bfa8f2b18d29a29bb0f6b4b914279`
* **arm:** `2ba9e7e5e0d129776a80e70da115fc6bf258105a008be07fc5087eda30111ad0`

Beide Prüfsummen stimmen exakt mit den heruntergeladenen ZIP-Dateien in `libs/termux-app/build/bootstrap-packages/` überein.

### 2. Prefix- und Repo-Integritätsprüfung (ADR-5)
Die ZIP-Archive wurden auf Konsistenz geprüft:
* **Präfix in ELF-Binaries:** `/data/data/com.codeforge.app/files/usr` (z. B. in `bin/bash`, `bin/sh`, Libraries). Keine verbliebenen `com.itsaky.androidide` oder `com.termux`-Pfade in den Binaries.
* **APT-Paketquellen:** `etc/apt/sources.list` verweist auf:
  ```
  deb https://scto.github.io/terminal-packages-codeforge stable main
  ```
* **Symlinks:** `SYMLINKS.txt` ist konsistent und vollständig formatiert.

### 3. Anpassung der Build-Logik
* In [`TerminalBootstrapPackagesPlugin.kt`](file:///data/data/com.termux/files/home/CodeForgeMobile/build-logic/convention/src/main/kotlin/TerminalBootstrapPackagesPlugin.kt):
  * `BOOTSTRAP_PACKAGES` auf `aarch64` und `arm` beschränkt.
  * Standard-Prüfsummen auf Release `2026.10.11` gesetzt.
  * Assembly-Template für `termux-bootstrap-zip.S`: `#elif defined __x86_64__`-Zweig entfernt und `#else` mit `.byte 0` als Fallback ausgestattet.
* In [`gradle.properties`](file:///data/data/com.termux/files/home/CodeForgeMobile/gradle.properties):
  * `codeforgeBootstrapVersion=2026.10.11`
  * `codeforgeBootstrapUrlTemplate=https://github.com/scto/terminal-packages-codeforge/releases/download/bootstrap-%1$s/bootstrap-%2$s.zip`
  * SHA-256 für `aarch64` und `arm` eingetragen.
  * Keine alten `x86_64`-Einträge oder Verweise auf `2026.10.08` mehr vorhanden.

### 4. Einbettung & Assembly-Generierung
* Task `:libs:termux-app:embedTerminalBootstrap` generierte [`libs/termux-app/src/main/cpp/termux-bootstrap-zip.S`](file:///data/data/com.termux/files/home/CodeForgeMobile/libs/termux-app/src/main/cpp/termux-bootstrap-zip.S):
  ```assembly
      .global blob
      .global blob_size
      .section .rodata
  blob:
  #if defined __aarch64__
      .incbin "/data/data/com.termux/files/home/CodeForgeMobile/libs/termux-app/build/bootstrap-packages/bootstrap-aarch64.zip"
  #elif defined __arm__
      .incbin "/data/data/com.termux/files/home/CodeForgeMobile/libs/termux-app/build/bootstrap-packages/bootstrap-arm.zip"
  #else
      .byte 0
  #endif
  1:
  blob_size:
      .int 1b - blob
  ```

### 5. APK-Build (`:app:assembleDebug`)
* `./gradlew :app:assembleDebug` erfolgreich ausgeführt.
* APK-Größe: **318 MB** (`app/build/outputs/apk/debug/app-debug.apk`).
* Enthaltene native Bibliotheken:
  * `lib/arm64-v8a/libtermux-bootstrap.so`: **128.106.072 Bytes** (vollständiges aarch64-Bootstrap-Zip)
  * `lib/armeabi-v7a/libtermux-bootstrap.so`: **120.279.468 Bytes** (vollständiges arm-Bootstrap-Zip)
  * `lib/x86/libtermux-bootstrap.so`: **21.440 Bytes** (leerer Stub)
  * `lib/x86_64/libtermux-bootstrap.so`: **23.024 Bytes** (leerer Stub)

---

## 3. Akzeptanzkriterien-Abgleich

| Kriterium | Status | Nachweis |
|---|---|---|
| Kein Checksum-Mismatch | ✅ Erfüllt | Hashes stimmen mit Release-Assets überein |
| `:app:assembleDebug` baut mit eingebettetem Bootstrap | ✅ Erfüllt | `libtermux-bootstrap.so` enthält Blob (128 MB aarch64 / 120 MB arm) |
| Keine Werte aus defektem Release `2026.10.08` | ✅ Erfüllt | `gradle.properties` und Plugin bereinigt |
| Präfix `/data/data/com.codeforge.app/files/usr` | ✅ Erfüllt | In `bin/bash`, `etc/apt/sources.list`, etc. verifiziert |
