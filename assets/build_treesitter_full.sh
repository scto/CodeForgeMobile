#!/bin/bash
set -e

PROJECT_DIR="/data/data/com.termux/files/home/storage/shared/Development/CodeForgeMobile"
EDITOR_MAIN="$PROJECT_DIR/feature/editor/src/main"
WORK_DIR="$HOME/treesitter_workspace"
ABIS=("arm64-v8a" "armeabi-v7a" "x86" "x86_64")
LANGUAGES=("java" "kotlin" "xml" "json" "properties" "c" "cpp" "python" "aidl" "log" "bash" "yaml" "toml" "cmake")

echo "=== 1. Vorbereitung ==="
mkdir -p "$WORK_DIR"
cd "$WORK_DIR"

# Ensure target jniLibs directories exist for all ABIs
for ABI in "${ABIS[@]}"; do
    mkdir -p "$EDITOR_MAIN/jniLibs/$ABI"
done

echo "=== 2. Sprachen (.so) kompilieren und Queries (.scm) kopieren ==="
for LANG in "${LANGUAGES[@]}"; do
    cd "$WORK_DIR"
    
    REPO_URL="https://github.com/tree-sitter/tree-sitter-$LANG"
    [ "$LANG" == "kotlin" ] && REPO_URL="https://github.com/fwcd/tree-sitter-kotlin"
    [ "$LANG" == "cmake" ] && REPO_URL="https://github.com/uyha/tree-sitter-cmake"
    [ "$LANG" == "log" ] && REPO_URL="https://github.com/AndroidIDEOfficial/tree-sitter-log"
    [ "$LANG" == "aidl" ] && REPO_URL="https://github.com/AndroidIDEOfficial/tree-sitter-aidl"

    if [ ! -d "ts-$LANG" ]; then
        echo "Klone $LANG von $REPO_URL..."
        if ! git clone --depth 1 "$REPO_URL" "ts-$LANG" 2>/dev/null; then
            FALLBACK_URL="https://github.com/tree-sitter-grammars/tree-sitter-$LANG"
            echo "Primary clone failed. Trying fallback $FALLBACK_URL..."
            git clone --depth 1 "$FALLBACK_URL" "ts-$LANG" 2>/dev/null || echo "Warnung: Konnte $LANG nicht klonen."
        fi
    fi
    
    if [ -d "ts-$LANG" ]; then
        cd "ts-$LANG"
        
        # 1. Queries (.scm) in den assets-Ordner kopieren
        if [ -d "queries" ]; then
            ASSET_DIR="$EDITOR_MAIN/assets/treesitter/$LANG"
            mkdir -p "$ASSET_DIR"
            cp queries/*.scm "$ASSET_DIR/" 2>/dev/null || true
            echo "Queries für $LANG nach assets/treesitter/$LANG kopiert."
        fi

        # 2. Source-Dateien ermitteln & kompilieren
        SRC_DIR="src"
        if [ -f "$SRC_DIR/parser.c" ]; then
            SOURCES=("$SRC_DIR/parser.c")
            HAS_CPP=0
            for s in scanner.c scanner.cc scanner.cpp scanner.cxx; do
                if [ -f "$SRC_DIR/$s" ]; then
                    SOURCES+=("$SRC_DIR/$s")
                    if [[ "$s" == *.cc || "$s" == *.cpp || "$s" == *.cxx ]]; then
                        HAS_CPP=1
                    fi
                fi
            done

            COMPILER="clang"
            [ "$HAS_CPP" -eq 1 ] && COMPILER="clang++"

            OUT_SO="libtree-sitter-$LANG.so"
            echo "Kompiliere $OUT_SO mit $COMPILER..."
            if ${COMPILER} -shared -fPIC -I./src "${SOURCES[@]}" -o "$OUT_SO" 2>/dev/null; then
                for ABI in "${ABIS[@]}"; do
                    cp "$OUT_SO" "$EDITOR_MAIN/jniLibs/$ABI/"
                done
                echo "Erfolgreich kompiliert und in jniLibs eingepflegt."
            else
                echo "Warnung: Kompilierung von $LANG fehlgeschlagen."
            fi
        else
            echo "Warnung: $LANG enthält kein src/parser.c"
        fi
    fi
done

echo "=== 3. Version Catalog prüfen ==="
TOML_FILE="$PROJECT_DIR/gradle/libs.versions.toml"
if [ -f "$TOML_FILE" ]; then
    if ! grep -q "language-treesitter" "$TOML_FILE"; then
        echo "Füge Treesitter zu TOML hinzu..."
        echo 'sora-treesitter = { module = "io.github.Rosemoe.sora-editor:language-treesitter", version.ref = "sora" }' >> "$TOML_FILE"
    fi
fi

echo "=== 4. Aufräumen ==="
cd "$PROJECT_DIR"
rm -rf "$WORK_DIR"

echo "=== Fertig! Alle .so Dateien und .scm Queries wurden in CodeForgeMobile integriert. ==="
