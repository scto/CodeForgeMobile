#!/usr/bin/env bash
# Modul: Paketübersicht (Backup/Referenz) für das CodeForge-APT-Repo und den Bootstrap.
#
# Zweck: ein Ort, an dem man auf einen Blick sieht, WAS in den Bootstrap kommt und WAS in welcher Etappe ins Repo gebaut wird.
# Vorbild: packages.sh aus Wadamzmail/terminal-packages-aarch64 (CodeOnTheGo), angepasst an unsere Etappen.
#
# WICHTIG: Maßgeblich für den Build bleibt apt-stages.conf (und scripts/build-bootstraps.sh im Fork).
# Diese Datei ist eine statische Kopie. Auf Abweichung prüfen:
#     bash packages.sh --check        # Exit 0 = identisch zu apt-stages.conf
#     bash packages.sh --list         # alle Pakete, ein Name pro Zeile (sortiert, eindeutig)
#     bash packages.sh --stages       # Etappe: Pakete
# Oder per `source packages.sh` die Arrays CODEFORGE_PACKAGES__BOOTSTRAP / __STAGE_* / CODEFORGE_PACKAGES nutzen.

# --- Bootstrap (Inhalt von bootstrap-<arch>.zip; Quelle: scripts/build-bootstraps.sh im Fork) -----------------------------
declare -a CODEFORGE_PACKAGES__BOOTSTRAP=(
    # Kern
    "apt" "bash" "bzip2" "coreutils" "dash" "diffutils" "findutils" "gawk" "grep" "gzip" "less"
    "procps" "psmisc" "sed" "tar" "termux-core" "termux-exec" "termux-keyring" "termux-tools" "util-linux"
    # Zusatz
    "ed" "debianutils" "dos2unix" "inetutils" "lsof" "nano" "net-tools" "patch" "unzip"
)

# --- Etappen (nur im APT-Repo, nicht im Bootstrap; Reihenfolge egal, jede ergänzt das Repo) --------------------------------
declare -a CODEFORGE_PACKAGES__STAGE_00_codeforge=("codeforge-env")  # Flags: single-arch nodeps=all
declare -a CODEFORGE_PACKAGES__STAGE_01_jdk17=("openjdk-17")
declare -a CODEFORGE_PACKAGES__STAGE_02_jdk21=("openjdk-21")
declare -a CODEFORGE_PACKAGES__STAGE_03_jdk25=("openjdk-25")
declare -a CODEFORGE_PACKAGES__STAGE_04_jvm_tools=("gradle" "kotlin" "maven" "ant" "apksigner" "d8" "dx" "ecj")  # Flags: single-arch nodeps=all
declare -a CODEFORGE_PACKAGES__STAGE_04a_libjansi=("libjansi")  # Flags: nodeps=all
declare -a CODEFORGE_PACKAGES__STAGE_05_android_tools=("libprotobuf" "aapt")
declare -a CODEFORGE_PACKAGES__STAGE_06_dev_core=("git" "git-lfs" "zip" "unzip" "tar" "libcurl" "wget" "liblzma" "7zip" "jq" "ninja" "make" "cmake" "openssh")
declare -a CODEFORGE_PACKAGES__STAGE_07_utils=("vim" "nano" "ripgrep" "fd" "tmux" "patch" "diffutils" "findutils" "binutils" "which" "libmagic" "libsqlite" "bash-completion")
declare -a CODEFORGE_PACKAGES__STAGE_08_python=("python" "python-pip")
declare -a CODEFORGE_PACKAGES__STAGE_09_nodejs=("nodejs" "npm")
declare -a CODEFORGE_PACKAGES__STAGE_10_llvm_clang=("libllvm")
declare -a CODEFORGE_PACKAGES__STAGE_11_dart=("dart")
declare -a CODEFORGE_PACKAGES__STAGE_12_lsp_misc=("gopls" "taplo" "ruff")  # Flags: nodeps=gopls
declare -a CODEFORGE_PACKAGES__STAGE_13_pure_tools=("yarn" "autoconf" "automake")  # Flags: single-arch nodeps=all
declare -a CODEFORGE_PACKAGES__STAGE_14_ide_tools=("neocmakelsp" "esbuild" "pnpm" "shellcheck")
declare -a CODEFORGE_PACKAGES__STAGE_15_rust_analyzer=("rust-analyzer")
declare -a CODEFORGE_PACKAGES__STAGE_16_native_dev=("pkg-config" "libtool" "flex" "bison" "ccache" "patchelf" "strace")
declare -a CODEFORGE_PACKAGES__STAGE_17_debug_misc=("gdb" "android-tools" "gh" "rsync" "xmlstarlet")

