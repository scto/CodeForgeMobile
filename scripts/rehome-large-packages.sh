#!/usr/bin/env bash
# =============================================================================
# rehome-large-packages.sh — Große Pakete aus dem APT-Pool ins Release large-packages umziehen (Task 53)
#
# Liest das Repo (gh-pages), listet Pakete über der Schwelle (Standard 30 MiB),
# lädt sie als Asset ins Release large-packages hoch, verifiziert den Upload,
# entfernt sie erst danach aus dem Pool, regeneriert Packages/Release und Index,
# signiert neu und führt die Konsistenzprüfung (Task 51) aus.
#
# Standardmäßig läuft das Skript als Trockenlauf (dry_run=true).
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

# Standardwerte & Limits einlesen
LARGE_PKG_THRESHOLD_MIB=30
POOL_WARN_LIMIT_MB=800
POOL_MAX_LIMIT_MB=950
if [ -f "${WORKSPACE_DIR}/apt-limits.conf" ]; then
    # shellcheck disable=SC1091
    . "${WORKSPACE_DIR}/apt-limits.conf"
fi

REPO_DIR=""
TARGET_REPO="scto/terminal-packages-codeforge"
THRESHOLD_MIB="${LARGE_PKG_THRESHOLD_MIB}"
DRY_RUN=true
PUBLISH=false
GPG_PASSPHRASE="${CODEFORGE_GPG_PASSPHRASE:-}"
GPG_KEY="${KEY_FINGERPRINT:-}"

usage() {
    cat <<EOF
Verwendung: $(basename "$0") [OPTIONEN]

Optionen:
  --repo-dir <DIR>        Pfad zum lokalen APT-Repository (z. B. gh-pages Checkout)
  --threshold-mib <MIB>   Schwelle für große Pakete in MiB (Standard: ${THRESHOLD_MIB})
  --target-repo <REPO>    GitHub-Ziel-Repository (Standard: ${TARGET_REPO})
  --dry-run               Nur analysieren und anzeigen, nichts ändern (Standard)
  --execute               Umzug tatsächlich durchführen (Upload, Pool-Bereinigung, Signatur)
  --publish               Nach erfolgreichem Umzug Git-Commit und Push auf gh-pages
  --gpg-passphrase <PASS> GPG-Passphrase zum Signieren
  --gpg-key <FP>          GPG-Schlüsselfingerprint
  -h, --help              Diese Hilfe anzeigen
EOF
    exit 0
}

while [ $# -gt 0 ]; do
    case "$1" in
        --repo-dir)
            REPO_DIR="$2"; shift 2 ;;
        --threshold-mib)
            THRESHOLD_MIB="$2"; shift 2 ;;
        --target-repo)
            TARGET_REPO="$2"; shift 2 ;;
        --dry-run)
            DRY_RUN=true; shift ;;
        --execute)
            DRY_RUN=false; shift ;;
        --publish)
            PUBLISH=true; shift ;;
        --gpg-passphrase)
            GPG_PASSPHRASE="$2"; shift 2 ;;
        --gpg-key)
            GPG_KEY="$2"; shift 2 ;;
        -h|--help)
            usage ;;
        *)
            echo "Unbekannte Option: $1" >&2; exit 1 ;;
    esac
done

if [ -z "${REPO_DIR}" ]; then
    if [ -d "pool" ] && [ -d "dists" ]; then
        REPO_DIR="$PWD"
    elif [ -d "${WORKSPACE_DIR}/base-repo/pool" ]; then
        REPO_DIR="${WORKSPACE_DIR}/base-repo"
    else
        echo "FEHLER: --repo-dir muss angegeben werden oder im Repo-Verzeichnis ausgeführt werden." >&2
        exit 1
    fi
fi

REPO_DIR="$(cd "${REPO_DIR}" && pwd)"

