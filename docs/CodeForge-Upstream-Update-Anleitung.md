# CodeForge: `terminal-packages-codeforge` aktuell halten (Upstream-Update)

Stand: 2026-10-11 · gehört zu `CodeForge-Pipeline-Anleitung.md`

## 0. Kurzfassung

- **Upstream** ist `https://github.com/termux/termux-packages` (Branch `master`), nicht `Wadamzmail/terminal-packages-androidide`.
- **Merge und Cherry-Pick taugen hier nicht.** Dein Fork hat nur *einen* Commit und keine gemeinsame Historie mit Upstream. Er unterscheidet sich von heutigem Upstream in rund 900 Dateien, fast alles davon ist reiner Versionsunterschied, nicht deine Arbeit.
- **Deine eigene Anpassung ist winzig** (Abschnitt 1). Das lässt sich mit einem kleinen, wiederholbaren Patch auf frischen Upstream-Code anwenden.
- **Empfehlung:** gar nicht mehr aktualisieren, sondern bei jedem Build Upstream *an einem festen Tag* klonen und den Patch anwenden („Weg A“, Abschnitt 3). Das Repo `terminal-packages-codeforge` dient dann nur noch als Ziel für Release und GitHub Pages.

## 1. Befund: Was ist wirklich anders als Upstream?

Gemessen am Fork-Stand (reparierte Kopie) gegen `termux-packages` `master` vom 2026-10-11:

| Messung | Wert |
|---|---|
| Dateien Upstream / Fork | 10 642 / 10 473 |
| Nur Upstream (im Fork nicht vorhanden) | 207 |
| Nur im Fork | 38 (überwiegend veraltete Patches, die Upstream inzwischen entfernt hat) |
| Inhaltlich verschieden | 694 (überwiegend Paketversionen: Fork ist älter) |
| Dateien, die „codeforge“ enthalten | **7** |

Die sieben Dateien mit eigener Anpassung:

| Datei | Änderung | Wird im Build ohnehin selbst gesetzt? |
|---|---|---|
| `scripts/properties.sh` | 7 Werte: `TERMUX_APP__PACKAGE_NAME`, `TERMUX_APP__NAMESPACE`, `TERMUX_API_APP__PACKAGE_NAME`, `TERMUX_API_APP__NAMESPACE`, `TERMUX_AM_APP__NAMESPACE`, `CGCT_DEFAULT_PREFIX`, `CGCT_DIR` (der Rest sind Kommentartexte) | **nein – das ist die eigentliche Fork-Änderung** |
| `packages/termux-keyring/build.sh` + `codeforge.gpg`, `codeforge_pub.gpg` | Schlüssel installieren | ja, Step 4 von `build_codeforge_repo.sh` |
| `build-package.sh` | GPG-Import | ja, Step 4 |
| `scripts/generate-bootstraps.sh` | nachträgliches `sed com.termux -> com.codeforge.app` | wird vom aktuellen Weg nicht benutzt (in `build-bootstraps.sh` kein Aufruf gefunden); **nicht übernehmen**, siehe Hinweis in `build_codeforge_repo.sh` zu ELF-Dateien |
| `scripts/build-bootstraps.sh` | nur ein Hilfetext | unwichtig |
| `scripts/test-runner.sh` | Shebang | unwichtig für den Bootstrap-Bau (nicht geprüft) |
| `disabled-packages/codeforge-tools/` | eigenes, deaktiviertes Paket | wird im Build nicht verwendet (die Zeilen in `generate-bootstraps.sh` sind auskommentiert) |

Alles andere am Fork (verlorene Symlinks, kaputte URLs, fehlende Ausführbar-Bits) stammt aus früheren Kopier- und Ersetzungsläufen. Ein frischer Upstream-Checkout hat diese Schäden nicht.

Ein Merge-Versuch würde mit `--allow-unrelated-histories` an rund 700 Dateien Konflikte erzeugen, in denen du jeweils nur „Upstream nehmen“ wählen willst. Das ist dieselbe Arbeit wie ein Neuaufsatz, nur mit mehr Risiko.

