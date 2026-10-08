#!/usr/bin/env bash
set -eo pipefail

WORKSPACE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CLONE_DIR="${WORKSPACE_DIR}/terminal-packages-codeforge"
OUTPUT_DIR="${WORKSPACE_DIR}/output"
BOOTSTRAP_DIR="${OUTPUT_DIR}/bootstraps"
REPO_DIR="${WORKSPACE_DIR}/github_repo_ready"
GPG_DIR="${WORKSPACE_DIR}/.gpg"
DOCS_DIR="${WORKSPACE_DIR}/docs"

echo "===================================================="
echo "Starting CodeForge Build & APT Repository Pipeline"
echo "===================================================="

# ----------------------------------------------------
# Step 1: Environment Setup & Cloning
# ----------------------------------------------------
echo "[Step 1/8] Environment Setup & Cloning..."

# Ensure private GPG keys never leak into a git repository
echo ".gpg/private/" >> "${WORKSPACE_DIR}/.gitignore"
echo "Added .gpg/private/ to .gitignore to prevent secret key leakage."

REQUIRED_TOOLS=("apt-ftparchive" "gpg" "wget" "curl" "rsync" "zip" "jq" "ar" "dpkg")
MISSING_TOOLS=()
for tool in "${REQUIRED_TOOLS[@]}"; do
    if ! command -v "$tool" >/dev/null 2>&1; then
        MISSING_TOOLS+=("$tool")
    fi
done

