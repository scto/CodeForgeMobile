#!/usr/bin/env bash
# Erzeugt EINMAL den CodeForge-Signaturschlüssel in einem temporären GNUPGHOME und hinterlegt
# ihn als GitHub-Secrets für den Workflow build-bootstrap-repo.yml.
#
# Voraussetzungen: gpg, gh (angemeldet: `gh auth login`), base64.
# Die Passphrase kommt aus $CODEFORGE_GPG_PASSPHRASE oder der letzten Zuweisung in ~/.bashrc
# (kein `source`; der Wert wird nie ausgegeben).
#
# Aufruf im Repo-Verzeichnis:   bash scripts/setup-gpg-secrets.sh [owner/repo]
# Ohne Argument wird das Repo des aktuellen Verzeichnisses verwendet.
# NO_SECRETS=1 bash scripts/setup-gpg-secrets.sh   -> nur lokal prüfen, nichts bei GitHub setzen.
set -euo pipefail
umask 077

KEY_NAME="Thomas Schmid"
KEY_EMAIL="tschmid35@gmail.com"
REPO_ARG="${1:-}"

die() { echo "FEHLER: $*" >&2; exit 1; }

for t in gpg base64; do command -v "$t" >/dev/null 2>&1 || die "'$t' fehlt."; done
if [ "${NO_SECRETS:-0}" != "1" ]; then
    command -v gh >/dev/null 2>&1 || die "'gh' fehlt."
    gh auth status >/dev/null 2>&1 || die "gh ist nicht angemeldet (gh auth login)."
fi

load_pass() {
    if [ -n "${CODEFORGE_GPG_PASSPHRASE:-}" ]; then printf '%s' "${CODEFORGE_GPG_PASSPHRASE}"; return 0; fi
    local rc="${HOME}/.bashrc" line value
    [ -r "$rc" ] || die "$rc nicht lesbar und CODEFORGE_GPG_PASSPHRASE nicht gesetzt."
    line="$(grep -E '^[[:space:]]*(export[[:space:]]+)?CODEFORGE_GPG_PASSPHRASE=' "$rc" | tail -n1 || true)"
    [ -n "$line" ] || die "CODEFORGE_GPG_PASSPHRASE steht nicht in $rc."
    value="${line#*=}"
    case "$value" in
        \"*\") value="${value#\"}"; value="${value%\"}" ;;
        \'*\') value="${value#\'}"; value="${value%\'}" ;;
    esac
    [ -n "$value" ] || die "CODEFORGE_GPG_PASSPHRASE in $rc ist leer."
    printf '%s' "$value"
}
PASS="$(load_pass)"

WORK="$(mktemp -d)"
export GNUPGHOME="${WORK}/gnupg"
mkdir -m 700 "${GNUPGHOME}"
trap 'rm -rf "${WORK}"' EXIT

gpg_pass() { printf '%s\n' "${PASS}" | gpg --batch --yes --pinentry-mode loopback --passphrase-fd 0 "$@"; }

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
    || die "Test-Signatur mit dem exportierten Schlüssel schlug fehl (falsche Passphrase?)."
GNUPGHOME="${TEST_HOME}" gpg --verify "${WORK}/t.sig" "${WORK}/t.txt" 2>&1 | grep -q "Good signature" \
    || die "Test-Signatur ungültig."
echo "      OK"

base64 -w0 "${WORK}/private.asc" > "${WORK}/private.b64"

if [ "${NO_SECRETS:-0}" = "1" ]; then
    echo "[4/4] NO_SECRETS=1 – es wurde nichts bei GitHub gesetzt."
    exit 0
fi

echo "[4/4] GitHub-Secrets setzen..."
GH_ARGS=()
[ -n "$REPO_ARG" ] && GH_ARGS=(--repo "$REPO_ARG")
gh secret set GPG_PRIVATE_KEY_B64 "${GH_ARGS[@]}" < "${WORK}/private.b64"
printf '%s' "${PASS}" | gh secret set CODEFORGE_GPG_PASSPHRASE "${GH_ARGS[@]}"
echo "Fertig. Secrets gesetzt: GPG_PRIVATE_KEY_B64, CODEFORGE_GPG_PASSPHRASE"
echo "Öffentlicher Schlüssel wird vom Workflow-Lauf als Artefakt/Repo-Datei veröffentlicht."
