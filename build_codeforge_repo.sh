#!/usr/bin/env bash
# =============================================================================
# CodeForge: Bootstraps + signiertes APT-Repository (Prefix com.codeforge.app)
#
# Läuft auf einem Linux-x86_64-Rechner/Runner mit Docker (z. B. PC, VPS oder GitHub Actions) –
# NICHT im Termux auf dem Telefon: Die Pakete werden mit dem Termux-Docker-Builder aus dem
# Quellcode gebaut, weil ELF-Binaries mit dem Prefix /data/data/com.codeforge.app/files/usr
# kompiliert sein müssen. Ein nachträgliches `sed` com.termux -> com.codeforge.app zerstört
# ELF-Dateien (anderer Längenunterschied = verschobene Offsets) und ist deshalb verboten.
#
# Ergebnis:
#   output/bootstraps/bootstrap-<arch>.zip (+ .sha256)   -> GitHub-Release `bootstrap-<version>`
#   github_repo_ready/                                    -> Inhalt per GitHub Pages ausliefern (apt-Repo)
#   .gpg/ (öffentlich) und .gpg/private/ (NICHT einchecken)
#
# Konfiguration über Umgebungsvariablen (alle optional):
#   ARCHS               "aarch64 arm i686 x86_64"
#   EXTRA_PACKAGES      Pakete, die IM BOOTSTRAP landen (Komma-getrennt). Vergrößert jede Bootstrap-ZIP und
#                       damit die APK – nur für Kleinkram verwenden (z. B. "zip").
#   REPO_PACKAGES       Pakete, die NUR gebaut und ins APT-Repo gelegt werden (Komma-getrennt), z. B.
#                       "openjdk-17,git,protobuf,aapt2,wget". Installation später per `pkg install`.
#   PREBUILD_PACKAGES   Pakete, die vor REPO_PACKAGES gebaut werden (Reihenfolge-Workaround). Standard: "libsndfile",
#                       wenn REPO_PACKAGES openjdk-17/alsa-plugins/pulseaudio enthält; "" schaltet es ab.
#                       Solange es gesetzt ist, wird zusätzlich der Zyklus libmpg123 <-> pulseaudio aufgelöst
#                       (BUILD_DEPENDS pulseaudio von libmpg123 entfällt für den Lauf).
#   PHASE               all (Standard): Bootstraps + Pakete + Repo in einem Lauf (ersetzt das Repo komplett).
#                       build:    nur REPO_PACKAGES für die Architekturen in ARCHS bauen (keine Bootstraps); die .deb liegen
#                                 danach in terminal-packages-codeforge/output/. Für parallele Läufe pro Architektur.
#                       assemble: kein Docker; fügt die .deb aus EXTRA_DEBS_DIR dem bestehenden Repo (BASE_REPO_DIR) hinzu,
#                                 erzeugt Packages/Release neu und signiert. Die Bootstraps bleiben unverändert.
#   BASE_REPO_DIR       (assemble) Verzeichnis des bestehenden Repos (Checkout von gh-pages)
#   EXTRA_DEBS_DIR      (assemble) Verzeichnis mit den neu gebauten .deb (rekursiv)
#   SKIP_DEPS_PACKAGES  Pakete, die ohne Abhängigkeiten gebaut werden (build-package.sh -s), Komma-getrennt. Nur für
#                       Pakete ohne BUILD_DEPENDS (z. B. gradle, kotlin): spart den erneuten Bau von openjdk-21.
#   KEEP_GOING          1 = in PHASE=build bei einem fehlgeschlagenen Paket weitermachen und es am Ende melden (Standard 0)
#   CODEFORGE_APT_URL   öffentliche URL des APT-Repos (Standard: GitHub Pages dieses Repos)
#   CODEFORGE_FORK_URL  Fork mit den Paketquellen (Standard: scto/terminal-packages-codeforge)
#   CODEFORGE_GPG_PASSPHRASE  Passphrase (sonst Zeile aus ~/.bashrc)
# =============================================================================
set -eo pipefail
umask 077   # Schlüssel und Temp-Verzeichnisse nur für den Benutzer lesbar

WORKSPACE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CLONE_DIR="${WORKSPACE_DIR}/terminal-packages-codeforge"
OUTPUT_DIR="${WORKSPACE_DIR}/output"
BOOTSTRAP_DIR="${OUTPUT_DIR}/bootstraps"
REPO_DIR="${WORKSPACE_DIR}/github_repo_ready"
GPG_DIR="${WORKSPACE_DIR}/.gpg"
DOCS_DIR="${WORKSPACE_DIR}/docs"
TMP_ROOT="${WORKSPACE_DIR}/tmp"

FORK_URL="${CODEFORGE_FORK_URL:-https://github.com/scto/terminal-packages-codeforge}"
APT_URL="${CODEFORGE_APT_URL:-https://scto.github.io/terminal-packages-codeforge}"
APT_URL="${APT_URL%/}"
ARCHS_STR="${ARCHS:-aarch64 arm i686 x86_64}"
read -r -a ARCHS_LIST <<< "${ARCHS_STR}"
EXTRA_PACKAGES="${EXTRA_PACKAGES:-}"
REPO_PACKAGES="${REPO_PACKAGES:-}"
APP_PACKAGE="com.codeforge.app"
PREFIX_PATH="/data/data/${APP_PACKAGE}/files/usr"

KEY_NAME="Thomas Schmid"
KEY_EMAIL="tschmid35@gmail.com"

die() { echo "FEHLER: $*" >&2; exit 1; }

PHASE="${PHASE:-all}"
case "${PHASE}" in all|build|assemble) ;; *) die "PHASE muss all, build oder assemble sein (ist: ${PHASE})." ;; esac
BASE_REPO_DIR="${BASE_REPO_DIR:-}"
EXTRA_DEBS_DIR="${EXTRA_DEBS_DIR:-}"
SKIP_DEPS_PACKAGES="${SKIP_DEPS_PACKAGES:-}"
KEEP_GOING="${KEEP_GOING:-0}"
FAIL_ON_SKIPPED="${FAIL_ON_SKIPPED:-false}"
FAILED_PKGS=()

mkdir -p "${TMP_ROOT}"
export TMPDIR="${TMP_ROOT}"

echo "===================================================="
echo "CodeForge Build & APT Repository Pipeline"
echo "Prefix: ${PREFIX_PATH}"
echo "APT-URL: ${APT_URL}"
echo "Architekturen: ${ARCHS_LIST[*]}"
echo "Phase: ${PHASE}"
echo "===================================================="

# ----------------------------------------------------
# Step 1: Voraussetzungen, Klonen, .gitignore
# ----------------------------------------------------
echo "[Step 1/9] Voraussetzungen & Klonen..."

[ "$(uname -m)" = "x86_64" ] || die "Dieses Skript braucht einen x86_64-Linux-Rechner (gefunden: $(uname -m)). Auf dem Telefon kann der Termux-Builder nicht laufen."
if [ "${PHASE}" = assemble ]; then
    NEED_TOOLS="git gpg curl jq python3 dpkg-deb apt-ftparchive sha256sum"
    [ -n "${EXTRA_DEBS_DIR}" ] && [ -d "${EXTRA_DEBS_DIR}" ] || die "PHASE=assemble braucht EXTRA_DEBS_DIR (Verzeichnis mit den neuen .deb)."
else
    NEED_TOOLS="docker git gpg curl zip unzip jq python3 readelf dpkg-deb apt-ftparchive sha256sum"
fi
for tool in ${NEED_TOOLS}; do
    command -v "${tool}" >/dev/null 2>&1 || die "'${tool}' fehlt (Debian/Ubuntu: apt-get install docker.io git gnupg curl zip unzip jq python3 binutils dpkg apt-utils)."
done
if [ "${PHASE}" != assemble ]; then
    docker info >/dev/null 2>&1 || die "Docker ist nicht erreichbar (läuft der Daemon, gehört der Benutzer zur Gruppe 'docker'?)."
    if [ ! -d "${CLONE_DIR}/.git" ]; then
        git clone --depth 1 "${FORK_URL}" "${CLONE_DIR}"
    else
        echo "Fork bereits vorhanden: ${CLONE_DIR} (Patches werden idempotent angewendet)."
    fi
fi

touch "${WORKSPACE_DIR}/.gitignore"
for entry in ".gpg/" ".gpg/private/" "output/" "github_repo_ready/" "tmp/" "terminal-packages-codeforge/"; do
    grep -qxF "${entry}" "${WORKSPACE_DIR}/.gitignore" || echo "${entry}" >> "${WORKSPACE_DIR}/.gitignore"
done

# ----------------------------------------------------
# Step 2: Prefix prüfen (kein sed auf properties.sh!)   [nicht in PHASE=assemble]
# ----------------------------------------------------
if [ "${PHASE}" != assemble ]; then
echo "[Step 2/9] Prefix prüfen..."
PROPS="${CLONE_DIR}/scripts/properties.sh"
[ -f "${PROPS}" ] || die "scripts/properties.sh nicht gefunden."
grep -q "^TERMUX_APP__PACKAGE_NAME=\"${APP_PACKAGE}\"" "${PROPS}" \
    || die "TERMUX_APP__PACKAGE_NAME in scripts/properties.sh ist nicht \"${APP_PACKAGE}\". Im Fork setzen und committen."
