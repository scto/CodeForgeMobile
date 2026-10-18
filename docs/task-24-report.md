# Task 24 Report – Onboarding-Farben, Rechte, Launcher-Icon, volle Termux-UI, sichtbare Setup-Fehler & Bootstrap Second-Stage

**Datum:** 2026-10-18  
**Auftrag:** [`agy-tasks/24-onboarding-permissions-icon-termux-ui.md`](file:///data/data/com.termux/files/home/CodeForgeMobile/agy-tasks/24-onboarding-permissions-icon-termux-ui.md)

---

## 1. Übersicht der umgesetzten Punkte (24.1 – 24.7)

### 24.1 Onboarding: Schwarzer Text auf dunklem Hintergrund & Screen-Audit
- **Ursache:** [`OnboardingRoute.kt`](file:///data/data/com.termux/files/home/CodeForgeMobile/feature/onboarding/src/main/kotlin/com/codeforge/feature/onboarding/OnboardingRoute.kt) renderte die drei Onboarding-Schritte (`IntroPagerScreen`, `PermissionScreen`, `SetupScreen`) direkt in einer `Box` ohne `Surface` oder `Scaffold`. Dadurch war `LocalContentColor` nicht auf `MaterialTheme.colorScheme.onBackground` gesetzt und fiel im Dark-Mode auf den Compose-Default (`Color.Black`) zurück.
- **Umsetzung:**
  1. In [`OnboardingRoute.kt`](file:///data/data/com.termux/files/home/CodeForgeMobile/feature/onboarding/src/main/kotlin/com/codeforge/feature/onboarding/OnboardingRoute.kt#L171-L178) die äußere `Box` in `Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground)` eingebettet (`safeDrawingPadding()` verbleibt auf der inneren `Box`).
  2. In [`IntroPagerScreen.kt`](file:///data/data/com.termux/files/home/CodeForgeMobile/feature/onboarding/src/main/kotlin/com/codeforge/feature/onboarding/IntroPagerScreen.kt#L92-L97) das Seiten-Icon auf `Modifier.size(64.dp)` vergrößert und `tint = MaterialTheme.colorScheme.primary` gesetzt.
  3. **Audit aller 17 Navigationsrouten** (`grep -rn "setContent\|composable(" app feature`):
     - 15 Routen nutzen ein eigenes `Scaffold` (setzt automatisch `background` + `contentColorFor(background)`).
     - `Routes.ONBOARDING` (`OnboardingRoute.kt`): durch `Surface` behoben.
     - `Routes.CLONE_PROJECT` ([`GitCloneScreen.kt`](file:///data/data/com.termux/files/home/CodeForgeMobile/feature/git/src/main/kotlin/com/codeforge/feature/git/ui/GitCloneScreen.kt#L35-L42)): nutzte eine `Column` ohne `Surface`/`Scaffold` → ebenfalls in `Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground)` eingebettet.
  4. **Compose-`@Preview` (Hell & Dunkel):** Für [`IntroPagerScreen.kt`](file:///data/data/com.termux/files/home/CodeForgeMobile/feature/onboarding/src/main/kotlin/com/codeforge/feature/onboarding/IntroPagerScreen.kt), [`PermissionScreen.kt`](file:///data/data/com.termux/files/home/CodeForgeMobile/feature/onboarding/src/main/kotlin/com/codeforge/feature/onboarding/PermissionScreen.kt) und [`SetupScreen.kt`](file:///data/data/com.termux/files/home/CodeForgeMobile/feature/onboarding/src/main/kotlin/com/codeforge/feature/onboarding/SetupScreen.kt) jeweils Light- und Dark-`@Preview`-Funktionen ergänzt und die String-Auflösung in den Composables auf `stringRes(...)` umgestellt, damit Previews ohne Hilt-/App-Initialisierung rendern.

---

### 24.2 Rechte im Onboarding
- **Umsetzung:**
  1. **Manifest ([`app/src/main/AndroidManifest.xml`](file:///data/data/com.termux/files/home/CodeForgeMobile/app/src/main/AndroidManifest.xml)):**
     - `android.permission.MANAGE_EXTERNAL_STORAGE` (`tools:ignore="ScopedStorage"`) ergänzt.
     - `android.permission.READ_EXTERNAL_STORAGE` und `WRITE_EXTERNAL_STORAGE` auf `android:maxSdkVersion="29"` gesetzt.
     - `android.permission.REQUEST_INSTALL_PACKAGES` und `android.permission.REQUEST_DELETE_PACKAGES` ergänzt.
  2. **Statusprüfung & `ON_RESUME`-Refresh ([`OnboardingRoute.kt`](file:///data/data/com.termux/files/home/CodeForgeMobile/feature/onboarding/src/main/kotlin/com/codeforge/feature/onboarding/OnboardingRoute.kt#L49-L68)):**
     - Ab API 30 (`Build.VERSION_CODES.R`): `Environment.isExternalStorageManager()`, Anfrage per `Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName"))` (mit Fallback auf `ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION`).
     - API 26–29: `READ_EXTERNAL_STORAGE` + `WRITE_EXTERNAL_STORAGE` per `RequestMultiplePermissions`.
     - Apps installieren: `packageManager.canRequestPackageInstalls()`, Anfrage per `Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName"))`.
     - `LifecycleEventObserver` prüft bei jedem `Lifecycle.Event.ON_RESUME` alle Berechtigungen neu (`OnboardingUiEvent.PermissionsRefreshed`), sodass die Rückkehr aus den Systemeinstellungen sofort den aktuellen Status widerspiegelt.
  3. **UI & State ([`OnboardingContract.kt`](file:///data/data/com.termux/files/home/CodeForgeMobile/feature/onboarding/src/main/kotlin/com/codeforge/feature/onboarding/OnboardingContract.kt), [`PermissionScreen.kt`](file:///data/data/com.termux/files/home/CodeForgeMobile/feature/onboarding/src/main/kotlin/com/codeforge/feature/onboarding/PermissionScreen.kt)):**
     - `OnboardingUiState` um `installPackagesGranted: Boolean = false` und `deletePackagesInfo: Boolean = true` erweitert.
     - „Apps löschen“ (`REQUEST_DELETE_PACKAGES`) wird als nicht-klickbare Info-Karte mit Häkchen („Wird automatisch bei der App-Installation erteilt“) dargestellt.
     - Ganze `ElevatedCard` ist klickbar, solange die Berechtigung noch nicht erteilt ist; bereits erteilte Berechtigungen lösen keinen erneuten Intent/Dialog aus.
     - Der „Weiter“-Button ist aktiv, sobald `storageGranted == true` (Installieren und Benachrichtigungen sind optional, aber sichtbar).
  4. **Technische Begründung: Warum `MANAGE_EXTERNAL_STORAGE` trotz `targetSdk = 28` auf Android 11+ benötigt wird:**
     - Zwar gewährt Android 10 (API 29) einer App mit `targetSdk <= 28` automatisch Legacy External Storage (`requestLegacyExternalStorage` ist für `targetSdk < 29` implizit aktiv).
     - **Ab Android 11 (API 30) greift Scoped Storage jedoch unabhängig vom `targetSdkVersion` für direkte Dateisystem-Zugriffe (`java.io.File` / POSIX `open()`, `readdir()`) auf fremde Ordner in `/storage/emulated/0/`**, sofern die App nicht `MANAGE_EXTERNAL_STORAGE` („Zugriff auf alle Dateien“) besitzt: Ohne `MANAGE_EXTERNAL_STORAGE` werden `READ/WRITE_EXTERNAL_STORAGE` auf Android 11+ durch das FUSE-Dateisystem auf Medien-Sammlungen beschränkt; Quellcode-Dateien (`.kt`, `.java`, `.gradle.kts`, `.xml`, `.git/*`) in `/storage/emulated/0/Projects/...` oder `~/storage/shared/...` sind für `java.io.File`, JGit und die Termux-Shell sonst unsichtbar bzw. liefern `EACCES`.
     - Zudem ignoriert Android 13+ (API 33+) `READ_EXTERNAL_STORAGE` bei `maxSdkVersion="32"` komplett und zeigt überhaupt keinen Laufzeitdialog mehr an (genau das im Gerätetest beobachtete Verhalten). Mit `MANAGE_EXTERNAL_STORAGE` + `ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION` ab API 30 funktioniert der Vollzugriff auf Android 11–15 zuverlässig.

---

### 24.3 Launcher-Icon & Vektor-Logo wiederherstellen
- **Umsetzung:**
  1. Original-Launcher-Drawables aus Commit `de2caf0` wiederhergestellt:
     - [`app/src/main/res/drawable/ic_launcher_background.xml`](file:///data/data/com.termux/files/home/CodeForgeMobile/app/src/main/res/drawable/ic_launcher_background.xml) (Deep Indigo `#111318` mit feinem 12dp-Blueprint-Grid)
     - [`app/src/main/res/drawable/ic_launcher_foreground.xml`](file:///data/data/com.termux/files/home/CodeForgeMobile/app/src/main/res/drawable/ic_launcher_foreground.xml) (Squircle-Badge `#1A1D24`, Cyan-Akzentring, Code-Klammern `<` + Terminal-Prompt `>_` in `#00E5FF`, `@author Thomas Schmid`)
     - [`app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`](file:///data/data/com.termux/files/home/CodeForgeMobile/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml) und [`ic_launcher_round.xml`](file:///data/data/com.termux/files/home/CodeForgeMobile/app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml) (`monochrome` verweist weiterhin auf `@drawable/ic_launcher_monochrome`).
  2. Nicht mehr referenzierte Farben `codeforge_icon_background`, `codeforge_icon_foreground`, `codeforge_icon_accent` aus [`app/src/main/res/values/colors.xml`](file:///data/data/com.termux/files/home/CodeForgeMobile/app/src/main/res/values/colors.xml) entfernt (`grep -rn "codeforge_icon_"` = 0 Treffer).
  3. Großes Vektor-Icon `icon.xml` aus `de2caf0` in [`core/designsystem/src/main/res/drawable/icon.xml`](file:///data/data/com.termux/files/home/CodeForgeMobile/core/designsystem/src/main/res/drawable/icon.xml) und [`feature/onboarding/src/main/res/drawable/icon.xml`](file:///data/data/com.termux/files/home/CodeForgeMobile/feature/onboarding/src/main/res/drawable/icon.xml) wiederhergestellt und als Marken-Logo in [`IntroPagerScreen.kt`](file:///data/data/com.termux/files/home/CodeForgeMobile/feature/onboarding/src/main/kotlin/com/codeforge/feature/onboarding/IntroPagerScreen.kt#L68-L73) (`88.dp`) sowie [`WelcomeScreen.kt`](file:///data/data/com.termux/files/home/CodeForgeMobile/feature/welcome/src/main/kotlin/com/codeforge/feature/welcome/ui/WelcomeScreen.kt#L179-L185) (`48.dp`) eingebunden.

---

### 24.4 Volle Termux-UI (Task 06)
- **Umsetzung:**
  1. **Abhängigkeit & Manifest ([`app/build.gradle.kts`](file:///data/data/com.termux/files/home/CodeForgeMobile/app/build.gradle.kts), [`libs/termux-app/src/main/AndroidManifest.xml`](file:///data/data/com.termux/files/home/CodeForgeMobile/libs/termux-app/src/main/AndroidManifest.xml)):**
     - `implementation(project(":libs:termux-app"))` und `manifestPlaceholders["termuxAppName"] = "CodeForge"` / `manifestPlaceholders["termuxPackageName"] = "com.codeforge.app"` in `:app` ergänzt.
     - `com.codeforge.app.activities.HelpActivity` im Manifest von `:libs:termux-app` registriert; `READ/WRITE_EXTERNAL_STORAGE` auf `maxSdkVersion="29"` harmonisiert.
     - Gemergtes Manifest (`app/build/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml`) geprüft: keine doppelten Komponenten, `android:name=".CodeForgeApplication"` bleibt erhalten.
  2. **Initialisierung ([`TermuxApplication.java`](file:///data/data/com.termux/files/home/CodeForgeMobile/libs/termux-app/src/main/java/com/codeforge/app/TermuxApplication.java), [`CodeForgeApplication.kt`](file:///data/data/com.termux/files/home/CodeForgeMobile/app/src/main/kotlin/com/codeforge/app/CodeForgeApplication.kt)):**
     - Statische Methode `TermuxApplication.initTermuxApplication(Context)` extrahiert und in `CodeForgeApplication.onCreate()` aufgerufen (richtet Crash-Handler, Log-Level, SharedProperties, ShellEnvironment und Theme ein).
  3. **Start-Befehl in `TermuxActivity` ([`TermuxActivity.java`](file:///data/data/com.termux/files/home/CodeForgeMobile/libs/termux-app/src/main/java/com/codeforge/app/TermuxActivity.java), [`TermuxTerminalSessionActivityClient.java`](file:///data/data/com.termux/files/home/CodeForgeMobile/libs/termux-app/src/main/java/com/codeforge/app/terminal/TermuxTerminalSessionActivityClient.java)):**
     - `TermuxActivity.EXTRA_INITIAL_COMMAND = "com.codeforge.app.termux.INITIAL_COMMAND"` eingeführt (inkl. `onNewIntent`-Unterstützung).
     - Sobald die erste `TerminalSession` aktiv ist (`session.getPid() > 0`), sendet `dispatchPendingInitialCommandIfNeeded()` das Setup-Kommando (`codeforge-env setup ...`) direkt an die laufende Shell-Session.
  4. **Navigation & Umschalter Termux-UI / Kompakt ([`settings.proto`](file:///data/data/com.termux/files/home/CodeForgeMobile/core/datastore/src/main/proto/settings.proto), [`CodeForgeNavHost.kt`](file:///data/data/com.termux/files/home/CodeForgeMobile/app/src/main/kotlin/com/codeforge/app/CodeForgeNavHost.kt), [`TerminalSettingsScreen.kt`](file:///data/data/com.termux/files/home/CodeForgeMobile/feature/settings/src/main/kotlin/com/codeforge/feature/settings/ui/TerminalSettingsScreen.kt), [`TerminalScreen.kt`](file:///data/data/com.termux/files/home/CodeForgeMobile/feature/terminal/src/main/kotlin/com/codeforge/feature/terminal/ui/TerminalScreen.kt)):**
     - Proto-Feld `bool use_compact_terminal = 3;` in `TerminalConfig` ergänzt (Proto3-Default `false` = volle Termux-UI ist Standard).
     - In `Settings → Terminal` Auswahl zwischen **Termux-UI (Standard)** und **Kompakt** ergänzt; im kompakten Compose-Terminal zusätzlich einen Action-Button in der TopAppBar zum direkten Öffnen der vollen Termux-UI bereitgestellt.

---

### 24.5 `codeforge-env`: Setup-Fehler sichtbar machen
- **Umsetzung in [`libs/terminal-engine/src/main/assets/codeforge-env`](file:///data/data/com.termux/files/home/CodeForgeMobile/libs/terminal-engine/src/main/assets/codeforge-env):**
  1. Logdatei unter `$HOME/.codeforge/logs/setup-$(date +%Y-%m-%d).log` angelegt.
  2. `pkg_update_once()` führt vor dem ersten Paket-Install `apt-get update -y` (bzw. `pkg update -y`) aus, protokolliert die Ausgabe in `$LOG_FILE` und gibt bei Fehlern (Netzwerk, GPG-Signatur, 404) die letzten 20 Zeilen auf der Konsole aus.
  3. `pkg_install()` ersetzt alle bisherigen `pkg install -y ... >/dev/null 2>&1`-Aufrufe:
     - Schreibt die komplette Ausgabe nach `$LOG_FILE`.
     - Gibt bei Fehlern die letzten 20 Zeilen auf `stderr` sowie `apt-cache policy <pkg>` aus.
     - Erkennt `"Unable to locate package"` und gibt gezielt aus:  
       `FEHLER: Paket '<pkg>' fehlt im APT-Repo (Etappe bauen) – URL: <url>` (gelesen aus `$PREFIX/etc/apt/sources.list`).
  4. `format_component_id()` formatiert interne Komponenten-Schlüssel für die Fortschrittsanzeige lesbar (`jdk;17` → `JDK 17`, `ndk;27.1.12297006` → `NDK 27.1.12297006`, `cmake;3.22.1` → `CMake 3.22.1`).
  5. Bei Fehlern in `do_setup()` beendet sich `codeforge-env` mit Exit-Code `1` und zeigt den Pfad zur Logdatei sowie die exakte Wiederholungszeile:  
     `Erneut versuchen: codeforge-env setup --jdk=17 ...`

---

### 24.6 Bootstrap-Skript Zeile 290 & Fallback-Run
- **Ursachenanalyse (belegt anhand von `bootstrap-aarch64.zip` und dem Fork `scto/terminal-packages-codeforge`):**
  1. **Warum `termux-bootstrap-second-stage.sh: line 290: [: =: unary operator expected` auftrat:**
     - Im gebauten Bootstrap (`etc/termux/termux-bootstrap/second-stage/termux-bootstrap-second-stage.sh`) standen in den Zeilen 4–6:
       ```bash
       export TERMUX_PREFIX="/data/data/com.codeforge.app/files/usr"
       export TERMUX_PACKAGE_MANAGER=""
       export TERMUX_PACKAGE_ARCH=""
       ```
     - Grund für die leeren Variablen: In `scripts/build-bootstraps.sh` (Zeilen 213–214 und 487) des Forks `scto/terminal-packages-codeforge` wird `@TERMUX_PACKAGE_MANAGER@` durch `${TERMUX_PACKAGE_MANAGER}` ersetzt, ohne dass `TERMUX_PACKAGE_MANAGER` in `build-bootstraps.sh` oder `scripts/properties.sh` vorbelegt ist (anders als in `build-package.sh`, wo `: "${TERMUX_PACKAGE_MANAGER:="apt"}"` gesetzt wird). Zudem wird `add_termux_bootstrap_second_stage_files "$package_arch"` in `main()` (Zeile 487) mit `$package_arch` aufgerufen – einer Variablen, die zuvor in `extract_debs()` als `local package_arch="$1"` deklariert war und außerhalb von `extract_debs()` leer ist!
     - In `termux-bootstrap-second-stage.sh` war Zeile 167 quotiert (`if [ "${TERMUX_PACKAGE_MANAGER}" = "apt" ]; then` → false, wodurch alle `*.postinst`-Skripte übersprungen wurden), Zeile 290 jedoch **unquotiert**:
       ```bash
       elif [ ${TERMUX_PACKAGE_MANAGER} = "pacman" ]; then
       ```
       Bei leerem `TERMUX_PACKAGE_MANAGER` expandierte Bash dies zu `elif [ = "pacman" ]; then`, was `line 290: [: =: unary operator expected` erzeugte.
  2. **Warum `"Starting fallback run of termux bootstrap second stage"` lief:**
     - Unser vendortes [`TermuxInstaller.java`](file:///data/data/com.termux/files/home/CodeForgeMobile/libs/termux-app/src/main/java/com/codeforge/app/TermuxInstaller.java) stammte von einer älteren Termux-Basis (`v0.118.0`), die `termux-bootstrap-second-stage.sh` nach dem Entpacken noch nicht aufrief.
     - Dadurch fehlte beim Start der ersten Login-Shell der Lock-Symlink `etc/termux/termux-bootstrap/second-stage/termux-bootstrap-second-stage.sh.lock`, sodass `/etc/profile.d/01-termux-bootstrap-second-stage-fallback.sh` einsprang.
- **Umsetzung:**
  1. **Build-Pipeline ([`build_codeforge_repo.sh`](file:///data/data/com.termux/files/home/CodeForgeMobile/build_codeforge_repo.sh#L344-L385)) & Fork-Reparaturskript ([`scripts/fix-fork.sh`](file:///data/data/com.termux/files/home/CodeForgeMobile/scripts/fix-fork.sh#L197-L231)):**
     - Funktion `fix_bootstrap_second_stage()` ergänzt:
       - Quotiert `"${TERMUX_PACKAGE_MANAGER}"` in Zeile 290 von `scripts/bootstrap/termux-bootstrap-second-stage.sh` und setzt `: "${TERMUX_PACKAGE_MANAGER:=apt}"` als Fallback.
       - Patcht `scripts/build-bootstraps.sh`, sodass `@TERMUX_PACKAGE_MANAGER@` mit `${TERMUX_PACKAGE_MANAGER:-apt}` ersetzt und `add_termux_bootstrap_second_stage_files "$TERMUX_ARCH"` statt des leeren `"$package_arch"` übergeben wird.
       - Sichert `packages/dpkg/dpkg-perl.subpackage.sh` (`cpan -Ti Locale::gettext` in `postinst`) für Offline-Läufe ab.
     - In Step 6 von `build_codeforge_repo.sh` eine Verifikationsprüfung ergänzt, die sicherstellt, dass `termux-bootstrap-second-stage.sh` im erzeugten Bootstrap weder ein unquotiertes `elif [ ${TERMUX_PACKAGE_MANAGER}` noch ein leeres `export TERMUX_PACKAGE_MANAGER=""` enthält.
  2. **Regulärer Second-Stage-Aufruf & In-Place-Reparatur in [`TermuxInstaller.java`](file:///data/data/com.termux/files/home/CodeForgeMobile/libs/termux-app/src/main/java/com/codeforge/app/TermuxInstaller.java#L204-L350):**
     - Setzt bereits beim Entpacken `Os.chmod(..., 0700)` auf `etc/termux/termux-bootstrap/second-stage/termux-bootstrap-second-stage.sh`.
     - Führt direkt nach `TermuxShellEnvironment.writeEnvironmentToFile(activity)` die Methode `runBootstrapSecondStageIfPresent()` aus:
       - Repariert bei bereits bestehenden Bootstraps (`bootstrap-2026.04.15`) in-place `export TERMUX_PACKAGE_MANAGER=""` → `"apt"`, `export TERMUX_PACKAGE_ARCH=""` → `"<arch>"` sowie Zeile 290 (`elif [ "${TERMUX_PACKAGE_MANAGER}" = "pacman" ]; then`).
       - Führt `termux-bootstrap-second-stage.sh` regulär im Installer-Thread aus, protokolliert die Ausgabe ins App-Log und entfernt nach erfolgreichem Lauf `etc/profile.d/01-termux-bootstrap-second-stage-fallback.sh`.

---

### 24.7 Offener Punkt außerhalb des Codes: `openjdk-17` im APT-Repository
- **Prüfung des öffentlichen APT-Repositories:**
  ```bash
  curl -s https://scto.github.io/terminal-packages-codeforge/dists/stable/main/binary-aarch64/Packages | grep -c '^Package: openjdk-17'
  ```
  **Ergebnis (2026-10-18):** `0`
- **Bedeutung:** Etappe `01-jdk17` (`H1-jdk17`) wurde noch nicht ins APT-Repository (`scto.github.io/terminal-packages-codeforge`) gebaut und veröffentlicht. Deshalb schlägt `pkg install openjdk-17` aktuell mit `E: Unable to locate package openjdk-17` fehl (was durch **24.5** nun klar mit URL und Hinweis auf den Etappen-Build im Terminal ausgegeben wird).
- **Aktion durch den Nutzer:** Im Fork `scto/terminal-packages-codeforge` den GitHub-Actions-Workflow `build-all-stages.yml` (bzw. `only=H1-jdk17`) starten, um `openjdk-17` für das APT-Repo zu bauen und auf `gh-pages` zu veröffentlichen. Ebenso bei Gelegenheit einen neuen Bootstrap-Build anstoßen, damit die Korrekturen aus **24.6** auch direkt im Release-ZIP liegen (bis dahin repariert `TermuxInstaller.java` das entpackte Skript automatisch beim Setup).

---

## 2. Getestet / Nicht getestet

### ✅ Getestet
- **Gradle-Build (`./gradlew :app:assembleDebug --no-daemon`):** Nach jedem Teilpunkt (`24.1`, `24.2`, `24.3`, `24.4`, `24.5`, `24.6`) erfolgreich durchgelaufen (`BUILD SUCCESSFUL`).
- **Manifest-Merge (`app/build/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml`):** Geprüft auf Konflikte, Berechtigungen (`MANAGE_EXTERNAL_STORAGE`, `REQUEST_INSTALL_PACKAGES`, `REQUEST_DELETE_PACKAGES`, `READ/WRITE_EXTERNAL_STORAGE` mit `maxSdkVersion="29"`), `CodeForgeApplication`, `TermuxActivity`, `TermuxService` und `HelpActivity`.
- **Shell-Syntax (`bash -n`):** Für `libs/terminal-engine/src/main/assets/codeforge-env`, `build_codeforge_repo.sh` und `scripts/fix-fork.sh` fehlerfrei geprüft.
- **Lokalisierungs- & String-Prüfskripte:**
  - `python3 scripts/check_string_locales.py` (Parität zwischen `values/strings.xml` und `values-de/strings.xml`, Platzhalter, Escapes)
  - `python3 scripts/find_hardcoded_strings.py --check` (0 hartcodierte UI-Strings)
- **Code-Style (`./gradlew ktlintCheck --no-daemon`):** Auf allen geänderten Dateien ausgeführt.
- **APT-Repo-Abfrage (`curl`):** `Packages`-Index von `https://scto.github.io/terminal-packages-codeforge/dists/stable/main/binary-aarch64/Packages` live abgefragt (`openjdk-17` Treffer = `0`).

### ⚠️ Nicht getestet (erfordert interaktiven Gerätetest / GitHub-Actions-Lauf)
- **Visuelle Darstellung des Launcher-Icons (rund/eckig/Themed Icon) & Onboarding-Screens auf einem physischen Gerät/Launcher:** Compose-`@Preview`-Funktionen (Hell/Dunkel) sind im Code angelegt, können aber im Headless-Termux-CLI ohne Android Studio Preview-Renderer nicht als Screenshot gerendert werden.
- **Interaktiver Systemdialog für `ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION` und `ACTION_MANAGE_UNKNOWN_APP_SOURCES`** sowie die Rückkehr per `ON_RESUME` auf dem Gerät.
- **Interaktive Nutzung von `TermuxActivity`** (Drawer, Extra-Keys, Mehrfach-Sessions, Long-Press Copy/Paste, Zurück-Navigation zur App und automatischer Start von `codeforge-env setup ...` in der ersten Session).
- **End-to-End-Installation von `openjdk-17` via `codeforge-env setup`:** Blockiert, bis Etappe `H1-jdk17` im Fork gebaut und ins APT-Repo veröffentlicht wurde (siehe 24.7).
- **Neubau der Bootstrap-ZIPs im Docker-Container (`build_codeforge_repo.sh`):** Gemäß Auftrag nur vorbereitet und nicht ausgeführt (erfordert x86_64-Docker-Runner).
