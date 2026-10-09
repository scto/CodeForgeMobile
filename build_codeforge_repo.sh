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

mkdir -p "${TMP_ROOT}"
export TMPDIR="${TMP_ROOT}"

echo "===================================================="
echo "CodeForge Build & APT Repository Pipeline"
echo "Prefix: ${PREFIX_PATH}"
echo "APT-URL: ${APT_URL}"
echo "Architekturen: ${ARCHS_LIST[*]}"
echo "===================================================="

# ----------------------------------------------------
# Step 1: Voraussetzungen, Klonen, .gitignore
# ----------------------------------------------------
echo "[Step 1/9] Voraussetzungen & Klonen..."

[ "$(uname -m)" = "x86_64" ] || die "Dieses Skript braucht einen x86_64-Linux-Rechner mit Docker (gefunden: $(uname -m)). Auf dem Telefon kann der Termux-Builder nicht laufen."
for tool in docker git gpg curl zip unzip jq python3 readelf dpkg-deb apt-ftparchive sha256sum; do
    command -v "${tool}" >/dev/null 2>&1 || die "'${tool}' fehlt (Debian/Ubuntu: apt-get install docker.io git gnupg curl zip unzip jq python3 binutils dpkg apt-utils)."
done
docker info >/dev/null 2>&1 || die "Docker ist nicht erreichbar (läuft der Daemon, gehört der Benutzer zur Gruppe 'docker'?)."

if [ ! -d "${CLONE_DIR}/.git" ]; then
    git clone --depth 1 "${FORK_URL}" "${CLONE_DIR}"
else
    echo "Fork bereits vorhanden: ${CLONE_DIR} (Patches werden idempotent angewendet)."
fi

touch "${WORKSPACE_DIR}/.gitignore"
for entry in ".gpg/" ".gpg/private/" "output/" "github_repo_ready/" "tmp/" "terminal-packages-codeforge/"; do
    grep -qxF "${entry}" "${WORKSPACE_DIR}/.gitignore" || echo "${entry}" >> "${WORKSPACE_DIR}/.gitignore"
done

# ----------------------------------------------------
# Step 2: Prefix prüfen (kein sed auf properties.sh!)
# ----------------------------------------------------
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

export GNUPGHOME="$(mktemp -d "${TMP_ROOT}/codeforge_gnupg.XXXXXX")"
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
# Step 4: Fork patchen (idempotent)
# ----------------------------------------------------
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

# ----------------------------------------------------
# Step 5: Pakete + Bootstraps aus dem Quellcode bauen (Docker)
# ----------------------------------------------------
echo "[Step 5/9] Bootstraps bauen (Docker, build-bootstraps.sh) – dauert Stunden..."
cd "${CLONE_DIR}"
[ -x scripts/run-docker.sh ] || die "scripts/run-docker.sh fehlt/nicht ausführbar."
[ -f scripts/build-bootstraps.sh ] || die "scripts/build-bootstraps.sh fehlt im Fork."

ARCH_CSV="$(IFS=,; echo "${ARCHS_LIST[*]}")"
BUILD_ARGS=(--architectures "${ARCH_CSV}")
[ -n "${EXTRA_PACKAGES}" ] && BUILD_ARGS+=(--add "${EXTRA_PACKAGES}")

rm -f "${CLONE_DIR}"/bootstrap-*.zip
./scripts/run-docker.sh ./scripts/build-bootstraps.sh "${BUILD_ARGS[@]}"

for arch in "${ARCHS_LIST[@]}"; do
    [ -s "${CLONE_DIR}/bootstrap-${arch}.zip" ] || die "bootstrap-${arch}.zip wurde nicht erzeugt."
