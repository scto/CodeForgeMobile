# TERMUX-PORTING.md — Status & offene Anpassungen (echte Integration)

Vendorter, auf `com.codeforge` umgeschriebener Termux-Code (Quelle:
`scto/AndroidIDE`, dev-Branch, `termux/`), eingebaut in dein tatsächliches
CodeForgeMobile-Projekt. **GPLv3**, siehe `LICENSE.termux` und `NOTICE.md`.
CodeForgeMobile ist ab Einbindung dieser Module GPLv3-pflichtig — bewusste
Entscheidung von dir, siehe `docs/architecture-decisions.md`.

Stand dieser Datei: alle unten mit ✅ markierten Punkte sind **erledigt und
verifiziert** (Grep-Sweep über das komplette Projekt, keine offenen
`dev.mutwakil.*`-Importe oder `com.termux`-Pfade mehr außerhalb von
Kommentaren/Lizenzdateien). Offene Punkte sind mit 🔧 markiert — das sind
die "einzelnen Punkte", die du selbst noch anpassen musst.

## Was konkret geändert wurde

### Neue Module ✅
`:libs:termux-emulator`, `:libs:termux-view`, `:libs:termux-shared`,
`:libs:termux-app` — in `settings.gradle.kts` eingetragen, Version-Catalog-
Einträge (`markwon-*`, `google-material`, `google-guava`,
`hidden-api-bypass`, `androidx-appcompat`, `androidx-preference`,
`androidx-drawerlayout`, `androidx-viewpager2`, `androidx-window`, `ndk`) in
`gradle/libs.versions.toml` ergänzt.

### `:libs:terminal-engine` — umgestellt, NICHT neu geschrieben ✅
Das war die chirurgisch wichtigste Stelle: **die öffentliche Schnittstelle
`TerminalSessionRepository` (in `:core:domain`) ist unverändert geblieben**
— `sessionState`, `output`, `start(distro)`, `sendInput(text)`, `stop()`.
Dadurch mussten `:feature:terminal` (Contract/ViewModel/Screen), die
Hilt-Bindings in `TerminalEngineModule.kt` und `:app` **überhaupt nicht
angefasst werden.**

Geändert wurde nur `TerminalSessionRepositoryImpl.kt`:
- Vorher: `com.termux.terminal.TerminalSession` (aus dem Termix-Projekt
  vendort, lag in diesem Modul unter `src/main/java/com/termux/terminal/`
  + `src/main/jni/`) + PRoot-Start (damals; inzwischen entfernt)
- Jetzt: `com.codeforge.terminal.TerminalSession` (aus `:libs:termux-emulator`,
  echtes Termux) + Start der Shell direkt im Termux-Bootstrap
  (`TermuxConstants.TERMUX_PREFIX_DIR_PATH` = `/data/data/com.codeforge.app/files/usr`)
- Die alte Termix-Kopie (`src/main/java/com/termux/terminal/`,
  `src/main/jni/`) wurde **gelöscht** — sonst hätten zwei Module dieselbe
  native Bibliothek `libtermux.so` mitgebracht (Packaging-Konflikt)
- `externalNativeBuild`-Block aus `terminal-engine/build.gradle.kts`
  entfernt (nicht mehr nötig, `:libs:termux-emulator` bringt seine eigene
  native Lib mit)
- Zwei reale API-Unterschiede zur vorherigen Annahme gefunden und korrigiert:
  `TerminalSession.write()` existiert nur als `write(byte[], offset, count)`,
  nicht `write(String)`; `updateSize()` hat nur `(columns, rows)`, nicht die
  4-Parameter-Variante mit Zellengröße
- `distro`-Parameter von `start(distro)` wird für Termux ignoriert (geloggt)
  — Termux kennt kein PRoot-artiges Mehrfach-Rootfs-Konzept

### PRoot (ÜBERHOLT, 2026-10-07)
PRoot, Rootfs-Download und `DistroBootstrapRepository` wurden inzwischen vollständig entfernt (siehe `docs/architecture-decisions.md` ADR-3 und `libs/terminal-engine/BOOTSTRAP.md`). SDK Manager, Plugin-Runtime und Onboarding laufen im Termux-Prefix.

