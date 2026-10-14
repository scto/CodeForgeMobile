# Termux-Umgebung (ersetzt PRoot/Rootfs/Multi-Distro)

**Stand:** PRoot, Rootfs-Download, `DistroBootstrapRepository` und die Distro-Auswahl sind
entfernt. Es gibt genau eine Umgebung: den Termux-Bootstrap unter
`/data/data/com.codeforge.app/files/usr`.

## Ablauf

1. **Onboarding → Setup:** `TermuxInstaller.setupBootstrapIfNeeded(activity)` (`:libs:termux-app`)
   entpackt den eingebetteten Bootstrap (Prefix `com.codeforge.app`; der Release `bootstrap-2026.10.08` ist defekt und muss neu gebaut werden). Er enthält die
   Termux-Basispakete (bash, coreutils, curl, tar, unzip, xz-utils, gawk, grep, sed, …), **nicht** `openjdk-17` und nicht
   `codeforge-env`. Das Skript kommt aus `assets/codeforge-env` (ScriptInstaller). Das neue Build-Skript patcht `apt` auf
   das eigene APT-Repo (`CODEFORGE_APT_URL`); `openjdk-17` o. Ä. kommen über `EXTRA_PACKAGES` ins Repo/Bootstrap.
2. **Skript sicherstellen:** `ScriptInstaller` kopiert `assets/codeforge-env` nach
   `$PREFIX/bin`, falls es nicht als Paket im Bootstrap liegt (Platzhalter `@TERMUX_PREFIX@` etc.
   werden ersetzt).
3. **Terminal öffnet sich** (Route `terminal?initialCommand=…`) und führt
   `codeforge-env setup --jdk 17|21 --ndk r29 --cmake 3.22.1` sichtbar aus (gibt es eine Version nicht, nimmt das Skript das NDK gleicher Hauptversion bzw. das Termux-CMake).
4. **SDK-Manager** (`:feature:sdkmanager`) ist nur noch die grafische Oberfläche über
   `codeforge-env --machine list|install|uninstall` (`TermuxScriptSdkRepository`).

## Skript `codeforge-env`