done
ls "${CLONE_DIR}"/output/*.deb >/dev/null 2>&1 || die "Keine .deb-Dateien in ${CLONE_DIR}/output (build-bootstraps.sh hätte sie erzeugen müssen)."

# Zusätzliche Pakete NUR fürs APT-Repo (nicht ins Bootstrap): einzeln pro Architektur aus dem Quellcode bauen.
# Abhängigkeiten werden von build-package.sh ebenfalls aus dem Quellcode gebaut (kein -I, keine Pakete vom offiziellen Repo).
if [ -n "${REPO_PACKAGES}" ]; then
    [ -f build-package.sh ] || die "build-package.sh fehlt im Fork."
    IFS=',' read -r -a REPO_PKG_LIST <<< "${REPO_PACKAGES}"
    for arch in "${ARCHS_LIST[@]}"; do
        for pkg in "${REPO_PKG_LIST[@]}"; do
            [ -n "${pkg}" ] || continue
            echo "  -> ${pkg} (${arch})"
            ./scripts/run-docker.sh ./build-package.sh -a "${arch}" -o output "${pkg}"
            ls output/"${pkg}"_*_"${arch}".deb >/dev/null 2>&1 \
                || die "${pkg}: keine ${pkg}_*_${arch}.deb in output/ (Paketname im Fork vorhanden? Baut es für ${arch}?)."
            # Das fertige Paket darf keinen alten Prefix enthalten (ELF und Text).
            for deb in output/"${pkg}"_*_"${arch}".deb; do
                chk="$(mktemp -d "${TMP_ROOT}/debcheck.XXXXXX")"
                dpkg-deb -x "${deb}" "${chk}"
                bad="$(grep -rla 'com\.termux' "${chk}" || true)"
                if [ -n "${bad}" ]; then
                    echo "FEHLER: ${deb} enthält 'com.termux':" >&2
                    printf '%s\n' "${bad}" | head >&2
                    rm -rf "${chk}"; exit 1
                fi
                rm -rf "${chk}"
            done
        done
    done
fi

# ----------------------------------------------------
# Step 6: Verifikation der Bootstraps (hart, ohne Ausnahmen)
# ----------------------------------------------------
echo "[Step 6/9] Bootstraps verifizieren..."
VERIFY_DIR="${TMP_ROOT}/verify"
rm -rf "${VERIFY_DIR}"; mkdir -p "${VERIFY_DIR}"
verify_failed=0
for arch in "${ARCHS_LIST[@]}"; do
    dest="${VERIFY_DIR}/${arch}"; mkdir -p "${dest}"
    unzip -q "${CLONE_DIR}/bootstrap-${arch}.zip" -d "${dest}"

    # a) kein Pfad und kein Textinhalt mit com.termux
    bad_paths="$(find "${dest}" -path '*com.termux*')"
    if [ -n "${bad_paths}" ]; then
        echo "[${arch}] Pfade mit com.termux gefunden:" >&2; printf '%s\n' "${bad_paths}" | head >&2; verify_failed=1
    fi
    bad_text="$(grep -rIl 'com\.termux' "${dest}" || true)"
    if [ -n "${bad_text}" ]; then
        echo "[${arch}] Textdateien mit com.termux:" >&2; printf '%s\n' "${bad_text}" | head >&2; verify_failed=1
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
    [ -e "${dest}/etc/apt/trusted.gpg.d/codeforge.gpg" ] || { echo "[${arch}] trusted.gpg.d/codeforge.gpg fehlt" >&2; verify_failed=1; }
    grep -q "${PREFIX_PATH}" "${dest}/SYMLINKS.txt" || { echo "[${arch}] SYMLINKS.txt ohne ${PREFIX_PATH}" >&2; verify_failed=1; }
    echo "[${arch}] geprüft: $(find "${dest}" -type f | wc -l) Dateien"
done
rm -rf "${VERIFY_DIR}"
[ "${verify_failed}" -eq 0 ] || die "Verifikation fehlgeschlagen – Bootstraps NICHT veröffentlichen."

# ----------------------------------------------------
# Step 7: Bootstraps + Prüfsummen ablegen
# ----------------------------------------------------
echo "[Step 7/9] Bootstraps ablegen & Prüfsummen..."
mkdir -p "${BOOTSTRAP_DIR}"
for arch in "${ARCHS_LIST[@]}"; do
    mv -f "${CLONE_DIR}/bootstrap-${arch}.zip" "${BOOTSTRAP_DIR}/"
done
(cd "${BOOTSTRAP_DIR}" && for z in bootstrap-*.zip; do sha256sum "${z}" > "${z}.sha256"; echo "  $(cat "${z}.sha256")"; done)

# ----------------------------------------------------
# Step 8: APT-Repository aus den gebauten .deb-Dateien
# ----------------------------------------------------
echo "[Step 8/9] APT-Repository erzeugen & signieren..."
rm -rf "${REPO_DIR}"
mkdir -p "${REPO_DIR}/pool/main"

# Pool-Layout wie bei Termux: pool/main/<erster Buchstabe | lib+Buchstabe>/<paket>/<datei>.deb
shopt -s nullglob
deb_count=0
for deb in "${CLONE_DIR}"/output/*.deb; do
    pkg="$(dpkg-deb -f "${deb}" Package)"
    [ -n "${pkg}" ] || die "Kein Package-Feld in ${deb}"
    case "${pkg}" in lib?*) bucket="${pkg:0:4}" ;; *) bucket="${pkg:0:1}" ;; esac
    mkdir -p "${REPO_DIR}/pool/main/${bucket}/${pkg}"
    cp -f "${deb}" "${REPO_DIR}/pool/main/${bucket}/${pkg}/"
    deb_count=$((deb_count + 1))
done
shopt -u nullglob
[ "${deb_count}" -gt 0 ] || die "Pool ist leer."
echo "  ${deb_count} Pakete im Pool."

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
        echo "- \`$(basename "${z}")\` – SHA-256 \`$(cut -d' ' -f1 "${z}.sha256")\`"
    done
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