## 2. Die Wege im Vergleich

| Weg | Aufwand pro Update | Risiko | Bewertung |
|---|---|---|---|
| **A: Kein Fork – Upstream-Tag klonen und patchen (Build-Zeit)** | Tag in einer Variable ändern | gering; Patch bricht laut ab, wenn Upstream die Zeilen ändert | **empfohlen** |
| **B: Fork als „Upstream + 1 Commit“ neu aufsetzen** | ca. 5 Minuten, Force-Push | gering bis mittel | nur sinnvoll, wenn du den Quellcode im eigenen Repo sehen oder ändern willst |
| C: `git merge upstream/master` | hoch (700+ Konflikte) | hoch | nicht empfohlen |
| D: Cherry-Pick einzelner Commits | je Commit Handarbeit | mittel | nur für einen einzelnen Fix, den du sofort brauchst |

Zu D: `termux-packages` bekommt täglich viele Commits. Jedes Paket hat eigene Abhängigkeiten, Patches und Versionen, die zusammenpassen müssen. Einzelne Commits herauszupicken führt schnell zu inkonsistenten Ständen.

## 3. Weg A (empfohlen): kein Fork, Upstream pinnen und patchen

### 3.1 Prinzip

1. `build_codeforge_repo.sh` klont `termux/termux-packages` **auf einen festen Tag oder Commit** (nicht auf `master`).
2. `scripts/apply-codeforge-properties.sh` setzt die sieben Werte in `scripts/properties.sh`. Das Script ist idempotent und bricht ab, wenn eine Zeile nicht genau einmal im erwarteten Zustand vorkommt.
3. Step 4 (Keyring, apt-Quelle, `termux-tools`-Export, HTTP/1.1-Downloads) läuft unverändert weiter.

Als fester Punkt eignen sich die **offiziellen Bootstrap-Tags** von Termux, z. B. `bootstrap-2026.10.11-r1+apt.android-7`. Sie erscheinen wöchentlich; das Termux-Team baut daraus seine eigenen Bootstraps.

### 3.2 Was ich geprüft habe

Gegen Upstream-Tag `bootstrap-2026.10.11-r1+apt.android-7` (Checkout `5c411e0`) und gegen `master` (`c4294e1`):
- Klonen per Tag und per vollem Commit-SHA funktioniert (flach, `--depth 1`).
- `apply-codeforge-properties.sh` setzt exakt dieselben sieben Werte wie dein alter Fork und ist beim zweiten Aufruf wirkungslos.
- Alle Patches aus Step 4 lassen sich auf den frischen Stand anwenden; geändert werden danach genau 6 Dateien plus die 2 Schlüsseldateien.
- Die Prüfung in Step 2 (`TERMUX_APP__PACKAGE_NAME="com.codeforge.app"`, `repo.json` zeigt auf `termux.dev`) wird erfüllt.

**Nicht geprüft:** ob der komplette Docker-Build mit diesem neueren Stand fehlerfrei durchläuft. Das zeigt erst ein Lauf in Actions.

### 3.3 Das Patch-Script `scripts/apply-codeforge-properties.sh`

Liegt neu im Repo `CodeForgeMobile` unter `scripts/`.

