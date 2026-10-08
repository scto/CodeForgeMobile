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
