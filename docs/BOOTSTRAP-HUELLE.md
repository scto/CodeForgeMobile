# Bootstrap enthält 159 statt ~80 Pakete – Ursache und Fix (Lauf 2026-10-22, build-bootstrap-repo.yml, mode full)

**Befund (Log 2_build_full.txt):** Alle drei Architekturen (aarch64, arm, x86_64) bauen durch (Step 5, ca. 2 h 10). Step 6 (`check-apt-consistency.py`) meldet 13 Pakete auf der Verbotsliste und "Bootstrap enthält 159 installierte Pakete" (Richtwert 80). Der Lauf endet mit "Bootstraps NICHT veröffentlichen": die Sperre hat gewirkt, es wurde nichts Kaputtes veröffentlicht.

**Ursache:** `scripts/build-bootstraps.sh` ruft für jedes Bootstrap-Paket `build-package.sh` auf. Das legt nach `output/` alles ab, was dabei mitgebaut wird: Build-Abhängigkeiten (`docbook-xsl` → `xsltproc`, `docbook-xml`, `doxygen`; `swig`) und Subpackages (`dpkg-perl`, `dpkg-scanpackages`, `apt-transport-tor` → `tor`, `unbound`, `pyunbound`, `ca-certificates-java`, `python-tkinter`). `extract_debs()` extrahiert danach `for deb in *.deb`, also **jede** Datei in `output/`. Im Log steht die Liste unter "Deb Files:" (alphabetisch, ab Zeile 132 000 mit `current_package_name:`).
Das ist dieselbe Ursache wie bei `dpkg-perl` mit unerfüllter Abhängigkeit `clang` auf dem Gerät (Task 45). Die Prüfung ist also richtig; der Bootstrap-Bau ist zu großzügig.

**Fix:** `bootstrap-closure.patch` (gegen `build_codeforge_repo.sh`) fügt `fix_bootstrap_closure` hinzu. Die Funktion patcht beim Klonen `scripts/build-bootstraps.sh` im Fork: Vor dem Extrahieren wird die Abhängigkeitshülle der Bootstrap-Liste (`PACKAGES` inkl. `--add`) aus den `Depends:`-Feldern der .deb in `output/` berechnet (wie `pull_package` in `generate-bootstraps.sh`); alles andere wird übersprungen. Das Log zeigt `Bootstrap-Hülle: <Paket> (via <Elternpaket>)`, damit man sieht, warum jedes Paket drin ist. Fehlt ein Paket der Hülle in `output/`, steht eine WARNUNG. Idempotent (Marker `CODEFORGE-CLOSURE`); passt der Fork-Stand nicht, bricht der Lauf laut ab.

**Getestet:** Patch lässt sich mit `git apply` anwenden; `bash -n` für beide Skripte; Hüllenberechnung mit Mock-Debs (Wurzel apt/dpkg/tar, Beiwerk dpkg-perl, dpkg-scanpackages, swig, tor, apt-transport-tor, fremde Architektur): nur die Hülle bleibt, Beiwerk fällt weg, fehlende Wurzel gibt WARNUNG.
**Nicht getestet:** echter Bootstrap-Lauf. Offen, wie viele Pakete danach übrig sind (z. B. `gnupg`, `dialog`, `fontconfig` kommen über Abhängigkeiten; die Warnung "Richtwert 80" ist nur ein Hinweis). Ob die Fork-Datei im Repo genau dem Stand im Reparatur-Archiv entspricht (der Patcher bricht sonst ab).

**Neu starten:** `build-bootstrap-repo.yml`, `mode=full`, gleiche Eingaben wie zuvor. Dauer wieder rund 2 h, weil die Abhängigkeiten weiterhin gebaut werden (nötig für die Bibliotheken). Danach: Verifikation (Step 6) muss grün sein, dann neue Bootstrap-Version und SHA-256 in `gradle.properties`.

**Commit-Vorschlag:**
```
fix(bootstrap): only extract the dependency closure of the bootstrap package list

build-bootstraps.sh extracted every .deb in output/, so build-time dependencies
and subpackages (swig, doxygen, tor, dpkg-perl, ...) ended up in the bootstrap
(159 packages, 13 forbidden). Compute the closure from Depends and skip the rest.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01Lf4ojCvX11BeECzDFXhL7X
```
