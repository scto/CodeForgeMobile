#!/usr/bin/env bash
set -eo pipefail

WORKSPACE_DIR="/data/data/com.termux/files/home/CodeForgeMobile"
REPO_DIR="$WORKSPACE_DIR/terminal-packages-codeforge"
GPG_DIR="$WORKSPACE_DIR/.gpg"
GPG_PRIVATE_DIR="$GPG_DIR/private"
OUTPUT_DIR="$WORKSPACE_DIR/output"
REPO_READY_DIR="$WORKSPACE_DIR/github_repo_ready"
TMP_DIR="$WORKSPACE_DIR/tmp"

echo "=== Step 1: Environment Setup & Cloning ==="
pkg install -y apt-ftparchive gnupg wget curl rsync unzip

if [ ! -d "$REPO_DIR" ]; then
    git clone https://github.com/scto/terminal-packages-codeforge "$REPO_DIR"
fi

touch "$WORKSPACE_DIR/.gitignore"
for entry in ".gpg/" ".gpg/private/" "output/" "github_repo_ready/" "tmp/" "terminal-packages-codeforge/"; do
    if ! grep -q "^$entry$" "$WORKSPACE_DIR/.gitignore"; then
        echo "$entry" >> "$WORKSPACE_DIR/.gitignore"
    fi
done

echo "=== Step 3: GPG Key Generation & Checksum ==="
mkdir -p "$GPG_DIR" "$GPG_PRIVATE_DIR"
chmod 700 "$GPG_DIR"
chmod 700 "$GPG_PRIVATE_DIR"
mkdir -p "$TMP_DIR"

