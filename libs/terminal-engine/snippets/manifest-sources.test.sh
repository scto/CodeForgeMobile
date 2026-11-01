#!/usr/bin/env bash
# Prüft die Manifest-Quellen von codeforge-env (build-tools/platform-tools aus dem Mirror) gegen Beispiel-Releases.
# Aufruf: bash libs/terminal-engine/snippets/manifest-sources.test.sh
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"
SCRIPT="$HERE/../src/main/assets/codeforge-env"
T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT
mkdir -p "$T/usr/tmp" "$T/home" "$T/bin"
sed -e "s#@TERMUX_PREFIX@#$T/usr#g" -e "s#@TERMUX_HOME@#$T/home#g" "$SCRIPT" | awk '/^remove_legacy_ndk_bundle$/{exit} {print}' > "$T/lib.sh"
cat > "$T/releases.json" <<'JSON'
[
 {"tag_name":"v36.1.0","name":"v36.1.0","assets":[{"name":"buildtools_36.1.0_arm64-v8a.tar.xz"},{"name":"buildtools_36.1.0_armeabi-v7a.tar.xz"},{"name":"platformtools_arm64-v8a.tar.xz"},{"name":"platformtools_armeabi-v7a.tar.xz"}]},
 {"tag_name":"android-sdk-37.0.0","name":"android-sdk-37.0.0","assets":[{"name":"android-sdk.tar.gz"},{"name":"android-sdk.tar.gz.sha256"}]},
 {"tag_name":"android-sdk-36.0.1","name":"36.0.1","assets":[{"name":"android-sdk-aarch64-linux-android.tar.xz"},{"name":"android-sdk-armv7a-linux-androideabi.tar.xz"}]},
 {"tag_name":"vscode-helper","name":"x","assets":[{"name":"buildtools_x_arm64-v8a.tar.xz"}]}
]
JSON
manifest_for() { # arch
  printf '#!/bin/sh\necho %s\n' "$1" > "$T/bin/uname"; chmod +x "$T/bin/uname"
  ( PATH="$T/bin:$PATH"; source "$T/lib.sh" 2>/dev/null; gh_releases_json() { cat "$T/releases.json"; }; MANIFEST_URL=""; build_manifest )
}
fail=0
check() { # beschreibung muster datei
  if grep -qF -- "$2" "$3"; then echo "ok   $1"; else echo "FAIL $1 (fehlt: $2)"; fail=1; fi
}
absent() { if grep -qF -- "$2" "$3"; then echo "FAIL $1 (unerwartet: $2)"; fail=1; else echo "ok   $1"; fi; }
manifest_for aarch64 > "$T/a64.txt"; manifest_for armv7l > "$T/arm.txt"
check "aarch64: build-tools;37.0.0 aus android-sdk.tar.gz" "build-tools;37.0.0|37.0.0|ghsdkfull|" "$T/a64.txt"
check "aarch64: platform-tools;37.0.0 aus android-sdk.tar.gz" "platform-tools;37.0.0|37.0.0|ghptfull|" "$T/a64.txt"
check "aarch64: build-tools;36.1.0 aus v36.1.0" "build-tools;36.1.0|36.1.0|ghaidebt|" "$T/a64.txt"
check "aarch64: platform-tools;36.1.0 aus v36.1.0" "platform-tools;36.1.0|36.1.0|ghaidept|" "$T/a64.txt"
check "aarch64: bisheriges build-tools;36.0.1 bleibt" "build-tools;36.0.1|36.0.1|ghsdk|" "$T/a64.txt"
absent "keine Tags wie vscode-helper" "vscode-helper" "$T/a64.txt"
absent "arm: android-sdk.tar.gz ist aarch64-only" "ghsdkfull" "$T/arm.txt"
check "arm: build-tools;36.1.0 (armeabi-v7a)" "build-tools;36.1.0|36.1.0|ghaidebt|" "$T/arm.txt"
exit $fail
