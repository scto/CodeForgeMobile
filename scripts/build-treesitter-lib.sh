#!/data/data/com.termux/files/usr/bin/env bash
# ==============================================================================
# build-treesitter-lib.sh
# 
# Baut native Shared Libraries (libtree-sitter-<sprache>.so) für:
# - Bash (tree-sitter/tree-sitter-bash @ v0.23.3, ABI 14)
# - CMake (uyha/tree-sitter-cmake @ v0.7.5, ABI 14)
# - TOML (tree-sitter-grammars/tree-sitter-toml @ v0.7.0, ABI 14)
# - YAML (tree-sitter-grammars/tree-sitter-yaml @ v0.7.2, ABI 14)
#
# Ziel-ABIs: arm64-v8a, armeabi-v7a, x86_64, x86
# Ausgabeverzeichnis: feature/editor/src/main/jniLibs/<abi>/
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
WORK_DIR="${TMPDIR:-/data/data/com.termux/files/usr/tmp}/treesitter-build"
mkdir -p "${WORK_DIR}"

# 1. NDK-Pfad ermitteln (bevorzuge Version aus gradle/libs.versions.toml)
NDK_VERSION="$(grep -E '^ndk\s*=' "${PROJECT_ROOT}/gradle/libs.versions.toml" | cut -d'"' -f2 || true)"
NDK_DIR=""

if [ -n "${NDK_VERSION}" ]; then
    for CANDIDATE in \
        "/data/data/com.termux/files/home/android-sdk/ndk/${NDK_VERSION}" \
        "${ANDROID_HOME:-/data/data/com.termux/files/home/android-sdk}/ndk/${NDK_VERSION}" \
        "${HOME}/Android/Sdk/ndk/${NDK_VERSION}" \
        "${HOME}/android-sdk/ndk/${NDK_VERSION}"; do
        if [ -d "${CANDIDATE}" ]; then
            NDK_DIR="${CANDIDATE}"
            break
        fi
    done
fi

if [ -z "${NDK_DIR}" ]; then
    NDK_DIR="${ANDROID_NDK_ROOT:-${ANDROID_NDK_HOME:-${NDK_HOME:-}}}"
    if [ -z "${NDK_DIR}" ] || [ ! -d "${NDK_DIR}" ] || [[ "${NDK_DIR}" == *"ndk-bundle"* ]]; then
        for CANDIDATE in \
            "/data/data/com.termux/files/home/android-sdk/ndk/29.0.14206865" \
            "/data/data/com.termux/files/home/android-sdk/ndk/"* \
            "${HOME}/Android/Sdk/ndk/"* \
            "${HOME}/android-sdk/ndk/"*; do
            if [ -d "${CANDIDATE}" ] && [[ "${CANDIDATE}" != *"ndk-bundle"* ]]; then
                NDK_DIR="${CANDIDATE}"
                break
            fi
        done
    fi
fi

if [ -z "${NDK_DIR}" ] || [ ! -d "${NDK_DIR}" ]; then
    echo "FEHLER: Android NDK nicht gefunden. Bitte ANDROID_NDK_HOME setzen." >&2
    exit 1
fi

echo "Verwende NDK: ${NDK_DIR}"

# LLVM Prebuilt Toolchain ermitteln
TOOLCHAIN_BIN=""
for CANDIDATE in \
    "${NDK_DIR}/toolchains/llvm/prebuilt/linux-arm64/bin" \
    "${NDK_DIR}/toolchains/llvm/prebuilt/linux-x86_64/bin" \
    "${NDK_DIR}/toolchains/llvm/prebuilt/"*/bin; do
    if [ -d "${CANDIDATE}" ]; then
        TOOLCHAIN_BIN="${CANDIDATE}"
        break
    fi
done

if [ -z "${TOOLCHAIN_BIN}" ]; then
    echo "FEHLER: NDK Toolchain-Binaries nicht gefunden in ${NDK_DIR}" >&2
    exit 1
fi

echo "Verwende Toolchain: ${TOOLCHAIN_BIN}"

# Unterstützte ABIs und deren Clang-Compiler-Präfixe (minSdk 24/26)
API_LEVEL="24"
declare -A ABI_COMPILERS=(
    ["arm64-v8a"]="${TOOLCHAIN_BIN}/aarch64-linux-android${API_LEVEL}-clang"
    ["armeabi-v7a"]="${TOOLCHAIN_BIN}/armv7a-linux-androideabi${API_LEVEL}-clang"
    ["x86_64"]="${TOOLCHAIN_BIN}/x86_64-linux-android${API_LEVEL}-clang"
    ["x86"]="${TOOLCHAIN_BIN}/i686-linux-android${API_LEVEL}-clang"
)