load_gpg_passphrase() {
    if [ -n "${CODEFORGE_GPG_PASSPHRASE:-}" ]; then
        echo "Loaded passphrase from environment."
    else
        if [ ! -r "$HOME/.bashrc" ]; then
            echo "Error: ~/.bashrc is unreadable" >&2
            exit 1
        fi
        local match
        match=$(grep '^[[:space:]]*\(export[[:space:]]\+\)\?CODEFORGE_GPG_PASSPHRASE=' "$HOME/.bashrc" | tail -n 1) || true
        if [ -z "$match" ]; then
            echo "Error: CODEFORGE_GPG_PASSPHRASE not found in ~/.bashrc" >&2
            exit 1
        fi
        
        local val="${match#*=}"
        if [[ "$val" == \'*\' ]]; then
            val="${val#\'}"
            val="${val%\'}"
        elif [[ "$val" == \"*\" ]]; then
            val="${val#\"}"
            val="${val%\"}"
        fi
        
        if [ -z "$val" ]; then
            echo "Error: CODEFORGE_GPG_PASSPHRASE is empty in ~/.bashrc" >&2
            exit 1
        fi
        export CODEFORGE_GPG_PASSPHRASE="$val"
    fi
}

load_gpg_passphrase

export GNUPGHOME="$TMP_DIR/gpg_home"
mkdir -p "$GNUPGHOME"
chmod 700 "$GNUPGHOME"
umask 077

cleanup() {
    rm -rf "$GNUPGHOME"
}
trap cleanup EXIT

cat <<EOF > "$GNUPGHOME/gen-key-script"
%echo Generating key
Key-Type: RSA
Key-Length: 4096
Subkey-Type: RSA
Subkey-Length: 4096
Name-Real: Thomas Schmid
Name-Email: tschmid35@gmail.com
Expire-Date: 0
%ask-passphrase
%commit
%echo done
EOF

if ! gpg --list-keys "tschmid35@gmail.com" >/dev/null 2>&1; then
    printf '%s\n' "$CODEFORGE_GPG_PASSPHRASE" | gpg --batch --pinentry-mode loopback --passphrase-fd 0 --generate-key "$GNUPGHOME/gen-key-script"
fi

gpg --armor --export "tschmid35@gmail.com" > "$GPG_DIR/codeforge.gpg"
printf '%s\n' "$CODEFORGE_GPG_PASSPHRASE" | gpg --armor --batch --pinentry-mode loopback --passphrase-fd 0 --export-secret-keys "tschmid35@gmail.com" > "$GPG_PRIVATE_DIR/codeforge.gpg"
chmod 600 "$GPG_PRIVATE_DIR/codeforge.gpg"

sha256sum "$GPG_DIR/codeforge.gpg" | awk '{print $1}' > "$GPG_DIR/sha256.txt"
sha256sum "$GPG_PRIVATE_DIR/codeforge.gpg" | awk '{print $1}' > "$GPG_PRIVATE_DIR/sha256.txt"

echo "=== Step 2 & 4: Patching the Build Environment ==="
KEY_FINGERPRINT=$(gpg --list-keys --with-colons "tschmid35@gmail.com" | grep -m1 '^fpr:' | cut -d: -f10)

mkdir -p "$REPO_DIR/packages/termux-keyring"
cp "$GPG_DIR/codeforge.gpg" "$REPO_DIR/packages/termux-keyring/codeforge_pub.gpg"
cp "$GPG_DIR/codeforge.gpg" "$REPO_DIR/packages/termux-keyring/codeforge.gpg"

if [ -f "$REPO_DIR/packages/termux-keyring/build.sh" ]; then
    if ! grep -q "install -Dm600 \$TERMUX_PKG_BUILDER_DIR/codeforge.gpg \$GPG_SHARE_DIR" "$REPO_DIR/packages/termux-keyring/build.sh"; then
        sed -i '/termux_step_make_install()/a \    install -Dm600 $TERMUX_PKG_BUILDER_DIR/codeforge.gpg $GPG_SHARE_DIR' "$REPO_DIR/packages/termux-keyring/build.sh"
    fi
fi

if [ -f "$REPO_DIR/build-package.sh" ]; then
    cat << 'EOF_PYTHON' > "$TMP_DIR/patch_build_package.py"
import sys, re
file_path = sys.argv[1]
key_id = sys.argv[2]
with open(file_path, "r") as f:
    content = f.read()

content = re.sub(r'gpg --list-keys.*?>/dev/null 2>&1 \|\| \{.*?\n\s*\}', 
f'''gpg --list-keys {key_id} >/dev/null 2>&1 || {{
    gpg --import "$TERMUX_SCRIPTDIR/packages/termux-keyring/codeforge_pub.gpg"
    gpg --no-tty --command-file <(echo -e "trust\\n5\\ny") --edit-key {key_id}
}}''', content, flags=re.DOTALL)

with open(file_path, "w") as f:
    f.write(content)
EOF_PYTHON
    python3 "$TMP_DIR/patch_build_package.py" "$REPO_DIR/build-package.sh" "$KEY_FINGERPRINT"
fi

if [ -f "$REPO_DIR/scripts/properties.sh" ]; then
    sed -i -e 's/com\.termux/com.codeforge.app/g' -e 's/com\.codeforge\.app\.app/com.codeforge.app/g' "$REPO_DIR/scripts/properties.sh"
fi

echo "=== Step 5: Bootstrap Generation ==="
cat << 'EOF_PYTHON_BOOTSTRAP' > "$TMP_DIR/patch_bootstraps.py"
import sys, re

file_path = sys.argv[1]
workspace_tmp = sys.argv[2]

with open(file_path, "r") as f:
    lines = f.readlines()

new_lines = []
for line in lines:
    line = line.replace("/data/TERMUX_ARCH", f"{workspace_tmp}/TERMUX_ARCH")
    line = line.replace("/tmp", workspace_tmp)
    
    if re.search(r'\b(zip|tar)\b.*\b(bootstrap|sysroot)', line):
        injection = r"""
        echo "Running recursive sed over extracted text files..."
        ROOTFS_DIR="."
        if [ -d "data/data/com.codeforge.app" ]; then ROOTFS_DIR="."; 
        elif [ -d "rootfs/data/data/com.codeforge.app" ]; then ROOTFS_DIR="rootfs"; 
        else ROOTFS_DIR=$(dirname $(find . -type d -path "*/data/data/com.codeforge.app" | head -n 1 2>/dev/null) 2>/dev/null || echo "."); fi
        
        if [ -d "$ROOTFS_DIR" ]; then
            find "$ROOTFS_DIR" -type f -exec grep -Iq \. {} \; -print0 | xargs -0 -r -I{} sed -i -e 's/com\.termux/com.codeforge.app/g' -e 's/com\.codeforge\.app\.app/com.codeforge.app/g' {}
            find "$ROOTFS_DIR" -type l -print0 | while IFS= read -r -d '' link; do
                target=$(readlink "$link")
                new_target=$(echo "$target" | sed -e 's/com\.termux/com.codeforge.app/g' -e 's/com\.codeforge\.app\.app/com.codeforge.app/g')
                if [ "$target" != "$new_target" ]; then
                    ln -sf "$new_target" "$link"
                fi
            done
            if [ -d "$ROOTFS_DIR/var/lib/dpkg/info" ]; then
                find "$ROOTFS_DIR/var/lib/dpkg/info" -name "*.list" -type f -print0 | xargs -0 -r sed -i -e 's/com\.termux/com.codeforge.app/g' -e 's/com\.codeforge\.app\.app/com.codeforge.app/g'
            fi
        fi
        """
        new_lines.append(injection + "\n")
    new_lines.append(line)

with open(file_path, "w") as f:
    f.writelines(new_lines)
EOF_PYTHON_BOOTSTRAP

if [ -f "$REPO_DIR/scripts/generate-bootstraps.sh" ]; then
    python3 "$TMP_DIR/patch_bootstraps.py" "$REPO_DIR/scripts/generate-bootstraps.sh" "$TMP_DIR"
    
    cd "$REPO_DIR"
    bash ./scripts/generate-bootstraps.sh || { echo "Bootstrap generation failed!"; exit 1; }
    
    mkdir -p "$OUTPUT_DIR/bootstraps"
    mv bootstrap-*.zip "$OUTPUT_DIR/bootstraps/" 2>/dev/null || true
    mv bootstrap-*.tar.xz "$OUTPUT_DIR/bootstraps/" 2>/dev/null || true

    mkdir -p "$TMP_DIR/verify"
    for archive in "$OUTPUT_DIR/bootstraps"/bootstrap-*; do
        if [ -f "$archive" ]; then
            echo "Verifying $archive..."
            verify_dir="$TMP_DIR/verify/$(basename "$archive")_extract"
            mkdir -p "$verify_dir"
            if [[ "$archive" == *.zip ]]; then
                unzip -q "$archive" -d "$verify_dir"
            elif [[ "$archive" == *.tar.xz ]]; then
                tar -xf "$archive" -C "$verify_dir"
            fi
            
            find "$verify_dir" -type f -exec sh -c 'head -c 4 "$1" | grep -q "^.ELF"' _ {} \; -exec grep -aq "com\.termux" {} \; -print > "$TMP_DIR/bad_elfs.txt"
            if [ -s "$TMP_DIR/bad_elfs.txt" ]; then
                echo "Error: The following ELF files contain 'com.termux':" >&2
                cat "$TMP_DIR/bad_elfs.txt" >&2
                exit 1
            fi
        fi
    done
fi

echo "=== Step 6: Checksum Generation ==="
for archive in "$OUTPUT_DIR/bootstraps"/bootstrap-*; do
    if [ -f "$archive" ]; then
        sha256sum "$archive" > "${archive}.sha256"
    fi
done

echo "=== Step 7: GitHub APT Repository Generation ==="
mkdir -p "$REPO_READY_DIR/pool/main"
for arch in aarch64 arm i686 x86_64; do
    mkdir -p "$REPO_READY_DIR/dists/stable/main/binary-$arch"
done

cd "$REPO_READY_DIR"
for arch in aarch64 arm i686 x86_64; do
    apt-ftparchive packages "pool/main" > "dists/stable/main/binary-$arch/Packages"
    gzip -9c "dists/stable/main/binary-$arch/Packages" > "dists/stable/main/binary-$arch/Packages.gz"
done

apt-ftparchive release "dists/stable" > "dists/stable/Release"
printf '%s\n' "$CODEFORGE_GPG_PASSPHRASE" | gpg --batch --pinentry-mode loopback --passphrase-fd 0 --default-key "tschmid35@gmail.com" --armor --detach-sign --sign --output "dists/stable/Release.gpg" "dists/stable/Release"
printf '%s\n' "$CODEFORGE_GPG_PASSPHRASE" | gpg --batch --pinentry-mode loopback --passphrase-fd 0 --default-key "tschmid35@gmail.com" --clearsign --output "dists/stable/InRelease" "dists/stable/Release"

echo "=== Step 8: Documentation Generation ==="
mkdir -p "$WORKSPACE_DIR/docs"
DOC_FILE="$WORKSPACE_DIR/docs/overview_and_summary.md"
DATE_STR=$(date)

cat <<EOF > "$DOC_FILE"
# Build Overview & Summary
**Execution Date:** $DATE_STR

## Built Architectures
- aarch64
- arm
- i686
- x86_64

## Generated Outputs
- **Bootstraps Directory:** $OUTPUT_DIR/bootstraps/
- **APT Repository:** $REPO_READY_DIR/

## GPG Keys & Checksums
- **Public Key:** $GPG_DIR/codeforge.gpg (Checksum: $(cat "$GPG_DIR/sha256.txt" 2>/dev/null || echo "N/A"))
- **Private Key:** $GPG_PRIVATE_DIR/codeforge.gpg (Checksum: $(cat "$GPG_PRIVATE_DIR/sha256.txt" 2>/dev/null || echo "N/A"))

## Security Note
The GPG passphrase was loaded securely from the environment (\`~/.bashrc\`) and was **not** stored or written anywhere during this process.
EOF

echo "Done! The build repository setup is complete."
