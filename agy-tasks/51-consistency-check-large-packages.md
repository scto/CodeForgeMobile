# Task 51 – Konsistenzprüfung im Zusammenbau: große Pakete mitzählen, texinfo-Prüfsumme

**Ziel:** Der Zusammenbau (Assemble) einer Gruppe mit einem großen Paket (> 95 MiB, liegt als Release-Asset `large-packages`, nicht im Pool) scheitert nicht mehr an der Konsistenzprüfung. Gruppe H2-jdk21 (und danach H1, H3, A) lässt sich veröffentlichen. Zusätzlich: der sporadische Prüfsummenfehler beim Quellarchiv von `texinfo` (arm) wird gezielt behandelt.
**Warum jetzt:** Lauf 108508658117 (2026-10-23/24, Gruppe `H2-jdk21`, `stop_on_error=false`). Zusammenbau brach ab mit `FEHLER: openjdk-21-x aarch64: Abhängigkeit 'openjdk-21 (= 21.0.12)' nicht erfüllbar`, unmittelbar nach `Abgetrennt: openjdk-21 21.0.12 (aarch64), 100 MiB` und `Große Pakete: 1`. Nichts wurde veröffentlicht; gh-pages bleibt unverändert (803 Pakete).

**Gemeinsame Regeln:** wie Task 46/48/49 (Shell mit `set -euo pipefail`, shellcheck-sauber; Workflows mit `actionlint`; Python nur Standardbibliothek, Aufruf mit `python3 -I`). Nichts erfinden: Was nicht selbst ausgeführt wurde, steht im Bericht unter **nicht getestet**. Geheimnisse (GPG-Passphrase, Tokens) nie ins Log, nie in Dateien, nie in Berichte. Keine Pushes ohne Auftrag des Inhabers. Bericht in `docs/large-packages-check-report.md`; `agy-tasks/TASKS-STATUS.md` und `README.md` ergänzen (Commit-Hash).

## 51.1 Befund (erst selbst prüfen)

1. **Ablauf in `build-bootstrap-repo.yml` (Assemble-Job):** `Große Pakete abtrennen (> 95 MiB)` verschiebt die `.deb` nach `big-debs/` und schreibt `big-new.txt` (Zeilenformat `name|ver|arch|asset|sha|size|url`). Danach läuft `Repo ergänzen, signieren, prüfen` (`build_codeforge_repo.sh`, Prüfung am Ende von Schritt 8, `check-apt-consistency.py repo …`). Erst **danach** schreibt `Manifest großer Pakete schreiben` die `large-packages.txt` (Basis-Manifest aus `base-repo/` plus `big-new.txt`).
2. **Das Skript kann es schon:** `scripts/check-apt-consistency.py repo` kennt `--large-packages` und liest ein Manifest, zählt die Einträge als vorhandene Pakete (Name, Version, Architektur). Der Aufruf in `build_codeforge_repo.sh` übergibt es **nicht**; die Ausweichpfade (`<repo>/large-packages.txt`, `<repo>/../large-packages.txt`) treffen im Assemble nicht, weil das Manifest dort noch nicht existiert und `BASE_REPO_DIR` nicht durchsucht wird. Prüfe das im Code und im Log (`Prüfe Abhängigkeits-Konsistenz des APT-Repositories...`).
3. **Folgen:** Betroffen ist jedes Paket, das ein großes Paket verlangt (`openjdk-21-x` → `openjdk-21`, `openjdk-25-x`, `openjdk-17-x`, alles mit Abhängigkeit auf diese JDKs: ant, apksigner, d8, kotlin, maven, codeforge-env aus Gruppe A). Im Pool ist das große Paket nie sichtbar.
4. **Zweiter Fehler im selben Lauf (nur arm):** `openjdk-21` bricht beim Herunterladen von `https://salsa.debian.org/tex-team/texinfo/-/archive/debian/7.3-1/texinfo-debian-7.3-1.tar.gz` mit `Wrong checksum` ab (Rezept `packages/texinfo/build.sh` im Fork, `TERMUX_PKG_SRCURL[1]`). aarch64 lud dieselbe Datei drei Minuten später ohne Fehler. Vermutung: GitLab erzeugt das `git archive` bei jeder Anfrage neu, die Bytes sind nicht garantiert stabil. Prüfe das (Prüfsumme im Rezept gegen zwei frische Downloads; Log beider Architekturen), bevor du etwas änderst.

Prüfe diese Aussagen vor dem Start und korrigiere sie im Bericht.

## 51.2 Änderung: Manifeste an die Prüfung übergeben

Referenzumsetzung des Agenten liegt dem Inhaber als `agy-fix-49-8.zip` vor (Patch `large-packages-check.patch` gegen `main` `1ef8d83`; getestet: `python3 -m unittest test_check_apt_consistency`, 14 Tests grün; der Workflow selbst ist **nicht** gelaufen). Setze sie um oder begründe Abweichungen:

