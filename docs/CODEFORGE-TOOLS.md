# codeforge-tools (Mirror der Android-Toolchains)

Eigenes Repo `scto/codeforge-tools` (öffentlich). Ein GitHub-Actions-Workflow spiegelt dort Release-Assets fremder Repos, damit
`codeforge-env` (und damit der SDK-Manager) nicht von fünf Drittanbieter-Repos abhängt. Bedienung, Eingaben und Fehlersuche stehen in
der README des Mirror-Repos; hier steht nur die Anbindung an CodeForgeMobile.

## Quellen und Tags

| Tag im Mirror | Upstream | `codeforge-env` bietet an |
|---|---|---|
| `android-sdk-<v>` | HomuHomu833/android-sdk-custom (Bionic ab 36.0.2) | `build-tools;<v>`, `platform-tools;<v>` |
| `android-ndk-<name>` | HomuHomu833/android-ndk-custom | `ndk;<name>` |
| `cmake-<v>` | HomuHomu833/cmake-custom | `cmake;<v>` |
| `androidide-tools-v<v>` | AndroidIDEOfficial/androidide-tools (archiviert, GPL-3.0) | `build-tools;<v>`, `platform-tools;<v>` (33.x/34.x) |
| `lzhiyong-sdk-tools-<v>` | Lzhiyong/android-sdk-tools | `build-tools;<v>` (nur Versionen, die AndroidIDE nicht hat, z. B. 35.0.2) |

Nicht gespiegelt: Releases mit `beta`/`rc` im Namen oder Tag, glibc-Builds (laufen in Termux nicht), und bei HomuHomu833 die Releases
ohne Android-Variante (35.0.2, 36.0.0: nur musl).

## Anbindung in `codeforge-env`

- `TOOLS_REPO` (Standard `scto/codeforge-tools`; überschreibbar über `CODEFORGE_TOOLS_REPO` oder `sdk.conf`; leer schaltet den Mirror ab).
- Je Komponente: Mirror → HomuHomu833 direkt → feste Rückfallliste. Der Mirror-Tag-Präfix steht im Manifest-Feld `extra` (`tagprefix=…`).
- Neue Typen im Manifest: `ghaidebt` und `ghaidept` (androidide-tools); `lzhiyong-sdk-tools-*` läuft über `ghsdk` (Binaries über Googles build-tools).
- Prüfsumme: `digest` von GitHub, sonst die `<asset>.sha256` des Mirror-Releases.
- Die Version ist der Tag ohne Präfix (`android-ndk-r29` → `ndk;r29`).

## Stand der Prüfung

Das Skript wurde nur mit simulierten Releases (gemockte `curl`/`pkg`) getestet: Liste, Installation je Quelle, Prüfsummenfehler,
Rückfall bei fehlendem Mirror, `setup`. Ein echter Lauf gegen GitHub, das Layout der echten Archive (NDK, CMake) und der Start der
Binaries auf dem Gerät sind nicht geprüft.

## Architekturen

`codeforge-env` und `setup-termux-android.sh` wählen die Asset-Variante nach `uname -m`:

| `uname -m` | sdk / ndk / cmake | aide / lzhi |
|---|---|---|
| `aarch64` | `*-aarch64-linux-android` | `aarch64` |
| `armv7l`, `armv8l` (32-bit-Userland) | `*-armv7a-linux-androideabi` | `arm` |

Überschreibbar mit `ASSET_VARIANT` / `AIDE_VARIANT`. Der Mirror spiegelt standardmäßig beide Varianten. `agy` (Antigravity CLI) läuft nur auf aarch64 und wird auf 32-bit-ARM übersprungen.

## cmdline-tools aus dem Mirror

`codeforge-env` installiert `cmdline-tools;latest` aus dem Mirror-Release `cmdline-tools-<Rev>` (Standard `19.0`, Asset `cmdline-tools-19.0.tar.xz`,
SHA-256 fest im Skript). Das Release erzeugt einmalig der Workflow `publish-cmdline-tools` in `scto/codeforge-tools` aus dem Archiv `android-sdk-37.0.0`.
Fehlt das Release, entpackt `codeforge-env` `cmdline-tools/` aus `android-sdk-37.0.0` (Mirror). Überschreiben: `CODEFORGE_CMDLINE_REV`,
`CODEFORGE_CMDLINE_TOOLS_URL`, `CODEFORGE_CMDLINE_TOOLS_SHA256`.
