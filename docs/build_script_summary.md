# Build Script Übersicht

Stand: 2026-10-11 · Datei: `build_codeforge_repo.sh` (Repo `CodeForgeMobile`)

Das Script baut mit dem Termux-Docker-Builder aus dem Quellcode Bootstraps und ein signiertes APT-Repository für den Prefix `/data/data/com.codeforge.app/files/usr`. Es läuft auf einem x86_64-Linux-Rechner mit Docker, in der Regel im Workflow `build-bootstrap-repo.yml` (manuell gestartet). Auf dem Telefon läuft es nicht.

## Grundsatz

Binaries werden **nicht nachträglich** umgeschrieben. Ein `sed com.termux -> com.codeforge.app` über fertige Dateien würde ELF-Dateien zerstören (andere Länge, verschobene Offsets). Alles wird deshalb mit dem richtigen Prefix kompiliert. Das frühere Einschleusen eines Anpassungs-Scripts in `generate-bootstraps.sh` gibt es nicht mehr.

## Ablauf (9 Schritte)

| Schritt | Inhalt |
|---|---|
| 1 | Werkzeuge prüfen (Docker, git, gpg, curl, zip, jq, python3 …), Fork `scto/terminal-packages-codeforge` flach klonen, `.gitignore` idempotent ergänzen |
| 2 | Prefix prüfen: `scripts/properties.sh` muss `TERMUX_APP__PACKAGE_NAME="com.codeforge.app"` enthalten; `TERMUX_REPO_*` bleibt `com.termux` (zeigt auf das offizielle Repo). Danach Reparatur des beschädigten Forks: kaputte URLs, verlorene Ausführbar-Bits, verlorene Symlinks |
| 3 | GPG: vorhandenen privaten Schlüssel importieren (im Workflow aus dem Secret `GPG_PRIVATE_KEY_B64`), sonst neuen RSA-4096-Schlüssel erzeugen; öffentliche Schlüssel (`.gpg` binär, `.asc`) und Prüfsummen schreiben |
| 4 | Fork patchen (idempotent): Keyring (`codeforge.gpg`), apt-Quelle auf `https://scto.github.io/terminal-packages-codeforge`, GPG-Block in `build-package.sh`, **`termux-tools` exportiert `TERMUX_APP_PACKAGE` und die Pfade** (sonst landet `com.termux` in `login`, `pkg`, `termux-*`), Downloads über HTTP/1.1 mit 5 Wiederholungen |
| 5 | Bootstraps bauen (`build-bootstraps.sh` im Docker, dauert Stunden); optional `REPO_PACKAGES` einzeln pro Architektur, Unterpakete (`protobuf`, `aapt2`) über ihr Elternpaket; jedes Paket wird auf `com.termux` geprüft |
| 6 | Bootstraps streng verifizieren (siehe unten) |
| 7 | ZIPs nach `output/bootstraps/` legen, SHA-256-Dateien schreiben |
| 8 | APT-Repository: Pool (Dateinamen ohne Debian-Epoche, z. B. `ca-certificates_2026.09.25_all.deb`), `Packages`, `Release`, `Release.gpg`, `InRelease`; eigene Signaturen und Prüfsummen aller Pool-Dateien werden kontrolliert; `codeforge.gpg`, `codeforge.asc`, `.nojekyll`, `index.html` |
| 9 | Zusammenfassung nach `docs/overview_and_summary.md` |

## GPG und Geheimnisse

- Die Passphrase kommt aus `CODEFORGE_GPG_PASSPHRASE` (im Workflow ein Secret), sonst aus der letzten Zuweisung in `~/.bashrc`. Sie wird nie ausgegeben und nur über Pipes an `gpg` übergeben.
- Der private Schlüssel liegt nur in `.gpg/private/` und wird nie eingecheckt oder hochgeladen. Das Aufräumen am Ende des Workflows löscht ihn.
- Das Script `scripts/setup-gpg-secrets.sh` legt die Secrets bei GitHub an (siehe `CodeForge-Pipeline-Anleitung.md`).

## Verifikation (Step 6)

Der Lauf bricht ab, wenn eines davon zutrifft:
- ein Pfad mit `com.termux`
- eine Textdatei mit `com.termux` – erlaubt sind **nur** Kommentarzeilen und die Java-Klasse `com.termux.termuxam.Am` (sie steckt in der `am.apk`); die Treffer-Zeilen stehen im Log
- eine ELF-Datei mit `com.termux` oder mit defekten Headern (`readelf`)
- `etc/apt/sources.list` zeigt nicht auf die CodeForge-URL
- `share/termux-keyring/codeforge.gpg` fehlt oder der Link `etc/apt/trusted.gpg.d/codeforge.gpg` fehlt in `SYMLINKS.txt` (Symlinks stehen im ZIP nur in `SYMLINKS.txt`, nicht als Datei)
- `SYMLINKS.txt` enthält den Prefix nicht

## Konfiguration über Umgebungsvariablen

| Variable | Bedeutung |
|---|---|
| `ARCHS` | Architekturen, Standard `aarch64 arm i686 x86_64` (Workflow-Standard `aarch64,arm`) |
| `EXTRA_PACKAGES` | Pakete **im** Bootstrap (vergrößert die APK), normalerweise leer |
| `REPO_PACKAGES` | Pakete **nur im APT-Repo**, z. B. `openjdk-17,git,protobuf,aapt2,wget` |
| `CODEFORGE_APT_URL` | öffentliche URL des APT-Repos (Standard `https://scto.github.io/terminal-packages-codeforge`) |
| `CODEFORGE_FORK_URL` | Quelle der Paketdefinitionen (Standard `https://github.com/scto/terminal-packages-codeforge`) |
| `CODEFORGE_GPG_PASSPHRASE` | Passphrase des Schlüssels |

## Ausführung

Normalfall: Workflow `Build Bootstraps & APT-Repo` in `CodeForgeMobile` starten (Actions → Run workflow). Lokal auf einem x86_64-Linux-Rechner mit Docker:

```bash
chmod +x build_codeforge_repo.sh
CODEFORGE_GPG_PASSPHRASE='…' ./build_codeforge_repo.sh   # dauert Stunden
```

Ergebnis: `output/bootstraps/`, `github_repo_ready/` (APT-Repo für GitHub Pages), `.gpg/` (öffentlich) und `docs/overview_and_summary.md`.

## Offene Punkte (nicht geprüft)

- Ein vollständiger Lauf mit allen aktuellen Patches ist noch nicht bestätigt (zuletzt scheiterte der Download von `apt` bei `salsa.debian.org`; seitdem HTTP/1.1 und mehr Wiederholungen).
- Geplante Umstellung: statt eines Forks direkt `termux/termux-packages` an einem festen Tag klonen und `scripts/apply-codeforge-properties.sh` anwenden (siehe `CodeForge-Upstream-Update-Anleitung.md`). Im Script ist das noch **nicht** umgesetzt.
- Ob die Klassen `com.codeforge.app.app.TermuxService` und `TermuxOpenReceiver` in der App existieren, ist offen.
