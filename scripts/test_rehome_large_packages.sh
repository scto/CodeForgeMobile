#!/usr/bin/env bash
# =============================================================================
# test_rehome_large_packages.sh — Tests für scripts/rehome-large-packages.sh (Task 53)
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
REHOME_SCRIPT="${WORKSPACE_DIR}/scripts/rehome-large-packages.sh"

TMP_TEST="$(mktemp -d)"
trap 'rm -rf "${TMP_TEST}"' EXIT

REPO="${TMP_TEST}/repo"
BIN_MOCK="${TMP_TEST}/bin"
mkdir -p "${REPO}/pool/main/o/openjdk-17" "${REPO}/pool/main/j/jq"
mkdir -p "${REPO}/dists/stable/main/binary-aarch64" "${REPO}/dists/stable/main/binary-arm"
mkdir -p "${BIN_MOCK}"

# 1. Test-Pakete anlegen:
# - jq: klein (~100 KB)
# - openjdk-17: groß (> 30 MiB, z. B. 32 MiB)
truncate -s 100K "${REPO}/pool/main/j/jq/jq_1.7.1_aarch64.deb"
truncate -s 32M "${REPO}/pool/main/o/openjdk-17/openjdk-17_17.0.20_aarch64.deb"

# 2. Bestehende large-packages.txt
echo "gradle|9.8.0|all|gradle.deb|sha123|140000000|https://github.com/test/releases/download/large-packages/gradle.deb" > "${REPO}/large-packages.txt"

# 3. Test 1: Trockenlauf (dry-run)
echo "=== Test 1: Trockenlauf ==="
dry_out="$(bash "${REHOME_SCRIPT}" --repo-dir "${REPO}" --threshold-mib 30 --dry-run)"
echo "${dry_out}"
printf '%s' "${dry_out}" | grep -q "Gefundene Pakete > 30 MiB: 1" || { echo "FEHLER: openjdk-17 nicht erkannt!" >&2; exit 1; }
printf '%s' "${dry_out}" | grep -q "openjdk-17_17.0.20_aarch64.deb" || { echo "FEHLER: Datei nicht in Tabelle!" >&2; exit 1; }
printf '%s' "${dry_out}" | grep -q "Trockenlauf (dry-run) erfolgreich abgeschlossen" || { echo "FEHLER: Abschlussmeldung fehlt!" >&2; exit 1; }

# Prüfen, dass nichts gelöscht wurde
[ -f "${REPO}/pool/main/o/openjdk-17/openjdk-17_17.0.20_aarch64.deb" ] || { echo "FEHLER: Datei im Trockenlauf gelöscht!" >&2; exit 1; }
[ -f "${REPO}/pool/main/j/jq/jq_1.7.1_aarch64.deb" ] || { echo "FEHLER: jq gelöscht!" >&2; exit 1; }

# 4. Test 2: Ausführung (--execute) mit Mocks für gh und gpg
echo "=== Test 2: Ausführung mit Mocks ==="
cat > "${BIN_MOCK}/gh" <<'EOF'
#!/bin/sh
if [ "$1" = "release" ] && [ "$2" = "view" ]; then
    for arg in "$@"; do
        if [ "$arg" = "--json" ]; then
            echo '{"assets":[{"name":"openjdk-17_17.0.20_aarch64.deb","size":33554432}]}'
            exit 0
        fi
    done
    exit 0
fi
if [ "$1" = "release" ] && [ "$2" = "upload" ]; then
    exit 0
fi
exit 0
EOF
chmod +x "${BIN_MOCK}/gh"

cat > "${BIN_MOCK}/gpg" <<'EOF'
#!/bin/sh
# Mock für gpg: signiert nur, wenn vorher ein Schlüssel in das temporäre GNUPGHOME importiert wurde
# (reproduziert "gpg: signing failed: No secret key") und lehnt --passphrase als Argument ab.
state="${GNUPGHOME:-}"
[ -n "$state" ] && [ -d "$state" ] || { echo "gpg-mock: GNUPGHOME nicht gesetzt" >&2; exit 2; }
for arg in "$@"; do
    case "$arg" in
        --passphrase) echo "gpg-mock: Passphrase darf nicht als Argument übergeben werden" >&2; exit 2 ;;
        --import) cat >/dev/null; touch "$state/imported"; exit 0 ;;
        --list-secret-keys) echo "fpr:::::::::0123456789ABCDEF0123456789ABCDEF01234567:"; exit 0 ;;
        --verify) exit 0 ;;
    esac
done
[ -f "$state/imported" ] || { echo "gpg: signing failed: No secret key" >&2; exit 2; }
cat >/dev/null
while [ $# -gt 0 ]; do
    if [ "$1" = "-o" ]; then
        echo "FAKE_GPG_SIGNATURE" > "$2"
        exit 0
    fi
    shift
done
exit 0
EOF
chmod +x "${BIN_MOCK}/gpg"

# apt-ftparchive-Stub, falls apt-utils fehlt (liefert nur plausible Ausgabe, kein echter Index)
if ! command -v apt-ftparchive >/dev/null 2>&1; then
    cat > "${BIN_MOCK}/apt-ftparchive" <<'EOF'
#!/bin/sh
for a in "$@"; do
    case "$a" in
        packages) echo "Package: stub"; exit 0 ;;
        release) printf 'Origin: CodeForge\nSuite: stable\n'; exit 0 ;;
    esac
done
exit 0
EOF
    chmod +x "${BIN_MOCK}/apt-ftparchive"
fi

KEYFILE="${TMP_TEST}/codeforge-test.gpg"
printf 'FAKE KEY' > "${KEYFILE}"
CODEFORGE_GPG_KEY_FILE="${KEYFILE}" CODEFORGE_GPG_PASSPHRASE="test-passphrase" \
    PATH="${BIN_MOCK}:${PATH}" bash "${REHOME_SCRIPT}" --repo-dir "${REPO}" --threshold-mib 30 --execute

# Verifikation:
# 1. openjdk-17 muss aus dem Pool entfernt sein
[ ! -f "${REPO}/pool/main/o/openjdk-17/openjdk-17_17.0.20_aarch64.deb" ] || { echo "FEHLER: openjdk-17 noch im Pool!" >&2; exit 1; }
# 2. jq muss noch im Pool sein
[ -f "${REPO}/pool/main/j/jq/jq_1.7.1_aarch64.deb" ] || { echo "FEHLER: jq fälschlicherweise entfernt!" >&2; exit 1; }
# 3. large-packages.txt muss openjdk-17 und gradle enthalten
grep -q "openjdk-17" "${REPO}/large-packages.txt" || { echo "FEHLER: openjdk-17 fehlt in large-packages.txt!" >&2; exit 1; }
grep -q "gradle" "${REPO}/large-packages.txt" || { echo "FEHLER: gradle fehlt in large-packages.txt!" >&2; exit 1; }
# 4. Signaturdateien müssen existieren
[ -f "${REPO}/large-packages.txt.asc" ] || { echo "FEHLER: large-packages.txt.asc fehlt!" >&2; exit 1; }
[ -f "${REPO}/dists/stable/Release" ] || { echo "FEHLER: Release fehlt!" >&2; exit 1; }
[ -f "${REPO}/dists/stable/Release.gpg" ] || { echo "FEHLER: Release.gpg fehlt!" >&2; exit 1; }
[ -f "${REPO}/dists/stable/InRelease" ] || { echo "FEHLER: InRelease fehlt!" >&2; exit 1; }

echo "✅ Alle Tests in test_rehome_large_packages.sh erfolgreich bestanden!"