if [ ! -d "${REPO_DIR}/pool" ]; then
    echo "FEHLER: Kein 'pool'-Verzeichnis in '${REPO_DIR}' gefunden." >&2
    exit 1
fi

echo "================================================================="
echo "CodeForge Large Packages Rehoming (Task 53)"
echo "Repo-Verzeichnis:      ${REPO_DIR}"
echo "Auslagerungsgrenze:    ${THRESHOLD_MIB} MiB"
echo "Ziel-GitHub-Repo:      ${TARGET_REPO}"
echo "Modus:                 $([ "${DRY_RUN}" = true ] && echo "TROCKENLAUF (dry-run)" || echo "AUSFÜHREN (execute)")"
echo "================================================================="

# 1. Pool-Größe vorher messen
pool_bytes_before=$(find "${REPO_DIR}/pool" -type f -exec stat -c%s {} + 2>/dev/null | awk '{s+=$1} END{print s+0}')
pool_mib_before=$((pool_bytes_before / 1048576))
pool_mb_before=$((pool_bytes_before / 1000000))
echo "Pool-Größe aktuell:    ${pool_bytes_before} Bytes (${pool_mib_before} MiB / ${pool_mb_before} MB)"
echo "Pages-Limits:          Warnung ab ${POOL_WARN_LIMIT_MB} MB · Abbruch ab ${POOL_MAX_LIMIT_MB} MB"

# 2. Alle .deb über der Grenze identifizieren
large_debs=()
while IFS= read -r -d '' f; do
    large_debs+=("$f")
done < <(find "${REPO_DIR}/pool" -type f -name '*.deb' -size "+${THRESHOLD_MIB}M" -print0)

