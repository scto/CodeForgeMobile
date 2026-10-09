#!/usr/bin/env bash
# fix-fork.sh – repariert den terminal-packages-codeforge-Fork (idempotent, ohne git apply).
#
# Behebt:
#   1. URLs, die ein globales `sed s/com.termux/com.codeforge/` zerstört hat
#      (github.com/termux/<repo> -> github.com.codeforge/<repo>)
#   2. den glibc-Pfad in termux_step_setup_cgct_environment.sh (bleibt com.termux)
#   3. test-runner.sh-Shebang und die Meldung in build-bootstraps.sh
#   4. verlorene Symlinks (kleine Textdateien, deren Inhalt der Link-Pfad ist)
#   5. verlorene Ausführbar-Bits (chmod +x laut Liste aus dem offiziellen termux-packages)
#
# Aufruf (aus dem Fork-Root oder mit Pfad):
#   bash fix-fork.sh [PFAD_ZUM_FORK]     # Standard: aktuelles Verzeichnis
# Danach prüfen und committen:
#   git status --short | head; git add -A; git commit -m "Fix: URLs, glibc-Pfad, Symlinks"
#
# Hinweis: build_codeforge_repo.sh macht dasselbe bei jedem Lauf selbst; dieses Script ist nur
# nötig, wenn du die Reparatur dauerhaft im Fork committen willst.
set -euo pipefail

APP_PACKAGE="${APP_PACKAGE:-com.codeforge.app}"
ROOT="$(cd "${1:-.}" && pwd)"

die() { echo "FEHLER: $*" >&2; exit 1; }

[ -d "${ROOT}/.git" ] || die "${ROOT} ist kein Git-Repository (Fork-Root angeben)."
[ -f "${ROOT}/build-package.sh" ] && [ -d "${ROOT}/scripts" ] || die "${ROOT} sieht nicht wie der Fork aus (build-package.sh/scripts fehlen)."
command -v python3 >/dev/null || die "python3 fehlt."

# 1. Beschädigte URLs
changed=0
while IFS= read -r f; do
    sed -i -e 's#github\.com\.codeforge\.app/#github.com/termux/#g' \
           -e 's#github\.com\.codeforge/#github.com/termux/#g' "${f}"
    changed=$((changed + 1))
done < <(grep -rlIE 'github\.com\.codeforge(\.app)?/' --exclude-dir=.git "${ROOT}" || true)

# 2. glibc-Pfad innerhalb des offiziellen glibc-Pakets: bleibt com.termux
cgct="${ROOT}/scripts/build/termux_step_setup_cgct_environment.sh"
if [ -f "${cgct}" ]; then
    hit="$(grep -l 'data/data/com\.codeforge/files/usr/glibc' "${cgct}" || true)"
    if [ -n "${hit}" ]; then
        sed -i 's#data/data/com\.codeforge/files/usr/glibc#data/data/com.termux/files/usr/glibc#' "${cgct}"
        changed=$((changed + 1))
    fi
fi

# 3. test-runner.sh und build-bootstraps.sh
tr_file="${ROOT}/scripts/test-runner.sh"
if [ -f "${tr_file}" ]; then
    first="$(head -n1 "${tr_file}")"
    case "${first}" in
        *'/data/data/com.codeforge/files'*)
            sed -i "1s#/data/data/com\.codeforge/files#/data/data/${APP_PACKAGE}/files#" "${tr_file}"
            changed=$((changed + 1)) ;;
    esac
fi
bb="${ROOT}/scripts/build-bootstraps.sh"
[ -f "${bb}" ] && sed -i "s#It defaults to 'com\.codeforge'\.#It defaults to '${APP_PACKAGE}'.#" "${bb}"

echo "Textreparatur: ${changed} Datei(en) angepasst."
bad="$(grep -rlIE 'github\.com\.codeforge' --exclude-dir=.git "${ROOT}" || true)"
[ -z "${bad}" ] || die "Beschädigte Verweise bleiben: ${bad}"

# 4. Symlinks wiederherstellen (nur eindeutige Fälle: <=150 Byte, eine Zeile, reiner Pfad, Ziel existiert)
python3 -I - "${ROOT}" <<'PY'
import os, re, subprocess, sys
root = sys.argv[1]
out = subprocess.run(["git", "-C", root, "ls-files", "-s", "-z"], capture_output=True, check=True).stdout.decode("utf8", "surrogateescape")
restored = 0
for entry in out.split("\0"):
    if not entry:
        continue
    meta, path = entry.split("\t", 1)
    if meta.split()[0] != "100644":
        continue
    p = os.path.join(root, path)
    if os.path.islink(p) or not os.path.isfile(p) or os.path.getsize(p) > 150:
        continue
    try:
        text = open(p, "rb").read().decode("utf8")
    except UnicodeDecodeError:
        continue
    target = text.rstrip("\r\n")
    if not target or "\n" in target or not re.fullmatch(r"[A-Za-z0-9._/+@~-]+/?", target):
        continue
    resolved = os.path.normpath(os.path.join(os.path.dirname(p), target))
    if not os.path.exists(resolved) or os.path.abspath(resolved) == os.path.abspath(p):
        continue
    os.remove(p)
    os.symlink(target, p)
    restored += 1
