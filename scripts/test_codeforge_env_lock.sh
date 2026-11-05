#!/usr/bin/env bash
# Prüft die Installationssperre von codeforge-env: parallele do_install-Aufrufe laufen nacheinander,
# ein verschachtelter Aufruf (cmdline-tools für sdkmanager) blockiert nicht, die Sperre wird entfernt.
set -euo pipefail
here="$(cd "$(dirname "$0")/.." && pwd)"
script="$here/libs/terminal-engine/src/main/assets/codeforge-env"
work="$(mktemp -d)"; trap 'rm -rf "$work"' EXIT
python3 - "$script" "$work/lock_fn.sh" <<'PY'
import sys
s = open(sys.argv[1]).read()
a = s.index('do_install() { # id\n  if [ "${CF_INSTALL_LOCK_HELD')
b = s.index('_do_install() { # id')
open(sys.argv[2], 'w').write(s[a:b])
PY
SDK_ROOT="$work/sdk"; mkdir -p "$SDK_ROOT"
progress() { :; }
# shellcheck disable=SC1091
. "$work/lock_fn.sh"
_do_install() { echo "start $1 $(date +%s%N)"; if [ "$1" = outer ]; then ( do_install inner ); fi; sleep 1; echo "end $1 $(date +%s%N)"; }
( do_install A ) > "$work/a" & ( do_install B ) > "$work/b" & ( do_install outer ) > "$work/o" & wait
# Intervalle der drei äußeren Läufe dürfen sich nicht überlappen
python3 - "$work" <<'PY'
import sys, re
iv = []
for n in "abo":
    t = {}
    for l in open(f"{sys.argv[1]}/{n}"):
        k, name, ns = l.split()
        t[(k, name)] = int(ns)
    name = {"a": "A", "b": "B", "o": "outer"}[n]
    iv.append((t[("start", name)], t[("end", name)], name))
iv.sort()
for (s1, e1, n1), (s2, e2, n2) in zip(iv, iv[1:]):
    assert e1 <= s2, f"{n1} und {n2} überlappen"
print("ok: Installationen laufen nacheinander")
PY
[ ! -e "$SDK_ROOT/.cf-install.lock" ] || { echo "FEHLER: Sperre nicht entfernt"; exit 1; }