# TERMUX_REPO_* beschreiben das Paket-Repo, aus dem -i/-I Abhängigkeiten laden würde. Zeigt repo.json weiter auf das
# offizielle Termux-Repo (Pakete für com.termux), MÜSSEN diese Werte auf com.termux bleiben (so steht es im Kommentar
# von properties.sh): Dann stimmt der Prefix nicht überein, -i/-I wird ignoriert und alles wird lokal mit dem
# Prefix com.codeforge.app gebaut. Wären sie ebenfalls com.codeforge.app, würden Binaries mit falschem Prefix geladen.
REPO_JSON="${CLONE_DIR}/repo.json"
REPO_PKG_NAME="$(sed -n 's/^TERMUX_REPO_APP__PACKAGE_NAME="\(.*\)"/\1/p' "${PROPS}" | head -n1)"
[ -n "${REPO_PKG_NAME}" ] || die "TERMUX_REPO_APP__PACKAGE_NAME nicht in scripts/properties.sh gefunden."
if [ -f "${REPO_JSON}" ] && grep -q 'termux\.dev' "${REPO_JSON}"; then
    [ "${REPO_PKG_NAME}" != "${APP_PACKAGE}" ] \
        || die "repo.json zeigt auf das offizielle Termux-Repo, aber TERMUX_REPO_APP__PACKAGE_NAME ist '${APP_PACKAGE}'. Auf \"com.termux\" zurücksetzen (und TERMUX_REPO_APP__DATA_DIR, TERMUX_REPO__CORE_DIR, __APPS_DIR, __ROOTFS, __HOME, __PREFIX entsprechend), sonst lädt build-package.sh -i/-I Pakete mit falschem Prefix."
    echo "repo.json zeigt auf das offizielle Repo; TERMUX_REPO_APP__PACKAGE_NAME='${REPO_PKG_NAME}' (richtig: Abhängigkeiten werden lokal gebaut)."
else
    echo "Hinweis: repo.json nicht gefunden bzw. ohne termux.dev – prüfe selbst, dass TERMUX_REPO_* zum Repo in repo.json passen."
fi
# Ein früherer globaler Ersetzungslauf hat URLs und Pfade in Kommentaren verunstaltet (z. B. 'github.com.codeforge.app').
if grep -q "github\.com\.codeforge\.app" "${PROPS}"; then
    echo "Hinweis: 'github.com.codeforge.app' in properties.sh gefunden (alter Ersetzungsfehler, betrifft nur Kommentare). Die Meldung ist harmlos; eine bereinigte properties.sh wurde mitgeliefert."
fi

# Verweise reparieren, die ein früherer globaler `sed s/com.termux/com.codeforge/` zerstört hat (der Punkt matcht auch '/'):
#   github.com/termux/<repo>   ->  github.com.codeforge/<repo>   (kaputte Downloads, z. B. termux-elf-cleaner, flang, ghc)
# Idempotent und eng begrenzt; im Fork sollten die Änderungen zusätzlich einmal committet werden (fork-fix.patch).
repair_mangled_references() {
    local d="$1" f changed=0
    while IFS= read -r f; do
        sed -i -e 's#github\.com\.codeforge\.app/#github.com/termux/#g' -e 's#github\.com\.codeforge/#github.com/termux/#g' "${f}"
        changed=$((changed + 1))
    done < <(grep -rlIE 'github\.com\.codeforge(\.app)?/' --exclude-dir=.git "${d}" || true)
    # Pfad INNERHALB des heruntergeladenen offiziellen glibc-Pakets: bleibt com.termux.
    local cgct="${d}/scripts/build/termux_step_setup_cgct_environment.sh"
    if [ -f "${cgct}" ] && grep -q 'data/data/com\.codeforge/files/usr/glibc' "${cgct}"; then
        sed -i 's#data/data/com\.codeforge/files/usr/glibc#data/data/com.termux/files/usr/glibc#' "${cgct}"; changed=$((changed + 1))
    fi
    if [ -f "${d}/scripts/test-runner.sh" ] && head -n1 "${d}/scripts/test-runner.sh" | grep -q 'data/data/com\.codeforge/files'; then
        sed -i "1s#/data/data/com\.codeforge/files#/data/data/${APP_PACKAGE}/files#" "${d}/scripts/test-runner.sh"; changed=$((changed + 1))
    fi
    if [ -f "${d}/scripts/build-bootstraps.sh" ]; then
        sed -i "s#It defaults to 'com\.codeforge'\.#It defaults to '${APP_PACKAGE}'.#" "${d}/scripts/build-bootstraps.sh"
    fi
    echo "Reparatur beschädigter Verweise: ${changed} Datei(en) angepasst."
    bad="$(grep -rlIE 'github\.com\.codeforge' --exclude-dir=.git "${d}" || true)"
    [ -z "${bad}" ] || die "Beschädigte Verweise bleiben: ${bad}"
}
repair_mangled_references "${CLONE_DIR}"

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

# Paketnamen in build-bootstraps.sh, die nur als Subpackage existieren (z. B. "bzip2" ist Subpackage von "libbz2"):
# build-package.sh kennt nur Verzeichnisse und bricht mit "No package bzip2 found in any of the enabled repositories" ab.
# Ersetzt den Namen durch das Elternpaket; dessen Build erzeugt das Subpackage-.deb mit. Idempotent.
fix_bootstrap_subpackage_names() {
    local d="$1" f="$1/scripts/build-bootstraps.sh" name sub parent n=0
    [ -f "${f}" ] || die "scripts/build-bootstraps.sh fehlt im Fork."
    while IFS= read -r name; do
        [ -n "${name}" ] || continue
        if [ -d "${d}/packages/${name}" ] || [ -d "${d}/root-packages/${name}" ] || [ -d "${d}/x11-packages/${name}" ]; then
            continue
        fi
        sub="$(find "${d}/packages" "${d}/root-packages" "${d}/x11-packages" -maxdepth 2 -name "${name}.subpackage.sh" 2>/dev/null | head -n1 || true)"
        [ -n "${sub}" ] || die "Paket '${name}' aus build-bootstraps.sh existiert weder als Paket noch als Subpackage."
        parent="$(basename "$(dirname "${sub}")")"
        sed -i "s#PACKAGES+=(\"${name}\")#PACKAGES+=(\"${parent}\") \# ${name} ist Subpackage von ${parent}#" "${f}"
        echo "build-bootstraps.sh: '${name}' -> '${parent}' (Subpackage)"
        n=$((n + 1))
    done < <(grep -oE 'PACKAGES\+=\("[a-z0-9+._-]+"\)' "${f}" | sed -E 's/.*\("(.*)"\)/\1/' | sort -u || true)
    echo "Subpackage-Namen korrigiert: ${n}"
}

# Das Docker-Profil (scripts/profile-restricted.apparmor) verbietet dem Container Schreibzugriff auf das Repo-Root
# ("deny /home/builder/termux-packages/[^o]** wlk"); erlaubt ist nur output/. build-bootstraps.sh verschiebt die fertige ZIP
# aber ins Repo-Root -> "mv: cannot create regular file ... Permission denied" nach Stunden Bauzeit. Ziel nach output/ umbiegen
# und bei Fehlschlag sofort abbrechen. Idempotent; bricht ab, wenn die Zeile nicht gefunden wird.
# shellcheck disable=SC2016 # Literal dollar variables intended for grep -F pattern matching
fix_bootstrap_zip_destination() {
    local f="$1/scripts/build-bootstraps.sh" before after
    [ -f "${f}" ] || die "scripts/build-bootstraps.sh fehlt im Fork."
    if grep -qF 'bootstrap-${1}.zip" "$TERMUX_PACKAGES_DIRECTORY/output/" || exit 1' "${f}"; then
        echo "build-bootstraps.sh: ZIP-Ziel bereits output/."
        return 0
    fi
    before="$(grep -cF 'mv -f "${BOOTSTRAP_TMPDIR}/bootstrap-${1}.zip" "$TERMUX_PACKAGES_DIRECTORY/"' "${f}" || true)"
    [ "${before}" = "1" ] || die "mv-Zeile für bootstrap-<arch>.zip in build-bootstraps.sh nicht eindeutig gefunden (Treffer: ${before})."
    sed -i 's#mv -f "${BOOTSTRAP_TMPDIR}/bootstrap-${1}.zip" "$TERMUX_PACKAGES_DIRECTORY/"#mv -f "${BOOTSTRAP_TMPDIR}/bootstrap-${1}.zip" "$TERMUX_PACKAGES_DIRECTORY/output/" || exit 1#' "${f}"
    after="$(grep -cF 'bootstrap-${1}.zip" "$TERMUX_PACKAGES_DIRECTORY/output/" || exit 1' "${f}" || true)"
    [ "${after}" = "1" ] || die "Patch der mv-Zeile in build-bootstraps.sh fehlgeschlagen."
    echo "build-bootstraps.sh: ZIP-Ziel -> output/ (AppArmor erlaubt nur dort Schreibzugriff)."
}