### AndroidIDE-interne Abhängigkeiten im vendorten Code (`dev.mutwakil.*`) ✅
Keine davon ist `com.termux`-präfixt, daher hat sie der automatische
Rename nicht erfasst — alle neun betroffenen Dateien wurden einzeln per
Grep aufgespürt und gefixt (nicht nur dokumentiert):

| Datei | War | Jetzt |
|---|---|---|
| `termux-app/.../TermuxApplication.java` | `extends BaseApplication` (dev.mutwakil) | `extends Application`. Wird nicht mehr automatisch instanziiert — `onCreate()`-Logik muss manuell aus `CodeForgeApplication.onCreate()` (`:app`) aufgerufen werden, falls gewünscht (🔧 offen, s.u.) |
| `termux-app/.../TermuxActivity.java` | `extends BaseIDEActivity` | `extends AppCompatActivity`. Alle `super.*`-Aufrufe geprüft (Grep) — ausschließlich Standard-Activity-Lifecycle, keine AndroidIDE-spezifische Logik verloren |
| `termux-app/.../TermuxInstaller.java` | `setupBootstrapIfNeeded(...)` package-private | auf `public` erweitert, damit von außerhalb (z. B. Onboarding) aufrufbar |
| `termux-shared/.../activities/ReportActivity.java` | `extends BaseIDEActivity` | `extends AppCompatActivity` (wird real von `TermuxCrashUtils`, `TermuxPluginUtils`, `TextIOActivity` referenziert — keine tote Klasse) |
| `termux-shared/.../shell/TermuxShellUtils.java` | `Environment.BIN_DIR` | `TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH` |
| `termux-shared/.../shell/command/environment/TermuxShellEnvironment.java` | `ANDROID_HOME`-Pfad für `cmdline-tools` im `PATH` | ✅ wiederhergestellt: `TermuxDevEnvironment.appendSdkToPath(...)` (nur wenn `cmdline-tools/latest/bin` existiert) |
| `termux-shared/.../shell/command/environment/AndroidShellEnvironment.java` | `Environment.putEnvironment(...)` | ✅ nach Vorlage `Environment.kt` neu umgesetzt als `TermuxDevEnvironment.putEnvironment(env, forFailsafe)`, aufgerufen in `TermuxShellEnvironment` (s. u.) |
| `termux-shared/.../theme/ThemeUtils.java` | `GeneralPreferences.getUiMode()`, `ContextUtilsKt.isSystemInDarkMode()` | Standard-`Configuration.UI_MODE_NIGHT_MASK`-Check; App-eigene Theme-Einstellung (🔧 optional, s. u.) nicht mehr berücksichtigt |
| `termux-shared/.../view/ViewUtils.java` | `dev.mutwakil.androidide.window.WindowManager/WindowMetrics` | `androidx.window.layout.WindowMetricsCalculator` |

Verifiziert per projektweitem Grep: **keine** `dev.mutwakil.*`-Importe mehr
außerhalb von `TODO(Thomas)`-Erklärkommentaren und einer harmlosen Log-
String-Konstante in `TermuxTerminalSessionActivityClient.java` (zitiert nur
einen alten Exception-Text, kein Compile-Dependency).

#### `TermuxDevEnvironment` (Ersatz für AndroidIDE `Environment.kt`) ✅
Die ursprünglich fehlende Datei wurde nachgeliefert; sie setzte `HOME`, `ANDROID_HOME`, `ANDROID_SDK_ROOT`,
`ANDROID_USER_HOME`, `JAVA_HOME`, `GRADLE_USER_HOME`, `SYSROOT`, `PROJECTS` und (nicht-failsafe)
`TERMUX_PKG_NO_MIRROR_SELECT`. Umsetzung: `libs/termux-shared/.../command/environment/TermuxDevEnvironment.java`,
genutzt von `TermuxShellEnvironment` (Java) und `TermuxEnvironment.kt` (`:libs:terminal-engine`, Plugins, SDK-Skript).

