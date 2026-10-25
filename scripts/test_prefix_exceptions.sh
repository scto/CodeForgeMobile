#!/usr/bin/env bash
set -euo pipefail

# Test für die Prefix-Ausnahmenlogik (Task 49.3)
# Prüft:
# 1. ELF-Datei in prefix-exceptions.conf (z.B. taplo usr/bin/taplo) -> AUSNAHME (akzeptiert)
# 2. ELF-Datei in prefix-exceptions.conf (z.B. shellcheck usr/bin/shellcheck) -> AUSNAHME (akzeptiert)
# 3. Textdatei mit com.termux (z.B. openssh ssh-agent/run) -> FEHLER (nicht akzeptiert)
# 4. ELF-Datei NICHT in prefix-exceptions.conf -> FEHLER (nicht akzeptiert)
# 5. Textdatei in einem Ausnahme-Paket (z.B. taplo README) -> FEHLER (nur deklariertes ELF-Glob erlaubt)

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "${TMP}"' EXIT

CONF="${TMP}/prefix-exceptions.conf"
cat << 'EOF' > "${CONF}"
taplo|usr/bin/taplo|Rust Crate rustls-native-certs
shellcheck|usr/bin/shellcheck|GHC RTS Fallbacks
EOF

is_prefix_exception() {
    local target_pkg="$1"
    local full_file="$2"
    local chk_root="$3"
    local exceptions_file="${CONF}"
    [ -f "${exceptions_file}" ] || return 1

    [ -f "${full_file}" ] || return 1
    [ "$(head -c 4 "${full_file}" 2>/dev/null)" = $'\x7fELF' ] || return 1

    local rel_file="${full_file#"${chk_root}"/}"
    local usr_rel="${rel_file#data/data/*/files/}"
    usr_rel="${usr_rel#/}"

    while IFS='|' read -r ex_pkg ex_glob ex_reason || [ -n "${ex_pkg}" ]; do
        [[ "${ex_pkg}" =~ ^[[:space:]]*# ]] && continue
        [ -n "${ex_pkg}" ] || continue
        ex_pkg="$(echo "${ex_pkg}" | xargs)"
        ex_glob="$(echo "${ex_glob}" | xargs)"
        ex_reason="$(echo "${ex_reason}" | xargs)"

        if [ "${ex_pkg}" = "${target_pkg}" ]; then
            # shellcheck disable=SC2053
            if [[ "${usr_rel}" == ${ex_glob} ]] || [[ "${rel_file}" == ${ex_glob} ]] || [[ "${usr_rel}" == *"${ex_glob}"* ]]; then
                echo "${ex_reason}"
                return 0
            fi
        fi
    done < "${exceptions_file}"

    return 1
}

make_fake_elf() {
    local path="$1"
    mkdir -p "$(dirname "$path")"
    printf '\x7fELFfake binary with /data/data/com.termux/files/usr/etc/tls' > "$path"
}

make_fake_text() {
    local path="$1"
    mkdir -p "$(dirname "$path")"
    printf '#!/bin/sh\n# /data/data/com.termux/files/usr/var/run\n' > "$path"
}

CHK="${TMP}/chk"
mkdir -p "${CHK}"

echo "Starte Tests für prefix-exceptions..."

# Test 1: taplo usr/bin/taplo (ELF) -> OK
F1="${CHK}/data/data/com.codeforge.app/files/usr/bin/taplo"
make_fake_elf "${F1}"
if is_prefix_exception "taplo" "${F1}" "${CHK}" >/dev/null; then
    echo "  [PASS] Test 1: taplo ELF usr/bin/taplo ist Ausnahme"
else
    echo "  [FAIL] Test 1: taplo ELF sollte Ausnahme sein" >&2
    exit 1
fi

# Test 2: shellcheck usr/bin/shellcheck (ELF) -> OK
F2="${CHK}/data/data/com.codeforge.app/files/usr/bin/shellcheck"
make_fake_elf "${F2}"
if is_prefix_exception "shellcheck" "${F2}" "${CHK}" >/dev/null; then
    echo "  [PASS] Test 2: shellcheck ELF usr/bin/shellcheck ist Ausnahme"
else
    echo "  [FAIL] Test 2: shellcheck ELF sollte Ausnahme sein" >&2
    exit 1
fi

# Test 3: openssh ssh-agent/run (Text) -> KEINE Ausnahme
F3="${CHK}/data/data/com.codeforge.app/files/usr/var/service/ssh-agent/run"
make_fake_text "${F3}"
if is_prefix_exception "openssh" "${F3}" "${CHK}" >/dev/null; then
    echo "  [FAIL] Test 3: Textdatei darf NIE Ausnahme sein" >&2
    exit 1
else
    echo "  [PASS] Test 3: Textdatei in openssh wird korrekt als Fehler erkannt"
fi

# Test 4: ungelistetes Paket randompkg (ELF) -> KEINE Ausnahme
F4="${CHK}/data/data/com.codeforge.app/files/usr/bin/randompkg"
make_fake_elf "${F4}"
if is_prefix_exception "randompkg" "${F4}" "${CHK}" >/dev/null; then
    echo "  [FAIL] Test 4: Unbekanntes Paket darf keine Ausnahme sein" >&2
    exit 1
else
    echo "  [PASS] Test 4: Unbekanntes ELF-Paket wird abgewiesen"
fi

# Test 5: taplo Textdatei (z.B. README) -> KEINE Ausnahme
F5="${CHK}/data/data/com.codeforge.app/files/usr/share/doc/taplo/README"
make_fake_text "${F5}"
if is_prefix_exception "taplo" "${F5}" "${CHK}" >/dev/null; then
    echo "  [FAIL] Test 5: Textdatei in taplo darf keine Ausnahme sein" >&2
    exit 1
else
    echo "  [PASS] Test 5: Textdatei in taplo wird korrekt abgewiesen"
fi

echo "Alle 5 Tests für prefix-exceptions erfolgreich!"
