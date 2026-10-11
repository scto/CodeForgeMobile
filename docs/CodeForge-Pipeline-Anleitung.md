# CodeForge: Bootstrap- & APT-Repo-Pipeline – Einrichtung und Ablauf

Stand: 2026-10-11 · Prefix der App: `com.codeforge.app` · Anleitung für Thomas Schmid (GitHub-Konto `scto`)

## 1. Überblick

Der Workflow `Build Bootstraps & APT-Repo` baut mit dem Termux-Docker-Builder aus dem Quellcode:

- **Bootstrap-ZIPs** (`bootstrap-<arch>.zip` + `.sha256`) mit dem Prefix `/data/data/com.codeforge.app/files/usr`
- ein **signiertes APT-Repository** (`dists/`, `pool/`, `codeforge.gpg`), das per GitHub Pages ausgeliefert wird

Ein nachträgliches `sed com.termux -> com.codeforge.app` ist verboten, weil es ELF-Dateien zerstört. Alles wird deshalb mit dem richtigen Prefix kompiliert (Dauer ca. 1–2 Stunden pro Lauf bei `aarch64,arm`).

## 2. Repositories und URLs

| Zweck | Repo | URL |
|---|---|---|
| App + Workflow + Build-Script | `scto/CodeForgeMobile` | https://github.com/scto/CodeForgeMobile |
| Paketquellen (Fork, Ziel für Release + Pages) | `scto/terminal-packages-codeforge` | https://github.com/scto/terminal-packages-codeforge |
| Öffentliche APT-URL (GitHub Pages) | – | https://scto.github.io/terminal-packages-codeforge |
| APT-Quelle in der Bootstrap-ZIP | – | `deb https://scto.github.io/terminal-packages-codeforge stable main` |

Wichtig:
- `terminal-packages-codeforge` muss **öffentlich** sein. Der Workflow klont es ohne Token, und GitHub Pages geht bei kostenlosen Konten nur mit öffentlichen Repos. Ist es privat, bricht Step 1 ab mit `fatal: could not read Username for 'https://github.com'`.
- Es ist ein **normales Repo, kein Fork von Wadamzmail**. Dessen Prefix ist `dev.mutwakil.androidide`, nicht `com.codeforge.app`.

## 3. Datei im Repo `CodeForgeMobile`

| Datei | Aufgabe |
|---|---|
| `.github/workflows/build-bootstrap-repo.yml` | Workflow (nur manuell startbar) |
| `build_codeforge_repo.sh` | Haupt-Script (9 Schritte) |
| `scripts/setup-gpg-secrets.sh` | erzeugt Schlüssel und setzt die Secrets |
| `scripts/fix-fork.sh` | repariert den Fork einmalig (optional) |

## 4. Fork `terminal-packages-codeforge` einrichten

1. Bei GitHub ein **leeres, öffentliches** Repo `scto/terminal-packages-codeforge` anlegen (ohne README, `.gitignore`, Lizenz).
2. Den reparierten Stand entpacken und pushen:

```bash
mkdir terminal-packages-codeforge
tar xzf terminal-packages-codeforge-repariert.tar.gz -C terminal-packages-codeforge
cd terminal-packages-codeforge
git init -b main
git add -A
git commit -m "Fork repariert (Prefix com.codeforge.app)"
git remote add origin https://github.com/scto/terminal-packages-codeforge.git
git push -u origin main
```

Beim Push fragt Git nach Benutzername und Passwort. Als Passwort dient ein **Personal Access Token** (kein Konto-Passwort). Meldung „No configuration to push to remote“ = es fehlt `git remote add origin …`.

3. Prüfen: `scripts/properties.sh` enthält `TERMUX_APP__PACKAGE_NAME="com.codeforge.app"`. Das prüft auch Step 2 des Builds.

`fix-fork.sh` ist nur nötig, wenn du einen beschädigten Fork reparieren willst (kaputte URLs, verlorene Symlinks, fehlende Ausführbar-Bits). Aufruf im Fork-Root: `bash fix-fork.sh`. `build_codeforge_repo.sh` repariert dasselbe bei jedem Lauf selbst.