| Variable | AndroidIDE | CodeForge |
|---|---|---|
| `ANDROID_HOME`/`ANDROID_SDK_ROOT` | `$HOME/android-sdk` | `$PREFIX/opt/android-sdk` (Root des Skripts `codeforge-env`) |
| `JAVA_HOME` | fest `$PREFIX/lib/jvm/java-21-openjdk` | höchste installierte `java-N-openjdk` mit `bin/java`; ohne JDK nicht gesetzt |
| `GRADLE_USER_HOME` / `ANDROID_USER_HOME` | `$HOME/.gradle` / `$HOME/.android` | identisch |
| `SYSROOT` | `$PREFIX` | identisch |
| `PROJECTS` | externer Speicher `/AndroidIDEProjects` | `<filesDir>/projects` (= `defaultProjectsDirectory()`) |
| `TERMUX_PKG_NO_MIRROR_SELECT` | `true` außer Failsafe | identisch |
| PATH | `+ $ANDROID_HOME/cmdline-tools/latest/bin` | identisch, nur wenn das Verzeichnis existiert |

Nicht übernommen (AndroidIDE-IDE-intern, hier ohne Entsprechung): `ANDROID_JAR`, `TOOLING_API_JAR`, `AAPT2`, `INIT_SCRIPT`,
`REALM_DB_DIR`, `SNIPPETS_DIR`, `COMPOSE_HOME`, `JavacConfigProvider`-Property, `android-37.0`-Plattformpfad.

### AndroidManifest.xml von `:libs:termux-app` korrigiert ✅
Beim Review aufgefallen (nicht Teil des ursprünglichen Rename-Scripts):
drei Komponenten-Namen waren als **relative** Manifest-Namen angegeben
(`.shared.activities.ReportActivity`, `.app.api.file.FileReceiverActivity`,
`.app.TermuxService`). Relative Namen werden von Android gegen den
Modul-`namespace` (`com.codeforge.app`) aufgelöst — das hätte zu
`com.codeforge.app.shared.activities.ReportActivity` (`ReportActivity`
liegt aber im *anderen* Modul `com.codeforge.shared`) bzw. doppeltem
`.app.app.` geführt → `ClassNotFoundException` zur Laufzeit. Jetzt auf
vollqualifizierte bzw. korrekt-relative Namen korrigiert. Betrifft aktuell
nur Komponenten, die (s. u.) noch nicht in `:app` verdrahtet sind, wäre
also erst beim Aktivieren der vollen `TermuxActivity`-UI aufgefallen —
daher jetzt vorab gefixt.

### Native Bootstrap-Einbettung ✅ (Mechanismus) / 🔧 (Prefix-Fork)
Das Convention-Plugin `codeforge.terminal.bootstrap` (`build-logic/convention/…/TerminalBootstrapPackagesPlugin.kt`,
angewendet in `libs/termux-app/build.gradle.kts`) registriert den Task `embedTerminalBootstrap`
(hängt an `preBuild`): lädt die ABI-Zips (`aarch64`, `arm`, `x86_64`), prüft SHA-256 und schreibt
`src/main/cpp/termux-bootstrap-zip.S` als `.incbin`-Blob, der von `termux-bootstrap.c`/`Android.mk`
nativ einkompiliert und zur Laufzeit von `TermuxInstaller.java` nach `TERMUX_PREFIX_DIR_PATH`
extrahiert wird. Der Download passiert im Task, nicht beim Gradle-Sync.

Standard-Quelle: `AndroidIDEOfficial/terminal-packages`, Release `bootstrap-16.12.2023`
(Prüfsummen am 2026-10-06 gegen die echten Dateien verifiziert). **Diese Pakete sind für den Prefix
`/data/data/com.itsaky.androidide/files/usr` gebaut** (in `etc/profile` und `etc/apt/sources.list`
nachgewiesen). Für `com.codeforge.app` mit Prefix `/data/data/com.codeforge.app/files/usr` laufen
sie nicht korrekt – eigenen Fork bauen und per Property einhängen:
`codeforgeBootstrapUrlTemplate` (`%1$s` = Version, `%2$s` = ABI), `codeforgeBootstrapVersion`,
`codeforgeBootstrapSha256.<abi>`. Offline/CI mit vorhandener `.S`-Datei: `-PcodeforgeBootstrapSkip=true`.

