#!/usr/bin/env bash
# ==============================================================================
# verify-extension-catalog.sh
# Verifiziert alle Download-Einträge des LSP-Extension-Katalogs gegen die realen
# Upstream-Downloads und berechnet die SHA-256-Prüfsummen.
# Nicht in CI ausführen (lädt ~150 MB aus externen Quellen).
# ==============================================================================
set -euo pipefail

TMP_DIR="$(mktemp -d -t cf-ext-verify-XXXXXX)"
trap 'rm -rf "${TMP_DIR}"' EXIT

FAILED=0
TOTAL=0

verify_package() {
    local id="$1"
    local version="$2"
    local url="$3"
    local expected_sha="$4"

    TOTAL=$((TOTAL + 1))
    echo "============================================================"
    echo "Prüfe [${id}] (v${version})"
    echo "URL: ${url}"
    echo "Erwarteter SHA-256: ${expected_sha}"

    local target_file="${TMP_DIR}/${id}.archive"
    if ! curl -fsSL -A "CodeForgeCatalogVerifier/1.0" -o "${target_file}" "${url}"; then
        echo "❌ FEHLER: Download fehlgeschlagen für ${id} von ${url}"
        FAILED=$((FAILED + 1))
        return
    fi

    local actual_sha
    actual_sha="$(sha256sum "${target_file}" | awk '{print $1}')"
    echo "Berechneter SHA-256: ${actual_sha}"

    if [ "${actual_sha}" = "${expected_sha}" ]; then
        echo "✅ OK: Prüfsumme stimmt überein."
    else
        echo "❌ FEHLER: Prüfsummen-Abweichung!"
        echo "   Erwartet: ${expected_sha}"
        echo "   Erhalten: ${actual_sha}"
        FAILED=$((FAILED + 1))
    fi
    rm -f "${target_file}"
}

echo "=== CodeForge Mobile: Verifikation des Extension-Katalogs ==="

verify_package \
    "kotlin-language-server" \
    "1.3.11" \
    "https://github.com/fwcd/kotlin-language-server/releases/download/1.3.11/server.zip" \
    "c65efea1c09d88ddb1fe227d8e2c5dbb3d8dd568453bccbbf2e20dfa2f004dcd"

verify_package \
    "jdtls" \
    "1.45.0" \
    "https://download.eclipse.org/jdtls/milestones/1.45.0/jdt-language-server-1.45.0-202502271238.tar.gz" \
    "c09e79e958beb5ce41fb3b5097aec0455298bc88a14c8e41462b57edb2812797"

verify_package \
    "pyright" \
    "1.1.377" \
    "https://registry.npmjs.org/pyright/-/pyright-1.1.377.tgz" \
    "b11dc22e92560b38ebad99a892544d9ce81e2ce995db430b3683e86db57d7710"

verify_package \
    "vscode-langservers-extracted" \
    "4.8.0" \
    "https://registry.npmjs.org/vscode-langservers-extracted/-/vscode-langservers-extracted-4.8.0.tgz" \
    "8c7102927a32fa145077f0406b66bb3a7d85f5b0a208d4efeb40702b5a6cd2e1"

verify_package \
    "lemminx" \
    "0.31.2" \
    "https://download.eclipse.org/lemminx/releases/0.31.2/org.eclipse.lemminx-uber.jar" \
    "f4fde164e785c635e5f86361dbf4b993bfe2c5f83fdb52891d058b7dfc9bcfc8"

verify_package \
    "yaml-language-server" \
    "2.0.0" \
    "https://registry.npmjs.org/yaml-language-server/-/yaml-language-server-2.0.0.tgz" \
    "8dcd68cc8bcdde719def62e651de9744576d3227de2e4ab85961603c92a52423"

verify_package \
    "bash-language-server" \
    "5.8.1" \
    "https://registry.npmjs.org/bash-language-server/-/bash-language-server-5.8.1.tgz" \
    "2ae910de8c50f4ec148222bdc580c121f2c621bdacc1225716218ca700f8ec79"

echo "============================================================"
echo "Gesamt geprüft: ${TOTAL} Pakete"
if [ "${FAILED}" -eq 0 ]; then
    echo "🎉 Alle ${TOTAL} Download-Einträge sind OK."
    exit 0
else
    echo "⚠️ ${FAILED} von ${TOTAL} Paketen fehlerhaft."
    exit 1
fi