```bash
#!/usr/bin/env bash
# Setzt in scripts/properties.sh (Upstream termux-packages) die CodeForge-Werte. Idempotent, bricht bei Abweichungen ab.
set -euo pipefail
ROOT="$(cd "${1:-.}" && pwd)"; F="${ROOT}/scripts/properties.sh"
APP="${APP_PACKAGE:-com.codeforge.app}"
[ -f "$F" ] || { echo "FEHLER: $F fehlt" >&2; exit 1; }
set_var() {  # set_var <Zeilenanfang-Regex> <alt> <neu>
    local pat="$1" old="$2" new="$3" n
    if grep -qE "^${pat}\"${new}\"\$" "$F"; then return 0; fi
    n="$(grep -cE "^${pat}\"${old}\"\$" "$F" || true)"
    [ "$n" = "1" ] || { echo "FEHLER: ${pat}\"${old}\" kommt ${n}x vor (erwartet 1x) – properties.sh hat sich geändert, Script anpassen." >&2; exit 1; }
    sed -i -E "s#^(${pat})\"${old}\"\$#\1\"${new}\"#" "$F"
}
set_var 'TERMUX_APP__PACKAGE_NAME=' 'com.termux' "${APP}"
set_var 'TERMUX_APP__NAMESPACE=' 'com.termux' "${APP}"
set_var 'TERMUX_API_APP__PACKAGE_NAME=' 'com.termux.api' "${APP}.api"
set_var 'TERMUX_API_APP__NAMESPACE=' 'com.termux.api' "${APP}.api"
set_var 'TERMUX_AM_APP__NAMESPACE=' 'com.termux.termuxam' "${APP}.termuxam"
set_var 'CGCT_DEFAULT_PREFIX=' '/data/data/com.termux/files/usr/glibc' "/data/data/${APP}/files/usr/glibc"
set_var 'export CGCT_DIR=' '/data/data/com.termux/cgct' "/data/data/${APP}/cgct"
# TERMUX_REPO_APP__PACKAGE_NAME bleibt com.termux (zeigt auf das offizielle Repo)
grep -q '^TERMUX_REPO_APP__PACKAGE_NAME="com.termux"$' "$F" || { echo "FEHLER: TERMUX_REPO_APP__PACKAGE_NAME ist nicht com.termux." >&2; exit 1; }
bash -n "$F"
echo "properties.sh gepatcht für ${APP}"
```

Der Wert für `TERMUX_REPO_APP__PACKAGE_NAME` muss `com.termux` bleiben (Repo-Beschreibung des offiziellen Termux-Repos). Das Script prüft das.

### 3.4 Änderungen in `build_codeforge_repo.sh`

**(1) Variablen** (Zeile 38) – `FORK_URL` ersetzen:

```bash
UPSTREAM_URL="${CODEFORGE_UPSTREAM_URL:-https://github.com/termux/termux-packages}"
UPSTREAM_REF="${CODEFORGE_UPSTREAM_REF:-bootstrap-2026.10.11-r1+apt.android-7}"   # Tag oder 40-stelliger Commit-SHA
```

Kommentar im Kopf entsprechend ändern (`CODEFORGE_FORK_URL` → `CODEFORGE_UPSTREAM_URL`, `CODEFORGE_UPSTREAM_REF`).

**(2) Step 1: Klonen** (Zeilen 74–78) – ersetzen durch:

```bash
if [ ! -d "${CLONE_DIR}/.git" ]; then
    git init -q "${CLONE_DIR}"
    git -C "${CLONE_DIR}" remote add origin "${UPSTREAM_URL}"
    git -C "${CLONE_DIR}" fetch -q --depth 1 origin "${UPSTREAM_REF}" \
        || die "Upstream-Referenz '${UPSTREAM_REF}' nicht gefunden (Tag oder voller Commit-SHA in ${UPSTREAM_URL})."
    git -C "${CLONE_DIR}" checkout -q FETCH_HEAD
    echo "Upstream: ${UPSTREAM_URL} @ ${UPSTREAM_REF} ($(git -C "${CLONE_DIR}" rev-parse --short HEAD))"
else
    echo "Upstream bereits vorhanden: ${CLONE_DIR} (Patches werden idempotent angewendet)."
fi
```

**(3) Step 2: Patch vor der Prüfung** – direkt unter `PROPS=...` und `[ -f "${PROPS}" ] || die …` einfügen:

```bash
APP_PACKAGE="${APP_PACKAGE}" bash "${WORKSPACE_DIR}/scripts/apply-codeforge-properties.sh" "${CLONE_DIR}" \
    || die "properties.sh konnte nicht auf ${APP_PACKAGE} gepatcht werden (Upstream-Datei geändert? Script anpassen)."
```

Die Fehlermeldung der folgenden `grep`-Prüfung („Im Fork setzen und committen“) kann bleiben oder auf „Patch-Script prüfen“ geändert werden.