# Sprachen, Repositories, Tags und Build-Flags
declare -A LANG_REPOS=(
    ["bash"]="https://github.com/tree-sitter/tree-sitter-bash.git"
    ["cmake"]="https://github.com/uyha/tree-sitter-cmake.git"
    ["toml"]="https://github.com/tree-sitter-grammars/tree-sitter-toml.git"
    ["yaml"]="https://github.com/tree-sitter-grammars/tree-sitter-yaml.git"
)

declare -A LANG_TAGS=(
    ["bash"]="v0.23.3"
    ["cmake"]="v0.7.5"
    ["toml"]="v0.7.0"
    ["yaml"]="v0.7.2"
)

declare -A LANG_EXTRA_FLAGS=(
    ["bash"]=""
    ["cmake"]=""
    ["toml"]=""
    ["yaml"]="-std=c11 -DYAML_SCHEMA=core"
)

# Zielverzeichnis im Modul
JNI_LIBS_DIR="${PROJECT_ROOT}/feature/editor/src/main/jniLibs"

# 2. Build-Prozess je Sprache
for LANG in "bash" "cmake" "toml" "yaml"; do
    REPO_URL="${LANG_REPOS[$LANG]}"
    TAG="${LANG_TAGS[$LANG]}"
    EXTRA_FLAGS="${LANG_EXTRA_FLAGS[$LANG]}"
    REPO_DIR="${WORK_DIR}/tree-sitter-${LANG}"

    echo "=========================================================="
    echo "Baue libtree-sitter-${LANG}.so (Tag: ${TAG})..."
    echo "=========================================================="

    if [ ! -d "${REPO_DIR}" ]; then
        echo "Klone ${REPO_URL}..."
        git clone --depth 1 --branch "${TAG}" "${REPO_URL}" "${REPO_DIR}" || {
            echo "Shallow clone fehlgeschlagen, volles Klonen..."
            git clone "${REPO_URL}" "${REPO_DIR}"
            git -C "${REPO_DIR}" checkout "${TAG}"
        }
    else
        echo "Verwende vorhandenes Repository ${REPO_DIR}..."
        git -C "${REPO_DIR}" fetch --tags
        git -C "${REPO_DIR}" checkout "${TAG}"
    fi

    # Prüfe ABI-Version (MUSS 13 oder 14 sein)
    PARSER_C="${REPO_DIR}/src/parser.c"
    if [ ! -f "${PARSER_C}" ]; then
        echo "FEHLER: ${PARSER_C} existiert nicht!" >&2
        exit 1
    fi

    ABI_VER="$(grep -E '#define LANGUAGE_VERSION' "${PARSER_C}" | awk '{print $3}')"
    echo "Erkannte Tree-sitter Language Version (ABI): ${ABI_VER}"
    if [ "${ABI_VER}" != "13" ] && [ "${ABI_VER}" != "14" ]; then
        echo "FEHLER: Inkompatible ABI-Version ${ABI_VER} in ${LANG} (Erwartet: 13 oder 14)!" >&2
        exit 1
    fi

    # Zusätzliche Scanner-Dateien prüfen
    SOURCES=("${PARSER_C}")
    if [ -f "${REPO_DIR}/src/scanner.c" ]; then
        SOURCES+=("${REPO_DIR}/src/scanner.c")
    fi

    # Für jede ABI kompilieren
    for ABI in "arm64-v8a" "armeabi-v7a" "x86_64" "x86"; do
        COMPILER="${ABI_COMPILERS[$ABI]}"
        if [ ! -x "${COMPILER}" ]; then
            echo "WARNUNG: Compiler ${COMPILER} nicht ausführbar, überspringe ${ABI}."
            continue
        fi

        OUT_DIR="${JNI_LIBS_DIR}/${ABI}"
        mkdir -p "${OUT_DIR}"
        OUT_FILE="${OUT_DIR}/libtree-sitter-${LANG}.so"

        echo "  [${ABI}] Kompiliere ${OUT_FILE}..."
        # Flags: -O3, PIC, shared library, soname, Includes auf src/
        ${COMPILER} -O3 -fPIC -shared \
            -Wl,-soname,"libtree-sitter-${LANG}.so" \
            -I"${REPO_DIR}/src" \
            ${EXTRA_FLAGS} \
            "${SOURCES[@]}" \
            -o "${OUT_FILE}"

        # Strip unneeded symbols while preserving dynamic export table
        STRIP_BIN="${TOOLCHAIN_BIN}/llvm-strip"
        if [ -x "${STRIP_BIN}" ]; then
            ${STRIP_BIN} --strip-unneeded "${OUT_FILE}"
        fi

        echo "  [${ABI}] Fertig: $(ls -lh "${OUT_FILE}" | awk '{print $5, $9}')"
    done
done

echo ""
echo "=========================================================="
echo "Alle nativen Bibliotheken erfolgreich gebaut!"
echo "Ausgabe:"
ls -lh "${JNI_LIBS_DIR}"/*/*.so
echo "=========================================================="
