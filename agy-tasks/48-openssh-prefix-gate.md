# Task 48 – openssh fehlt im APT-Repo: `com.termux` im Service-Skript, und der grüne Lauf verschweigt es

**Ziel:** `openssh` (Stage 06-dev-core, Gruppe B-leicht-dev) wird für aarch64 und arm gebaut und ins Repo aufgenommen. Wird ein Paket vom Prefix-Check verworfen, ist das in Job-Status und Zusammenfassung sichtbar.
**Warum jetzt:** Lauf 39593711451 (2026-10-21, Gruppe B-leicht-dev, build_add aarch64 und arm): openssh 10.5p1 baut, der Check in `build_codeforge_repo.sh` (~Zeile 705) verwirft die .deb, die Jobs bleiben grün, openssh fehlt im Repo.

**Gemeinsame Regeln:** wie Task 46 (Clean Architecture entfällt hier; Shell mit `set -euo pipefail`, shellcheck-sauber). Nichts erfinden: was nicht selbst ausgeführt wurde, steht im Bericht unter **nicht getestet**. Geheimnisse (GPG-Passphrase, Tokens) nie ins Log, nie in Dateien, nie in Berichte. Keine Pushes ohne Auftrag des Inhabers. Bericht in `docs/openssh-prefix-report.md` (getestet / nicht getestet / offene Punkte); `agy-tasks/TASKS-STATUS.md` und `README.md` ergänzen.

## 48.1 Befund (erst selbst prüfen)
Log, beide Architekturen identisch:
```
FEHLER: output/openssh_10.5p1_<arch>.deb enthält 'com.termux':
  .../data/data/com.codeforge.app/files/usr/var/service/ssh-agent/run
--- Fundstelle: #   unix_listener: cannot bind to path /data/data/com.termux/files/usr/var/run/ssh-agent.socket: Address already in use
FEHLGESCHLAGEN: openssh: .deb enthält 'com.termux' (siehe oben).
```
* Einziger Treffer ist ein **Kommentar** im runit-Skript `ssh-agent/run`, das `packages/openssh/build.sh` im Fork erzeugt (fest eingetragener Pfad, nicht `@TERMUX_PREFIX@`).
* Prüfe: `grep -rn "com.termux" packages/openssh/` im Fork; Fundstelle(n) und ob weitere (z. B. `sshd`-Service, `sshd_config`) betroffen sind. Im Log war nur eine Datei gelistet (`head -3`).
* Prüfe, ob Upstream den Pfad bereits geändert hat (nicht raten, Quelle nennen).

## 48.2 Fix im Rezept
* In `packages/openssh/build.sh` den Pfad prefix-neutral machen (`${TERMUX_PREFIX}` bzw. `@TERMUX_PREFIX@`), **nicht** in `build_codeforge_repo.sh` global ersetzen: der Check bleibt streng für alle anderen Pakete.
* Falls das Skript per Heredoc mit festem Text entsteht: Variablen-Expansion sicherstellen, Anführungszeichen prüfen.
* Nach dem Bau: `dpkg-deb -x` und `grep -rla 'com\.termux'` muss leer sein; `ssh-agent/run` enthält den Prefix `/data/data/com.codeforge.app/files/usr`.

## 48.3 Sichtbarkeit verworfener Pakete
* `FAILED_PKGS` (aus `build_codeforge_repo.sh`) am Ende des Laufs ausgeben und als Datei `failed-packages.txt` in die Artefakte legen.
* Bei `keep_going=true` und nicht leerer Liste: GitHub-Annotation `::warning::` und Eintrag in der Zusammenfassung (`$GITHUB_STEP_SUMMARY`) mit Paket und Architektur. Neuer Workflow-Input `fail_on_skipped` (Standard `false`, nach Wunsch `true`): dann endet der Lauf mit Fehlerstatus.
* `build-all-stages.yml`: Zusammenfassungs-Job listet pro Gruppe die verworfenen Pakete; Status nicht mehr nur "success".
* Ausgabe der Fundstelle (heute `head -3`) auf alle Treffer bis maximal 10 erweitern.

## 48.4 Verifikation
* Lauf: `build-bootstrap-repo.yml`, mode `add`, `repo_packages=openssh`, archs `aarch64,arm`, `publish=true` (oder `build-all-stages.yml`, `only=B-leicht-dev`).
* Danach (Gerät, nur durch den Inhaber): `apt update && apt-cache policy openssh`, `pkg install openssh`, `ssh -V`, `sv`-Dienst ssh-agent.
* `scripts/check-apt-consistency.py` (Task 45) um die 71 Pakete laufen lassen: openssh darf nicht mehr fehlen.
* Mit absichtlich verseuchtem Paket (Testrezept) prüfen, dass Warnung und `failed-packages.txt` erscheinen.

## 48.5 Commit
Vorschlag: `fix(openssh): prefix-neutral service script; report skipped packages`, Zeilen am Ende:
```
Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01Lf4ojCvX11BeECzDFXhL7X
```
