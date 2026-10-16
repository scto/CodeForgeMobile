#!/usr/bin/env bash
# Kopiert das App-Skript (Asset) als Quelle des Termux-Pakets "codeforge-env" in den Fork terminal-packages-codeforge.
# Aufruf (Projekt-Root):  scripts/sync-codeforge-env-package.sh [Pfad/zum/terminal-packages-codeforge]
# Danach im Fork:  TERMUX_PKG_VERSION in packages/codeforge-env/build.sh erhöhen und committen.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FORK="${1:-${CODEFORGE_FORK_DIR:-$HOME/terminal-packages-codeforge}}"
SRC="$ROOT/libs/terminal-engine/src/main/assets/codeforge-env"
DST="$FORK/packages/codeforge-env/codeforge-env.in"
[ -f "$SRC" ] || { echo "Quelle fehlt: $SRC" >&2; exit 1; }
[ -d "$FORK/packages" ] || { echo "Fork nicht gefunden: $FORK (Pfad als Argument oder CODEFORGE_FORK_DIR)" >&2; exit 1; }
mkdir -p "$(dirname "$DST")"
if [ -f "$DST" ] && cmp -s "$SRC" "$DST"; then echo "unverändert: $DST"; exit 0; fi
cp "$SRC" "$DST"
echo "aktualisiert: $DST – TERMUX_PKG_VERSION in packages/codeforge-env/build.sh erhöhen!"
