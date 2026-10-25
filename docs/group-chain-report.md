# Task 49 Bericht: Gruppenkette, Reihenfolge, Abbruch & ELF-Prefix-Ausnahmen

## 1. Befund (49.1)
- **Lauf 108064644844 (2026-10-22/23):**
  - Gruppe `A-leicht-jvm` scheiterte im Zusammenbau an der APT-Konsistenzprüfung (19 Fehler):
    - `openjdk-17` und `openjdk-21` nicht erfüllbar (ant, apksigner, d8, kotlin, maven, codeforge-env), da die Heavy-Gruppen `H1-jdk17` und `H2-jdk21` noch nie zuvor liefen.
    - `m4` nicht erfüllbar (autoconf): stand ursprünglich in keiner Stage.
    - `jq` (codeforge-env) und `nodejs`/`nodejs-lts` (yarn): lagen erst in den nachfolgenden Gruppen B bzw. E.
  - **Kette lief trotz Fehler weiter:** `stop_on_error` war standardmäßig auf `false` gesetzt. Gruppen B bis F wurden gebaut, zusammengebaut und mit `git push --force origin gh-pages` veröffentlicht. Dadurch fehlten die 12 neuen Pakete aus Gruppe A im veröffentlichten Repo.
  - **Verworfene Pakete bei grünen Jobs (`keep_going`):**
    - `openssh` (B, aarch64+arm): Kommentar im runit-Skript (in Task 48 behoben).
    - `taplo` (D, aarch64+arm): Zertifikatspfade im ELF-Binary (`.../data/data/com.termux/files/usr/etc/tls`).
    - `shellcheck` (D aarch64: GHC-RTS-Pfade im ELF-Binary; D arm: Cabal-Download `splitmix` HTTP 403 vom Hackage-Mirror).
  - Da `com.codeforge.app` länger als `com.termux` ist, ist ein binäres Ersetzen im fertigen ELF nicht ohne Puffer möglich.

## 2. Reihenfolge und Abbruch (49.2)
- **Reihenfolge nach Abhängigkeiten:**
  - `apt-groups.json` wurde so geordnet, dass vorbereitende Gruppen vor ihren Konsumenten laufen:
    1. `B-leicht-dev`: liefert `m4`, `make`, `jq` und Entwicklerwerkzeuge.
    2. `E-leicht-py-node`: liefert `python` (für `gdb` in C) und `nodejs`/`npm` (für `yarn` in A und `pnpm` in D).
    3. `C-leicht-android`: Android-Tools und `gdb`.
    4. `D-leicht-lsp`: LSP-Tools, `pnpm`, `taplo`, `shellcheck`.
    5. `F-leicht-utils`: Hilfswerkzeuge.
    6. `A-leicht-jvm`: `gradle`, `kotlin`, `maven`, `codeforge-env`, `yarn`, `autoconf`, `automake`.
- **Prüfskript `scripts/check-group-order.py`:**
  - Implementiert mit Standardbibliothek (`python3 -I`).
  - Liest `apt-groups.json`, `apt-stages.conf` und `TERMUX_PKG_DEPENDS` aus den Fork-Rezepten.
  - Bestätigt, dass alle leichten Gruppen nach der Neuordnung aufeinander abgestimmt sind.
- **Abbruch:**
  - In `.github/workflows/build-all-stages.yml` ist der Standard von `stop_on_error` nun `true` (`default: true`).
  - Dadurch bricht die GitHub Actions Matrix bei einem Fehler sofort ab (`fail-fast: true`), nachfolgende Gruppen laufen nicht an, und es wird kein unvollständiger Stand veröffentlicht.
- **Nachhol-Lauf:**
  - Einzelne Gruppen (z. B. `A-leicht-jvm` nach Fertigstellung von `H1-jdk17` / `H2-jdk21`) können gezielt nachgeholt werden mit `build-all-stages.yml` (`only=A-leicht-jvm`, `mode=add`, `publish=true`) oder `build-bootstrap-repo.yml` (`stage=04-jvm-tools`, `mode=add`, `publish=true`).
  - Der Modus `add` verändert bereits im Pool liegende Pakete nicht.

