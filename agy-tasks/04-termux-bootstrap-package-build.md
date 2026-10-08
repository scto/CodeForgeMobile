# Auftrag: Eigenen Bootstrap-Release (Prefix com.codeforge.app) einbinden

## Kontext

Repo: CodeForgeMobile (Android), Arbeitsverzeichnis: Projekt-Root. **Voraussetzung:** Dein eigener
`terminal-packages`-Fork (GitHub) besitzt mindestens einen Release mit gebauten Bootstrap-Zips für
`aarch64`, `arm` (optional `x86_64`). Das Bauen des Forks ist NICHT Teil dieses Auftrags.

Lies zuerst `docs/sub/TERMUX-PORTING.md` (Abschnitte „Native Bootstrap-Einbettung“ und „2. Bootstrap-Zip selbst
bauen“), `build-logic/convention/src/main/kotlin/TerminalBootstrapPackagesPlugin.kt` (KDoc listet alle
Properties) und `docs/build-logic.md`.

**Kritische Randbedingung (siehe ADR-5):** Die Pakete MÜSSEN mit Präfix
`/data/data/com.codeforge.app/files/usr` gebaut sein (`.app` am Ende = `applicationId` von `:app`).
Die Plugin-Standardquelle (AndroidIDE `bootstrap-16.12.2023`) hat Präfix
`/data/data/com.itsaky.androidide/files/usr` und ist dafür NICHT geeignet. Baut dein Fork mit einem
anderen Präfix, ist das ein Fehler im Fork – weise darauf hin, statt hier zu „reparieren“.

## Aufgabe

1. In `gradle.properties` (Projekt-Root) setzen:
   - `codeforgeBootstrapUrlTemplate=https://github.com/<org>/<repo>/releases/download/bootstrap-%1$s/bootstrap-%2$s.zip`
     (`%1$s` = Version, `%2$s` = ABI; Dateiname ggf. anpassen, z. B. `bootstrap-release-%2$s.zip`)
   - `codeforgeBootstrapVersion=<release-tag-suffix>`
   - `codeforgeBootstrapSha256.aarch64=<hex>`, `codeforgeBootstrapSha256.arm=<hex>`,
     `codeforgeBootstrapSha256.x86_64=<hex>` (`sha256sum <datei>`, lowercase)
2. Falls dein Fork kein `x86_64` liefert: das Plugin erwartet alle drei ABIs – in dem Fall
   `BOOTSTRAP_PACKAGES` im Plugin um die fehlende ABI kürzen und den `#elif defined __x86_64__`-Zweig
   entfernen (nicht mit falscher Prüfsumme „durchwinken“).
3. `./gradlew :libs:termux-app:embedTerminalBootstrap` ausführen (oder `:app:assembleDebug`, der Task
   hängt an `preBuild`). `libs/termux-app/src/main/cpp/termux-bootstrap-zip.S` muss danach den
   `.incbin`-Blob enthalten (nicht mehr der leere Platzhalter).

## Akzeptanzkriterien

- Kein Checksum-Mismatch; `./gradlew :app:assembleDebug` baut mit eingebettetem Bootstrap.
- Auf dem Gerät: `TermuxInstaller` extrahiert nach `/data/data/com.codeforge.app/files/usr` und
  `$PREFIX/bin/sh` startet (Onboarding-Schritt, siehe `libs/terminal-engine/BOOTSTRAP.md`).
- Bei Mismatch NICHT die Prüfsumme anpassen, bevor geklärt ist, ob der Release-Asset geändert wurde.
