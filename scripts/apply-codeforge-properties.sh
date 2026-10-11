#!/usr/bin/env bash
# Setzt in scripts/properties.sh (Upstream termux-packages) die CodeForge-Werte. Idempotent, bricht bei Abweichungen ab.
set -euo pipefail
ROOT="$(cd "${1:-.}" && pwd)"; F="${ROOT}/scripts/properties.sh"
APP="${APP_PACKAGE:-com.codeforge.app}"
[ -f "$F" ] || { echo "FEHLER: $F fehlt" >&2; exit 1; }
set_var() {  # set_var <Zeilenanfang-Regex> <alt> <neu>
    local pat="$1" old="$2" new="$3" n
    if grep -qE "^${pat}\"${new}\"\$" "$F"; then return 0; fi
    n="$(grep -cE "^${pat}\"${old}\"\$" "$F" || true)"
    [ "$n" = "1" ] || { echo "FEHLER: ${pat}\"${old}\" kommt ${n}x vor (erwartet 1x) – properties.sh hat sich geändert, Script anpassen." >&2; exit 1; }
    sed -i -E "s#^(${pat})\"${old}\"\$#\1\"${new}\"#" "$F"
}
set_var 'TERMUX_APP__PACKAGE_NAME=' 'com.termux' "${APP}"
set_var 'TERMUX_APP__NAMESPACE=' 'com.termux' "${APP}"
set_var 'TERMUX_API_APP__PACKAGE_NAME=' 'com.termux.api' "${APP}.api"
set_var 'TERMUX_API_APP__NAMESPACE=' 'com.termux.api' "${APP}.api"
set_var 'TERMUX_AM_APP__NAMESPACE=' 'com.termux.termuxam' "${APP}.termuxam"
set_var 'CGCT_DEFAULT_PREFIX=' '/data/data/com.termux/files/usr/glibc' "/data/data/${APP}/files/usr/glibc"
set_var 'export CGCT_DIR=' '/data/data/com.termux/cgct' "/data/data/${APP}/cgct"
# TERMUX_REPO_APP__PACKAGE_NAME bleibt com.termux (zeigt auf das offizielle Repo)
grep -q '^TERMUX_REPO_APP__PACKAGE_NAME="com.termux"$' "$F" || { echo "FEHLER: TERMUX_REPO_APP__PACKAGE_NAME ist nicht com.termux." >&2; exit 1; }
bash -n "$F"
echo "properties.sh gepatcht für ${APP}"