## 3. Ausnahmeliste für eingebaute Pfade in ELF-Dateien (49.3)
- **Konfigurationsdatei `prefix-exceptions.conf`:**
  - Format: `paket|Dateiglob|Begründung`.
  - Einträge:
    - `taplo|usr/bin/taplo|...` (Fallback-Zertifikatspfade der Rust-Crate `rustls-native-certs`).
    - `shellcheck|usr/bin/shellcheck|...` (GHC-RTS-Rückfallpfade).
- **Prüfung in `build_codeforge_repo.sh` (`is_prefix_exception`):**
  - Eine Ausnahme wird nur gewährt, wenn:
    1. Das Paket in `prefix-exceptions.conf` gelistet ist,
    2. Die Datei ein echtes ELF-Binary ist (Magic `\x7fELF`),
    3. Der relative Dateipfad auf das deklarierte Glob passt.
  - Alle anderen Fundstellen (z. B. Textdateien wie in `openssh`, nicht gelistete Pakete oder andere Dateien) führen weiterhin zum Verwerfen des Pakets.
  - Bei einer Ausnahme wird `AUSNAHME: <Paket>: <Datei> enthält com.termux (siehe prefix-exceptions.conf)` protokolliert und in `$GITHUB_STEP_SUMMARY` vermerkt; das Paket wird ins Repo aufgenommen.
- **Laufzeit-Wirkung:**
  - `taplo`: Die eingebauten Pfade dienen nur als Rückfall für TLS-Zertifikate bei Online-Schema-Downloads. Durch das Setzen von `SSL_CERT_FILE` auf den tatsächlichen Zertifikatspfad im Prefix (`/data/data/com.codeforge.app/files/usr/etc/tls/cert.pem`) wird der eingebaute Pfad zur Laufzeit umgangen.
  - `shellcheck`: Die GHC-RTS-Pfade sind interne Standardwerte, die nur greifen, wenn keine Umgebungsvariablen (`HOME`, `PATH`) übergeben werden. In der CodeForge-Umgebung sind diese immer gesetzt.
- **Test-Suite `scripts/test_prefix_exceptions.sh`:**
  - 5 automatisierte Testfälle prüfen ELF-Ausnahmen, Ablehnung von Textdateien, Ablehnung unbekannter Pakete und Ablehnung nicht deklarierter Pfade. Alle 5 Tests erfolgreich.

## 4. Netzwerk-Wiederholungen beim Bauen (49.5)
- In `build_codeforge_repo.sh` wurde für `build-package.sh` eine Wiederholungsschleife mit bis zu 3 Versuchen und 15s Wartezeit eingebaut.
- Fängt vorübergehende HTTP-403-/Netzwerkfehler (wie beim Cabal-Download von `splitmix` für `shellcheck` auf arm) automatisch ab.

## 5. Getestet / Nicht getestet / Offene Punkte

### Getestet
- `scripts/check-group-order.py` mit `python3 -I` ausgeführt (Reihenfolge verifiziert).
- `scripts/test_prefix_exceptions.sh` (alle 5 Fälle erfolgreich bestanden).
- `build_codeforge_repo.sh` mit `bash -n` und `shellcheck` geprüft.
- `actionlint` auf `.github/workflows/build-all-stages.yml` und `.github/workflows/build-bootstrap-repo.yml` erfolgreich ausgeführt.

### Nicht getestet
- Vollständiger Kettendurchlauf über GitHub Actions Runner.
- Physischer Test der gebauten Binaries `taplo` und `shellcheck` auf Pixel 9 Pro XL (durch Inhaber).

### Offene Punkte
- Nach dem Merge: Lauf der Heavy-Gruppen `H1-jdk17` und `H2-jdk21`, danach `only=A-leicht-jvm, mode=add` zur Bereitstellung der JVM-Werkzeuge.