**(4) Reparaturfunktionen abschalten.** Sie waren nur für den beschädigten Fork nötig und gehören nicht auf sauberen Upstream-Code (`restore_symlinks` fasst kleine Dateien an, die wie ein Pfad aussehen). Drei Aufrufe in Bedingungen setzen:

```bash
# Zeile 136
if [ "${CODEFORGE_REPAIR_FORK:-0}" = "1" ]; then repair_mangled_references "${CLONE_DIR}"; fi
# Zeilen 312–313
if [ "${CODEFORGE_REPAIR_FORK:-0}" = "1" ]; then
    restore_symlinks "${CLONE_DIR}"
    restore_exec_bits "${CLONE_DIR}"
fi
```

Der Hinweis in Zeile 108 („github.com.codeforge.app in properties.sh“) entfällt von selbst.

**(5) Workflow `build-bootstrap-repo.yml`** – optionaler Input und Weitergabe:

```yaml
      upstream_ref:
        description: "Upstream-Referenz in termux/termux-packages (Tag, z. B. bootstrap-2026.10.11-r1+apt.android-7, oder Commit-SHA). Leer = Standard des Scripts"
        required: false
        default: ""
        type: string
```

und im Schritt „Bootstraps und APT-Repo bauen“ unter `env:` ergänzen:

```yaml
          CODEFORGE_UPSTREAM_REF: ${{ inputs.upstream_ref }}
```

Ein leerer Wert wird in Bash als „nicht gesetzt“ behandelt (`${VAR:-Standard}`), der Standard greift dann.

**(6) Repo `terminal-packages-codeforge`:** wird nur noch für Release und `gh-pages` gebraucht. Es kann (fast) leer sein: ein `README.md` genügt, damit der Branch `main` existiert. Der Quellcode-Stand darin spielt keine Rolle mehr; `target_repo` und die APT-URL bleiben unverändert. Öffentlich bleibt es trotzdem (GitHub Pages).

### 3.5 Update-Routine mit Weg A

1. Neuen Tag wählen: `git ls-remote --tags https://github.com/termux/termux-packages | grep bootstrap- | tail`
2. Workflow starten: `archs`, `publish = true`, `upstream_ref` = neuer Tag.
3. Bricht Step 2 oder Step 4 mit „… nicht gefunden“ ab, hat Upstream eine gepatchte Stelle geändert. Das Script nennt die Zeile; den Patch dort anpassen.
4. Nach dem Lauf: `gradle.properties` mit Version und SHA-256 aktualisieren, App bauen, auf dem Gerät `pkg update && pkg upgrade` und einen Paketinstall testen.
5. Rückweg: Alten Tag eintragen und neu bauen. Release-Tags (`bootstrap-JJJJ.MM.TT`) bleiben erhalten; das APT-Repo auf `gh-pages` wird mit jedem Publish ersetzt (siehe Hinweis im Workflow).

Wie oft? Nur bei Bedarf: Sicherheitsupdates wichtiger Pakete, eine neue Android-Version, ein Paket, das du brauchst. Alle paar Monate genügt.

## 4. Weg B: Fork als „Upstream + 1 Commit“ neu aufsetzen

Nur wählen, wenn du den Quellcode im eigenen Repo behalten willst. Der Fork bekommt dabei **eine neue Historie**, die alte `main` wird ersetzt.

```bash
# 1. Sicherung der alten main (Tag auf GitHub)
git clone https://github.com/scto/terminal-packages-codeforge sicherung
cd sicherung && git tag backup-vor-update-$(date +%F) && git push origin --tags && cd ..

# 2. Frischen Upstream-Stand holen (flach)
git init -b main terminal-packages-codeforge-neu
cd terminal-packages-codeforge-neu
git remote add upstream https://github.com/termux/termux-packages.git
git fetch --depth 1 upstream "bootstrap-2026.10.11-r1+apt.android-7"
git checkout -b main FETCH_HEAD

# 3. CodeForge-Patch anwenden (Script aus CodeForgeMobile)
bash /pfad/zu/CodeForgeMobile/scripts/apply-codeforge-properties.sh .
git config user.name  "scto"
git config user.email "tschmid35@gmail.com"
git add -A
git commit -m "CodeForge: Prefix com.codeforge.app (Upstream bootstrap-2026.10.11-r1+apt.android-7)"

# 4. Auf dein Repo schieben (ersetzt main!)
git remote add origin https://github.com/scto/terminal-packages-codeforge.git
git push --force-with-lease origin main
```