1. **`scripts/check-apt-consistency.py`:** `--large-packages` ist mehrfach angebbar (`action="append"`); alle Manifeste werden gelesen, bei gleichem Paket und gleicher Architektur gewinnt der **spätere** Eintrag. Wird nichts übergeben, bleibt das bisherige Ausweichverhalten (erste vorhandene Datei). `run_repo_check(..., large_packages_path=…)` nimmt String **oder** Liste.
2. **`build_codeforge_repo.sh`** (Aufruf der Prüfung, Schritt 8): `BASE_REPO_DIR/large-packages.txt` zuerst, danach `BIG_NEW_FILE` (neu, Pfad zu `big-new.txt`), jeweils nur wenn die Datei nicht leer ist. Leeres Array sicher expandieren (`${large_args[@]+"${large_args[@]}"}`). Die bestehende Zeile `|| [ "${CONSISTENCY_CHECK}" = "warn" ] || die …` bleibt.
3. **`build-bootstrap-repo.yml`** (Schritt `Repo ergänzen, signieren, prüfen`): `export BIG_NEW_FILE="$PWD/big-new.txt"`.
4. **Test** in `scripts/test_check_apt_consistency.py`: `openjdk-21-x` mit `Depends: openjdk-21 (= 21.0.12)`; Basis-Manifest mit `21.0.11`, `big-new.txt` mit `21.0.12`. Mit beiden Manifesten keine Fehler; nur mit dem Basis-Manifest genau ein Fehler. Optional: ein Fall, in dem das große Paket in der falschen Architektur steht (Fehler bleibt).
5. **Grenze der Prüfung dokumentieren:** Für große Pakete kennt die Prüfung nur Name, Version und Architektur aus dem Manifest, nicht deren eigene `Depends`. Hat ein großes Paket Abhängigkeiten, werden sie nicht geprüft. Entscheide, ob das reicht (Vorschlag: ja, im Bericht festhalten; ein Nachtrag wäre, die `Depends` beim Abtrennen mit `dpkg-deb -f` in einer zusätzlichen Spalte des Manifests zu speichern, ohne das Format bestehender Leser zu brechen: prüfe alle Leser von `large-packages.txt`, auch in der App und in `codeforge-env`, bevor du die Spalten änderst).

## 51.3 texinfo-Prüfsumme (arm)

1. Zuerst prüfen, ob ein erneuter Lauf genügt (Inhaber startet `only=H2-jdk21`, `mode=add`). Tritt der Fehler nur gelegentlich auf, genügt eine Wiederholung mit Wartezeit beim Herunterladen (Task 49.5 hat dafür bereits einen Mechanismus für Cabal vorgesehen: wiederverwenden, nicht neu erfinden).
2. Tritt er wiederholt auf: Quelle austauschen. Optionen mit Folgen im Bericht: (a) das `.debian.tar.xz` von `deb.debian.org` (stabile Prüfsumme, das Rezept benutzt dessen URL schon in `termux_pkg_auto_update`), (b) Archiv einmal spiegeln (`codeforge-tools`-Muster) und Prüfsumme dort fixieren. Änderungen am Rezept laufen über die `fix_*`-Funktionen in `build_codeforge_repo.sh` (der Fork bleibt unverändert), nicht über Handarbeit im Fork.
3. Kein stilles Überspringen: Schlägt der Download endgültig fehl, bleibt der Job rot.

## 51.4 Verifikation (Inhaber führt aus, Agent bereitet vor)

1. Nach dem Merge: `build-all-stages.yml` mit `only=H2-jdk21`, `mode=add`, `archs=aarch64,arm`, `publish=true`, `consistency_check=fail`. Erwartet: Zusammenbau grün, `large-packages.txt` im Repo enthält `openjdk-21` (aarch64, bei arm je nach Größe), Release `large-packages` hat das Asset.
2. Danach H1-jdk17, danach `only=A-leicht-jvm` (Reihenfolge nach Task 49.2). Erwartet: 12 Pakete aus A im Repo.
3. `python3 -I scripts/check-apt-consistency.py repo <repo> --large-packages <large-packages.txt>` gegen das veröffentlichte Repo: keine Fehler außer bewusst verworfenen Paketen.
4. Gerät (nur durch den Inhaber): `apt update`, `apt install openjdk-21` (lädt das Asset über `large-packages.txt`), `java -version`. **Nicht getestet**, bis er es gemacht hat.
5. Doku (Task 99.8, `08-fehlersuche.md`): Zeile „Zusammenbau meldet ‚Abhängigkeit nicht erfüllbar‘ für ein JDK-Paket → große Pakete im Manifest?“ ergänzen.

## 51.5 Nicht Teil dieses Tasks

* Neuer Bootstrap (`mode=full`) mit der Hüllen-Korrektur (Task 45/Closure), `release_tag` neu setzen (alter Lauf: „a release with the same tag name already exists“).
* Umstieg auf aptly.

## 51.6 Commit-Vorschlag
```
fix(apt-check): count large packages (release assets) in assemble consistency check

The assemble check only saw the pool, so packages depending on a
separated large .deb (openjdk-21-x -> openjdk-21) failed. Pass the base
large-packages.txt and this run's big-new.txt to check-apt-consistency.py
(--large-packages is repeatable, later entries win). Add a test.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01Lf4ojCvX11BeECzDFXhL7X
```
