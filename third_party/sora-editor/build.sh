#!/usr/bin/env bash
# Baut sora-editor aus dem Fork scto/codeforgemobile-sora-editor (Code-Folding) für CodeForgeMobile neu
# und legt die Maven-Artefakte unter third_party/sora-editor/maven ab (werden committet).
#
# Aufruf: ANDROID_HOME=/pfad/zum/sdk third_party/sora-editor/build.sh [commit]
# Braucht: git, JDK 21, Android-SDK (Plattform 36, Build-Tools 36). Siehe README.md.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
COMMIT="${1:-$(cat "$HERE/FORK_COMMIT")}"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

git -C "$WORK" init -q
git -C "$WORK" remote add origin https://github.com/scto/codeforgemobile-sora-editor
git -C "$WORK" fetch -q --depth 1 origin "$COMMIT"
git -C "$WORK" checkout -q FETCH_HEAD
git -C "$WORK" apply "$HERE/cfm.patch"

(cd "$WORK" && ./gradlew -q --no-configuration-cache -Dmaven.repo.local="$WORK/out" \
  :editor:publishToMavenLocal :language-textmate:publishToMavenLocal :language-treesitter:publishToMavenLocal)

rm -rf "$HERE/maven"
cp -r "$WORK/out" "$HERE/maven"
find "$HERE/maven" -name "maven-metadata-local.xml" -delete
echo "$COMMIT" > "$HERE/FORK_COMMIT"
echo "Fertig: $(find "$HERE/maven" -name '*.aar' | wc -l) AARs in $HERE/maven"