## 5. Secrets (Repo `CodeForgeMobile` → Settings → Secrets and variables → Actions)

| Secret | Inhalt | Wann nötig |
|---|---|---|
| `GPG_PRIVATE_KEY_B64` | privater Signaturschlüssel, armored und base64 (einzeilig) | immer |
| `CODEFORGE_GPG_PASSPHRASE` | Passphrase dieses Schlüssels | immer |
| `PACKAGES_REPO_TOKEN` | Token mit Schreibzugriff auf `terminal-packages-codeforge` | nur bei `publish` |

**Token (`PACKAGES_REPO_TOKEN`):**
- *Fine-grained PAT*: Repository access = `scto/terminal-packages-codeforge`, Permission **Contents: Read and write**.
- *Classic PAT*: Scope `repo`.
- GitHub erlaubt keine Secret-Namen mit Präfix `GITHUB_`, deshalb der Name `PACKAGES_REPO_TOKEN`.

**Sicherheit:**
- Passphrase und Token nie in Dateien, Commits, Chats oder Logs schreiben.
- Ist die Passphrase irgendwo gelandet, wo sie nicht hingehört (z. B. in einem Chat), gilt sie als kompromittiert. Dann **vor** dem Ausführen von `setup-gpg-secrets.sh` eine neue Passphrase wählen.
- Wird der Schlüssel neu erzeugt (`FORCE_NEW_KEY=1`), werden alle bisherigen Signaturen ungültig. Neue Bootstraps und ein neues Repo sind dann nötig.
- `.gpg/` und `.gpg/private/` gehören nie ins Git (stehen in der `.gitignore`).

### 5.1 Secrets mit dem Script anlegen

Voraussetzungen auf dem Rechner: `gpg`, `base64`, `gh` (angemeldet mit `gh auth login`).

```bash
# Passphrase und Token nur in der Umgebung, nicht in Dateien im Repo:
export CODEFORGE_GPG_PASSPHRASE='<neue-passphrase>'
export GITHUB_TOKEN='<PAT mit Contents: Read and write>'   # optional, nur für publish

cd CodeForgeMobile
bash scripts/setup-gpg-secrets.sh scto/CodeForgeMobile
```

Das Script (wiederholbar, überschreibt einen vorhandenen Schlüssel **nicht**):
1. prüft vorab, ob der Token Schreibzugriff auf das Ziel-Repo hat,
2. erzeugt in einem temporären `GNUPGHOME` einen RSA-4096-Signaturschlüssel,
3. exportiert ihn, macht einen Round-Trip-Test (Import + Signatur + Prüfung),
4. setzt die drei Secrets über `gh secret set`.

Umgebungsvariablen: `TARGET_REPO` (Standard `scto/terminal-packages-codeforge`), `TOKEN_VAR` (anderer Variablenname für den Token), `FORCE_NEW_KEY=1`, `NO_SECRETS=1` (nur lokal prüfen).

Alternativ von Hand: Secret `GPG_PRIVATE_KEY_B64` = Ausgabe von `gpg --armor --export-secret-keys <FPR> | base64 -w0`.

### 5.2 Vollständiges Script `scripts/setup-gpg-secrets.sh`

