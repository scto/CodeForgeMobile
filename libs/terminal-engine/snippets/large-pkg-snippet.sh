# Große Pakete (> 95 MiB, z. B. gradle, openjdk-*, libllvm) liegen wegen des
# 100-MB-Limits von GitHub Pages nicht im APT-Pool, sondern als Asset am Release
# 'large-packages'. Das Verzeichnis '<apt-url>/large-packages.txt' enthält Zeilen:
#   name|version|arch|datei|sha256|bytes|url
# Benötigt: say, err, download, $TMP, $PREFIX.

apt_base_url() {
  local sl="$PREFIX/etc/apt/sources.list" url=""
  if [ -f "$sl" ]; then
    url=$(awk '!/^[[:space:]]*#/ && $1=="deb" {for(i=2;i<=NF;i++) if ($i ~ /^https?:\/\//) {print $i; exit}}' "$sl")
  fi
  if [ -z "$url" ] && [ -d "$PREFIX/etc/apt/sources.list.d" ]; then
    url=$(awk '!/^[[:space:]]*#/ && $1=="deb" {for(i=2;i<=NF;i++) if ($i ~ /^https?:\/\//) {print $i; exit}}' "$PREFIX/etc/apt/sources.list.d"/*.list 2>/dev/null | head -n1)
  fi
  printf '%s' "${url%/}"
}

install_repo_pkg() {
  local want="${1:-}" base arch man="$TMP/.cfsdk-large-packages.txt" row ver rarch asset sha bytes url deb got keyring
  [ -n "$want" ] || return 1
  base=$(apt_base_url)
  arch=$(dpkg --print-architecture 2>/dev/null || echo "${CF_ARCH:-aarch64}")
  row=""
  if [ -n "$base" ]; then
    if [ ! -s "$man" ]; then
      curl -fsSL --max-time 15 "$base/large-packages.txt" >"$man" 2>/dev/null || rm -f "$man"
      keyring="$PREFIX/share/termux-keyring/codeforge.gpg"
      if [ -s "$man" ] && [ -s "$keyring" ] && command -v gpgv >/dev/null 2>&1; then
        if curl -fsSL --max-time 15 "$base/large-packages.txt.asc" >"$man.asc" 2>/dev/null && [ -s "$man.asc" ]; then
          if ! gpgv --keyring "$keyring" "$man.asc" "$man" >/dev/null 2>&1; then
            err "GPG-Signatur von large-packages.txt ungültig!"
            rm -f "$man" "$man.asc"
            return 1
          fi
        fi
        rm -f "$man.asc"
      fi
    fi
    if [ -s "$man" ]; then
      row=$(awk -F'|' -v p="$want" -v a="$arch" '$1==p && ($3==a || $3=="all") {v=$0} END{print v}' "$man")
    fi
  fi

  if [ -z "$row" ]; then
    if [ -n "${PKG_INSTALL_OUT:-}" ]; then
      DEBIAN_FRONTEND=noninteractive pkg install -y "$want" >"$PKG_INSTALL_OUT" 2>&1
    else
      DEBIAN_FRONTEND=noninteractive pkg install -y "$want"
    fi
    return $?
  fi

  IFS='|' read -r _ ver rarch asset sha bytes url <<<"$row"
  case "$url" in
    https://github.com/*) ;;
    *)
      err "Ungültige Download-URL für großes Paket $want (nur https://github.com/ erlaubt): $url"
      return 1
      ;;
  esac
  printf '%s' "$sha" | grep -Eq '^[0-9a-f]{64}$' || {
    err "Ungültige SHA-256 im Manifest für $want: $sha"
    return 1
  }

  deb="$TMP/${asset##*/}"
  say "Lade großes Paket $want $ver ($rarch) von GitHub Release …"
  download "$url" "$deb" 10 75 || { rm -f "$deb"; return 1; }

  got=$(sha256sum "$deb" | cut -d' ' -f1)
  if [ "$got" != "$sha" ]; then
    err "Prüfsumme für $asset stimmt nicht (erwartet $sha, erhalten $got)"
    rm -f "$deb"
    return 1
  fi

  say "Installiere $asset …"
  if [ -n "${PKG_INSTALL_OUT:-}" ]; then
    if ! DEBIAN_FRONTEND=noninteractive apt install -y "$deb" >"$PKG_INSTALL_OUT" 2>&1; then
      rm -f "$deb"
      return 1
    fi
  elif ! DEBIAN_FRONTEND=noninteractive apt install -y "$deb"; then
    rm -f "$deb"
    return 1
  fi
  rm -f "$deb"
  return 0
}