Hinweise:
- Der Push überschreibt `main`. Das Repo muss dafür öffentlich bleiben, und der Token braucht Schreibrechte (siehe `CodeForge-Pipeline-Anleitung.md`).
- Der Branch `gh-pages` (APT-Repo) bleibt unberührt.
- Bei einem späteren Update wiederholst du die Schritte 2–4 mit dem neuen Tag. Mit `git remote set-url` und `git fetch` geht das auch im bestehenden Checkout.
- `build_codeforge_repo.sh` bleibt bei Weg B fast gleich. Setze `CODEFORGE_FORK_URL` wie bisher und schalte nur die Reparaturfunktionen ab (Punkt 4 aus 3.4). Der Patch aus Punkt 3 schadet nicht, weil er idempotent ist.

## 5. Was bei jedem Update zu beachten ist

- **Neue Paketversionen** ändern die Bootstraps. Prüfe nach dem Lauf Step 6 (Verifikation) im Log und teste die App auf dem Gerät.
- **Geändertes Upstream-Verhalten:** Wenn `properties.sh`, `termux-tools/build.sh`, `termux-keyring/build.sh`, `packages/apt/build.sh` oder `build-package.sh` umgebaut werden, melden die Patches das mit `FEHLER: … nicht gefunden`. Das ist gewollt: ein stiller Fehlschlag würde Binaries mit falschem Prefix erzeugen.
- **Termux-eigene Klassen:** `bin/am` ruft die Klasse `com.termux.termuxam.Am` auf; sie steckt in der `am.apk` und wird nicht umbenannt. Die Verifikation erlaubt genau diese Stelle.
- **`termux-wake-lock` / `termux-open`** rufen `<Paket>.app.TermuxService` bzw. `TermuxOpenReceiver` auf. Ob diese Klassen in deiner App existieren, ist weiterhin nicht geprüft.
- **Eigene Zusätze** (z. B. das Paket `codeforge-tools`) gehören nicht in den Upstream-Klon. Wenn du sie brauchst, lege sie als Verzeichnis im Repo `CodeForgeMobile` ab und kopiere sie in Step 4 nach `packages/` des Klons.

## 6. Empfehlung

**Weg A.** Gründe:
1. Dein Fork enthält praktisch nur eine Änderung, die als Script nachvollziehbar und wiederholbar ist. Ein Fork hält dagegen Hunderte veraltete Dateien vor, die jedes Mal Aufwand erzeugen.
2. Ein fester Upstream-Tag macht Builds reproduzierbar; der Fehlerfall ist ein klarer Abbruch statt falscher Binaries.
3. Alle Beschädigungen des alten Forks (URLs, Symlinks, Ausführbar-Bits) entfallen, und mit ihnen die Reparaturfunktionen im Script.
4. Updates sind eine Zeile (Tag) statt eines Merge-Projekts.

Weg B nur dann, wenn du den Paketquellcode im eigenen Repo ändern willst (eigene Patches an einzelnen Paketen). Auch dann: pro Update neu aufsetzen, nicht mergen.

## 7. Offene Punkte

- Der erste Lauf mit Weg A ist ungetestet; der Docker-Build mit dem neuen Stand kann an Paketen scheitern, die sich seit deinem alten Fork geändert haben.
- Welche Version von `termux-packages` dein alter Fork ursprünglich hatte, ließ sich nicht feststellen (nur ein Commit, keine Historie).
- Die 38 „nur im Fork“-Dateien (u. a. Patches für `zsh`, `innoextract`, `spidermonkey`, `v4l-utils`) habe ich nicht einzeln bewertet. Brauchst du eines dieser Pakete mit dem alten Stand, prüfe vor dem Umstieg, ob Upstream es inzwischen anders löst.