```bash
#!/usr/bin/env bash
# Richtet die GitHub-Secrets für den Workflow build-bootstrap-repo.yml ein:
#   GPG_PRIVATE_KEY_B64        privater Signaturschlüssel (armored, base64) – wird EINMAL erzeugt
#   CODEFORGE_GPG_PASSPHRASE   Passphrase des Schlüssels
#   PACKAGES_REPO_TOKEN        Token zum Veröffentlichen (Release + gh-pages) im Ziel-Repo
#
# Quellen (nie ausgegeben, kein `source`):
#   Passphrase:  $CODEFORGE_GPG_PASSPHRASE, sonst letzte Zuweisung in ~/.bashrc
#   Token:       $GITHUB_TOKEN, sonst letzte Zuweisung (GITHUB_TOKEN=...) in ~/.bashrc
#                (andere Variable: TOKEN_VAR=MEIN_NAME)
#
# Hinweis: GitHub erlaubt keine Secret-Namen mit Präfix GITHUB_. Der Token wird deshalb als
# PACKAGES_REPO_TOKEN abgelegt – so heißt er im Workflow.
#
# Aufruf im Projektstamm:   bash scripts/setup-gpg-secrets.sh [owner/repo]
#   Ohne Argument: Repo des aktuellen Verzeichnisses.
# Umgebungsvariablen:
#   TARGET_REPO     Repo für Release/gh-pages (Standard scto/terminal-packages-codeforge)
#   FORCE_NEW_KEY=1 Schlüssel auch dann neu erzeugen, wenn GPG_PRIVATE_KEY_B64 schon existiert
#                   (ungültig machen alle bisherigen Signaturen!)
#   NO_SECRETS=1    nur lokal prüfen, bei GitHub wird nichts gesetzt
#
# Das Script ist wiederholbar: ein vorhandener Schlüssel wird NICHT überschrieben.
set -euo pipefail
umask 077

KEY_NAME="Thomas Schmid"
KEY_EMAIL="tschmid35@gmail.com"
REPO_ARG="${1:-}"
TARGET_REPO="${TARGET_REPO:-scto/terminal-packages-codeforge}"
TOKEN_VAR="${TOKEN_VAR:-GITHUB_TOKEN}"
NO_SECRETS="${NO_SECRETS:-0}"

die() { echo "FEHLER: $*" >&2; exit 1; }

for t in gpg base64; do command -v "$t" >/dev/null 2>&1 || die "'$t' fehlt."; done
[[ "${TOKEN_VAR}" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] || die "Ungültiger TOKEN_VAR."

# Wert einer Variablen: Umgebung, sonst letzte Zuweisung in ~/.bashrc. Gibt nur den Wert aus.
load_var() {
    local name="$1" env_val="${!1:-}" rc="${HOME}/.bashrc" line value
    if [ -n "${env_val}" ]; then printf '%s' "${env_val}"; return 0; fi
    [ -r "$rc" ] || return 1
    line="$(grep -E "^[[:space:]]*(export[[:space:]]+)?${name}=" "$rc" | tail -n1 || true)"
    [ -n "$line" ] || return 1
    value="${line#*=}"
    case "$value" in
        \"*\") value="${value#\"}"; value="${value%\"}" ;;
        \'*\') value="${value#\'}"; value="${value%\'}" ;;
    esac
    [ -n "$value" ] || return 1
    printf '%s' "$value"
}

PASS="$(load_var CODEFORGE_GPG_PASSPHRASE)" || die "CODEFORGE_GPG_PASSPHRASE weder in der Umgebung noch in ~/.bashrc gefunden."
PKG_TOKEN="$(load_var "${TOKEN_VAR}")" || PKG_TOKEN=""

GH_ARGS=()
[ -n "$REPO_ARG" ] && GH_ARGS=(--repo "$REPO_ARG")

if [ "${NO_SECRETS}" != "1" ]; then
    command -v gh >/dev/null 2>&1 || die "'gh' fehlt."
    # Ist gh nicht angemeldet, den Token aus ~/.bashrc für gh verwenden.
    if ! gh auth status >/dev/null 2>&1; then
        [ -n "${PKG_TOKEN}" ] || die "gh ist nicht angemeldet und kein ${TOKEN_VAR} gefunden (gh auth login)."
        export GH_TOKEN="${PKG_TOKEN}"
        gh auth status >/dev/null 2>&1 || die "gh-Anmeldung mit ${TOKEN_VAR} aus ~/.bashrc schlug fehl."
    fi

    # Token vorab prüfen (BEVOR ein Schlüssel erzeugt wird): Er braucht Schreibzugriff auf das Ziel-Repo.
    if [ -n "${PKG_TOKEN}" ]; then
        push_ok="$(GH_TOKEN="${PKG_TOKEN}" gh api "repos/${TARGET_REPO}" --jq '.permissions.push' 2>/dev/null || true)"
        if [ "${push_ok}" != "true" ]; then
            die "${TOKEN_VAR} hat keinen Schreibzugriff auf ${TARGET_REPO} (Fine-grained: Contents = Read and write; classic: Scope 'repo'). Nichts wurde gesetzt."
        fi
        echo "Token aus ${TOKEN_VAR}: Schreibzugriff auf ${TARGET_REPO} bestätigt."
    else
        echo "Hinweis: ${TOKEN_VAR} nicht gefunden – PACKAGES_REPO_TOKEN wird nicht gesetzt (nur für 'publish' nötig)."
    fi
fi

WORK="$(mktemp -d)"
export GNUPGHOME="${WORK}/gnupg"
mkdir -m 700 "${GNUPGHOME}"
trap 'rm -rf "${WORK}"' EXIT
gpg_pass() { printf '%s\n' "${PASS}" | gpg --batch --yes --pinentry-mode loopback --passphrase-fd 0 "$@"; }

# Vorhandenen Schlüssel nicht überschreiben (Signaturen bleiben gültig).
KEY_EXISTS=0
if [ "${NO_SECRETS}" != "1" ] && [ "${FORCE_NEW_KEY:-0}" != "1" ]; then
    if gh secret list "${GH_ARGS[@]}" 2>/dev/null | awk '{print $1}' | grep -qx 'GPG_PRIVATE_KEY_B64'; then
        KEY_EXISTS=1
    fi
fi

if [ "${KEY_EXISTS}" = "1" ]; then
    echo "[Schlüssel] GPG_PRIVATE_KEY_B64 existiert bereits – wird NICHT neu erzeugt (FORCE_NEW_KEY=1 erzwingt das)."
else
    echo "[1/4] Schlüssel erzeugen (temporäres GNUPGHOME, ~/.gnupg bleibt unberührt)..."
    gpg_pass --quick-generate-key "${KEY_NAME} <${KEY_EMAIL}>" rsa4096 sign never 2>/dev/null \
        || die "Schlüssel konnte nicht erzeugt werden."
    FPR="$(gpg --list-secret-keys --with-colons "${KEY_EMAIL}" | awk -F: '/^fpr:/ {print $10; exit}')"
    [ -n "$FPR" ] || die "Fingerprint nicht ermittelbar."
    echo "      Fingerprint: ${FPR}"

    echo "[2/4] Privaten Schlüssel exportieren..."
    gpg_pass --armor --export-secret-keys "${FPR}" > "${WORK}/private.asc"
    grep -q 'BEGIN PGP PRIVATE KEY BLOCK' "${WORK}/private.asc" || die "Export enthält keinen privaten Schlüssel."

    echo "[3/4] Round-Trip-Test (Import in leeres GNUPGHOME + Signatur)..."
    TEST_HOME="${WORK}/gnupg-test"; mkdir -m 700 "${TEST_HOME}"
    GNUPGHOME="${TEST_HOME}" gpg_pass --import "${WORK}/private.asc" 2>/dev/null
    echo "test" > "${WORK}/t.txt"
    GNUPGHOME="${TEST_HOME}" gpg_pass --local-user "${FPR}" --armor --detach-sign -o "${WORK}/t.sig" "${WORK}/t.txt" \
        || die "Test-Signatur mit dem exportierten Schlüssel schlug fehl."
    verify_out="$(GNUPGHOME="${TEST_HOME}" gpg --verify "${WORK}/t.sig" "${WORK}/t.txt" 2>&1)" || die "Test-Signatur ungültig."
    printf '%s' "${verify_out}" | grep -q "Good signature" || die "Test-Signatur ungültig."
    echo "      OK"
    base64 -w0 "${WORK}/private.asc" > "${WORK}/private.b64"
fi

if [ "${NO_SECRETS}" = "1" ]; then
    echo "[4/4] NO_SECRETS=1 – bei GitHub wurde nichts gesetzt."
    exit 0
fi

echo "[4/4] GitHub-Secrets setzen..."
set_secrets=()
if [ "${KEY_EXISTS}" != "1" ]; then
    gh secret set GPG_PRIVATE_KEY_B64 "${GH_ARGS[@]}" < "${WORK}/private.b64"
    set_secrets+=(GPG_PRIVATE_KEY_B64)
fi
printf '%s' "${PASS}" | gh secret set CODEFORGE_GPG_PASSPHRASE "${GH_ARGS[@]}"
set_secrets+=(CODEFORGE_GPG_PASSPHRASE)
if [ -n "${PKG_TOKEN}" ]; then
    printf '%s' "${PKG_TOKEN}" | gh secret set PACKAGES_REPO_TOKEN "${GH_ARGS[@]}"
    set_secrets+=(PACKAGES_REPO_TOKEN)
fi
echo "Gesetzt: ${set_secrets[*]}"
```