if [ ${#MISSING_TOOLS[@]} -gt 0 ]; then
    echo "Installing missing build tools: ${MISSING_TOOLS[*]}..."
    if command -v pkg >/dev/null 2>&1; then
        pkg install -y apt-utils gnupg wget curl rsync zip jq binutils dpkg || true
    elif command -v apt-get >/dev/null 2>&1; then
        if command -v sudo >/dev/null 2>&1; then
            sudo apt-get install -y apt-utils gnupg wget curl rsync zip jq binutils dpkg || true
        else
            apt-get install -y apt-utils gnupg wget curl rsync zip jq binutils dpkg || true
        fi
    fi
fi

if [ ! -d "${CLONE_DIR}" ]; then
    echo "Cloning repository: https://github.com/scto/terminal-packages-codeforge..."
    git clone --depth 1 https://github.com/scto/terminal-packages-codeforge "${CLONE_DIR}"
else
    echo "Repository directory ${CLONE_DIR} already exists."
fi

cd "${CLONE_DIR}"

# ----------------------------------------------------
# Step 2: Namespace & Prefix Refactoring
# ----------------------------------------------------
echo "[Step 2/8] Namespace & Prefix Refactoring (com.termux -> com.codeforge.app)..."

if [ -f "scripts/properties.sh" ]; then
    python3 -c "
import re

path = 'scripts/properties.sh'
with open(path, 'r') as f:
    content = f.read()

content = re.sub(r'com\.termux', 'com.codeforge.app', content)
content = re.sub(r'com\.codeforge(?!\.app)', 'com.codeforge.app', content)

with open(path, 'w') as f:
    f.write(content)
"
fi

# ----------------------------------------------------
# Step 3: GPG Key Generation & Checksum
# ----------------------------------------------------
echo "[Step 3/8] GPG Key Generation & Checksums..."

mkdir -p "${GPG_DIR}/private"

# Use internal storage tmp directory for GPG homedir to avoid FAT/FUSE socket restrictions
export GPGHOMEDIR=$(mktemp -d "${TMPDIR:-/tmp}/codeforge_gnupg.XXXXXX")
chmod 700 "${GPGHOMEDIR}"
trap 'rm -rf "${GPGHOMEDIR}"' EXIT

KEY_NAME="Thomas Schmid"
KEY_EMAIL="tschmid35@gmail.com"
KEY_PASS="${CODEFORGE_GPG_PASSPHRASE:?Bitte CODEFORGE_GPG_PASSPHRASE als Umgebungsvariable setzen}"

cat <<EOF > "${GPGHOMEDIR}/gpg_batch_params"
Key-Type: RSA
Key-Length: 3072
Subkey-Type: RSA
Subkey-Length: 3072
Name-Real: ${KEY_NAME}
Name-Email: ${KEY_EMAIL}
Passphrase: ${KEY_PASS}
Expire-Date: 0
%commit
EOF

gpg --homedir "${GPGHOMEDIR}" --batch --pinentry-mode loopback --gen-key "${GPGHOMEDIR}/gpg_batch_params"

# Export public and private keys (Securely piping the passphrase via fd 0)
gpg --homedir "${GPGHOMEDIR}" --armor --export "${KEY_EMAIL}" > "${GPG_DIR}/codeforge.gpg"
echo "${KEY_PASS}" \vert{} gpg --homedir "${GPGHOMEDIR}" --batch --pinentry-mode loopback --passphrase-fd 0 --armor --export-secret-keys "${KEY_EMAIL}" > "${GPG_DIR}/private/codeforge.gpg"

# Checksums for public and private keys (Removed the duplicate sha56.txt generation)
(cd "${GPG_DIR}" && sha256sum codeforge.gpg > sha256.txt)
(cd "${GPG_DIR}/private" && sha256sum codeforge.gpg > sha256.txt)

echo "Public GPG key saved to: ${GPG_DIR}/codeforge.gpg (SHA256: $(cat "${GPG_DIR}/sha256.txt"))"
echo "Private GPG key saved to: ${GPG_DIR}/private/codeforge.gpg (SHA256: $(cat "${GPG_DIR}/private/sha256.txt"))"

# ----------------------------------------------------
# Step 4: Patching the Build Environment
# ----------------------------------------------------
echo "[Step 4/8] Patching the Build Environment (Direct File Substitution)..."

KEY_FINGERPRINT=$(gpg --homedir "${GPGHOMEDIR}" --list-secret-keys --with-colons "${KEY_EMAIL}" | grep '^fpr:' | head -n1 | cut -d: -f10)
echo "Generated GPG Key Fingerprint: ${KEY_FINGERPRINT}"

mkdir -p "${CLONE_DIR}/packages/termux-keyring"
cp -f "${GPG_DIR}/codeforge.gpg" "${CLONE_DIR}/packages/termux-keyring/codeforge_pub.gpg"
cp -f "${GPG_DIR}/codeforge.gpg" "${CLONE_DIR}/packages/termux-keyring/codeforge.gpg"

# Safely inject the install command directly into the keyring build script
if [ -f "${CLONE_DIR}/packages/termux-keyring/build.sh" ]; then
    sed -i '/install -Dm600.*codeforge\.gpg/d' "${CLONE_DIR}/packages/termux-keyring/build.sh"
    sed -i '/install -Dm600.*termux-pacman\.gpg.*GPG_SHARE_DIR/a \	install -Dm600 "$TERMUX_PKG_BUILDER_DIR/codeforge.gpg" "$GPG_SHARE_DIR"' "${CLONE_DIR}/packages/termux-keyring/build.sh"
fi

# Locate and patch build-package.sh directly on the fly
BUILD_PKG_SCRIPT=""
if [ -f "${CLONE_DIR}/build-package.sh" ]; then
    BUILD_PKG_SCRIPT="${CLONE_DIR}/build-package.sh"
elif [ -f "${CLONE_DIR}/scripts/build-package.sh" ]; then
    BUILD_PKG_SCRIPT="${CLONE_DIR}/scripts/build-package.sh"
fi

if [ -n "${BUILD_PKG_SCRIPT}" ] && [ -f "${BUILD_PKG_SCRIPT}" ]; then
    python3 -c "
import sys, re

path = '${BUILD_PKG_SCRIPT}'
with open(path, 'r') as f:
    content = f.read()

replacement = '''\tgpg --list-keys ${KEY_FINGERPRINT} >/dev/null 2>&1 || {
\t\tgpg --import \"\$TERMUX_SCRIPTDIR/packages/termux-keyring/codeforge_pub.gpg\"
\t\tgpg --no-tty --command-file <(echo -e \"trust\\\\n5\\\\ny\") --edit-key ${KEY_FINGERPRINT}
\t}'''

pattern = r'(\t*gpg --list-keys\s+[0-9A-Fa-f]+[\s\S]*?--edit-key\s+[0-9A-Fa-f]+\s*\n\t*\})'
if re.search(pattern, content):
    content = re.sub(pattern, replacement, content, count=1)
    with open(path, 'w') as f:
        f.write(content)
    print('Successfully substituted dynamic GPG verification block in ' + path)
else:
    print('Notice: GPG verification block pattern already updated or not found in ' + path)
"
fi

# Build codeforge-tools package deb
echo "Building custom codeforge-tools deb package..."
PKG_TMP=$(mktemp -d "${TMPDIR:-/tmp}/codeforge_pkg.XXXXXX")
chmod 755 "$PKG_TMP"
mkdir -p "$PKG_TMP/pkg/DEBIAN"
chmod 755 "$PKG_TMP/pkg" "$PKG_TMP/pkg/DEBIAN"
mkdir -p "$PKG_TMP/pkg/data/data/com.codeforge.app/files/usr/bin"

cat <<EOF > "$PKG_TMP/pkg/DEBIAN/control"
Package: codeforge-tools
Version: 0.10.0
Architecture: all
Maintainer: @scto
Installed-Size: 10
Depends: libcurl, jq, nano, wget
Section: utils
Priority: optional
Homepage: https://codeforge.com/
Description: Basic system tools for CodeForgeMobile
EOF

curl -sL https://github.com/scto/codeforge-tools/releases/download/v0.10.0/codeforge-tools.tar.xz -o "$PKG_TMP/src.tar.xz"
tar -xf "$PKG_TMP/src.tar.xz" -C "$PKG_TMP"
cp "$PKG_TMP/codeforge-tools/scripts/codeforgesetup" "$PKG_TMP/pkg/data/data/com.codeforge.app/files/usr/bin/"
cp "$PKG_TMP/codeforge-tools/scripts/codeforgeenv" "$PKG_TMP/pkg/data/data/com.codeforge.app/files/usr/bin/"
chmod 755 "$PKG_TMP/pkg/data/data/com.codeforge.app/files/usr/bin/"*

mkdir -p "${CLONE_DIR}/packages/codeforge-tools"
dpkg-deb -b "$PKG_TMP/pkg" "${CLONE_DIR}/packages/codeforge-tools/codeforge-tools.deb"
rm -rf "$PKG_TMP"

# Patch generate-bootstraps.sh to support local deb packages in case it is called internally
python3 -c "
path = '${CLONE_DIR}/scripts/generate-bootstraps.sh'
with open(path, 'r') as f:
    content = f.read()

# 1. Update destination in data/data move to com.codeforge.app
old_move = '''\t\t\t\tif [ -d \"\${BOOTSTRAP_ROOTFS}/data/data/com.termux\" ]; then
\t\t\t\t\tmkdir -p \"\${BOOTSTRAP_ROOTFS}/data/data/com.codeforge\"
\t\t\t\t\tcp -af \"\${BOOTSTRAP_ROOTFS}/data/data/com.termux/.\" \"\${BOOTSTRAP_ROOTFS}/data/data/com.codeforge/\"
\t\t\t\t\trm -rf \"\${BOOTSTRAP_ROOTFS}/data/data/com.termux\"
\t\t\t\tfi'''

new_move = '''\t\t\t\tif [ -d \"\${BOOTSTRAP_ROOTFS}/data/data/com.termux\" ]; then
\t\t\t\t\tmkdir -p \"\${BOOTSTRAP_ROOTFS}/data/data/com.codeforge.app\"
\t\t\t\t\tcp -af \"\${BOOTSTRAP_ROOTFS}/data/data/com.termux/.\" \"\${BOOTSTRAP_ROOTFS}/data/data/com.codeforge.app/\"
\t\t\t\t\trm -rf \"\${BOOTSTRAP_ROOTFS}/data/data/com.termux\"
\t\t\t\tfi
\t\t\t\tif [ -d \"\${BOOTSTRAP_ROOTFS}/data/data/com.codeforge\" ] && [ ! -d \"\${BOOTSTRAP_ROOTFS}/data/data/com.codeforge.app\" ]; then
\t\t\t\t\tmv \"\${BOOTSTRAP_ROOTFS}/data/data/com.codeforge\" \"\${BOOTSTRAP_ROOTFS}/data/data/com.codeforge.app\"
\t\t\t\tfi'''

if old_move in content:
    content = content.replace(old_move, new_move)

# 2. Update dpkg list replacement
content = content.replace('sed -i \"s/com.termux/com.codeforge/g\" \"\${BOOTSTRAP_ROOTFS}/\${TERMUX_PREFIX}/var/lib/dpkg/info/\${package_name}.list\"',
                          'sed -i \"s/com.termux/com.codeforge.app/g\" \"\${BOOTSTRAP_ROOTFS}/\${TERMUX_PREFIX}/var/lib/dpkg/info/\${package_name}.list\"')

# 3. Update create_bootstrap_archive replacements
content = content.replace('find \"\${BOOTSTRAP_ROOTFS}/\${TERMUX_PREFIX}\" -type f -exec sed -i \"s/com.termux/com.codeforge/g\" {} + 2>/dev/null || true',
                          'find \"\${BOOTSTRAP_ROOTFS}/\${TERMUX_PREFIX}\" -type f -exec sed -i \"s/com.termux/com.codeforge.app/g; s/com.codeforge /com.codeforge.app/g; s/com.codeforge.app.app/com.codeforge.app/g\" {} + 2>/dev/null || true')

content = content.replace('new_target=\$(echo \"\$target\" | sed \"s/com\\.termux/com\\.codeforge/g\")',
                          'new_target=\$(echo \"\$target\" | sed \"s/com\\.termux/com\\.codeforge\\.app/g; s/com\\.codeforge /com\\.codeforge\\.app/g; s/com\\.codeforge\\.app\\.app/com\\.codeforge\\.app/g\")')

# 4. Safe URL check for local packages
url_check = '''\t\tlocal package_url
\t\tpackage_url=\"\$REPO_BASE_URL/\$(echo \"\${PACKAGE_METADATA[\${package_name}]}\" | grep -i \"^Filename:\" | awk \x27{ print \$2 }\x27)\"
\t\tif [ \"\${package_url}\" = \"\$REPO_BASE_URL\" ] || [ \"\${package_url}\" = \"\${REPO_BASE_URL}/\" ]; then
\t\t\techo \"[!] Failed to determine URL for package \x27\$package_name\x27.\"
\t\t\texit 1
\t\tfi'''

safe_url_check = '''\t\tlocal package_url=\"\"
\t\tif [ -f \"\${TERMUX_SCRIPTDIR}/packages/\${package_name}/\${package_name}.deb\" ]; then
\t\t\tcp \"\${TERMUX_SCRIPTDIR}/packages/\${package_name}/\${package_name}.deb\" \"\$package_tmpdir/package.deb\"
\t\tfi
\t\tif [ ! -f \"\$package_tmpdir/package.deb\" ]; then
\t\t\tpackage_url=\"\$REPO_BASE_URL/\$(echo \"\${PACKAGE_METADATA[\${package_name}]}\" | grep -i \"^Filename:\" | awk '{ print \$2 }')\"
\t\t\tif [ \"\${package_url}\" = \"\$REPO_BASE_URL\" ] || [ \"\${package_url}\" = \"\${REPO_BASE_URL}/\" ]; then
\t\t\t\techo \"[!] Failed to determine URL for package '\$package_name'.\"
\t\t\t\texit 1
\t\t\tfi
\t\tfi'''

if url_check in content:
    content = content.replace(url_check, safe_url_check)

# 5. Cleanup per architecture
archive_call = '''\t# Create bootstrap archive.
