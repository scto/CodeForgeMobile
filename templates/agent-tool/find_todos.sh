#!/bin/sh
# Aufruf: find_todos.sh <projektordner> [unterordner]
set -eu
root="$1"; sub="${2:-}"
case "$sub" in /*|*..*) echo "Nur relative Pfade ohne .. erlaubt" >&2; exit 2;; esac
cd "$root/$sub" 2>/dev/null || { echo "Ordner nicht gefunden: $sub" >&2; exit 1; }
grep -rnE "TODO|FIXME" --exclude-dir=.git --exclude-dir=build --exclude-dir=.gradle . | head -200 || true
