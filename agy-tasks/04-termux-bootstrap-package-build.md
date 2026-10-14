> **ERLEDIGT (Stand 2026-10-14):** Release `bootstrap-2026.10.11` ist eingebunden (Commit `a2edcaf`, `gradle.properties`; Bericht `docs/bootstrap-package-build.md`). Der frühere Release `2026.10.08` war defekt und ist ersetzt. Das Paketangebot im APT-Repo wächst über die Etappen-Pipeline (Task 21); der Bootstrap selbst bleibt unverändert.

# Auftrag: Eigenen Bootstrap-Release (Prefix com.codeforge.app) einbinden

## Kontext

Repo: CodeForgeMobile (Android), Arbeitsverzeichnis: Projekt-Root. **Voraussetzung:** Dein eigener
`terminal-packages-codeforge`-Repo (GitHub) besitzt den Release `bootstrap-2026.10.11` mit den Bootstrap-Zips
für `aarch64` und `arm` (je mit `.sha256`). Das Bauen des Bootstraps ist NICHT Teil dieses Auftrags
(separater Workflow `build-bootstrap-repo.yml`, siehe `build_script_summary.md`).

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
   - `codeforgeBootstrapUrlTemplate=https://github.com/scto/terminal-packages-codeforge/releases/download/bootstrap-%1$s/bootstrap-%2$s.zip`
     (`%1$s` = Version, `%2$s` = ABI; passt zu den Asset-Namen `bootstrap-aarch64.zip`, `bootstrap-arm.zip`)
   - `codeforgeBootstrapVersion=2026.10.11`
   - `codeforgeBootstrapSha256.aarch64=05bb4a96a74e955333cc96139f6d05df394bfa8f2b18d29a29bb0f6b4b914279`
   - `codeforgeBootstrapSha256.arm=2ba9e7e5e0d129776a80e70da115fc6bf258105a008be07fc5087eda30111ad0`

   Diese Werte stammen aus dem Build-Log (Step 7). **Vergleiche sie vor dem Eintragen** mit den
   `.sha256`-Dateien des Releases (`bootstrap-aarch64.zip.sha256`, `bootstrap-arm.zip.sha256`) bzw. mit
   `sha256sum` der heruntergeladenen Zips. Weichen sie ab, gelten die Werte der Release-Assets; melde die
   Abweichung, statt sie stillschweigend zu übernehmen.
2. **x86_64:** Das Plugin `TerminalBootstrapPackagesPlugin` erwartet alle drei ABIs, der Release hat nur
   `aarch64` und `arm`. Der bisherige Eintrag `codeforgeBootstrapSha256.x86_64` gehört zum defekten Release
   `2026.10.08` und muss weg. Standard-Vorgehen (Zielgerät Pixel 9 Pro XL = `aarch64`): `BOOTSTRAP_PACKAGES`
   im Plugin auf `aarch64` und `arm` kürzen, den `#elif defined __x86_64__`-Zweig entfernen und die
   `x86_64`-Property löschen (nicht mit falscher Prüfsumme „durchwinken“). Folge: Auf einem x86_64-Emulator
   läuft das Terminal nicht. Wird x86_64 gebraucht, stattdessen den Workflow mit `archs = aarch64,arm,x86_64`
   neu starten und diesen Auftrag mit den neuen Werten wiederholen; **frage vor dieser Entscheidung nach**.
3. `./gradlew :libs:termux-app:embedTerminalBootstrap` ausführen (oder `:app:assembleDebug`, der Task
   hängt an `preBuild`). `libs/termux-app/src/main/cpp/termux-bootstrap-zip.S` muss danach den
   `.incbin`-Blob enthalten (nicht mehr der leere Platzhalter).

## Akzeptanzkriterien

- Kein Checksum-Mismatch; `./gradlew :app:assembleDebug` baut mit eingebettetem Bootstrap (nur `aarch64` und `arm`, wenn nicht anders entschieden).
- `gradle.properties` enthält keine Werte des defekten Releases `2026.10.08` mehr.
- Auf dem Gerät: `TermuxInstaller` extrahiert nach `/data/data/com.codeforge.app/files/usr` und
  `$PREFIX/bin/sh` startet (Onboarding-Schritt, siehe `libs/terminal-engine/BOOTSTRAP.md`).
- Bei Mismatch NICHT die Prüfsumme anpassen, bevor geklärt ist, ob der Release-Asset geändert wurde.
