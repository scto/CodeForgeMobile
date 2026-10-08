# Termux-Umgebung (ersetzt PRoot/Rootfs/Multi-Distro)

**Stand:** PRoot, Rootfs-Download, `DistroBootstrapRepository` und die Distro-Auswahl sind
entfernt. Es gibt genau eine Umgebung: den Termux-Bootstrap unter
`/data/data/com.codeforge.app/files/usr`.

## Ablauf

1. **Onboarding → Setup:** `TermuxInstaller.setupBootstrapIfNeeded(activity)` (`:libs:termux-app`)
   entpackt den eingebetteten Bootstrap. Er enthält die Pflichtpakete (u. a. `openjdk-17`) und
   das Paket **`codeforge-env`** (Skript, Quelle `assets/codeforge-env`).
2. **Skript sicherstellen:** `ScriptInstaller` kopiert `assets/codeforge-env` nach
   `$PREFIX/bin`, falls es nicht als Paket im Bootstrap liegt (Platzhalter `@TERMUX_PREFIX@` etc.
   werden ersetzt).
3. **Terminal öffnet sich** (Route `terminal?initialCommand=…`) und führt
   `codeforge-env setup --jdk 17|21 --ndk 27d|30b --cmake 4.3.0` sichtbar aus.
4. **SDK-Manager** (`:feature:sdkmanager`) ist nur noch die grafische Oberfläche über
   `codeforge-env --machine list|install|uninstall` (`TermuxScriptSdkRepository`).

## Skript `codeforge-env`

* JDKs: Termux-Pakete `openjdk-17` / `openjdk-21` (`$PREFIX/lib/jvm/java-N-openjdk`).
* Android-spezifische Teile (cmdline-tools, platform-tools, build-tools mit aapt2, NDK 27d/30b,
  CMake 4.3.0): **eigene Builds**, beschrieben durch ein Manifest
  `$CODEFORGE_SDK_REPO/manifest.txt` (`id|version|kind|source|sha256|dest|extra`).
* Repo-URL: `CODEFORGE_SDK_REPO` in `$PREFIX/etc/codeforge/sdk.conf` (oder beim Paket-Build den
  Platzhalter `@CODEFORGE_SDK_REPO@` ersetzen). Ohne Konfiguration zeigt `list` nur die JDKs.
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