* JDKs: Termux-Pakete `openjdk-17` / `openjdk-21` (`$PREFIX/lib/jvm/java-N-openjdk`).
* Quellen der Android-Teile (Vorbild: `scto/termux-android-setup`):
  * JDK und Ninja: Termux-Pakete aus dem CodeForge-APT-Repo (`openjdk-17`, `ninja`; dazu `jq`). `p7zip` lädt das Skript
    nur nach, wenn ein `.7z`-Asset auftaucht (die Mirror-Assets sind `.tar.xz`/`.zip`).
  * cmdline-tools (`sdkmanager`): aus dem Mirror-Release **`cmdline-tools-19.0`** (Asset `cmdline-tools-19.0.tar.xz`, SHA-256 fest im Skript;
    einmalig mit dem Workflow `publish-cmdline-tools` aus dem SDK-Archiv 37.0.0 erzeugt). Ist das Release nicht erreichbar, entpackt das Skript
    `cmdline-tools/` aus dem Mirror-Archiv `android-sdk-37.0.0`. Googles cmdline-tools werden nicht mehr geladen. Überschreibbar über
    `CODEFORGE_CMDLINE_REV`, `CODEFORGE_CMDLINE_TOOLS_URL/_SHA256` (oder `sdk.conf`). `platforms;android-N` kommen weiterhin per `sdkmanager` von Google.
  * Die Android-Binaries (build-tools, platform-tools, NDK, CMake) kommen aus dem eigenen Mirror
    **`scto/codeforge-tools`** (Standard, überschreibbar über `CODEFORGE_TOOLS_REPO` bzw. `TOOLS_REPO` in `sdk.conf`;
    leer = Mirror aus). Der Mirror kopiert Releases von HomuHomu833, AndroidIDEOfficial und Lzhiyong, siehe `docs/CODEFORGE-TOOLS.md`:

    | Tag im Mirror | liefert | Upstream |
    |---|---|---|
    | `android-sdk-<v>` | `build-tools;<v>`, `platform-tools;<v>` (ab 36.0.2: 36.0.2, 37.0.0) | HomuHomu833/android-sdk-custom |
    | `android-ndk-<name>` | `ndk;<name>` (r29, r28c, r27d, r26d …) | HomuHomu833/android-ndk-custom |
    | `cmake-<v>` | `cmake;<v>` (3.6.0 … 4.1.2, u. a. 3.22.1 als AGP-Standard) | HomuHomu833/cmake-custom |
    | `androidide-tools-v<v>` | `build-tools;<v>`, `platform-tools;<v>` (33.x/34.x, statisch, vollständig mit Jars) | AndroidIDEOfficial/androidide-tools |
    | `lzhiyong-sdk-tools-<v>` | `build-tools;<v>` (nur dort, wo AndroidIDE keine Version hat, z. B. 35.0.2; nur Binaries über Googles build-tools) | Lzhiyong/android-sdk-tools |

    `cmake;termux` ist das Termux-Paket als Alternative.
  * Reihenfolge je Komponente: Mirror → Upstream-Repos HomuHomu833 direkt → feste Rückfallliste. Doppelte IDs: AndroidIDE
    vor Lzhiyong. Beta-/RC-Releases (`beta`/`rc` im Namen oder Tag) werden nie angeboten.
  * Prüfsumme: GitHubs `digest` des Assets, sonst die `<asset>.sha256` aus dem Mirror-Release; fehlt beides, gibt es eine Warnung.
  * `platform-tools` gibt es im SDK nur einmal: Die installierte Version steht in `platform-tools/.cf-version`, eine neue Installation
    ersetzt den Ordner. In der Oberfläche gilt also nur die zuletzt installierte Version als installiert.
  * Die Listen bildet `codeforge-env list` bei jedem Aufruf aus den GitHub-Releases (bis 300 Releases, 6 Stunden
    zwischengespeichert, neueste zuerst). Ohne Netz gilt eine feste Rückfallliste. Ein eigenes Manifest `$CODEFORGE_SDK_REPO/manifest.txt` (`id|version|kind|source|sha256|dest|extra`)
    ergänzt sie optional. Ohne `GITHUB_TOKEN` gilt das API-Limit von 60 Anfragen pro Stunde.
* SDK-Root: `CODEFORGE_SDK_ROOT` → `ANDROID_SDK_ROOT` aus `$PREFIX/etc/codeforge-environment.properties` (optionale Override-Datei) → `$PREFIX/opt/android-sdk` (= `TermuxEnvironment.sdkRoot`).
* Schreibt `$PREFIX/etc/profile.d/codeforge-android.sh` (ANDROID_HOME, JAVA_HOME,
  ANDROID_NDK_HOME, PATH) und setzt `android.aapt2FromMavenOverride` in `~/.gradle/gradle.properties`.
* Maschinenprotokoll (`--machine`): `CFSDK|ITEM|id|version|0/1|pfad`, `CFSDK|PROGRESS|%|text`,
  `CFSDK|OK|id`, `CFSDK|ERR|text` (Parser: `SdkScriptProtocol`, getestet).

## Als Termux-Paket

Im `terminal-packages-codeforge`-Fork: Paket `codeforge-env` mit `build.sh`, das
`assets/codeforge-env` nach `$TERMUX_PREFIX/bin/codeforge-env` installiert (Shebang/Platzhalter
per `sed`), `TERMUX_PKG_DEPENDS="bash, coreutils, curl, tar, unzip, xz-utils, gawk, grep, sed, openjdk-17"`. Dann ist der
`ScriptInstaller` nur noch Fallback.

## Wichtiges Risiko (ungeprüft)

Ausführen von Dateien aus `files/usr` ist für Apps mit `targetSdk >= 29` per W^X-Regel eingeschränkt.
Termux selbst löst das mit `targetSdkVersion 28`. Prüfen, ob `:app` (aktuell `targetSdk = 35`) die
Termux-Binaries starten darf; Optionen: `targetSdk 28` (Play-Store-Konsequenzen) oder
`termux-exec`/Linker-Wrapper. Betrifft Terminal, SDK-Manager (ProcessBuilder) und Plugins gleichermaßen.
