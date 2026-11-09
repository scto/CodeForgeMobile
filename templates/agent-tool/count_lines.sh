#!/bin/sh
# Aufruf: count_lines.sh <projektordner> <relativer-pfad>
set -eu
root="$1"; rel="${2:-}"
[ -n "$rel" ] || { echo "Pfad fehlt" >&2; exit 2; }
case "$rel" in /*|*..*) echo "Nur relative Pfade ohne .. erlaubt" >&2; exit 2;; esac
[ -f "$root/$rel" ] || { echo "Datei nicht gefunden: $rel" >&2; exit 1; }
echo "$rel: $(wc -l < "$root/$rel") Zeilen"