# Symlinks wiederherstellen: Der Fork enthält nur noch wenige echte Symlinks. Die übrigen wurden beim Kopieren zu kleinen
# Textdateien, deren Inhalt der Link-Pfad ist (z. B. packages/procps/hsearch -> ../../root-packages/arp-scan/hsearch/).
# Folge: "cp: cannot stat '.../procps/hsearch/*.h': Not a directory". Nur eindeutige Fälle: <150 Byte, eine Zeile,
# reiner Pfad, Ziel existiert. Idempotent.
restore_symlinks() {
    python3 - "$1" <<'PY'
import os, re, subprocess, sys
root = sys.argv[1]
out = subprocess.run(["git", "-C", root, "ls-files", "-s", "-z"], capture_output=True, check=True).stdout.decode("utf8", "surrogateescape")
restored = []
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
    target = text.rstrip("\n")
    if not target or "\n" in target or not re.fullmatch(r"[A-Za-z0-9._/+@~-]+/?", target):
        continue
    resolved = os.path.normpath(os.path.join(os.path.dirname(p), target))
    if not os.path.exists(resolved) or os.path.abspath(resolved) == os.path.abspath(p):
        continue
    os.remove(p)
    os.symlink(target, p)
    restored.append(path)
print(f"Symlinks wiederhergestellt: {len(restored)}")
PY
}
# termux-bootstrap-second-stage.sh (Zeile 290) und build-bootstraps.sh reparieren:
# 1) In scripts/bootstrap/termux-bootstrap-second-stage.sh steht Zeile 290 unquotiert:
#      elif [ ${TERMUX_PACKAGE_MANAGER} = "pacman" ]; then
#    Wenn TERMUX_PACKAGE_MANAGER leer ist, bricht `[` mit "[: =: unary operator expected" ab.
# 2) In scripts/build-bootstraps.sh ist TERMUX_PACKAGE_MANAGER nicht vorbelegt (anders als in build-package.sh)
#    und add_termux_bootstrap_second_stage_files wird mit "$package_arch" (lokal in extract_debs -> leer) statt
#    "$TERMUX_ARCH" aufgerufen. Dadurch landen TERMUX_PACKAGE_MANAGER="" und TERMUX_PACKAGE_ARCH="" im Bootstrap.
# 3) dpkg-perl.subpackage.sh führt in postinst `cpan -Ti Locale::gettext` mit `set -e` aus; im Offline-Bootstrap
#    ohne Compiler darf das den Second-Stage-Lauf nicht abbrechen.
# shellcheck disable=SC2016
fix_bootstrap_second_stage() {
    local d="$1"
    local ss="${d}/scripts/bootstrap/termux-bootstrap-second-stage.sh"
    local bb="${d}/scripts/build-bootstraps.sh"
    local dp="${d}/packages/dpkg/dpkg-perl.subpackage.sh"
    [ -f "${ss}" ] || die "scripts/bootstrap/termux-bootstrap-second-stage.sh fehlt im Fork."
    [ -f "${bb}" ] || die "scripts/build-bootstraps.sh fehlt im Fork."

    if grep -qF 'elif [ ${TERMUX_PACKAGE_MANAGER} = "pacman" ]; then' "${ss}"; then
        sed -i 's/elif \[ \${TERMUX_PACKAGE_MANAGER} = "pacman" \]; then/elif [ "${TERMUX_PACKAGE_MANAGER}" = "pacman" ]; then/' "${ss}"
        echo "termux-bootstrap-second-stage.sh: Zeile 290 Variable quotiert."
    fi
    if grep -qF 'export TERMUX_PACKAGE_MANAGER="@TERMUX_PACKAGE_MANAGER@"' "${ss}"; then
        sed -i 's/export TERMUX_PACKAGE_MANAGER="@TERMUX_PACKAGE_MANAGER@"/export TERMUX_PACKAGE_MANAGER="${TERMUX_PACKAGE_MANAGER:-@TERMUX_PACKAGE_MANAGER@}"\n: "${TERMUX_PACKAGE_MANAGER:=apt}"/' "${ss}"
        echo "termux-bootstrap-second-stage.sh: Fallback TERMUX_PACKAGE_MANAGER=apt ergänzt."
    fi

    if grep -qF 's|@TERMUX_PACKAGE_MANAGER@|${TERMUX_PACKAGE_MANAGER}|g' "${bb}"; then
        sed -i 's/s|@TERMUX_PACKAGE_MANAGER@|\${TERMUX_PACKAGE_MANAGER}|g/s|@TERMUX_PACKAGE_MANAGER@|${TERMUX_PACKAGE_MANAGER:-apt}|g/' "${bb}"
        echo "build-bootstraps.sh: @TERMUX_PACKAGE_MANAGER@ -> \${TERMUX_PACKAGE_MANAGER:-apt}."
    fi
    if grep -qF 'add_termux_bootstrap_second_stage_files "$package_arch"' "${bb}"; then
        sed -i 's/add_termux_bootstrap_second_stage_files "\$package_arch"/add_termux_bootstrap_second_stage_files "$TERMUX_ARCH"/' "${bb}"
        echo "build-bootstraps.sh: add_termux_bootstrap_second_stage_files mit \$TERMUX_ARCH statt leerem \$package_arch."
    fi

    if [ -f "${dp}" ]; then
        if grep -qF 'cpan -Ti Locale::gettext' "${dp}" && ! grep -qF 'cpan -Ti Locale::gettext || true' "${dp}"; then
            sed -i 's/cpan -Ti Locale::gettext/command -v make >\/dev\/null 2>\&1 \&\& cpan -Ti Locale::gettext || true/' "${dp}"
            echo "dpkg-perl.subpackage.sh: cpan-Aufruf in postinst abgesichert."
        fi
        if grep -qE 'TERMUX_SUBPKG_DEPENDS=.*clang' "${dp}"; then
            sed -i -E 's/clang,[[:space:]]*//g; s/,[[:space:]]*clang//g' "${dp}"
            echo "dpkg-perl.subpackage.sh: clang aus TERMUX_SUBPKG_DEPENDS entfernt."
        fi
    fi
}

# Bootstrap nur aus der Abhängigkeitshülle der Bootstrap-Liste bauen. scripts/build-bootstraps.sh extrahiert sonst JEDES .deb in
# output/ – also auch Build-Abhängigkeiten und Subpackages, die build-package.sh nebenbei baut (swig, doxygen, docbook-*, xsltproc,
# tor, unbound, python-tkinter, ca-certificates-java, dpkg-perl, dpkg-scanpackages, ...). Lauf 2026-10-22: 159 statt ~80 Pakete,
# dpkg-perl mit unerfüllter Abhängigkeit clang macht apt auf dem Gerät kaputt (Task 45). Hülle = Depends-Felder, wie pull_package.
fix_bootstrap_closure() {
    local d="$1"
    local bb="${d}/scripts/build-bootstraps.sh"
    [ -f "${bb}" ] || die "scripts/build-bootstraps.sh fehlt im Fork."
    python3 -I - "${bb}" <<'PYEOF' || die "Abhängigkeitshülle in build-bootstraps.sh konnte nicht eingebaut werden (siehe oben)."
import sys
p = sys.argv[1]
s = open(p, encoding="utf-8").read()
MARK = "CODEFORGE-CLOSURE"
if MARK in s:
    print("build-bootstraps.sh: Abhängigkeitshülle bereits vorhanden.")
    sys.exit(0)
FUNC = r'''# CODEFORGE-CLOSURE: build-package.sh legt neben den Bootstrap-Paketen auch Build-Abhängigkeiten und Subpackages
# (swig, doxygen, tor, dpkg-perl, ...) in output/ ab. Nur die Abhängigkeitshülle von ${PACKAGES[@]} darf in den Bootstrap.
declare -A CF_CLOSURE=()
compute_bootstrap_closure() {
	local package_arch="$1"
	local deb name arch dep field i=0
	local -A deb_of=()
	local -A via=()
	local -a queue=()
	CF_CLOSURE=()
	for deb in "$TERMUX_BUILT_DEBS_DIRECTORY"/*.deb; do
		[ -e "$deb" ] || continue
		name="$(dpkg-deb -f "$deb" Package)"
		arch="$(dpkg-deb -f "$deb" Architecture)"
		[[ "$arch" == "$package_arch" || "$arch" == "all" ]] || continue
		deb_of["$name"]="$deb"
	done
	for name in "${PACKAGES[@]}"; do
		queue+=("$name")
		via["$name"]="Wurzel"
	done
	while [ "$i" -lt "${#queue[@]}" ]; do
		name="${queue[$i]}"
		i=$((i + 1))
		[ -z "${CF_CLOSURE[$name]:-}" ] || continue
		if [ -z "${deb_of[$name]:-}" ]; then
			echo "[!] WARNUNG: '$name' (benötigt von ${via[$name]:-?}) liegt nicht in output/ und wird nicht aufgenommen."
			continue
		fi
		CF_CLOSURE["$name"]=1
		echo "[*] Bootstrap-Hülle: $name (via ${via[$name]:-?})"
		field="$(dpkg-deb -f "${deb_of[$name]}" Depends 2>/dev/null || true)"
		while IFS= read -r dep; do
			dep="${dep%%|*}"
			dep="${dep%%(*}"
			dep="${dep// /}"
			dep="${dep%%:*}"
			[ -n "$dep" ] || continue
			[ -z "${CF_CLOSURE[$dep]:-}" ] || continue
			[ -n "${via[$dep]:-}" ] || via["$dep"]="$name"
			queue+=("$dep")
		done < <(printf '%s\n' "$field" | tr ',' '\n')
	done
	echo "[*] Bootstrap-Hülle: ${#CF_CLOSURE[@]} Pakete für ${package_arch}."
}

'''
a = "# Extract *.deb files to the bootstrap root.\nextract_debs() {"
if a not in s:
    sys.exit("FEHLER: extract_debs() nicht gefunden – Fork-Stand geändert, Patch anpassen.")
s = s.replace(a, FUNC + a, 1)
b = "\tfor deb in *.deb; do\n"
if s.count(b) != 1:
    sys.exit("FEHLER: 'for deb in *.deb' nicht eindeutig gefunden.")
s = s.replace(b, "\tcompute_bootstrap_closure \"$package_arch\"\n" + b, 1)
c = '\t\tif [[ "$current_package_name" == *"-static" ]]; then\n'
if s.count(c) != 1:
    sys.exit("FEHLER: Static-Prüfung nicht eindeutig gefunden.")
skip = ('\t\tif [ -z "${CF_CLOSURE[$current_package_name]:-}" ]; then\n'
        '\t\t\techo "[*] Überspringe \'$deb\': nicht in der Abhängigkeitshülle des Bootstraps."\n'
        '\t\t\tcontinue\n\t\tfi\n\n')
s = s.replace(c, skip + c, 1)
open(p, "w", encoding="utf-8").write(s)
print("build-bootstraps.sh: Beschränkung auf die Abhängigkeitshülle eingebaut.")
PYEOF
}

restore_symlinks "${CLONE_DIR}"
restore_exec_bits "${CLONE_DIR}"
fix_bootstrap_subpackage_names "${CLONE_DIR}"
fix_bootstrap_zip_destination "${CLONE_DIR}"
fix_bootstrap_second_stage "${CLONE_DIR}"
fix_bootstrap_closure "${CLONE_DIR}"
[ -x "${CLONE_DIR}/packages/termux-core/build/scripts/termux-replace-termux-core-src-scripts" ] || [ ! -e "${CLONE_DIR}/packages/termux-core/build/scripts/termux-replace-termux-core-src-scripts" ] || die "termux-replace-termux-core-src-scripts ist nicht ausführbar."
[ -d "${CLONE_DIR}/packages/procps/hsearch" ] || die "packages/procps/hsearch ist kein Verzeichnis – Symlink-Reparatur unvollständig."
fi   # Ende Step 2 (PHASE != assemble)

# ----------------------------------------------------
# Step 3: GPG-Schlüssel (einmal erzeugen, danach wiederverwenden)
# ----------------------------------------------------
echo "[Step 3/9] GPG-Schlüssel & Prüfsummen..."