total_large_count=${#large_debs[@]}
echo "Gefundene Pakete > ${THRESHOLD_MIB} MiB: ${total_large_count}"

if [ "${total_large_count}" -eq 0 ]; then
    echo "Keine Pakete über ${THRESHOLD_MIB} MiB im Pool vorhanden. Nichts zu tun."
    exit 0
fi

# Metadaten der großen Pakete sammeln
savings_bytes=0
parsed_items=() # Array von "deb_path|name|ver|arch|size|sha|asset_name|url"

printf "\n%-25s %-12s %-10s %-12s %s\n" "PAKET" "VERSION" "ARCH" "GRÖSSE (MiB)" "DATEI"
printf "%-25s %-12s %-10s %-12s %s\n" "-------------------------" "------------" "----------" "------------" "----------------------------------"

for deb in "${large_debs[@]}"; do
    size=$(stat -c%s "${deb}")
    savings_bytes=$((savings_bytes + size))
    size_mib=$((size / 1048576))
    
    # Metadaten über dpkg-deb oder Dateinamen ermitteln
    if command -v dpkg-deb >/dev/null 2>&1; then
        pkg_name=$(dpkg-deb -f "${deb}" Package 2>/dev/null || basename "${deb}" | cut -d'_' -f1)
        pkg_ver=$(dpkg-deb -f "${deb}" Version 2>/dev/null || basename "${deb}" | cut -d'_' -f2)
        pkg_arch=$(dpkg-deb -f "${deb}" Architecture 2>/dev/null || basename "${deb}" | cut -d'_' -f3 | sed 's/\.deb$//')
    else
        base_name=$(basename "${deb}")
        pkg_name=$(echo "${base_name}" | cut -d'_' -f1)
        pkg_ver=$(echo "${base_name}" | cut -d'_' -f2)
        pkg_arch=$(echo "${base_name}" | cut -d'_' -f3 | sed 's/\.deb$//')
    fi
    
    asset_name="$(basename "${deb}")"
    asset_name="${asset_name//:/_}" # Doppelpunkte (Epochen) für Release-Assets ersetzen
    sha=$(sha256sum "${deb}" | cut -d' ' -f1)
    url="https://github.com/${TARGET_REPO}/releases/download/large-packages/${asset_name}"
    
    printf "%-25s %-12s %-10s %-12s %s\n" "${pkg_name}" "${pkg_ver}" "${pkg_arch}" "${size_mib} MiB" "$(basename "${deb}")"
    parsed_items+=("${deb}|${pkg_name}|${pkg_ver}|${pkg_arch}|${size}|${sha}|${asset_name}|${url}")
done

savings_mib=$((savings_bytes / 1048576))
savings_mb=$((savings_bytes / 1000000))
projected_pool_bytes=$((pool_bytes_before - savings_bytes))
projected_pool_mib=$((projected_pool_bytes / 1048576))
projected_pool_mb=$((projected_pool_bytes / 1000000))

echo "-----------------------------------------------------------------"
echo "Ersparnis durch Umzug: ${savings_bytes} Bytes (${savings_mib} MiB / ${savings_mb} MB)"
echo "Erwartete neue Pool-Größe: ${projected_pool_bytes} Bytes (${projected_pool_mib} MiB / ${projected_pool_mb} MB)"
echo "-----------------------------------------------------------------"

# Wenn Trockenlauf: Jetzt sauber beenden
if [ "${DRY_RUN}" = true ]; then
    echo
    echo "✅ Trockenlauf (dry-run) erfolgreich abgeschlossen."
    echo "Es wurden keine Dateien verändert, hochgeladen oder gelöscht."
    echo "Um den Umzug tatsächlich auszuführen, rufe das Skript mit --execute auf."
    exit 0
fi

# =============================================================================
# AUSFÜHRUNG (execute)
# =============================================================================
echo
echo "🚀 Starte tatsächlichen Umzug..."

# Voraussetzungen prüfen
command -v gh >/dev/null 2>&1 || { echo "FEHLER: 'gh' CLI wird benötigt." >&2; exit 1; }
command -v gpg >/dev/null 2>&1 || { echo "FEHLER: 'gpg' wird benötigt." >&2; exit 1; }
command -v apt-ftparchive >/dev/null 2>&1 || { echo "FEHLER: 'apt-ftparchive' wird benötigt." >&2; exit 1; }

# Release large-packages sicherstellen
if ! gh release view large-packages --repo "${TARGET_REPO}" >/dev/null 2>&1; then
    echo "Erstelle GitHub-Release 'large-packages'..."
    gh release create large-packages --repo "${TARGET_REPO}" --title "Große Pakete" \
        --notes "Pakete über ${THRESHOLD_MIB} MiB, ausgelagert aus dem APT-Pool. Verzeichnis: large-packages.txt."
fi

manifest_file="${REPO_DIR}/large-packages.txt"
new_manifest_lines=()
files_to_remove=()

# SCHRITT 1: Jede Datei hochladen und VOR dem Löschen verifizieren
echo "--- Schritt 1: Assets hochladen und verifizieren ---"
for item in "${parsed_items[@]}"; do
    IFS='|' read -r deb_path p_name p_ver p_arch p_size p_sha p_asset p_url <<<"${item}"
    
    echo "Lade hoch: ${p_asset} (${p_name} ${p_ver} ${p_arch}, $((p_size / 1048576)) MiB)..."
    gh release upload large-packages "${deb_path}" --repo "${TARGET_REPO}" --clobber
    
    # Upload-Verifikation: Asset-Informationen von GitHub abfragen
    echo "Verifiziere Upload von ${p_asset}..."
    asset_info=$(gh release view large-packages --repo "${TARGET_REPO}" --json assets)
    remote_size=$(echo "${asset_info}" | jq -r ".assets[]? | select(.name == \"${p_asset}\") | .size // 0")
    if [ -z "${remote_size}" ] || [ "${remote_size}" = "0" ]; then
        echo "FEHLER: Asset '${p_asset}' nach dem Upload im Release nicht gefunden!" >&2
        exit 1
    fi
    if [ "${remote_size}" -ne "${p_size}" ]; then
        echo "FEHLER: Größe für '${p_asset}' stimmt nicht überein (Lokal: ${p_size}, GitHub: ${remote_size})!" >&2
        exit 1
    fi
    echo "  -> Verifiziert (Größe: ${remote_size} Bytes OK)."
    
    new_manifest_lines+=("${p_name}|${p_ver}|${p_arch}|${p_asset}|${p_sha}|${p_size}|${p_url}")
    files_to_remove+=("${deb_path}")
done

# SCHRITT 2: Manifest large-packages.txt aktualisieren
echo "--- Schritt 2: large-packages.txt aktualisieren ---"
tmp_man=$(mktemp)
if [ -f "${manifest_file}" ]; then
    cat "${manifest_file}" > "${tmp_man}"
fi
for line in "${new_manifest_lines[@]}"; do
    echo "${line}" >> "${tmp_man}"
done

# Neueste Einträge je Paket+Architektur behalten und sortieren
awk -F'|' 'NF>=7 {k=$1"|"$3; if(!(k in l)) K[++n]=k; l[k]=$0} END{for(i=1;i<=n;i++) print l[K[i]]}' "${tmp_man}" | sort > "${manifest_file}"
rm -f "${tmp_man}"
echo "Aktualisierte Einträge in large-packages.txt: $(wc -l < "${manifest_file}")"

# SCHRITT 3: Ausgelagerte Dateien aus dem Pool entfernen
echo "--- Schritt 3: Ausgelagerte Dateien aus dem Pool entfernen ---"
for f in "${files_to_remove[@]}"; do
    echo "Entferne aus Pool: $(basename "$f")"
    rm -f "$f"
done

# Leere Verzeichnisse im Pool aufräumen
find "${REPO_DIR}/pool" -type d -empty -delete 2>/dev/null || true

# SCHRITT 4: Packages und Packages.gz für alle Architekturen neu bauen
echo "--- Schritt 4: Packages-Indizes neu erstellen ---"
arch_dirs=()
for ad in "${REPO_DIR}/dists/stable/main"/binary-*; do
    [ -d "$ad" ] || continue
    arch=$(basename "$ad" | sed 's/^binary-//')
    arch_dirs+=("$arch")
done

if [ ${#arch_dirs[@]} -eq 0 ]; then
    arch_dirs=("aarch64" "arm")
fi

cd "${REPO_DIR}"
for arch in "${arch_dirs[@]}"; do
    bin_dir="dists/stable/main/binary-${arch}"
    mkdir -p "${bin_dir}"
    echo "Erstelle Packages für ${arch}..."
    apt-ftparchive --arch "${arch}" packages pool > "${bin_dir}/Packages"
    gzip -9c "${bin_dir}/Packages" > "${bin_dir}/Packages.gz"
done

# SCHRITT 5: Release-Datei neu erstellen
echo "--- Schritt 5: Release-Datei erstellen ---"
dist_dir="${REPO_DIR}/dists/stable"
tmp_rel=$(mktemp)
apt-ftparchive \
    -o "APT::FTPArchive::Release::Origin=CodeForge" \
    -o "APT::FTPArchive::Release::Label=CodeForge Mobile Repository" \
    -o "APT::FTPArchive::Release::Suite=stable" \
    -o "APT::FTPArchive::Release::Codename=stable" \
    -o "APT::FTPArchive::Release::Architectures=${arch_dirs[*]}" \
    -o "APT::FTPArchive::Release::Components=main" \
    -o "APT::FTPArchive::Release::Description=CodeForge package repository (prefix com.codeforge.app)" \
    release "${dist_dir}" > "${tmp_rel}"
mv -f "${tmp_rel}" "${dist_dir}/Release"

# SCHRITT 6: Signaturen erzeugen
echo "--- Schritt 6: Signaturen erzeugen ---"
gpg_args=("--batch" "--yes" "--pinentry-mode" "loopback")
if [ -n "${GPG_PASSPHRASE}" ]; then
    gpg_args+=("--passphrase" "${GPG_PASSPHRASE}")
fi
if [ -n "${GPG_KEY}" ]; then
    gpg_args+=("--local-user" "${GPG_KEY}")
fi

gpg "${gpg_args[@]}" --armor --detach-sign -o "${dist_dir}/Release.gpg" "${dist_dir}/Release"
gpg "${gpg_args[@]}" --clearsign -o "${dist_dir}/InRelease" "${dist_dir}/Release"
gpg "${gpg_args[@]}" --armor --detach-sign -o "${manifest_file}.asc" "${manifest_file}"

# Signaturen verifizieren
gpg --verify "${dist_dir}/Release.gpg" "${dist_dir}/Release" >/dev/null 2>&1 || { echo "FEHLER: Release.gpg ungültig!" >&2; exit 1; }
gpg --verify "${dist_dir}/InRelease" "${dist_dir}/Release" >/dev/null 2>&1 || { echo "FEHLER: InRelease ungültig!" >&2; exit 1; }
gpg --verify "${manifest_file}.asc" "${manifest_file}" >/dev/null 2>&1 || { echo "FEHLER: large-packages.txt.asc ungültig!" >&2; exit 1; }
echo "Signaturen erfolgreich verifiziert."

# SCHRITT 7: Index-Seite aktualisieren
echo "--- Schritt 7: index.html aktualisieren ---"
if [ -f "${WORKSPACE_DIR}/.github/scripts/generate-apt-index.py" ]; then
    url="https://${TARGET_REPO%%/*}.github.io/${TARGET_REPO##*/}"
    python3 -I "${WORKSPACE_DIR}/.github/scripts/generate-apt-index.py" "${REPO_DIR}" "${url}"
fi

# SCHRITT 8: Konsistenzprüfung ausführen (Task 51)
echo "--- Schritt 8: Konsistenzprüfung (check-apt-consistency.py) ---"
if [ -f "${WORKSPACE_DIR}/scripts/check-apt-consistency.py" ]; then
    python3 -I "${WORKSPACE_DIR}/scripts/check-apt-consistency.py" repo "${REPO_DIR}" --large-packages "${manifest_file}" --on-error fail
fi

# SCHRITT 9: Neue Pool-Größe ausgeben
pool_bytes_after=$(find "${REPO_DIR}/pool" -type f -exec stat -c%s {} + 2>/dev/null | awk '{s+=$1} END{print s+0}')
pool_mib_after=$((pool_bytes_after / 1048576))
pool_mb_after=$((pool_bytes_after / 1000000))
echo
echo "================================================================="
echo "🎉 Umzug erfolgreich abgeschlossen!"
echo "Pool vorher:   ${pool_bytes_before} Bytes (${pool_mib_before} MiB / ${pool_mb_before} MB)"
echo "Pool nachher:  ${pool_bytes_after} Bytes (${pool_mib_after} MiB / ${pool_mb_after} MB)"
echo "Eingespart:    $((pool_bytes_before - pool_bytes_after)) Bytes ($(( (pool_bytes_before - pool_bytes_after) / 1048576 )) MiB)"
echo "================================================================="

# SCHRITT 10: Optionaler Git Commit & Push
if [ "${PUBLISH}" = true ]; then
    echo "--- Schritt 10: Git Commit & Push nach gh-pages ---"
    cd "${REPO_DIR}"
    if git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
        git add pool/ dists/ large-packages.txt large-packages.txt.asc index.html
        git commit -m "rehome: Auslagerung von ${total_large_count} großen Paketen ins Release large-packages (Task 53)"
        git push origin gh-pages
        echo "Änderungen erfolgreich nach gh-pages gepusht."
    else
        echo "WARNUNG: --publish angegeben, aber ${REPO_DIR} ist kein Git-Arbeitsverzeichnis." >&2
    fi
fi