## 6. Workflow-Ablauf

Start: Repo `CodeForgeMobile` → Actions → „Build Bootstraps & APT-Repo“ → **Run workflow** (nicht „Re-run jobs“ auf einem alten Lauf).

### 6.1 Eingaben

| Input | Standard | Bedeutung |
|---|---|---|
| `archs` | `aarch64,arm` | Architekturen (`aarch64`, `arm`, `i686`, `x86_64`) |
| `extra_packages` | leer | Pakete **im** Bootstrap (vergrößert die APK) – normalerweise leer |
| `repo_packages` | leer | Pakete **nur im APT-Repo**, z. B. `openjdk-17,git,protobuf,aapt2,wget` |
| `publish` | `false` | Release anlegen und APT-Repo auf `gh-pages` pushen |
| `target_repo` | `scto/terminal-packages-codeforge` | Ziel für Release und `gh-pages` |
| `release_tag` | leer | leer = `bootstrap-JJJJ.MM.TT` (UTC) |
| `apt_url` | leer | leer = `https://scto.github.io/terminal-packages-codeforge` |

Unterpakete wie `protobuf` (gehört zu `libprotobuf`) und `aapt2` (gehört zu `aapt`) werden automatisch über ihr Elternpaket gebaut.

### 6.2 Schritte des Workflows