# Passphrase: Umgebungsvariable, sonst die Zuweisung aus ~/.bashrc (nicht per `source`; Wert wird nie ausgegeben).
load_gpg_passphrase() {
    if [ -n "${CODEFORGE_GPG_PASSPHRASE:-}" ]; then
        printf '%s' "${CODEFORGE_GPG_PASSPHRASE}"
        return 0
    fi
    local rc="${HOME}/.bashrc" line value
    [ -r "${rc}" ] || { echo "FEHLER: ${rc} nicht lesbar und CODEFORGE_GPG_PASSPHRASE nicht gesetzt." >&2; return 1; }
    line=$(grep -E '^[[:space:]]*(export[[:space:]]+)?CODEFORGE_GPG_PASSPHRASE=' "${rc}" | tail -n1) || true
    [ -n "${line}" ] || { echo "FEHLER: CODEFORGE_GPG_PASSPHRASE steht nicht in ${rc}." >&2; return 1; }
    value="${line#*=}"
    case "${value}" in
        \"*\") value="${value#\"}"; value="${value%\"}" ;;
        \'*\') value="${value#\'}"; value="${value%\'}" ;;
    esac
    [ -n "${value}" ] || { echo "FEHLER: CODEFORGE_GPG_PASSPHRASE in ${rc} ist leer." >&2; return 1; }
    printf '%s' "${value}"
}
KEY_PASS="$(load_gpg_passphrase)"

mkdir -p "${GPG_DIR}/private"
chmod 700 "${GPG_DIR}" "${GPG_DIR}/private"

GNUPGHOME="$(mktemp -d "${TMP_ROOT}/codeforge_gnupg.XXXXXX")"
export GNUPGHOME
chmod 700 "${GNUPGHOME}"
trap 'rm -rf "${GNUPGHOME}"' EXIT

gpg_pass() { printf '%s\n' "${KEY_PASS}" | gpg --batch --yes --pinentry-mode loopback --passphrase-fd 0 "$@"; }

if [ -s "${GPG_DIR}/private/codeforge.gpg" ]; then
    echo "Vorhandenen privaten Schlüssel importieren (kein neuer Schlüssel; Signatur bleibt stabil)."
    gpg_pass --import "${GPG_DIR}/private/codeforge.gpg"
else
    echo "Neuen Schlüssel erzeugen..."
    cat > "${GNUPGHOME}/gen-key-params" <<EOF
Key-Type: RSA
Key-Length: 4096
Name-Real: ${KEY_NAME}
Name-Email: ${KEY_EMAIL}
Expire-Date: 0
Passphrase: ${KEY_PASS}
%commit
EOF
    gpg --batch --pinentry-mode loopback --gen-key "${GNUPGHOME}/gen-key-params"
    rm -f "${GNUPGHOME}/gen-key-params"
    gpg_pass --armor --export-secret-keys "${KEY_EMAIL}" > "${GPG_DIR}/private/codeforge.gpg"
    chmod 600 "${GPG_DIR}/private/codeforge.gpg"
fi

KEY_FINGERPRINT="$(gpg --list-secret-keys --with-colons "${KEY_EMAIL}" | awk -F: '/^fpr:/ {print $10; exit}')"
[ -n "${KEY_FINGERPRINT}" ] || die "Schlüssel-Fingerprint nicht ermittelbar."

# Öffentlicher Schlüssel: BINÄR als .gpg (apt liest in trusted.gpg.d nur binäre .gpg bzw. armored .asc), zusätzlich armored als .asc.
gpg --export "${KEY_FINGERPRINT}" > "${GPG_DIR}/codeforge.gpg"
gpg --armor --export "${KEY_FINGERPRINT}" > "${GPG_DIR}/codeforge.asc"
(cd "${GPG_DIR}" && sha256sum codeforge.gpg > sha256.txt)
(cd "${GPG_DIR}/private" && sha256sum codeforge.gpg > sha256.txt)
echo "Fingerprint: ${KEY_FINGERPRINT}"
echo "Public:  ${GPG_DIR}/codeforge.gpg ($(cut -d' ' -f1 "${GPG_DIR}/sha256.txt"))"
echo "Private: ${GPG_DIR}/private/codeforge.gpg ($(cut -d' ' -f1 "${GPG_DIR}/private/sha256.txt"))"

# ----------------------------------------------------
# Step 4: Fork patchen (idempotent)   [nicht in PHASE=assemble]
# ----------------------------------------------------
if [ "${PHASE}" != assemble ]; then
echo "[Step 4/9] Fork patchen (Keyring + apt-Quelle)..."

KEYRING_DIR="${CLONE_DIR}/packages/termux-keyring"
[ -d "${KEYRING_DIR}" ] || die "packages/termux-keyring fehlt im Fork."
cp -f "${GPG_DIR}/codeforge.gpg" "${KEYRING_DIR}/codeforge.gpg"
cp -f "${GPG_DIR}/codeforge.gpg" "${KEYRING_DIR}/codeforge_pub.gpg"

python3 - "${KEYRING_DIR}/build.sh" "${CLONE_DIR}/packages/apt/build.sh" "${APT_URL}" <<'PY'
import re, sys
keyring, apt, url = sys.argv[1:4]

# 1) termux-keyring installiert zusätzlich codeforge.gpg (sonst vertraut apt dem eigenen Repo nicht).
s = open(keyring, encoding="utf8").read()
line = '\tinstall -Dm600 "$TERMUX_PKG_BUILDER_DIR/codeforge.gpg" "$GPG_SHARE_DIR"\n'
if "codeforge.gpg" not in s:
    anchor = re.search(r'^\tinstall -Dm600 "\$TERMUX_PKG_BUILDER_DIR/termux-pacman\.gpg" "\$GPG_SHARE_DIR"\n', s, re.M)
    if not anchor:
        sys.exit("FEHLER: Anker (termux-pacman.gpg) in termux-keyring/build.sh nicht gefunden")
    s = s[:anchor.end()] + line + s[anchor.end():]
    open(keyring, "w", encoding="utf8").write(s)
    print("termux-keyring: codeforge.gpg eingetragen")

# 2) apt-Quelle zeigt auf das eigene Repo statt auf packages-cf.termux.dev (Pakete dort sind für com.termux gebaut).
s = open(apt, encoding="utf8").read()
new = ('\t{\n'
       '\t\techo "# CodeForge package repository (Prefix com.codeforge.app)"\n'
       f'\t\techo "deb {url} stable main"\n'
       '\t} > $TERMUX_PREFIX/etc/apt/sources.list\n')
pattern = re.compile(r'\t\{\n(?:\t\techo "[^\n]*"\n)+\t\} > \$TERMUX_PREFIX/etc/apt/sources\.list\n')
if pattern.search(s):
    s = pattern.sub(lambda m: new, s, count=1)
    open(apt, "w", encoding="utf8").write(s)
    print("apt: sources.list ->", url)
elif f'deb {url} stable main' in s:
    print("apt: sources.list bereits gepatcht")
else:
    sys.exit("FEHLER: sources.list-Block in packages/apt/build.sh nicht gefunden")
PY

# termux-tools 1.45.0 liest TERMUX_APP_PACKAGE/TERMUX_PREFIX per configure aus der Umgebung (Default: com.termux).
# build-package.sh exportiert diese Variablen nicht -> alle Skripte (login, pkg, termux-*) landen mit com.termux in der APK.
TOOLS_BUILD="${CLONE_DIR}/packages/termux-tools/build.sh"
[ -f "${TOOLS_BUILD}" ] || die "packages/termux-tools/build.sh fehlt im Fork."
python3 - "${TOOLS_BUILD}" <<'PY'
import re, sys
path = sys.argv[1]
s = open(path, encoding="utf8").read()
if "export TERMUX_APP_PACKAGE" in s:
    print("termux-tools: Export bereits vorhanden")
    sys.exit(0)
m = re.search(r'^termux_step_pre_configure\(\) \{\n', s, re.M)
if not m:
    sys.exit("FEHLER: termux_step_pre_configure in termux-tools/build.sh nicht gefunden")
ins = ('\t# Wird von configure.ac gelesen; Prefix/Home/Base-Dir leiten sich daraus ab (/data/data/<pkg>/files/...).\n'
       '\texport TERMUX_APP_PACKAGE\n'
       '\texport TERMUX_BASE_DIR="$TERMUX__ROOTFS" TERMUX_PREFIX="$TERMUX__PREFIX" TERMUX_ANDROID_HOME="$TERMUX__HOME"\n'
       '\texport TERMUX_CACHE_DIR="$TERMUX_APP__DATA_DIR/cache"\n')
open(path, "w", encoding="utf8").write(s[:m.end()] + ins + s[m.end():])
print("termux-tools: TERMUX_APP_PACKAGE & Pfade exportiert")
PY

# Quell-Downloads robuster machen: salsa.debian.org bricht HTTP/2-Downloads gelegentlich mit "nghttp2 ... PROTOCOL_ERROR" ab
# (hat den Lauf nach ~1 h bei 'apt' beendet). HTTP/1.1 erzwingen und öfter wiederholen.
DL_SCRIPT="${CLONE_DIR}/scripts/build/termux_download.sh"
if [ -f "${DL_SCRIPT}" ]; then
    if grep -q -- '--http1.1' "${DL_SCRIPT}"; then
        echo "termux_download.sh: bereits gepatcht"
    else
        sed -i -e 's/^\(\t\t\)--location           # Follow redirects$/\1--location           # Follow redirects\n\1--http1.1            # CodeForge: HTTP\/2-PROTOCOL_ERROR bei salsa.debian.org vermeiden/' \
               -e 's/^\t\tlocal retry=2$/\t\tlocal retry=5/' "${DL_SCRIPT}"
        grep -q -- '--http1.1' "${DL_SCRIPT}" || die "termux_download.sh: Patch (--http1.1) nicht angewendet; Datei im Fork prüfen."
        echo "termux_download.sh: --http1.1, 5 Wiederholungen"
    fi
fi

# Schlüssel für Abhängigkeits-Downloads (-I) von build-package.sh; für build-bootstraps.sh nicht nötig, aber konsistent.
BUILD_PKG_SCRIPT="${CLONE_DIR}/build-package.sh"
if [ -f "${BUILD_PKG_SCRIPT}" ]; then
    python3 - "${BUILD_PKG_SCRIPT}" "${KEY_FINGERPRINT}" <<'PY'
import re, sys
path, key = sys.argv[1:3]
s = open(path, encoding="utf8").read()
if key in s:
    print("build-package.sh: GPG-Block bereits gepatcht")
    sys.exit(0)
block = ('\tgpg --list-keys %s >/dev/null 2>&1 || {\n'
         '\t\tgpg --import "$TERMUX_SCRIPTDIR/packages/termux-keyring/codeforge_pub.gpg"\n'
         '\t\tgpg --no-tty --command-file <(echo -e "trust\\n5\\ny") --edit-key %s\n'
         '\t}') % (key, key)
pat = re.compile(r'\t*gpg --list-keys\s+[0-9A-Fa-f]+[\s\S]*?--edit-key\s+[0-9A-Fa-f]+\s*\n\t*\}')
if pat.search(s):
    open(path, "w", encoding="utf8").write(pat.sub(lambda m: block, s, count=1))
    print("build-package.sh: GPG-Block ersetzt")
else:
    print("Hinweis: GPG-Block in build-package.sh nicht gefunden (nur für -I relevant).")
PY
fi
fi   # Ende Step 4 (PHASE != assemble)

# ----------------------------------------------------
# Step 5: Pakete + Bootstraps aus dem Quellcode bauen (Docker)
# ----------------------------------------------------
if [ "${PHASE}" != assemble ]; then
if [ "${PHASE}" = all ]; then
    echo "[Step 5/9] Bootstraps bauen (Docker, build-bootstraps.sh) – dauert Stunden..."
else
    echo "[Step 5/9] Nur Pakete bauen (PHASE=build, keine Bootstraps)..."
    [ -n "${REPO_PACKAGES}" ] || die "PHASE=build braucht REPO_PACKAGES."
fi
cd "${CLONE_DIR}"
[ -x scripts/run-docker.sh ] || die "scripts/run-docker.sh fehlt/nicht ausführbar."
mkdir -p "${CLONE_DIR}/output"
if [ "${PHASE}" = all ]; then
[ -f scripts/build-bootstraps.sh ] || die "scripts/build-bootstraps.sh fehlt im Fork."

ARCH_CSV="$(IFS=,; echo "${ARCHS_LIST[*]}")"
BUILD_ARGS=(--architectures "${ARCH_CSV}")
[ -n "${EXTRA_PACKAGES}" ] && BUILD_ARGS+=(--add "${EXTRA_PACKAGES}")

mkdir -p "${CLONE_DIR}/output"
rm -f "${CLONE_DIR}"/bootstrap-*.zip "${CLONE_DIR}"/output/bootstrap-*.zip
./scripts/run-docker.sh ./scripts/build-bootstraps.sh "${BUILD_ARGS[@]}"

for arch in "${ARCHS_LIST[@]}"; do
    [ -s "${CLONE_DIR}/output/bootstrap-${arch}.zip" ] || die "output/bootstrap-${arch}.zip wurde nicht erzeugt."
done
ls "${CLONE_DIR}"/output/*.deb >/dev/null 2>&1 || die "Keine .deb-Dateien in ${CLONE_DIR}/output (build-bootstraps.sh hätte sie erzeugen müssen)."
fi   # Ende Bootstrap-Build (nur PHASE=all)

# Zusätzliche Pakete NUR fürs APT-Repo (nicht ins Bootstrap): einzeln pro Architektur aus dem Quellcode bauen.
# Abhängigkeiten werden von build-package.sh ebenfalls aus dem Quellcode gebaut (kein -I, keine Pakete vom offiziellen Repo).
if [ -n "${REPO_PACKAGES}" ]; then
    [ -f build-package.sh ] || die "build-package.sh fehlt im Fork."
    IFS=',' read -r -a REPO_PKG_LIST <<< "${REPO_PACKAGES}"
    # Workaround: scripts/buildorder.py sortiert für openjdk-17/-21/-25 (alle: DEPENDS alsa-plugins -> pulseaudio) pulseaudio VOR libsndfile
    # (Reihenfolge laut Log: ... speexdsp, pulseaudio, libmpg123, ..., libsndfile). pulseaudio bricht dann mit
    # 'Dependency "sndfile" not found' ab. Baut man libsndfile zuerst, gilt es als gebaut und die Reihenfolge ist egal.
    # Überschreibbar: PREBUILD_PACKAGES="" schaltet es ab.
    if [ -z "${PREBUILD_PACKAGES+x}" ]; then
        PREBUILD_PACKAGES=""
        case ",${REPO_PACKAGES}," in
            *,openjdk-17,*|*,openjdk-21,*|*,openjdk-25,*|*,alsa-plugins,*|*,pulseaudio,*) PREBUILD_PACKAGES="libsndfile" ;;
        esac
    fi
    if [ -n "${PREBUILD_PACKAGES}" ]; then
        IFS=',' read -r -a _pre <<< "${PREBUILD_PACKAGES}"
        _merged=()
        for p in "${_pre[@]}" "${REPO_PKG_LIST[@]}"; do
            [ -n "${p}" ] || continue
            case " ${_merged[*]-} " in *" ${p} "*) continue ;; esac
            _merged+=("${p}")
        done
        REPO_PKG_LIST=("${_merged[@]}")
        echo "  Vorab gebaut (Reihenfolge-Workaround): ${PREBUILD_PACKAGES}"
    fi
    # Zyklus: libsndfile -> libmp3lame -> libmpg123 -> pulseaudio -> libsndfile.
    # Auch mit Vorab-Build von libsndfile baut build-package.sh pulseaudio als Abhängigkeit VOR libsndfile
    # ('Dependency "sndfile" not found'). Lauf 39299099533 (H1-jdk17): Das Leeren von BUILD_DEPENDS in
    # packages/libmpg123/build.sh allein änderte die Reihenfolge von buildorder.py nicht. Die Kante steckt vermutlich
    # zusätzlich in Teilpaket-Dateien (z. B. packages/libmpg123/*.subpackage.sh, TERMUX_SUBPKG_DEPENDS). Deshalb werden
    # alle build.sh/*.subpackage.sh von libmpg123 und libmp3lame bereinigt: "pulseaudio" fällt aus *DEPENDS-Zeilen weg
    # (nur für diesen Lauf, danach wiederhergestellt). Es dient nur der Pulse-Ausgabe des mpg123-Players.
    CYCLE_FILES=()
    CYCLE_DROP_DEBS=()
    if [ "${PREBUILD_PACKAGES:-}" != "" ]; then
        for f in packages/libmpg123/*.sh packages/libmp3lame/*.sh; do
            [ -f "${f}" ] || continue
            grep -qE '^[[:space:]]*TERMUX_(PKG|SUBPKG)_[A-Z_]*DEPENDS=.*pulseaudio' "${f}" || continue
            cp "${f}" "${f}.orig"
            sed -i -E '/^[[:space:]]*TERMUX_(PKG|SUBPKG)_[A-Z_]*DEPENDS=.*pulseaudio/ {
                s/,[[:space:]]*pulseaudio//g
                s/pulseaudio,[[:space:]]*//g
                s/pulseaudio//g
            }' "${f}"
            CYCLE_FILES+=("${f}")
            echo "  Zyklus aufgelöst: pulseaudio aus ${f} entfernt (wird danach wiederhergestellt)"
            # Teilpakete (z. B. mpg123) hätten sonst eine Depends-Zeile ohne pulseaudio im Repo.
            case "${f}" in *.subpackage.sh) CYCLE_DROP_DEBS+=("$(basename "${f}" .subpackage.sh)") ;; esac
        done
        [ "${#CYCLE_FILES[@]}" -gt 0 ] || echo "  Hinweis: in libmpg123/libmp3lame wurde kein 'pulseaudio' in DEPENDS-Zeilen gefunden."
    fi
    # Build-Reihenfolge ins Log schreiben (Diagnose): zeigt, ob pulseaudio noch in der Kette liegt.
    log_buildorder() {
        local target="$1"
        [ -f scripts/buildorder.py ] && [ -d "packages/${target}" ] || return 0
        echo "::group::buildorder.py ${target}"
        local order
        order="$(python3 scripts/buildorder.py "packages/${target}" packages 2>&1 | awk 'NF {print $1}' || true)"
        printf '%s\n' "${order}"
        echo "::endgroup::"
        if printf '%s\n' "${order}" | grep -qx 'pulseaudio'; then
            echo "  WARNUNG: pulseaudio liegt in der Kette von ${target} (Zyklus mit libsndfile möglich)."
            grep -rn 'pulseaudio' packages/libmpg123 packages/libmp3lame packages/libsndfile 2>/dev/null | sed 's/^/    /' || true
        fi
    }
    is_prefix_exception() {
        local target_pkg="$1"
        local full_file="$2"
        local chk_root="$3"
        local exceptions_file="${WORKSPACE_DIR}/prefix-exceptions.conf"
        [ -f "${exceptions_file}" ] || return 1

        # 1. Muss ein reguläres ELF-Binary sein (ELF magic: \x7fELF)
        [ -f "${full_file}" ] || return 1
        [ "$(head -c 4 "${full_file}" 2>/dev/null)" = $'\x7fELF' ] || return 1

        # 2. Relativer Pfad ermitteln
        local rel_file="${full_file#"${chk_root}"/}"
        local usr_rel="${rel_file#data/data/*/files/}"
        usr_rel="${usr_rel#/}"

        while IFS='|' read -r ex_pkg ex_glob ex_reason || [ -n "${ex_pkg}" ]; do
            [[ "${ex_pkg}" =~ ^[[:space:]]*# ]] && continue
            [ -n "${ex_pkg}" ] || continue
            ex_pkg="$(echo "${ex_pkg}" | xargs)"
            ex_glob="$(echo "${ex_glob}" | xargs)"
            ex_reason="$(echo "${ex_reason}" | xargs)"

            if [ "${ex_pkg}" = "${target_pkg}" ]; then
                # Glob-Prüfung gegen usr_rel und rel_file
                # shellcheck disable=SC2053
                if [[ "${usr_rel}" == ${ex_glob} ]] || [[ "${rel_file}" == ${ex_glob} ]] || [[ "${usr_rel}" == *"${ex_glob}"* ]]; then
                    echo "${ex_reason}"
                    return 0
                fi
            fi
        done < "${exceptions_file}"

        return 1
    }

    if [ -n "${PREBUILD_PACKAGES}" ]; then log_buildorder libsndfile; fi
    for arch in "${ARCHS_LIST[@]}"; do
        for pkg in "${REPO_PKG_LIST[@]}"; do
            [ -n "${pkg}" ] || continue
            # Unterpakete (z. B. protobuf -> libprotobuf, aapt2 -> aapt) haben kein eigenes packages/<name>/build.sh:
            # dann das Elternpaket bauen; die .deb des Unterpakets entsteht dabei mit.
            build_pkg="${pkg}"
            if [ ! -f "packages/${pkg}/build.sh" ]; then
                sub="$(find packages -maxdepth 2 -name "${pkg}.subpackage.sh" 2>/dev/null | head -n1 || true)"
                if [ -n "${sub}" ]; then
                    build_pkg="$(basename "$(dirname "${sub}")")"
                else
                    # Ersatzpaket: z. B. 'p7zip' wird im Fork von '7zip' bereitgestellt (TERMUX_PKG_PROVIDES).
                    prov="$(grep -lE "^TERMUX_PKG_PROVIDES=.*([\" ,]|^)${pkg}([\" ,(]|\$)" packages/*/build.sh 2>/dev/null | head -n1 || true)"
                    [ -n "${prov}" ] || die "${pkg}: weder packages/${pkg}/build.sh noch packages/*/${pkg}.subpackage.sh noch ein Paket mit TERMUX_PKG_PROVIDES=${pkg} im Fork."
                    build_pkg="$(basename "$(dirname "${prov}")")"
                    echo "  Hinweis: '${pkg}' gibt es im Fork nicht, es wird von '${build_pkg}' bereitgestellt (PROVIDES) – baue '${build_pkg}'."
                    pkg="${build_pkg}"
                fi
            fi
            echo "  -> ${pkg} (${arch})$([ "${build_pkg}" != "${pkg}" ] && echo " via ${build_pkg}")"
            [ "${arch}" = "${ARCHS_LIST[0]}" ] && log_buildorder "${build_pkg}"
            build_args=(-a "${arch}" -o output)
            case ",${SKIP_DEPS_PACKAGES}," in *,"${pkg}",*) build_args+=(-s); echo "     (ohne Abhängigkeiten: -s)" ;; esac
            build_success=0
            for attempt in 1 2 3; do
                if ./scripts/run-docker.sh ./build-package.sh "${build_args[@]}" "${build_pkg}"; then
                    build_success=1
                    break
                fi
                if [ "${attempt}" -lt 3 ]; then
                    echo "WARNUNG: build-package.sh ${build_pkg} (${arch}) fehlgeschlagen (Versuch ${attempt}/3). Wiederhole nach 15s..." >&2
                    sleep 15
                fi
            done
            if [ "${build_success}" -ne 1 ]; then
                if [ "${KEEP_GOING}" = 1 ]; then
                    echo "FEHLGESCHLAGEN: ${pkg} (${arch}) – weiter mit dem nächsten Paket (KEEP_GOING=1)." >&2
                    FAILED_PKGS+=("${pkg} (${arch})"); continue
                fi
                die "${pkg} (${arch}): build-package.sh fehlgeschlagen."
            fi
            # Architekturunabhängige Pakete (z. B. gradle, kotlin) heißen *_all.deb.
            shopt -s nullglob
            pkg_debs=(output/"${pkg}"_*_"${arch}".deb output/"${pkg}"_*_all.deb)
            shopt -u nullglob
            if [ "${#pkg_debs[@]}" -eq 0 ]; then
                msg="${pkg}: keine ${pkg}_*_${arch}.deb bzw. *_all.deb in output/ (Paketname im Fork vorhanden? Baut es für ${arch}?)."
                if [ "${KEEP_GOING}" = 1 ]; then echo "FEHLGESCHLAGEN: ${msg}" >&2; FAILED_PKGS+=("${pkg} (${arch})"); continue; fi
                die "${msg}"
            fi
            # Das fertige Paket darf keinen alten Prefix enthalten (ELF und Text).
            for deb in "${pkg_debs[@]}"; do
                chk="$(mktemp -d "${TMP_ROOT}/debcheck.XXXXXX")"
                dpkg-deb -x "${deb}" "${chk}"
                bad="$(grep -rla 'com\.termux' "${chk}" || true)"
                if [ -n "${bad}" ]; then
                    real_bad=()
                    while IFS= read -r hit; do
                        [ -n "${hit}" ] || continue
                        if reason="$(is_prefix_exception "${pkg}" "${hit}" "${chk}")"; then
                            echo "AUSNAHME: ${pkg}: ${hit#"${chk}"} enthält com.termux (siehe prefix-exceptions.conf: ${reason})"
                            if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
                                echo "ℹ️ **AUSNAHME:** \`${pkg}\`: \`${hit#"${chk}"}\` enthält \`com.termux\` (Begründung: ${reason})" >> "${GITHUB_STEP_SUMMARY}"
                            fi
                        else
                            real_bad+=("${hit}")
                        fi
                    done <<< "${bad}"

                    if [ "${#real_bad[@]}" -gt 0 ]; then
                        echo "FEHLER: ${deb} enthält 'com.termux':" >&2
                        printf '%s\n' "${real_bad[@]}" | head -10 >&2
                        # Fundstellen mit Kontext zeigen, damit die Ursache im Log steht (Text: Zeile, Binärdatei: Umgebung).
                        printf '%s\n' "${real_bad[@]}" | head -10 | while IFS= read -r hit; do
                            echo "--- Fundstelle in ${hit#"${chk}"}:" >&2
                            grep -a -o '.\{0,60\}com\.termux.\{0,60\}' "${hit}" 2>/dev/null | head -10 >&2 || true
                        done
                        rm -rf "${chk}"
                        if [ "${KEEP_GOING}" = 1 ]; then
                            echo "FEHLGESCHLAGEN: ${pkg}: .deb enthält 'com.termux' (siehe oben)." >&2
                            echo "::warning title=Prefix-Check fehlgeschlagen::${pkg} (${arch}): .deb enthält 'com.termux' und wurde verworfen."
                            FAILED_PKGS+=("${pkg} (${arch})")
                            rm -f "${deb}"   # kaputtes Paket nicht ins Repo übernehmen
                            continue 2
                        fi
                        exit 1
                    fi
                fi
                rm -rf "${chk}"
            done
        done
    done
    for f in "${CYCLE_FILES[@]-}"; do
        [ -n "${f}" ] && [ -f "${f}.orig" ] && mv -f "${f}.orig" "${f}"
    done
    # .deb von Teilpaketen mit geänderten Abhängigkeiten nicht ins Repo übernehmen.
    for n in "${CYCLE_DROP_DEBS[@]-}"; do
        [ -n "${n}" ] || continue
        rm -f output/"${n}"_*.deb && echo "  Nicht übernommen (geänderte Abhängigkeiten): ${n}"
    done
fi

if [ "${PHASE}" = build ]; then
    cd "${WORKSPACE_DIR}"
    FAILED_FILE="${WORKSPACE_DIR}/failed-packages-${ARCHS_LIST[*]// /_}.txt"
    : > "${FAILED_FILE}"
    [ "${#FAILED_PKGS[@]}" -eq 0 ] || printf '%s\n' "${FAILED_PKGS[@]}" > "${FAILED_FILE}"
    cp -f "${FAILED_FILE}" "${WORKSPACE_DIR}/failed-packages.txt" 2>/dev/null || true
    echo "PHASE=build fertig: $(find "${CLONE_DIR}/output" -maxdepth 1 -name '*.deb' 2>/dev/null | wc -l) .deb in ${CLONE_DIR}/output, Fehlgeschlagen: ${#FAILED_PKGS[@]}"
    if [ "${#FAILED_PKGS[@]}" -gt 0 ]; then
        echo "Fehlgeschlagene Pakete:"
        printf '  %s\n' "${FAILED_PKGS[@]}"
        if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
            {
                echo "### ⚠️ Verworfene / fehlgeschlagene Pakete (${ARCHS_LIST[*]}):"
                printf '* %s\n' "${FAILED_PKGS[@]}"
            } >> "${GITHUB_STEP_SUMMARY}"
        fi
        for fp in "${FAILED_PKGS[@]}"; do
            echo "::warning::Verworfenes/fehlgeschlagenes Paket: ${fp}"
        done
        if [ "${FAIL_ON_SKIPPED:-false}" = "true" ] || [ "${FAIL_ON_SKIPPED:-0}" = "1" ]; then
            die "fail_on_skipped aktiv: ${#FAILED_PKGS[@]} Paket(e) fehlgeschlagen oder verworfen."
        fi
    fi
    exit 0
fi
fi   # Ende Step 5 (PHASE != assemble)

# ----------------------------------------------------
# Step 6: Verifikation der Bootstraps (hart, ohne Ausnahmen)   [nur PHASE=all]
# ----------------------------------------------------
if [ "${PHASE}" = all ]; then
echo "[Step 6/9] Bootstraps verifizieren..."
VERIFY_DIR="${TMP_ROOT}/verify"
rm -rf "${VERIFY_DIR}"; mkdir -p "${VERIFY_DIR}"
verify_failed=0
for arch in "${ARCHS_LIST[@]}"; do
    dest="${VERIFY_DIR}/${arch}"; mkdir -p "${dest}"
    unzip -q "${CLONE_DIR}/output/bootstrap-${arch}.zip" -d "${dest}"

    # a) kein Pfad und kein Textinhalt mit com.termux
    bad_paths="$(find "${dest}" -path '*com.termux*')"
    if [ -n "${bad_paths}" ]; then
        echo "[${arch}] Pfade mit com.termux gefunden:" >&2; printf '%s\n' "${bad_paths}" | head >&2; verify_failed=1
    fi
    # Erlaubt: Kommentarzeilen (Beispielpfade in Doku/Header) und die Java-Klasse com.termux.termuxam.Am
    # (liegt fest in am.apk; nur der Paketname der App wird per @TERMUX_APP_PACKAGE@ ersetzt, nicht diese Klasse).
    bad_text=""
    while IFS= read -r tf; do
        hits="$(grep -naI 'com\.termux' "${tf}" \
            | sed -E 's/com\.termux\.termuxam\.Am//g' \
            | grep -a 'com\.termux' \
            | grep -avE '^[0-9]+:[[:space:]]*(#|\*|//|/\*)' || true)"
        if [ -n "${hits}" ]; then
            bad_text+="${tf}"$'\n'
            echo "  --- ${tf#"${dest}"/}" >&2
            printf '%s\n' "${hits}" | head -n 3 | cut -c1-240 >&2
        fi
    done < <(grep -rIl 'com\.termux' "${dest}" || true)
    if [ -n "${bad_text}" ]; then
        echo "[${arch}] Textdateien mit com.termux (siehe Zeilen oben):" >&2
        printf '%s' "${bad_text}" | head -n 50 >&2; verify_failed=1
    fi
    # b) jede ELF-Datei muss gültige Header haben und darf com.termux nicht enthalten
    while IFS= read -r f; do
        if cmp -s -n 4 "${f}" <(printf '\177ELF'); then
            if grep -qa 'com\.termux' "${f}"; then
                echo "[${arch}] ELF mit com.termux: ${f#"${dest}"/}" >&2; verify_failed=1
            fi
            elf_msgs="$(readelf -h -l -S -W "${f}" 2>&1 >/dev/null)" || elf_msgs="readelf-Fehler"
            if [ -n "${elf_msgs}" ] && printf '%s' "${elf_msgs}" | grep -qiE 'warning|error'; then
                echo "[${arch}] ELF defekt (readelf): ${f#"${dest}"/}" >&2; verify_failed=1
            fi
        fi
    done < <(find "${dest}" -type f)
    # c) apt-Quelle und Keyring
    grep -qF "${APT_URL}" "${dest}/etc/apt/sources.list" || { echo "[${arch}] etc/apt/sources.list zeigt nicht auf ${APT_URL}" >&2; verify_failed=1; }
    # Keys liegen als echte Datei in share/termux-keyring/; trusted.gpg.d/codeforge.gpg ist ein Symlink (steht nur in SYMLINKS.txt, nicht als Datei im ZIP).
    [ -s "${dest}/share/termux-keyring/codeforge.gpg" ] || { echo "[${arch}] share/termux-keyring/codeforge.gpg fehlt" >&2; verify_failed=1; }
    grep -qF '/share/termux-keyring/codeforge.gpg←./etc/apt/trusted.gpg.d/codeforge.gpg' "${dest}/SYMLINKS.txt" \
        || { echo "[${arch}] SYMLINKS.txt ohne Link etc/apt/trusted.gpg.d/codeforge.gpg" >&2; verify_failed=1; }
    grep -q "${PREFIX_PATH}" "${dest}/SYMLINKS.txt" || { echo "[${arch}] SYMLINKS.txt ohne ${PREFIX_PATH}" >&2; verify_failed=1; }
    # d) termux-bootstrap-second-stage.sh (keine unquotierte Variable in Zeile 290, TERMUX_PACKAGE_MANAGER/ARCH gesetzt)
    ss_file="${dest}/etc/termux/termux-bootstrap/second-stage/termux-bootstrap-second-stage.sh"
    if [ -f "${ss_file}" ]; then
        # shellcheck disable=SC2016
        if grep -qF 'elif [ ${TERMUX_PACKAGE_MANAGER} = "pacman" ]; then' "${ss_file}"; then
            echo "[${arch}] second-stage enthält unquotierte TERMUX_PACKAGE_MANAGER in Zeile 290" >&2; verify_failed=1
        fi
        if grep -qF 'export TERMUX_PACKAGE_MANAGER=""' "${ss_file}"; then
            echo "[${arch}] second-stage hat leeres TERMUX_PACKAGE_MANAGER" >&2; verify_failed=1
        fi
    fi
    echo "[${arch}] geprüft: $(find "${dest}" -type f | wc -l) Dateien"

    if [ -f "${WORKSPACE_DIR}/scripts/check-apt-consistency.py" ]; then
        echo "  [${arch}] Prüfe Abhängigkeits-Konsistenz (check-apt-consistency.py)..."
        python3 -I "${WORKSPACE_DIR}/scripts/check-apt-consistency.py" bootstrap "${CLONE_DIR}/output/bootstrap-${arch}.zip" || verify_failed=1
    fi
done
rm -rf "${VERIFY_DIR}"
[ "${verify_failed}" -eq 0 ] || die "Verifikation fehlgeschlagen – Bootstraps NICHT veröffentlichen."

# ----------------------------------------------------
# Step 7: Bootstraps + Prüfsummen ablegen
# ----------------------------------------------------
echo "[Step 7/9] Bootstraps ablegen & Prüfsummen..."
mkdir -p "${BOOTSTRAP_DIR}"
for arch in "${ARCHS_LIST[@]}"; do
    mv -f "${CLONE_DIR}/output/bootstrap-${arch}.zip" "${BOOTSTRAP_DIR}/"
done
(cd "${BOOTSTRAP_DIR}" && for z in bootstrap-*.zip; do sha256sum "${z}" > "${z}.sha256"; echo "  $(cat "${z}.sha256")"; done)
fi   # Ende Step 6/7 (nur PHASE=all)

# ----------------------------------------------------
# Step 8: APT-Repository aus den gebauten .deb-Dateien
# ----------------------------------------------------
echo "[Step 8/9] APT-Repository erzeugen & signieren..."
rm -rf "${REPO_DIR}"
mkdir -p "${REPO_DIR}/pool/main"

shopt -s nullglob
DEB_FILES=()
if [ "${PHASE}" = assemble ]; then
    # Bestehendes Repo übernehmen und die neuen Pakete ergänzen (gleicher Dateiname = überschrieben).
    if [ -n "${BASE_REPO_DIR}" ] && [ -d "${BASE_REPO_DIR}/pool" ]; then
        if [ -s "${BASE_REPO_DIR}/codeforge.gpg" ]; then
            base_fpr="$(gpg --show-keys --with-colons "${BASE_REPO_DIR}/codeforge.gpg" 2>/dev/null | awk -F: '/^fpr:/ {print $10; exit}')"
            [ -z "${base_fpr}" ] || [ "${base_fpr}" = "${KEY_FINGERPRINT}" ] \
                || die "Der Signaturschlüssel (${KEY_FINGERPRINT}) passt nicht zum bestehenden Repo (${base_fpr}). Geräte würden das Repo nicht mehr akzeptieren – GPG_PRIVATE_KEY_B64 prüfen."
        fi
        cp -a "${BASE_REPO_DIR}/pool/." "${REPO_DIR}/pool/"
        echo "  Bestehendes Repo übernommen: $(find "${REPO_DIR}/pool" -name '*.deb' | wc -l) Pakete."
        for d in "${BASE_REPO_DIR}"/dists/stable/main/binary-*; do
            [ -d "${d}" ] || continue
            a="${d##*/binary-}"
            case " ${ARCHS_LIST[*]} " in *" ${a} "*) ;; *) ARCHS_LIST+=("${a}") ;; esac
        done
    else
        echo "Hinweis: BASE_REPO_DIR leer oder ohne pool/ – es entsteht ein neues Repo nur aus den neuen Paketen."
    fi
    while IFS= read -r -d '' f; do DEB_FILES+=("${f}"); done < <(find "${EXTRA_DEBS_DIR}" -type f -name '*.deb' -print0)
    [ "${#DEB_FILES[@]}" -gt 0 ] || die "Keine neuen .deb in ${EXTRA_DEBS_DIR} gefunden."
    echo "  Neue/aktualisierte Pakete: ${#DEB_FILES[@]} · Architekturen im Repo: ${ARCHS_LIST[*]}"
else
    DEB_FILES=("${CLONE_DIR}"/output/*.deb)
fi

# Pool-Layout wie bei Termux: pool/main/<erster Buchstabe | lib+Buchstabe>/<paket>/<datei>.deb
for deb in "${DEB_FILES[@]}"; do
    pkg="$(dpkg-deb -f "${deb}" Package)"
    [ -n "${pkg}" ] || die "Kein Package-Feld in ${deb}"
    case "${pkg}" in lib?*) bucket="${pkg:0:4}" ;; *) bucket="${pkg:0:1}" ;; esac
    mkdir -p "${REPO_DIR}/pool/main/${bucket}/${pkg}"
    # Epoche (z. B. "1:2026.09.25") steht nicht im Dateinamen (wie bei Debian): ":" ist in Artefakt-Uploads/Windows ungültig.
    deb_name="$(basename "${deb}" | sed -E 's/_[0-9]+:/_/')"
    cp -f "${deb}" "${REPO_DIR}/pool/main/${bucket}/${pkg}/${deb_name}"
done
shopt -u nullglob
deb_count="$(find "${REPO_DIR}/pool" -type f -name '*.deb' | wc -l)"
[ "${deb_count}" -gt 0 ] || die "Pool ist leer."
echo "  ${deb_count} Pakete im Pool."
pool_mb="$(du -sm "${REPO_DIR}/pool" | cut -f1)"
echo "  Pool-Größe: ${pool_mb} MB"
[ "${pool_mb}" -lt 900 ] || echo "WARNUNG: Pool > 900 MB. GitHub Pages ist auf ca. 1 GB je Seite begrenzt – anderes Hosting einplanen." >&2

DIST_DIR="${REPO_DIR}/dists/stable"
cd "${REPO_DIR}"
for arch in "${ARCHS_LIST[@]}"; do
    BIN_DIR="dists/stable/main/binary-${arch}"
    mkdir -p "${BIN_DIR}"
    apt-ftparchive --arch "${arch}" packages pool > "${BIN_DIR}/Packages"
    [ -s "${BIN_DIR}/Packages" ] || die "Packages für ${arch} ist leer (keine *_${arch}.deb / *_all.deb im Pool)."
    gzip -9c "${BIN_DIR}/Packages" > "${BIN_DIR}/Packages.gz"
done

# Release OHNE sich selbst aufzulisten: erst in Temp-Datei schreiben, dann verschieben.
apt-ftparchive \
    -o "APT::FTPArchive::Release::Origin=CodeForge" \
    -o "APT::FTPArchive::Release::Label=CodeForge Mobile Repository" \
    -o "APT::FTPArchive::Release::Suite=stable" \
    -o "APT::FTPArchive::Release::Codename=stable" \
    -o "APT::FTPArchive::Release::Architectures=${ARCHS_LIST[*]}" \
    -o "APT::FTPArchive::Release::Components=main" \
    -o "APT::FTPArchive::Release::Description=CodeForge package repository (prefix ${APP_PACKAGE})" \
    release "${DIST_DIR}" > "${TMP_ROOT}/Release.new"
mv -f "${TMP_ROOT}/Release.new" "${DIST_DIR}/Release"

gpg_pass --local-user "${KEY_FINGERPRINT}" --armor --detach-sign -o "${DIST_DIR}/Release.gpg" "${DIST_DIR}/Release"
gpg_pass --local-user "${KEY_FINGERPRINT}" --clearsign -o "${DIST_DIR}/InRelease" "${DIST_DIR}/Release"

# Eigene Signaturen prüfen
verify_out="$(gpg --verify "${DIST_DIR}/InRelease" 2>&1)" || die "InRelease-Signatur ungültig."
printf '%s' "${verify_out}" | grep -q "Good signature" || die "InRelease: keine gültige Signatur."
verify_out="$(gpg --verify "${DIST_DIR}/Release.gpg" "${DIST_DIR}/Release" 2>&1)" || die "Release.gpg-Signatur ungültig."
printf '%s' "${verify_out}" | grep -q "Good signature" || die "Release.gpg: keine gültige Signatur."

# Jede Filename-Angabe muss existieren und die Prüfsumme muss stimmen.
python3 - "${REPO_DIR}" "${ARCHS_LIST[@]}" <<'PY'
import hashlib, os, sys
root, archs = sys.argv[1], sys.argv[2:]
bad = 0
for arch in archs:
    p = os.path.join(root, "dists/stable/main", f"binary-{arch}", "Packages")
    for block in open(p, encoding="utf8").read().split("\n\n"):
        fields = dict(l.split(": ", 1) for l in block.splitlines() if ": " in l and not l.startswith(" "))
        if "Filename" not in fields:
            continue
        f = os.path.join(root, fields["Filename"])
        if not os.path.isfile(f) or hashlib.sha256(open(f, "rb").read()).hexdigest() != fields.get("SHA256"):
            print("FEHLER Packages/", arch, fields["Filename"]); bad = 1
sys.exit(bad)
PY

# Öffentliche Dateien für Nutzer und GitHub Pages
cp -f "${GPG_DIR}/codeforge.gpg" "${REPO_DIR}/codeforge.gpg"
cp -f "${GPG_DIR}/codeforge.asc" "${REPO_DIR}/codeforge.asc"
touch "${REPO_DIR}/.nojekyll"
cat > "${REPO_DIR}/index.html" <<EOF
<!doctype html><meta charset="utf-8"><title>CodeForge APT Repository</title>
<h1>CodeForge APT Repository</h1>
<p>Prefix <code>${PREFIX_PATH}</code>. Quelle in <code>\$PREFIX/etc/apt/sources.list</code>:</p>
<pre>deb ${APT_URL} stable main</pre>
<p>Schlüssel: <a href="codeforge.gpg">codeforge.gpg</a> (binär), <a href="codeforge.asc">codeforge.asc</a> (armored). Fingerprint: <code>${KEY_FINGERPRINT}</code></p>
EOF

# GitHub: Einzeldateien > 100 MB werden abgelehnt.
big_files="$(find "${REPO_DIR}" -type f -size +95M)"
if [ -n "${big_files}" ]; then
    echo "WARNUNG: Dateien > 95 MB (GitHub-Limit 100 MB), z. B.:" >&2
    find "${REPO_DIR}" -type f -size +95M -exec ls -l {} \; >&2
fi

# Konsistenzprüfung des APT-Repositories
if [ -f "${WORKSPACE_DIR}/scripts/check-apt-consistency.py" ]; then
    echo "Prüfe Abhängigkeits-Konsistenz des APT-Repositories..."
    CONSISTENCY_CHECK="${CONSISTENCY_CHECK:-fail}"
    # Große Pakete (> 95 MiB) liegen als Release-Asset, nicht im Pool: bestehendes Manifest (gh-pages) und die
    # in diesem Lauf abgetrennten (BIG_NEW_FILE) als verfügbare Pakete mitzählen.
    large_args=()
    if [ -n "${BASE_REPO_DIR}" ] && [ -s "${BASE_REPO_DIR}/large-packages.txt" ]; then
        large_args+=(--large-packages "${BASE_REPO_DIR}/large-packages.txt")
    fi
    if [ -n "${BIG_NEW_FILE:-}" ] && [ -s "${BIG_NEW_FILE}" ]; then
        large_args+=(--large-packages "${BIG_NEW_FILE}")
    fi
    python3 -I "${WORKSPACE_DIR}/scripts/check-apt-consistency.py" repo "${REPO_DIR}" ${large_args[@]+"${large_args[@]}"} --on-error "${CONSISTENCY_CHECK}" \
        || [ "${CONSISTENCY_CHECK}" = "warn" ] || die "Konsistenzprüfung des APT-Repositories fehlgeschlagen."
fi

# ----------------------------------------------------
# Step 9: Dokumentation
# ----------------------------------------------------
echo "[Step 9/9] Dokumentation..."
mkdir -p "${DOCS_DIR}"
SUMMARY_FILE="${DOCS_DIR}/overview_and_summary.md"
{
    echo "# CodeForge Build Overview & Summary"
    echo
    echo "## Ausführung"
    echo "- **Datum:** $(date -u +"%Y-%m-%d %H:%M:%S UTC")"
    echo "- **Architekturen:** ${ARCHS_LIST[*]}"
    echo "- **Prefix:** \`${PREFIX_PATH}\`"
    echo "- **Pakete im APT-Pool:** ${deb_count} (aus dem Quellcode gebaut, kein nachträgliches Patchen von Binaries)"
    echo "- **Extra-Pakete im Bootstrap:** ${EXTRA_PACKAGES:-keine}"
    echo "- **Nur im APT-Repo (REPO_PACKAGES):** ${REPO_PACKAGES:-keine}"
    echo
    echo "## Bootstraps (\`${BOOTSTRAP_DIR}\`)"
    for z in "${BOOTSTRAP_DIR}"/bootstrap-*.zip; do
        [ -f "${z}" ] || continue
        echo "- \`$(basename "${z}")\` – SHA-256 \`$(cut -d' ' -f1 "${z}.sha256")\`"
    done
    [ "${PHASE}" != assemble ] || echo "- (PHASE=assemble: Bootstraps unverändert, nur Pakete ergänzt)"
    echo
    echo "## gradle.properties (Release-Tag anpassen)"
    echo '```properties'
    echo "codeforgeBootstrapVersion=$(date -u +%Y.%m.%d)"
    for arch in aarch64 arm x86_64; do
        if [ -f "${BOOTSTRAP_DIR}/bootstrap-${arch}.zip.sha256" ]; then
            echo "codeforgeBootstrapSha256.${arch}=$(cut -d' ' -f1 "${BOOTSTRAP_DIR}/bootstrap-${arch}.zip.sha256")"
        fi
    done
    echo '```'
    echo
    echo "## APT-Repository (\`${REPO_DIR}\`)"
    echo "- Quelle in den Bootstraps: \`deb ${APT_URL} stable main\`"
    echo "- Veröffentlichen: Inhalt von \`github_repo_ready/\` auf den Branch \`gh-pages\` pushen und GitHub Pages aktivieren."
    echo "- Dateien: \`dists/stable/{Release,Release.gpg,InRelease}\`, \`pool/main/…\`, \`codeforge.gpg\` (binär), \`codeforge.asc\`, \`.nojekyll\`"
    echo
    echo "## GPG"
    echo "- **Identität:** ${KEY_NAME} <${KEY_EMAIL}>, Fingerprint \`${KEY_FINGERPRINT}\`"
    echo "- **Öffentlich:** \`${GPG_DIR}/codeforge.gpg\` – SHA-256 \`$(cut -d' ' -f1 "${GPG_DIR}/sha256.txt")\`"
    echo "- **Privat (nicht einchecken):** \`${GPG_DIR}/private/codeforge.gpg\` – SHA-256 \`$(cut -d' ' -f1 "${GPG_DIR}/private/sha256.txt")\`"
    echo "- Die Passphrase wurde aus der Umgebung bzw. \`~/.bashrc\` gelesen und nirgends gespeichert."
} > "${SUMMARY_FILE}"

echo "===================================================="
echo "Pipeline erfolgreich beendet."
echo "  Dokumentation:  ${SUMMARY_FILE}"
echo "  GPG:            ${GPG_DIR}"
echo "  Bootstraps:     ${BOOTSTRAP_DIR}"
echo "  APT-Repository: ${REPO_DIR}"
echo "===================================================="

if [ "${#FAILED_PKGS[@]}" -gt 0 ]; then
    FAILED_FILE="${WORKSPACE_DIR}/failed-packages.txt"
    printf '%s\n' "${FAILED_PKGS[@]}" > "${FAILED_FILE}"
    echo "Fehlgeschlagene/verworfene Pakete:"
    printf '  %s\n' "${FAILED_PKGS[@]}"
    if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
        {
            echo "### ⚠️ Verworfene / fehlgeschlagene Pakete:"
            printf '* %s\n' "${FAILED_PKGS[@]}"
        } >> "${GITHUB_STEP_SUMMARY}"
    fi
    for fp in "${FAILED_PKGS[@]}"; do
        echo "::warning::Verworfenes/fehlgeschlagenes Paket: ${fp}"
    done
    if [ "${FAIL_ON_SKIPPED:-false}" = "true" ] || [ "${FAIL_ON_SKIPPED:-0}" = "1" ]; then
        die "fail_on_skipped aktiv: ${#FAILED_PKGS[@]} Paket(e) fehlgeschlagen oder verworfen."
    fi
fi
