#!/usr/bin/env bash

set -e

# MobileIDE Tree-sitter libraries build script for Termux / Linux
# Step 1: Requirements check
echo "==> Checking required tools..."
for tool in git clang; do
    if ! command -v "$tool" >/dev/null 2>&1; then
        echo "[ERROR] Required tool '$tool' is not installed or not in PATH." >&2
        exit 1
    fi
done
echo "    git and clang are installed."

# Define project root and target directory
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TARGET_DIR="${PROJECT_ROOT}/feature/editor/src/main/jniLibs/arm64-v8a"
BUILD_DIR="${HOME}/treesitter_build"

# Target languages
LANGUAGES=("java" "kotlin" "xml" "json" "properties" "c" "cpp" "python" "aidl")

echo "==> Setting up temporary build directory: ${BUILD_DIR}"
rm -rf "${BUILD_DIR}"
mkdir -p "${BUILD_DIR}"

# Step 6 prep: Ensure target directory exists
mkdir -p "${TARGET_DIR}"

cd "${BUILD_DIR}"

COMPILED_COUNT=0

for lang in "${LANGUAGES[@]}"; do
    echo "--------------------------------------------------"
    echo "Processing language: ${lang}"

    # Determine GitHub repo URL
    if [ "$lang" = "kotlin" ]; then
        REPO_URL="https://github.com/fwcd/tree-sitter-kotlin.git"
    else
        REPO_URL="https://github.com/tree-sitter/tree-sitter-${lang}.git"
    fi

    LANG_DIR="${BUILD_DIR}/tree-sitter-${lang}"

    echo "==> Cloning ${REPO_URL}..."
    if ! git clone --depth 1 "${REPO_URL}" "${LANG_DIR}" 2>/dev/null; then
        # Try fallback organization if primary fails
        FALLBACK_URL="https://github.com/tree-sitter-grammars/tree-sitter-${lang}.git"
        echo "==> Primary clone failed. Trying fallback ${FALLBACK_URL}..."
        if ! git clone --depth 1 "${FALLBACK_URL}" "${LANG_DIR}" 2>/dev/null; then
            echo "[WARN] Could not clone repository for grammar '${lang}'. Skipping."
            continue
        fi
    fi

    cd "${LANG_DIR}"

    SRC_DIR="src"
    if [ ! -d "${SRC_DIR}" ] || [ ! -f "${SRC_DIR}/parser.c" ]; then
        echo "[WARN] '${lang}' does not contain src/parser.c. Skipping."
        cd "${BUILD_DIR}"
        continue
    fi

    # Find source files in src/
    SOURCES=()
    SOURCES+=("src/parser.c")
    
    HAS_CPP=0

    # Check for optional scanner
    for s in scanner.c scanner.cc scanner.cpp scanner.cxx; do
        if [ -f "${SRC_DIR}/${s}" ]; then
            SOURCES+=("src/${s}")
            if [[ "$s" == *.cc || "$s" == *.cpp || "$s" == *.cxx ]]; then
                HAS_CPP=1
            fi
        fi
    done

    echo "    Sources found: ${SOURCES[*]}"

    # Step 4: Dynamically generate Android.mk and CMakeLists.txt
    
    # Generate Android.mk
    cat <<EOF > Android.mk
LOCAL_PATH := \$(call my-dir)

include \$(CLEAR_VARS)

LOCAL_MODULE    := libtree-sitter-${lang}
LOCAL_SRC_FILES := ${SOURCES[*]}
LOCAL_C_INCLUDES := \$(LOCAL_PATH)/src

include \$(BUILD_SHARED_LIBRARY)
EOF
    echo "    Generated Android.mk"

    # Generate CMakeLists.txt
    cat <<EOF > CMakeLists.txt
cmake_minimum_required(VERSION 3.10)
project(tree_sitter_${lang} C CXX)

include_directories(src)
add_library(tree-sitter-${lang} SHARED ${SOURCES[*]})
EOF
    echo "    Generated CMakeLists.txt"

    # Step 5: Manual compilation with Clang / Clang++
    OUTPUT_SO="libtree-sitter-${lang}.so"
    echo "==> Compiling ${OUTPUT_SO}..."

    COMPILER="clang"
    if [ "$HAS_CPP" -eq 1 ]; then
        COMPILER="clang++"
    fi

    if ${COMPILER} -shared -fPIC -I./src "${SOURCES[@]}" -o "${OUTPUT_SO}"; then
        echo "    Successfully compiled ${OUTPUT_SO}"
        # Step 7: Move compiled .so to target directory
        mv "${OUTPUT_SO}" "${TARGET_DIR}/"
        echo "    Moved ${OUTPUT_SO} -> ${TARGET_DIR}/"
        COMPILED_COUNT=$((COMPILED_COUNT + 1))
    else
        echo "[WARN] Failed to compile ${OUTPUT_SO}. Skipping."
    fi

    cd "${BUILD_DIR}"
done

# Step 8: Cleanup temporary working directory
echo "--------------------------------------------------"
echo "==> Cleaning up temporary build directory: ${BUILD_DIR}"
cd "${PROJECT_ROOT}"
rm -rf "${BUILD_DIR}"

echo "==> Summary: Successfully compiled and installed ${COMPILED_COUNT} tree-sitter library/libraries into:"
echo "    ${TARGET_DIR}"
ls -la "${TARGET_DIR}"

echo "==> Build complete!"
