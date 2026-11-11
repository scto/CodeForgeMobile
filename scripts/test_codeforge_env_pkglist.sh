#!/usr/bin/env bash
# Test für `codeforge-env pkglist` mit gefälschten apt-cache/dpkg-query/dpkg/curl.
set -euo pipefail
ENV_SCRIPT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/libs/terminal-engine/src/main/assets/codeforge-env"
tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT
mkdir -p "$tmp/bin" "$tmp/prefix/etc/apt" "$tmp/prefix/tmp" "$tmp/home"
echo "deb https://example.org/apt stable main" > "$tmp/prefix/etc/apt/sources.list"

cat > "$tmp/bin/apt-cache" <<'EOT'
#!/bin/sh
cat <<'DATA'
Package: git
Version: 2.50.0
Installed-Size: 12000
Priority: optional
Description: Verteilte Versionsverwaltung | mit Pipe

Package: bash
Version: 5.3
Installed-Size: 1500
Priority: required
Description: Shell

Package: jq
Version: 1.8
Installed-Size: 300
Description: JSON-Werkzeug
DATA
EOT
cat > "$tmp/bin/dpkg-query" <<'EOT'
#!/bin/sh
printf 'git\t2.49.0\tii \nbash\t5.3\tii \nfremd\t1.0\tii \njq\t1.8\trc \n'
EOT
cat > "$tmp/bin/dpkg" <<'EOT'
#!/bin/sh
case "$1" in
  --print-architecture) echo aarch64 ;;
  --compare-versions) [ "$2" = "2.50.0" ] && [ "$4" = "2.49.0" ] ;;
esac
EOT
cat > "$tmp/bin/curl" <<'EOT'
#!/bin/sh
printf 'openjdk-21|21.0.12|aarch64|openjdk-21.deb|%s|104857600|https://github.com/x/y\n' "$(printf 'a%.0s' $(seq 1 64))"
printf 'openjdk-21|21.0.12|arm|openjdk-21-arm.deb|%s|104857600|https://github.com/x/y\n' "$(printf 'b%.0s' $(seq 1 64))"
EOT
chmod +x "$tmp/bin/"*

out=$(PATH="$tmp/bin:$PATH" PREFIX="$tmp/prefix" HOME="$tmp/home" TMPDIR="$tmp/prefix/tmp" bash "$ENV_SCRIPT" --machine pkglist)
echo "$out"
check() { echo "$out" | grep -qxF "$1" || { echo "FAIL: fehlt: $1"; exit 1; }; }
check 'CFSDK|PKG|git|2.49.0|2.50.0|12000|U|Verteilte Versionsverwaltung   mit Pipe'
check 'CFSDK|PKG|bash|5.3|5.3|1500|P|Shell'
check 'CFSDK|PKG|jq||1.8|300||JSON-Werkzeug'
check 'CFSDK|PKG|openjdk-21||21.0.12|102400|L|'
echo "$out" | grep -q 'fremd' && { echo "FAIL: Fremdpaket sichtbar"; exit 1; }
echo "PASS: pkglist"