print(f"Symlinks wiederhergestellt: {restored}")
PY

# Ausführbar-Bits wiederherstellen: Der Fork hat bei Dateien das Bit +x verloren (z. B. packages/termux-core/build/scripts/
# termux-replace-termux-core-src-scripts -> "Permission denied", make Error 126). Liste = Dateien, die im offiziellen
# termux-packages ausführbar sind (ohne .patch). Nur vorhandene Dateien; idempotent.
restore_exec_bits() {
    local d="$1" f n=0
    while IFS= read -r f; do
        [ -n "${f}" ] || continue
        if [ -f "${d}/${f}" ] && [ ! -L "${d}/${f}" ] && [ ! -x "${d}/${f}" ]; then
            chmod +x "${d}/${f}"; n=$((n + 1))
        fi
    done <<'EXEC_LIST'
build-all.sh
build-package.sh
clean.sh
disabled-packages/openethereum/cmake_mod.sh
disabled-packages/qt5-qmake/termux-build-qmake.sh
packages/8086tiny/8086tiny.sh
packages/bazel/termux-cross/toolchain/template/bin/wrapper.sh
packages/ecj/ecj
packages/ecj/ecj-24
packages/electric-fence/ef.sh
packages/emscripten/tests/0001-hello-world.sh
packages/emscripten/tests/0002-emscripten-tests-third-party-core0.sh
packages/guile/tests/system_star.sh
packages/jellyfin-server/ffmpeg-configureopts.sh
packages/ldc/tests/hello_world.sh
packages/libprotobuf/interface_link_libraries.sh
packages/openssl/add-trusted-certificate
packages/proot/termux-chroot
packages/rust/tests/0001-cargo-build.sh
packages/rust/tests/0002-issue-25360.sh
packages/rust/tests/0003-issue-27402.sh
packages/rust/tests/0004-broken-symlink.sh
packages/termux-core/build/scripts/termux-replace-termux-core-src-scripts
packages/tree-sitter/termux-tree-sitter
root-packages/dockerd/dockerd.sh
scripts/bin/add-to-path.sh
scripts/bin/build-package-dry-run-simulation.sh
scripts/bin/check-auto-update
scripts/bin/check-pie.sh
scripts/bin/ldd
scripts/bin/revbump
scripts/bin/test-buildorder-random
scripts/bin/update-checksum
scripts/bin/update-packages
scripts/bin/validation
scripts/bootstrap/termux-bootstrap-second-stage.sh
scripts/build-bootstraps.sh
scripts/build/termux_download.sh
scripts/build/termux_download_deb_pac.sh
scripts/build/termux_download_ubuntu_packages.sh
scripts/build/termux_extract_dep_info.sh
scripts/buildorder.py
scripts/check-built-packages.py
scripts/check-repository-health.js
scripts/check-versions.sh
scripts/config.guess
scripts/config.sub
scripts/free-space.sh
scripts/generate-apt-packages-list.sh
scripts/generate-bootstraps.sh
scripts/get_hash_from_file.py
scripts/lint-packages.sh
scripts/list-packages.sh
scripts/list-versions.sh
scripts/run-docker.sh
scripts/setup-android-sdk.sh
scripts/setup-archlinux.sh
scripts/setup-cgct.sh
scripts/setup-offline-bundle.sh
scripts/setup-termux-glibc.sh
scripts/setup-termux.sh
scripts/setup-ubuntu.sh
scripts/test-runner.sh
scripts/update-docker.sh
scripts/updates/api/dump-repology-data
scripts/updates/utils/termux_pkg_is_update_needed.sh
scripts/updates/utils/termux_pkg_upgrade_version.sh
scripts/utils/termux_audit_published_conflicts.sh
scripts/utils/termux_check_file_conflicts.sh
scripts/utils/termux_report_published_conflicts.sh
scripts/utils/termux_reuse_pr_build_artifacts.sh
x11-packages/openbox/configs/autostart
x11-packages/openbox/configs/environment
x11-packages/openbox/scripts/openbox-autostart
x11-packages/openbox/scripts/openbox-session
x11-packages/openbox/scripts/openbox-xdg-autostart
x11-packages/qt5-qtbase/postinst
x11-packages/qt5-qtdeclarative/postinst
x11-packages/qt5-qttools/postinst
x11-packages/shared-mime-info/postinst
x11-packages/shared-mime-info/postrm
x11-packages/tigervnc/vncserver
x11-packages/xorg-mkfontscale/postinst
x11-packages/xorg-mkfontscale/postrm
EXEC_LIST
    echo "Ausführbar-Bits wiederhergestellt: ${n}"
}

restore_exec_bits "${ROOT}"

[ -d "${ROOT}/packages/procps/hsearch" ] || die "packages/procps/hsearch ist kein Verzeichnis – Symlink-Reparatur unvollständig."
echo "Fertig. Prüfen: git status --short | head; dann git add -A && git commit."