1. **Eingaben prüfen** (nur erlaubte Zeichen).
2. **Speicherplatz freigeben**, **Abhängigkeiten installieren**.
3. **Secrets prüfen**, privaten Schlüssel nach `.gpg/private/codeforge.gpg` schreiben.
4. **`build_codeforge_repo.sh`** ausführen:
   1. Step 1 – Voraussetzungen, Fork klonen
   2. Step 2 – Prefix prüfen (`properties.sh` muss `com.codeforge.app` enthalten)
   3. Step 3 – GPG-Schlüssel importieren, Prüfsummen
   4. Step 4 – Fork patchen: Keyring (`codeforge.gpg`), apt-Quelle, `termux-tools` (exportiert `TERMUX_APP_PACKAGE`), robustere Downloads (HTTP/1.1, 5 Wiederholungen)
   5. Step 5 – Bootstraps bauen (Docker, **dauert Stunden**) und optional `repo_packages`
   6. Step 6 – Bootstraps verifizieren (kein `com.termux` in Pfaden/Texten/ELF, Keyring, `sources.list`, `SYMLINKS.txt`)
   7. Step 7 – ZIPs + SHA-256 ablegen
   8. Step 8 – APT-Repo erzeugen und signieren (`dists/stable/{Release,Release.gpg,InRelease}`, `pool/`)
   9. Step 9 – Dokumentation (`docs/overview_and_summary.md`)
5. **Zusammenfassung** mit den Einträgen für `gradle.properties`.
6. **Artefakte hochladen** (14 Tage; ohne privaten Schlüssel).
7. Nur bei `publish = true`: **Release anlegen** (`gh release create`) und **APT-Repo auf `gh-pages` pushen** (der Branch wird bei Bedarf angelegt; ein Lauf ersetzt den Snapshot komplett).
8. **Aufräumen** (privater Schlüssel, Docker-Cache).

### 6.3 Ergebnis in `gradle.properties` übernehmen

Die Zusammenfassung des Laufs zeigt Zeilen im Format:

```properties
codeforgeBootstrapVersion=2026.10.11
codeforgeBootstrapSha256.aarch64=<sha256>
codeforgeBootstrapSha256.arm=<sha256>
```

Diese Werte gehören in die `gradle.properties` der App. Die Hashes eines früheren Laufs sind ungültig, sobald neu gebaut wird.

