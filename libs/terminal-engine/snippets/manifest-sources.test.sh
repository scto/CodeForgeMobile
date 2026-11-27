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
 {"tag_name":"vscode-helper","name":"x","assets":[{"name":"buildtools_x_arm64-v8a.tar.xz"}]},
 {"tag_name":"msmt-platform-tools-37.0.3","name":"platform-tools-37.0.3","assets":[{"name":"build-tools-37.0.3-arm64-v8a.zip"},{"name":"build-tools-37.0.3-armeabi-v7a.zip"},{"name":"platform-tools-37.0.3-arm64-v8a.tar.gz"},{"name":"platform-tools-37.0.3-armeabi-v7a.tar.gz"}]},
 {"tag_name":"msmt-platform-tools-37.0.2","name":"platform-tools-37.0.2","assets":[{"name":"build-tools-37.0.0-arm64-v8a.zip"},{"name":"build-tools-37.0.0-armeabi-v7a.zip"},{"name":"platform-tools-37.0.2-arm64-v8a.tar.gz"},{"name":"platform-tools-37.0.2-armeabi-v7a.tar.gz"}]},
 {"tag_name":"msmt-platform-tools-37.0.1","name":"platform-tools-37.0.1","assets":[{"name":"build-tools-37.0.0-arm64-v8a.zip"},{"name":"platform-tools-37.0.1-arm64-v8a.tar.gz"}]},
 {"tag_name":"msmt-platform-tools-34.0.0-rc1","name":"x","assets":[{"name":"build-tools-34.0.0-arm64-v8a.zip"}]}
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
# build-tools;37.0.0 kommt jetzt aus msmt (Vorrang, siehe unten); platform-tools;37.0.0 weiter aus android-sdk.tar.gz
check "aarch64: platform-tools;37.0.0 aus android-sdk.tar.gz" "platform-tools;37.0.0|37.0.0|ghptfull|" "$T/a64.txt"
check "aarch64: build-tools;36.1.0 aus v36.1.0" "build-tools;36.1.0|36.1.0|ghaidebt|" "$T/a64.txt"
check "aarch64: platform-tools;36.1.0 aus v36.1.0" "platform-tools;36.1.0|36.1.0|ghaidept|" "$T/a64.txt"
check "aarch64: bisheriges build-tools;36.0.1 bleibt" "build-tools;36.0.1|36.0.1|ghsdk|" "$T/a64.txt"
absent "keine Tags wie vscode-helper" "vscode-helper" "$T/a64.txt"
absent "arm: android-sdk.tar.gz ist aarch64-only" "ghsdkfull" "$T/arm.txt"
check "arm: build-tools;36.1.0 (armeabi-v7a)" "build-tools;36.1.0|36.1.0|ghaidebt|" "$T/arm.txt"
# msmt2018: vollständige Pakete haben Vorrang; build-tools-Version aus dem Asset-Namen, rel= nennt das Release
check "aarch64: build-tools;37.0.3 aus msmt" "build-tools;37.0.3|37.0.3|ghaidebt|" "$T/a64.txt"
check "aarch64: build-tools;37.0.0 aus msmt 37.0.2 (vor android-sdk.tar.gz)" "build-tools;37.0.0|37.0.0|ghaidebt|scto/codeforge-tools|||tagprefix=msmt-platform-tools-;rel=37.0.2" "$T/a64.txt"
absent "aarch64: build-tools;37.0.0 nur einmal (ghsdkfull verdrängt)" "build-tools;37.0.0|37.0.0|ghsdkfull|" "$T/a64.txt"
check "aarch64: platform-tools;37.0.2 aus msmt" "platform-tools;37.0.2|37.0.2|ghaidept|scto/codeforge-tools|||tagprefix=msmt-platform-tools-" "$T/a64.txt"
absent "msmt: kein Release mit rc" "34.0.0" "$T/a64.txt"
check "arm: platform-tools;37.0.2 (armeabi-v7a)" "platform-tools;37.0.2|37.0.2|ghaidept|" "$T/arm.txt"
absent "arm: 37.0.1 hat kein armeabi-v7a" "platform-tools;37.0.1|" "$T/arm.txt"
[ "$(grep -c '^build-tools;37.0.0|' "$T/a64.txt")" = 1 ] && echo "ok   build-tools;37.0.0 genau einmal" || { echo "FAIL build-tools;37.0.0 mehrfach"; fail=1; }

# Asset-Suche für msmt (asset_re_for)
re=$( ( source "$T/lib.sh" 2>/dev/null; NU_VARIANT=arm64-v8a; asset_re_for msmt-platform-tools- ghaidebt 37.0.0 ) )
[ "$re" = '^build-tools-37\.0\.0-arm64-v8a\.zip$' ] && echo "ok   asset_re_for msmt build-tools" || { echo "FAIL asset_re_for: $re"; fail=1; }

# Installation findet das Asset im genannten Release (rel=), nicht in einem Release der build-tools-Version
asset=$( ( source "$T/lib.sh" 2>/dev/null; gh_releases_json() { cat "$T/releases.json"; }; NU_VARIANT=arm64-v8a
          gh_find_asset scto/codeforge-tools 37.0.2 msmt-platform-tools- "$(asset_re_for msmt-platform-tools- ghaidebt 37.0.0)" && echo "$GH_ASSET" ) )
[ "$asset" = build-tools-37.0.0-arm64-v8a.zip ] && echo "ok   gh_find_asset msmt (rel=37.0.2)" || { echo "FAIL gh_find_asset msmt: '$asset'"; fail=1; }

# Katalog: Zwischenspeicher → CDN → eingebaute Fassung; ohne alles die API (Rückgabe 1)
cat_test() { # erwartung beschreibung [vorbereitung]
  ( source "$T/lib.sh" 2>/dev/null
    TMP="$T/cat"; rm -rf "$TMP"; mkdir -p "$TMP"
    curl() { return 7; }   # kein Netz
    eval "${3:-:}"
    out=$(tools_catalog); rc=$?
    if [ "$1" = none ]; then [ $rc -ne 0 ]; else [ $rc -eq 0 ] && [ "$(printf '%s' "$out" | jq -r '.[0].tag_name')" = "$1" ]; fi ) \
    && echo "ok   $2" || { echo "FAIL $2"; fail=1; }
}
echo '[{"tag_name":"bundled","assets":[]}]' > "$T/bundled.json"
cat_test none "Katalog: ohne Netz, Cache und eingebaute Fassung → API"
cat_test bundled "Katalog: ohne Netz → eingebaute Fassung" "BUNDLED_CATALOG='$T/bundled.json'"
cat_test cached "Katalog: Zwischenspeicher vor eingebauter Fassung" "BUNDLED_CATALOG='$T/bundled.json'; echo '[{\"tag_name\":\"cached\"}]' > \"\$TMP/.cfsdk-catalog.json\""
cat_test online "Katalog: online (CDN) vor eingebauter Fassung" "BUNDLED_CATALOG='$T/bundled.json'; curl() { local o=''; while [ \$# -gt 0 ]; do [ \"\$1\" = -o ] && o=\$2; shift; done; echo '[{\"tag_name\":\"online\"}]' > \"\$o\"; }"
cat_test none "Katalog: leere URL → API" "TOOLS_CATALOG_URL=''"
exit $fail
