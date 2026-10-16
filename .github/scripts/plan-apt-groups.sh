#!/usr/bin/env bash
# Baut aus apt-groups.json die Matrix für build-all-stages.yml.
# Eingaben (Umgebung): IN_PRESET (light|heavy|all) IN_ONLY (Gruppennamen, Komma) GROUPS_FILE STAGES_FILE
# Ausgabe: $GITHUB_OUTPUT (matrix=...), sonst stdout.
set -euo pipefail
err() { echo "FEHLER: $*" >&2; exit 1; }
GROUPS_FILE="${GROUPS_FILE:-apt-groups.json}"
STAGES_FILE="${STAGES_FILE:-apt-stages.conf}"
preset="${IN_PRESET:-light}"
only="${IN_ONLY// /}"
name_re='^[a-z0-9+._-]+(,[a-z0-9+._-]+)*$'
[ -f "$GROUPS_FILE" ] || err "$GROUPS_FILE fehlt"
[ -f "$STAGES_FILE" ] || err "$STAGES_FILE fehlt"
jq -e '.groups|type=="array"' "$GROUPS_FILE" >/dev/null || err "$GROUPS_FILE: 'groups' fehlt"

case "$preset" in
  light) sel='map(select(.heavy|not))' ;;
  heavy) sel='map(select(.heavy))' ;;
  all)   sel='.' ;;
  *) err "preset '$preset' unbekannt (light|heavy|all)" ;;
esac

if [ -n "$only" ]; then
  [[ "$only" =~ ^[A-Za-z0-9._,-]+$ ]] || err "only: ungültige Zeichen"
  list="$(printf '%s' "$only" | jq -R 'split(",")')"
  for n in $(printf '%s' "$only" | tr ',' ' '); do
    jq -e --arg n "$n" '.groups[]|select(.name==$n)' "$GROUPS_FILE" >/dev/null \
      || err "Gruppe '$n' nicht in $GROUPS_FILE. Vorhanden: $(jq -r '[.groups[].name]|join(", ")' "$GROUPS_FILE")"
  done
  matrix="$(jq -c --argjson l "$list" '[.groups[]|select(.name as $n|$l|index($n))]' "$GROUPS_FILE")"
else
  matrix="$(jq -c ".groups|$sel" "$GROUPS_FILE")"
fi

[ "$(jq 'length' <<<"$matrix")" -gt 0 ] || err "keine Gruppen ausgewählt (preset=$preset only=${only:-leer})"

# Validierung
while IFS=$'\t' read -r n st rp; do
  [[ "$n" =~ ^[A-Za-z0-9._-]+$ ]] || err "Gruppenname '$n' ungültig"
  if [ -n "$st" ]; then
    [[ "$st" =~ ^[A-Za-z0-9._-]+$ ]] || err "$n: Etappenname ungültig"
    grep -v '^[[:space:]]*#' "$STAGES_FILE" | awk -F'|' -v s="$st" '{x=$1; gsub(/[ \t]/,"",x)} x==s{f=1} END{exit !f}' \
      || err "$n: Etappe '$st' nicht in $STAGES_FILE"
  fi
  [ -z "$rp" ] || [[ "$rp" =~ $name_re ]] || err "$n: repo_packages ungültig"
  [ -n "$st$rp" ] || err "$n: weder stage noch repo_packages"
done < <(jq -r '.[]|[.name,(.stage//""),(.repo_packages//"")]|@tsv' <<<"$matrix")

[ "$(jq '[.[].name]|length-(unique|length)' <<<"$matrix")" -eq 0 ] || err "doppelte Gruppennamen"

out="$(jq -c '{include: .}' <<<"$matrix")"
echo "Gruppen (Reihenfolge): $(jq -r '[.[].name]|join(" → ")' <<<"$matrix")" >&2
if [ -n "${GITHUB_OUTPUT:-}" ]; then echo "matrix=$out" >> "$GITHUB_OUTPUT"; else echo "$out"; fi