## 7. GitHub Pages einrichten (Repo `terminal-packages-codeforge`)

Pages gehört in `terminal-packages-codeforge`, **nicht** in `CodeForgeMobile`.

1. Den Workflow einmal mit `publish = true` erfolgreich laufen lassen (dabei entsteht der Branch `gh-pages`).
2. Im Repo `terminal-packages-codeforge`: **Settings → Pages**.
3. **Source:** „Deploy from a branch“ · **Branch:** `gh-pages` · **Folder:** `/ (root)` · Save.
4. Nach ein bis zwei Minuten prüfen:
   - https://scto.github.io/terminal-packages-codeforge/dists/stable/Release
   - https://scto.github.io/terminal-packages-codeforge/codeforge.gpg
5. Der Workflow legt `.nojekyll` an, damit Pages die Dateien unverändert ausliefert.

Voraussetzungen: Repo öffentlich; `PACKAGES_REPO_TOKEN` hat Schreibrechte auf `terminal-packages-codeforge`; Input `target_repo` stimmt.

## 8. Reihenfolge – Checkliste

1. [ ] Repo `scto/terminal-packages-codeforge` angelegt, **öffentlich**, reparierter Stand gepusht (Abschnitt 4)
2. [ ] Neue Passphrase gewählt (falls die alte irgendwo offen lag)
3. [ ] Token (PAT) erstellt: Contents = Read and write auf `terminal-packages-codeforge`
4. [ ] `scripts/setup-gpg-secrets.sh` ausgeführt → 3 Secrets in `CodeForgeMobile` (Abschnitt 5)
5. [ ] `build_codeforge_repo.sh` und Workflow in `CodeForgeMobile` committet und gepusht
6. [ ] Workflow mit `publish = true` starten
7. [ ] Pages einschalten (Abschnitt 7)
8. [ ] `gradle.properties` mit Version und SHA-256 aktualisieren (Abschnitt 6.3)

## 9. Fehlerbilder aus den bisherigen Läufen

| Meldung | Ursache | Lösung |
|---|---|---|
| `fatal: could not read Username for 'https://github.com'` (Step 1) | Fork-Repo privat, gelöscht oder falscher Name | Repo öffentlich machen bzw. neu anlegen (Abschnitt 4) |
| `TERMUX_APP__PACKAGE_NAME … ist nicht "com.codeforge.app"` (Step 2) | falscher Fork (z. B. von Wadamzmail) | reparierten Stand pushen |
| `No package protobuf found in any of the enabled repositories` | `protobuf`/`aapt2` sind Unterpakete | behoben: Script baut das Elternpaket |
| `Textdateien mit com.termux` (Step 6) | `termux-tools` ohne exportiertes `TERMUX_APP_PACKAGE` | behoben: Patch in Step 4 |
| `trusted.gpg.d/codeforge.gpg fehlt` (Step 6) | Symlinks stehen nicht als Datei im ZIP | behoben: Prüfung nutzt `share/termux-keyring/` und `SYMLINKS.txt` |
| `path … is not valid … Colon :` (Upload) | Debian-Epoche `1:` im Dateinamen | behoben: Pool-Namen ohne Epoche |
| `curl: (16) nghttp2 … PROTOCOL_ERROR` bei `salsa.debian.org` | gelegentlicher Serverfehler bei HTTP/2 | Downloads über HTTP/1.1 mit 5 Wiederholungen; sonst Lauf neu starten |
| `Secret GPG_PRIVATE_KEY_B64 fehlt` | Secret nicht angelegt | Abschnitt 5 |

## 10. Offene Punkte (nicht geprüft)

- Ob der komplette Lauf mit allen Patches fehlerfrei durchläuft, zeigt erst der nächste Actions-Lauf.
- Ob `pkg install` auf dem Gerät mit dem veröffentlichten Repo funktioniert.
- Ob die Klassen `com.codeforge.app.app.TermuxService` und `TermuxOpenReceiver` in der App existieren (`termux-wake-lock`, `termux-open`).
- Ob `PACKAGES_REPO_TOKEN` die nötigen Rechte hat (prüft `setup-gpg-secrets.sh` vorab).
