> **ERLEDIGT / ÜBERHOLT (2026-10-06):** Gegenstandslos: PRoot/Rootfs gibt es nicht mehr. Das SDK liegt direkt in `$PREFIX/opt/android-sdk` (`TermuxEnvironment.sdkRoot`); die Shell erhält ANDROID_HOME/JAVA_HOME über `profile.d/codeforge-android.sh`.

# Auftrag: SDK-Sichtbarkeit in der Termux-Shell lösen (ANDROID_HOME/cmdline-tools)

## Kontext

Repo: CodeForgeMobile (Android, Kotlin). Arbeitsverzeichnis: Projekt-Root
(`CodeForgeMobile/`).

Lies zuerst `docs/sub/TERMUX-PORTING.md`, Abschnitt "🔧 3. Architektur-Entscheidung:
SDK-Sichtbarkeit in der Termux-Shell", und
`docs/architecture-decisions.md`, ADR-3 — dort ist das offene Problem
beschrieben: `TermuxShellEnvironment.java` setzte ursprünglich einen `PATH` mit
`ANDROID_HOME`, der auf den vom SDK Manager installierten Commandline-Tools
zeigte. Dieser Teil wurde entfernt, weil unklar war, ob der SDK-Pfad aus der
(nicht gechrooteten) Termux-Bootstrap-Shell heraus überhaupt sichtbar ist.

## Tatsächlicher Befund (bereits recherchiert, nicht erneut in Frage stellen)

Der SDK Manager installiert den SDK NICHT in einem für Termux unerreichbaren
Ort. Nachvollzogen in `libs/terminal-engine/src/main/kotlin/com/codeforge/libs/terminal_engine/`:

- `DistroBootstrapRepositoryImpl.rootfsDir(distro)` →
  `File(context.filesDir, "distro/$distro")`, also ein ganz normales
  Unterverzeichnis von `context.filesDir`
  (`/data/data/com.codeforge.app/files/distro/<distro>/`)
- `CommandlineToolsInstaller.SDK_ROOT_RELATIVE_PATH` = `"opt/android-sdk"`,
  installiert unter `File(rootfsDir, SDK_ROOT_RELATIVE_PATH)`
- Daraus folgt: der SDK liegt absolut unter
  `/data/data/com.codeforge.app/files/distro/<distro>/opt/android-sdk/`

PRoot erzeugt eine *virtuelle* Root-Umgebung nur für Prozesse, die **unter**
PRoot laufen (`-r $ROOTFS`-Bind-Mount gilt nur innerhalb des PRoot-Prozesses).
Das Verzeichnis `.../distro/<distro>/opt/android-sdk/` ist aber ein ganz
normales Unterverzeichnis innerhalb der eigenen App-Sandbox
(`context.filesDir`) — eine Termux-Bootstrap-Shell, die NICHT unter PRoot
läuft, kann diesen absoluten Pfad trotzdem direkt lesen, es gibt dort keine
echte Zugriffsbarriere (keine andere UID, kein separates Mount-Namespace
außerhalb von PRoot). Die ursprüngliche Annahme "nicht sichtbar" war also nur
teilweise richtig: es ist kein `chroot`/Mount-Namespace-Problem, sondern
lediglich eine Frage des korrekten absoluten Pfads und davon, WELCHE
`distro`-Instanz gemeint ist (der Nutzer kann mehrere installiert haben /
wechseln).

`SettingsRepository.updateTerminalDistro(distro: String)` /
`setDefaultDistro(distro)`
(`core/datastore/src/main/kotlin/com/codeforge/core/datastore/SettingsRepository.kt`)
persistiert bereits, welche Distro der Nutzer zuletzt gewählt hat — das ist die
Quelle der Wahrheit für "welches SDK meinen wir".

## Aufgabe (Option c aus docs/sub/TERMUX-PORTING.md, Punkt 3 — direkte PATH-Erweiterung, kein Bind-Mount/Symlink nötig)

1. In
   `libs/terminal-engine/src/main/kotlin/com/codeforge/libs/terminal_engine/TerminalSessionRepositoryImpl.kt`:
   - `SettingsRepository` (aus `:core:datastore`) und `DistroBootstrapRepository`
     (aus `:core:domain`, Implementierung bereits in diesem Modul als
     `DistroBootstrapRepositoryImpl`) per Hilt in den Konstruktor injizieren
     (prüfe, ob `:libs:terminal-engine` bereits eine Dependency auf
     `:core:datastore` hat — laut `build.gradle.kts` dieses Moduls ja).
   - In `buildTermuxEnvironment()`: den zuletzt gewählten Distro-Namen aus
     `settingsRepository` lesen (achte auf den korrekten Flow/Property-Namen
     in `SettingsRepository` — ggf. `TerminalSettings.defaultDistro` o. ä.,
     prüfe die generierte Proto-Klasse bzw. die Lese-Methode neben
     `updateTerminalDistro`).
   - Daraus den absoluten Pfad `<filesDir>/distro/<distro>/opt/android-sdk/cmdline-tools/latest/bin`
     bilden (nutze dafür wenn möglich `distroBootstrapRepository.rootfsPath(distro)`
     statt den Pfad erneut von Hand zusammenzusetzen, um keine zweite
     Quelle der Wahrheit für das Pfadschema zu schaffen) und an den
     bestehenden `PATH`-Eintrag anhängen:
     `"PATH" to "${TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH}:$sdkCmdlineToolsBinPath:${System.getenv("PATH").orEmpty()}"`
   - Falls der Pfad zur Laufzeit nicht existiert (SDK noch nicht installiert),
     NICHT hart fehlschlagen — einfach weglassen (der SDK Manager ist optional,
     der Termux-Shell-Start darf davon nicht abhängen).

2. Setze zusätzlich (falls noch nicht vorhanden) `ANDROID_HOME` in derselben
   Methode auf denselben Rootfs-relativen SDK-Pfad, analog zum ursprünglichen,
   entfernten Verhalten in `TermuxShellEnvironment.java` — diesmal aber mit dem
   korrekt aufgelösten absoluten Pfad statt der früheren
   `dev.mutwakil.androidide.utils.Environment.ANDROID_HOME`-Abhängigkeit.

3. Aktualisiere `docs/sub/TERMUX-PORTING.md`, Punkt 3, und `docs/architecture-decisions.md`,
   ADR-3: markiere die Architekturfrage als gelöst (Option c gewählt und
   begründet, s. o.), nicht mehr als offen.

## Akzeptanzkriterien

- `./gradlew :libs:terminal-engine:assembleDebug` baut erfolgreich.
- `buildTermuxEnvironment()` liefert einen `PATH`, der den SDK-`cmdline-tools/bin`-
  Pfad enthält, wenn eine Distro mit installiertem SDK existiert, und
  funktioniert unverändert (ohne Absturz), wenn keine existiert.
- Kein Zugriff auf `ANDROID_HOME`/SDK-Pfad blockiert den Start einer normalen
  Termux-Shell-Session, falls der SDK (noch) nicht installiert ist.
- `docs/sub/TERMUX-PORTING.md` und `docs/architecture-decisions.md` spiegeln den neuen,
  gelösten Stand wider (kein offener 🔧-Punkt mehr für dieses Thema).
