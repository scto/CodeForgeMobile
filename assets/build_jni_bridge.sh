#!/bin/bash
set -e

export DEBIAN_FRONTEND=noninteractive
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-arm64
export ANDROID_HOME=/data/data/com.termux/files/home/android-sdk

PROJECT_DIR="/data/data/com.termux/files/home/storage/shared/Development/CodeForgeMobile"
EDITOR_MAIN="$PROJECT_DIR/feature/editor/src/main"
WORK_DIR="$HOME/treesitter_jni_workspace"
ABIS=("arm64-v8a" "armeabi-v7a" "x86" "x86_64")

echo "=== 1. Workspace & Tools vorbereiten ==="
mkdir -p "$WORK_DIR"
cd "$WORK_DIR"

if ! command -v git >/dev/null || ! command -v cmake >/dev/null; then
    apt-get update && apt-get install -y -o Dpkg::Options::="--force-confold" wget unzip git cmake ninja-build clang openjdk-21-jdk
fi

echo "=== 2. AndroidIDE Tree-sitter Repository klonen ==="
if [ ! -d "android-tree-sitter" ]; then
    git clone --recursive https://github.com/AndroidIDEOfficial/android-tree-sitter.git
fi
cd android-tree-sitter

# NDK-Version im build.gradle.kts anpassen
sed -i 's/ndkVersion = "24.0.8215888"/ndkVersion = "26.1.10909125"/g' build.gradle.kts 2>/dev/null || true

# local.properties konfigurieren
cat <<EOF > local.properties
sdk.dir=/data/data/com.termux/files/home/android-sdk
ndk.dir=/data/data/com.termux/files/home/android-sdk/ndk/26.1.10909125
EOF

echo "=== 3. JNI-Bridge via Gradle / CMake bauen ==="
./gradlew :android-tree-sitter:buildForHost || ./gradlew :android-tree-sitter:assembleRelease -x buildTreeSitter || ./gradlew assemble -x buildTreeSitter || true

echo "=== 4. Kopieren der .so-Dateien in die jniLibs aller ABIs ==="
SO_FILE=$(find "$WORK_DIR" -type f -name "*tree*sitter*.so" -o -name "libandroid-tree-sitter.so" | head -n 1)

if [ -n "$SO_FILE" ]; then
    for ABI in "${ABIS[@]}"; do
        DEST_DIR="$EDITOR_MAIN/jniLibs/$ABI"
        mkdir -p "$DEST_DIR"
        cp "$SO_FILE" "$DEST_DIR/libtreesitter-android.so"
        echo "Erfolgreich kopiert ($ABI): $SO_FILE -> $DEST_DIR/libtreesitter-android.so"
    done
else
    echo "Kritischer Fehler: Keine .so Datei im Workspace gefunden!"
    exit 1
fi

echo "=== JNI-Build abgeschlossen! ==="