# Reihenfolge/Zuordnung Etappe -> Variable (Name der Etappe wie in apt-stages.conf)
declare -a CODEFORGE_STAGE_NAMES=(
    "00-codeforge"
    "01-jdk17"
    "02-jdk21"
    "03-jdk25"
    "04-jvm-tools"
    "04a-libjansi"
    "05-android-tools"
    "06-dev-core"
    "07-utils"
    "08-python"
    "09-nodejs"
    "10-llvm-clang"
    "11-dart"
    "12-lsp-misc"
    "13-pure-tools"
    "14-ide-tools"
    "15-rust-analyzer"
    "16-native-dev"
    "17-debug-misc"
)
declare -a CODEFORGE_STAGE_VARS=(
    "CODEFORGE_PACKAGES__STAGE_00_codeforge"
    "CODEFORGE_PACKAGES__STAGE_01_jdk17"
    "CODEFORGE_PACKAGES__STAGE_02_jdk21"
    "CODEFORGE_PACKAGES__STAGE_03_jdk25"
    "CODEFORGE_PACKAGES__STAGE_04_jvm_tools"
    "CODEFORGE_PACKAGES__STAGE_04a_libjansi"
    "CODEFORGE_PACKAGES__STAGE_05_android_tools"
    "CODEFORGE_PACKAGES__STAGE_06_dev_core"
    "CODEFORGE_PACKAGES__STAGE_07_utils"
    "CODEFORGE_PACKAGES__STAGE_08_python"
    "CODEFORGE_PACKAGES__STAGE_09_nodejs"
    "CODEFORGE_PACKAGES__STAGE_10_llvm_clang"
    "CODEFORGE_PACKAGES__STAGE_11_dart"
    "CODEFORGE_PACKAGES__STAGE_12_lsp_misc"
    "CODEFORGE_PACKAGES__STAGE_13_pure_tools"
    "CODEFORGE_PACKAGES__STAGE_14_ide_tools"
    "CODEFORGE_PACKAGES__STAGE_15_rust_analyzer"
    "CODEFORGE_PACKAGES__STAGE_16_native_dev"
    "CODEFORGE_PACKAGES__STAGE_17_debug_misc"
)

# Alle Pakete (Bootstrap + alle Etappen)
declare -a CODEFORGE_PACKAGES=("${CODEFORGE_PACKAGES__BOOTSTRAP[@]}")
for _v in "${CODEFORGE_STAGE_VARS[@]}"; do
    declare -n _ref="$_v"
    CODEFORGE_PACKAGES+=("${_ref[@]}")
done
unset -n _ref
unset _v

_cf_stage_lines() {
    local i ref
    for i in "${!CODEFORGE_STAGE_NAMES[@]}"; do
        declare -n ref="${CODEFORGE_STAGE_VARS[$i]}"
        printf '%s|%s\n' "${CODEFORGE_STAGE_NAMES[$i]}" "$(IFS=,; echo "${ref[*]}")"
    done
}

# Nur bei direktem Aufruf (nicht bei `source`)
if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
    case "${1:-}" in
        --list)   printf '%s\n' "${CODEFORGE_PACKAGES[@]}" | sort -u ;;
        --stages) _cf_stage_lines | tr '|' ':' ;;
        --check)
            here="$(cd "$(dirname "$0")" && pwd)"
            conf="$(grep -v '^[[:space:]]*#' "$here/apt-stages.conf" | grep -v '^[[:space:]]*$' | awk -F'|' '{print $1"|"$2}')"
            if diff <(_cf_stage_lines) <(printf '%s\n' "$conf") ; then
                echo "OK: Etappen identisch zu apt-stages.conf"
            else
                echo "ABWEICHUNG zu apt-stages.conf (siehe oben: < packages.sh, > apt-stages.conf)" >&2; exit 1
            fi ;;
        *) echo "Aufruf: bash packages.sh --check | --list | --stages" >&2; exit 2 ;;
    esac
fi
