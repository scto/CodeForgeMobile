#!/usr/bin/env bash
# Prüft die Workflow-Eingaben, löst die Etappe (apt-stages.conf) auf und legt fest, ob das Repo neu gebaut (full)
# oder ergänzt (add) wird. Schreibt die Ergebnisse nach $GITHUB_OUTPUT.
#
# Eingaben (Umgebung): IN_ARCHS IN_EXTRA IN_REPO_PKGS IN_STAGE IN_MODE IN_TAG IN_TARGET IN_APT_URL
# Testhilfe: PLAN_REPO_EXISTS=yes|no überspringt die Netzabfrage.
set -euo pipefail

STAGES_FILE="${STAGES_FILE:-apt-stages.conf}"
OUT="${GITHUB_OUTPUT:-/dev/stdout}"
err() { echo "::error::$*"; exit 1; }

# ---- Architekturen ----
archs=""
IFS=',' read -ra parts <<< "${IN_ARCHS// /}"
for a in "${parts[@]}"; do
  case "$a" in
    aarch64|arm|i686|x86_64) ;;
    *) err "Unbekannte Architektur '$a' (erlaubt: aarch64, arm, i686, x86_64)" ;;
  esac
  case " $archs " in *" $a "*) ;; *) archs="${archs:+$archs }$a" ;; esac
done
[ -n "$archs" ] || err "Keine Architektur angegeben"

# ---- Paketlisten ----
name_re='^[a-z0-9+._-]+(,[a-z0-9+._-]+)*$'
extra="${IN_EXTRA// /}"
[ -z "$extra" ] || [[ "$extra" =~ $name_re ]] || err "extra_packages: nur Paketnamen, durch Komma getrennt"
repo_pkgs="${IN_REPO_PKGS// /}"
[ -z "$repo_pkgs" ] || [[ "$repo_pkgs" =~ $name_re ]] || err "repo_packages: nur Paketnamen, durch Komma getrennt"

stage="${IN_STAGE// /}"
stage_pkgs=""; stage_flags=""
if [ -n "$stage" ]; then
  [[ "$stage" =~ ^[A-Za-z0-9._-]+$ ]] || err "stage: ungültiger Name"
  [ -f "$STAGES_FILE" ] || err "$STAGES_FILE nicht gefunden"
  line="$(grep -v '^[[:space:]]*#' "$STAGES_FILE" | awk -F'|' -v s="$stage" '{n=$1; gsub(/[ \t]/,"",n)} n==s {print; exit}')"
  if [ -z "$line" ]; then
    avail="$(grep -v '^[[:space:]]*#' "$STAGES_FILE" | awk -F'|' 'NF>=2 {n=$1; gsub(/[ \t]/,"",n); printf "%s ", n}')"
    err "Etappe '$stage' nicht in $STAGES_FILE. Vorhanden: $avail"
  fi
  stage_pkgs="$(printf '%s' "$line" | awk -F'|' '{print $2}' | tr -d ' \t')"
  stage_flags="$(printf '%s' "$line" | awk -F'|' '{print $3}' | sed 's/^[ \t]*//;s/[ \t]*$//')"
  [[ "$stage_pkgs" =~ $name_re ]] || err "Etappe '$stage': ungültige Paketliste in $STAGES_FILE"
fi

# Vereinigung (Reihenfolge erhalten, ohne Duplikate)
all_pkgs=""
IFS=',' read -ra pl <<< "${stage_pkgs},${repo_pkgs}"
for p in "${pl[@]}"; do
  [ -n "$p" ] || continue
  case ",$all_pkgs," in *",$p,"*) ;; *) all_pkgs="${all_pkgs:+$all_pkgs,}$p" ;; esac
done

# ---- Flags der Etappe ----
single_arch=0; skip_deps=""
for f in $stage_flags; do
  case "$f" in
    single-arch) single_arch=1 ;;
    nodeps=all)  skip_deps="$all_pkgs" ;;
    nodeps=*)    skip_deps="${f#nodeps=}" ;;
    *) err "Etappe '$stage': unbekanntes Flag '$f'" ;;
  esac
done
if [ "$single_arch" = 1 ]; then
  first="${archs%% *}"
  case " $archs " in *" aarch64 "*) first=aarch64 ;; esac
  archs="$first"
fi

# ---- Release-Tag / Ziel / URL ----
tag="${IN_TAG:-bootstrap-$(date -u +%Y.%m.%d)}"
[[ "$tag" =~ ^[A-Za-z0-9._-]+$ ]] || err "Ungültiger release_tag"
[[ "$IN_TARGET" =~ ^[A-Za-z0-9._-]+/[A-Za-z0-9._-]+$ ]] || err "Ungültiges target_repo"
if [ -n "${IN_APT_URL:-}" ] && ! [[ "$IN_APT_URL" =~ ^https://[A-Za-z0-9._~/:%@+-]+$ ]]; then
  err "apt_url muss eine https-URL sein"
fi

# ---- Existiert das Repo schon? (gh-pages des Ziel-Repos) ----
exists="${PLAN_REPO_EXISTS:-}"
if [ -z "$exists" ]; then
  code="$(curl -s -o /dev/null -w '%{http_code}' --retry 3 --retry-all-errors \
          "https://raw.githubusercontent.com/${IN_TARGET}/gh-pages/dists/stable/Release" || true)"
  case "$code" in
    200) exists=yes ;;
    404) exists=no ;;
    *)   err "Konnte nicht prüfen, ob ${IN_TARGET} (gh-pages) schon ein Repo enthält (HTTP $code). Modus explizit wählen (add/full) und erneut starten, sobald GitHub erreichbar ist." ;;
  esac
fi

mode="${IN_MODE:-auto}"
case "$mode" in
  auto) [ "$exists" = yes ] && mode=add || mode=full ;;
  add)  [ "$exists" = yes ] || err "Modus add: ${IN_TARGET} hat noch kein Repo auf gh-pages. Erst einmal mit Modus full bauen." ;;
  full) : ;;
  *)    err "Unbekannter Modus '$mode'" ;;
esac

if [ "$mode" = add ]; then
  [ -n "$all_pkgs" ] || err "Modus add braucht Pakete (stage oder repo_packages) – sonst gibt es nichts zu ergänzen."
  [ -z "$extra" ] || err "extra_packages gehören in den Bootstrap und gehen nur im Modus full (der Bootstrap wird dabei neu gebaut)."
fi

# shellcheck disable=SC2086 # Word splitting is intentional to split space-separated architectures into lines
archs_json="$(printf '%s\n' $archs | jq -R . | jq -sc .)"
{
  echo "mode=$mode"
  echo "archs=$archs"
  echo "archs_json=$archs_json"
  echo "repo_packages=$all_pkgs"
  echo "skip_deps=$skip_deps"
  echo "extra=$extra"
  echo "tag=$tag"
  echo "repo_exists=$exists"
} >> "$OUT"

echo "Modus: $mode (Repo vorhanden: $exists) | Architekturen: $archs | Etappe: ${stage:-keine} | Pakete: ${all_pkgs:-keine} | ohne Abhängigkeiten: ${skip_deps:-keine} | Tag: $tag"
[ "$mode" != full ] || [ "$exists" != yes ] || echo "::warning::Modus full: Bootstraps UND das gesamte Repo auf gh-pages werden neu erzeugt und ersetzen den bestehenden Stand."
