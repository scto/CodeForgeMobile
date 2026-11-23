#!/usr/bin/env bash
# Übernimmt die Kotlin-Quellen von material-color-utilities nach :libs:color-utilities.
# Aufruf: scripts/vendor_color_utilities.sh <pfad-zum-geklonten-material-color-utilities>
set -euo pipefail
SRC="${1:?Pfad zu material-color-utilities}/kotlin"
DST="$(dirname "$0")/../libs/color-utilities/src/main/kotlin/com/codeforge/libs/color_utilities"
P="blend|contrast|dislike|dynamiccolor|hct|palettes|quantize|scheme|score|temperature|utils"
rm -rf "$DST" && mkdir -p "$DST"
(cd "$SRC" && find . -name '*.kt' -not -path '*/test/*') | while read -r f; do
  mkdir -p "$DST/$(dirname "$f")" && cp "$SRC/$f" "$DST/$f"
done
find "$DST" -name '*.kt' -exec sed -i -E \
  "s/^package ($P)\b/package com.codeforge.libs.color_utilities.\1/; s/^import ($P)\./import com.codeforge.libs.color_utilities.\1./" {} +
cp "$SRC/LICENSE" "$DST/../../../../../../LICENSE"
echo "Fertig. Commit im README von libs/color-utilities nachtragen."