Alle nativen JNI-Symbolnamen (`Java_com_codeforge_terminal_JNI_...` in
`termux.c`, `local-socket.cpp`, `termux-bootstrap.c`) wurden korrekt auf
`com_codeforge` umgestellt — ein reiner Text-Replace von `com.termux` auf
`com.codeforge` hätte die unterstrich-getrennten C-Funktionsnamen NICHT
erfasst, das wurde separat per Grep verifiziert und gezielt gefixt.

## Noch zu erledigen (deine "einzelnen Punkte")

### ✅ 1. Bootstrap-Installation — erledigt
Onboarding ruft `TermuxInstaller.setupBootstrapIfNeeded` (Effekt `RunTermuxBootstrapSetup`), danach öffnet das Terminal und führt `codeforge-env setup` aus. Details: `libs/terminal-engine/BOOTSTRAP.md`.

### 🔧 2. Bootstrap-Zip selbst bauen
Eigenen `terminal-packages`-Fork mit Präfix `/data/data/com.codeforge.app/files/usr` bauen (beachte: `.app`
am Ende — das ist der echte `applicationId` von `:app`) und die Properties `codeforgeBootstrapUrlTemplate`,
`codeforgeBootstrapVersion` und `codeforgeBootstrapSha256.aarch64|arm|x86_64` in `gradle.properties` setzen
(siehe oben bzw. Plugin-KDoc).

### ✅ 3. SDK-Sichtbarkeit — entschieden
PRoot gibt es nicht mehr. Das SDK liegt in `$PREFIX/opt/android-sdk`; `profile.d/codeforge-android.sh` setzt ANDROID_HOME/JAVA_HOME/PATH. **Offen:** Ausführbarkeit von Binaries aus `files/usr` bei `targetSdk 35` (W^X) — siehe BOOTSTRAP.md.

### 🔧 4. Optional: volle `TermuxActivity`-UI statt `:feature:terminal`
Aktuell aktiv ist ausschließlich der schlanke Pfad über
`TerminalSessionRepositoryImpl` (direkte `TerminalSession`-Instanziierung,
kein `TermuxService`, kein `TermuxActivity`). `:libs:termux-app` und
`:libs:termux-view` sind vollständig mitgebaut (stehen im Dependency-Graph
von `:app` aktuell **nicht**, siehe unten), falls du stattdessen/zusätzlich
die komplette Termux-Multi-Session-UI (Extra-Keys-Toolbar, Sessions-Drawer,
`TermuxActivity`) verwenden willst. Dazu müsstest du `:libs:termux-app`
als Dependency in `:app` (oder ein neues `:feature:termux-ui`) aufnehmen
und `TermuxActivity` im App-Manifest registrieren (Komponenten-Namen in
`libs/termux-app/.../AndroidManifest.xml` sind bereits korrekt).

### 🔧 5. `TermuxApplication.onCreate()`-Logik
Falls du die volle Termux-UI (Punkt 4) aktivierst: `TermuxApplication`
wird nicht mehr automatisch instanziiert (kein `<application>`-Tag mehr in
`:libs:termux-app`, nur `:app`s `CodeForgeApplication` läuft). Falls
`TermuxApplication.onCreate()` App-weite Initialisierung enthält (Shell-
Manager, Notification-Channels, `TermuxAmSocketServer`), müsste sie manuell
aus `CodeForgeApplication.onCreate()` aufgerufen werden.

## Was NICHT im Dependency-Graph von `:app` hängt (Stand jetzt)
`:app` → `:feature:terminal` → `:libs:terminal-engine` → `:libs:termux-emulator`
+ `:libs:termux-shared`. **`:libs:termux-app` und `:libs:termux-view` werden
aktuell von niemandem als Projekt-Dependency konsumiert** — sie sind Teil
des Gradle-Modulbaums (`settings.gradle.kts`) und kompilieren eigenständig,
liefern aber erst dann echten Wert, wenn du Punkt 4 oben umsetzt. Das ist
beabsichtigt: dein Wunsch war "die ganze Struktur haben", nicht zwingend
"die volle Termux-UI sofort aktiv".

## Lizenzfolge
`NOTICE.md` im Projekt-Root dokumentiert die GPLv3-Pflicht des
Gesamtprojekts ab Einbindung dieser Module. `LICENSE.termux` enthält den
vollständigen Lizenztext.
