# Task 45 – Bootstrap und APT-Repo: Abhängigkeits-Konsistenz prüfen, bereinigen, absichern

**Ziel:** Nach der Erstinstallation der App ist der Paketzustand **konsistent** (`apt-get check` ohne Meldung), und `pkg install jq` sowie `codeforge-env setup --jdk 17 --ndk r29 --cmake 3.22.1` laufen durch. Ein inkonsistenter Bootstrap oder ein Repo mit unerfüllbaren Abhängigkeiten wird **im Build** erkannt, nicht erst auf dem Gerät.
**Warum jetzt:** Auf dem Gerät des Inhabers (Pixel 9 Pro XL, 2026-10-21) scheitert **jede** Installation (`jq`, `openjdk-17`, `clang`), obwohl alle Pakete im Repo liegen. Ein einziger Blocker erzeugt bei allen anderen Paketen die Folgemeldung „… but it is not going to be installed“.

**Gemeinsame Regeln (gelten für jeden Task):**
* Clean Architecture wie im Rest des Repos; hier betrifft es vor allem Skripte, Workflows und `codeforge-env`. Gradle Kotlin DSL, Version Catalogs, wo Kotlin berührt wird.
* Alle UI-Texte (falls welche entstehen) über `:core:resources` (Englisch Standard, Deutsch in `values-de`); der String-Check (Task 17) bleibt grün. Skriptmeldungen bleiben deutsch wie im Bestand.
* Nichts erfinden: Was du nicht selbst ausgeführt hast, steht im Bericht unter **nicht getestet** (insbesondere Gerätetests; Zielgerät des Inhabers: Pixel 9 Pro XL, Android 17). Aussagen dieses Tasks über den Bestand stammen aus Ausgaben, die der Inhaber gepostet hat; prüfe sie vor dem Start selbst und korrigiere sie im Bericht.
* Geheimnisse (GPG-Schlüssel, Passphrase, Token) nie ins Log, nie in Dateien im Projektordner, nie in Berichte. Keine Pushes auf GitHub ohne Auftrag des Inhabers.
* Bericht in `docs/bootstrap-consistency-report.md` mit Abschnitten **getestet / nicht getestet / offene Punkte**; `agy-tasks/TASKS-STATUS.md` und `agy-tasks/README.md` ergänzen (Commit-Hash eintragen).
* Build grün: `./gradlew :app:assembleDebug`; dazu die Tests der neuen Skripte (siehe 45.6).

## 45.1 Befund (erst selbst prüfen)

Ausgaben des Inhabers vom Gerät (aarch64, Bootstrap-Release `bootstrap-2026.10.11`, APT-Quelle `https://scto.github.io/terminal-packages-codeforge stable/main`):

* `codeforge-env setup …` bricht ab: `pkg install jq` und `pkg install openjdk-17` enden mit Exit-Code 100.
* `apt-get -s install clang`: alle fünf Abhängigkeiten (`libcompiler-rt`, `lld`, `llvm`, `ndk-sysroot`, `libllvm (= 21.1.8-3)`) „not going to be installed“. Alle fünf stehen im Repo (Stand 2026-10-21, 447 Pakete laut `CodeForge_APT-Repo.pdf`, `libllvm` hat Kandidat 21.1.8-3).
* Simulation für jede dieser Abhängigkeiten einzeln: Die **einzige** eigene Meldung ist `dpkg-perl : Depends: clang but it is not going to be installed`. `libllvm`, `libcompiler-rt`, `ndk-sysroot` haben für sich keine Abhängigkeitsprobleme.
* `dpkg -l | wc -l` → **164** installierte Pakete. Auffällig für einen Bootstrap: `dpkg-perl`, `dpkg-scanpackages`, `doxygen`, `docbook-xml`, `docbook-xsl`, `apt-transport-tor`, `tor`, `unbound`, `ca-certificates-java`, `command-not-found`, `python`, `python-tkinter`, `tcl`, `tk`, `swig`, `perl`, `make`, `xsltproc`, `libx11`/`libxcb`/… , `ttf-dejavu`, `fontconfig`. (Der Inhaber hat die Zeilen 1–80 und die letzten 90 gepostet; die Mitte fehlt.)
* Der Inhaber hat `dpkg --remove --force-depends dpkg-perl` ausgeführt. Danach blockiert `dpkg-scanpackages : Depends: dpkg-perl but it is not installed`. Nach `dpkg --remove --force-depends dpkg-scanpackages` lief `pkg install jq` durch.
* **Belegt (Gerät, `apt-cache show`):** `dpkg-perl 1.22.6-5` hat im Repo `Depends: perl, clang, make, dpkg (= 1.22.6-5)`, `dpkg-scanpackages 1.22.6-5` hat `Depends: dpkg-perl, dpkg (= 1.22.6-5)`. Das `clang` im `Depends` von `dpkg-perl` ist damit eine Eigenschaft des Repo-Pakets.
* **Belegt (Inhaber):** Die Pakete aus der Liste (`dpkg-perl`, `doxygen`, …) hat er **nicht selbst nachinstalliert**. Sie kamen also mit der App (Bootstrap oder ein Schritt der App); welcher, ist offen (Frage 1).
* **Belegt (Repo):** Der Fork nimmt `dpkg-perl` bewusst in den Bootstrap: `build_codeforge_repo.sh` (rund Zeilen 351–382) und `scripts/fix-fork.sh` sichern `packages/dpkg/dpkg-perl.subpackage.sh` (`cpan -Ti Locale::gettext` im `postinst`) für den Offline-Bootstrap ab. Zu `doxygen`, `docbook-*`, `python` findet sich im Repo kein Hinweis auf eine Aufnahme in den Bootstrap (`doxygen` steht nur in `docs/apt-stages-verification.md` als Repo-Paket).
* **Download ohne Rückmeldung (Gerät, `~/.codeforge/logs/setup-2026-10-21.log`):** `codeforge-env setup` stand bei „[ 5%] Installiere Paket openjdk-17 …“ (`apt install -y openjdk-17`, Paket 91,6 MB plus Abhängigkeiten) ohne jede Ausgabe; der erste Versuch (16:28) schrieb zehn Minuten nichts ins Log, der zweite (16:38) lief durch. Das Setup zeigt keinen Download-Fortschritt und kennt keine Zeitgrenze.

**Zu klären, nichts davon vorwegnehmen:**
1. **Woher stammen die 164 Pakete?** Aus dem Bootstrap-ZIP selbst (Paketliste in `scripts/build-bootstraps.sh` des Forks bzw. `EXTRA_PACKAGES` im Lauf, der `bootstrap-2026.10.11` gebaut hat), oder aus späteren Installationen auf dem Gerät (frühere Versuche, `pkg install` von Hand, ein früherer `codeforge-env`-Lauf)? `libs/terminal-engine/BOOTSTRAP.md` sagt, der Bootstrap enthalte nur die Termux-Basispakete. Entscheide mit Belegen: Lade `bootstrap-aarch64.zip` aus dem Release, entpacke in ein leeres Verzeichnis und lies `usr/var/lib/dpkg/status` (bzw. `SYMLINKS.txt` und die Paketliste), vergleiche mit der Geräteliste. Das Setup-Log liegt unter `~/.codeforge/logs/setup-<Datum>.log` auf dem Gerät.
2. **Warum verlangt `dpkg-perl` `clang`?** (Dass es so ist, ist belegt, siehe oben; offen ist, ob wir es eingeführt haben.) Prüfe das `Depends:` von `dpkg-perl` im Packages-Index des Repos (`dists/stable/main/binary-aarch64/Packages`) und im Fork (`packages/dpkg/*.subpackage.sh`, `build.sh`). Vergleiche mit dem Upstream-Termux-Stand. Ist die Abhängigkeit von uns eingeführt (Patch in `build_codeforge_repo.sh`, Version, Subpackage-Mechanik), korrigiere die Ursache dort. Ist sie upstream so, gehört `dpkg-perl` nicht in den Bootstrap.
3. **Welche Pakete verlangt der Bootstrap-Build?** `build_codeforge_repo.sh` ruft `build-bootstraps.sh` mit `--add "${EXTRA_PACKAGES}"` (Standard leer). Prüfe, was der Lauf für `bootstrap-2026.10.11` tatsächlich als `EXTRA_PACKAGES` bekommen hat (Workflow-Eingabe, Lauf-Log).

## 45.2 Soll-Zustand des Bootstraps

* Der Bootstrap enthält nur das, was `apt`, `dpkg` und die Erstinstallation zum Laufen brauchen. Für Termux sind das die Basiswerkzeuge (`bash`, `coreutils`, `dash`, `diffutils`, `findutils`, `gawk`, `grep`, `gzip`, `less`, `procps`, `psmisc`, `sed`, `tar`, `util-linux` …), `apt`, `dpkg`, `ca-certificates`, `curl`, `termux-tools`, `termux-exec`, `termux-keyring`, `termux-core`, `libc++` und die jeweiligen Bibliotheken. **Die genaue Soll-Liste leitest du aus dem Fork ab** (Paketliste in `build-bootstraps.sh` ohne Zusatzpakete), nicht aus dieser Aufzählung.
* Entwicklerwerkzeuge und Dokumentation (`dpkg-perl`, `dpkg-scanpackages`, `doxygen`, `docbook-*`, `swig`, `python-tkinter`, `tor`, `apt-transport-tor`, `ca-certificates-java`, `command-not-found` …) gehören ins **Repo**, nicht in den Bootstrap, außer es gibt dafür einen begründeten Eintrag in der Soll-Liste.
* Ist die Herkunft nach 45.1 „Pakete wurden auf dem Gerät nachinstalliert“, entfällt dieser Abschnitt (Bootstrap bleibt) und Task 45 beschränkt sich auf 45.3 bis 45.5. (Der Inhaber sagt, er habe sie nicht selbst installiert; das schließt Schritte der App nicht aus.)
* **`dpkg-perl` / `dpkg-scanpackages` – zwei Lösungen, eine wählen und im Bericht begründen:**
  * **A:** Beide aus der Bootstrap-Liste nehmen und im Repo belassen (`clang` wird nur bei Bedarf installiert). Prüfe, ob etwas im Bootstrap sie braucht (`dpkg-buildpackage`, `dpkg-scanpackages` in Skripten der App oder von `codeforge-env`).
  * **B:** `clang` aus dem `Depends` von `dpkg-perl` streichen (Patch in `packages/dpkg/dpkg-perl.subpackage.sh`, dort wo `fix_...` bereits arbeitet), wenn der Bootstrap das Paket behalten soll. Folge: `dpkg-buildpackage` findet ohne `clang` keinen Compiler; im Bericht festhalten.

## 45.3 Konsistenzprüfung (Kern des Tasks)

Neues Skript `scripts/check-apt-consistency.py` (nur Standardbibliothek, mit `python3 -I` lauffähig, keine Netzzugriffe), zwei Modi:

* **`bootstrap <zip|verzeichnis>`:** liest `usr/var/lib/dpkg/status` (oder die Paketliste des entpackten Bootstraps) und prüft, dass jedes `Depends` und `Pre-Depends` jedes **installierten** Pakets durch ein installiertes Paket erfüllt ist. `Recommends`/`Suggests` zählen nicht. Unterstützt Alternativen (`a | b`), Versionsbedingungen (`=`, `>=`, `<=`, `<<`, `>>`) und `Provides`. Dazu eine **Verbotsliste** (Datei `scripts/bootstrap-forbidden.txt`) und eine Obergrenze für die Paketzahl (Warnung, kein Abbruch, Wert im Bericht begründen).
* **`repo <Verzeichnis mit Packages-Dateien>`:** prüft pro Architektur, dass jede `Depends`-Zeile **jedes Pakets im Repo** innerhalb des Repos (pro Architektur plus `all`) erfüllbar ist. Das ist die Abschlussprüfung, die bisher fehlt: Die Auswertung der 71 Pakete aus `apt-stages.conf` zeigt nur, dass die Top-Level-Pakete vorhanden sind, nicht ihre Abhängigkeiten. Pakete aus dem Release `large-packages` (`large-packages.txt`) zählen als vorhanden.
* Ausgabe: je Verstoß eine Zeile `FEHLER: <paket> <arch>: <Abhängigkeit> nicht erfüllbar`, Exit-Code 1 bei mindestens einem Verstoß. Kein Abbruch beim ersten Fund.

**Einbau:**
* `build_codeforge_repo.sh` bzw. `build-bootstrap-repo.yml`: Modus `bootstrap` auf jedes fertige `bootstrap-<arch>.zip` **vor** `gh release create/upload`; Modus `repo` nach dem Erzeugen von `Packages` und **vor** dem Veröffentlichen (Schritt `assemble`). Schlägt die Prüfung fehl, wird nichts veröffentlicht. Für den Repo-Modus ein Schalter `CONSISTENCY_CHECK=warn|fail` (Standard `fail`; `warn` nur als Notausgang, im Lauf-Log deutlich ausgeben).
* Beide Aufrufe schreiben ihre Verstöße zusätzlich in die Job-Zusammenfassung (`$GITHUB_STEP_SUMMARY`).

## 45.4 Bereinigung und Reparatur auf dem Gerät

Bereits installierte Apps entpacken den Bootstrap **nicht** erneut (`TermuxInstaller.setupBootstrapIfNeeded`), ein neuer Bootstrap hilft ihnen also nicht. Dafür:

* `codeforge-env` (Quelle: Paket `codeforge-env` im Repo bzw. `ScriptInstaller`; prüfe, wo der Quelltext liegt, `assets/codeforge-env` existiert im Repo nicht) bekommt vor `setup` einen **Vorabtest**: `apt-get check`. Ist der Zustand defekt, bricht `setup` mit einer verständlichen Meldung ab (welche Abhängigkeit, Hinweis auf `codeforge-env repair`) statt mit „pkg install jq fehlgeschlagen“. Die bisherige Fehlermeldung zeigt zusätzlich die **erste** Zeile `Depends:` aus der apt-Ausgabe.
* Neuer Unterbefehl `codeforge-env repair`:
  1. Zeigt den defekten Zustand (`apt-get check`).
  2. Entfernt **nur** Pakete aus einer festen Liste (`dpkg-perl`, `dpkg-scanpackages` und was 45.1 als Bootstrap-Fremdkörper belegt), und nur wenn deren Abhängigkeit tatsächlich unerfüllt ist. Abfrage mit Ja/Nein, `--yes` überspringt sie. Nie ein Paket außerhalb der Liste entfernen.
  3. Versucht danach `apt-get -f install -y`, prüft erneut mit `apt-get check` und meldet das Ergebnis.
  4. Schreibt eine Zeile pro Aktion in das Setup-Log.
* **Download sichtbar machen und begrenzen** (`install_repo_pkg`, Fehlerquelle: stilles `apt install -y …` in die Logdatei): Fortschritt anzeigen (apt-Fortschrittsanzeige bzw. eine Zeile je Paket mit Größe) und eine Zeitgrenze für Schritte ohne Fortschritt (z. B. kein Byte-Zuwachs in `var/cache/apt/archives/partial/` über 120 s → klare Meldung „Download hängt, erneut versuchen mit …“, Teil-Downloads bleiben für die Wiederaufnahme erhalten). Der Prozentwert („5 %“) muss zum tatsächlichen Stand passen oder weggelassen werden.
* Dasselbe nur als Hinweis: erscheint das Setup im Terminal der App, genügt der Verweis auf `codeforge-env repair`; keine automatische Reparatur ohne Zustimmung.

## 45.5 Neuer Bootstrap (nur wenn 45.1 den Bootstrap als Ursache belegt)

* Mit Soll-Liste aus 45.2 neu bauen (`mode=full`), danach 45.3 grün.
* `gradle.properties`: `codeforgeBootstrapVersion` und `codeforgeBootstrapSha256.aarch64` / `.arm` aus den `.sha256`-Dateien des neuen Release übernehmen (**vergleichen, nicht aus dem Log abschreiben**, Vorgehen wie Task 04). Der alte Release bleibt bestehen.
* Im Bericht festhalten: Bestandsgeräte brauchen `codeforge-env repair` oder eine Neuinstallation der App (App-Daten löschen).

## 45.6 Tests (Pflicht)

* **Prüfskript:** Python-Tests (`pytest` oder `unittest`, was im Repo üblich ist) mit kleinen `status`- und `Packages`-Fixtures: alles erfüllt; fehlende Abhängigkeit; Alternative `a | b` mit nur einer Hälfte vorhanden; Versionsbedingung verletzt; `Provides`; `Pre-Depends`; `Recommends` wird ignoriert; Paket aus `large-packages.txt`; Verbotsliste. Dazu der **Fall aus 45.1** als Fixture (`dpkg-perl` verlangt `clang`, `clang` fehlt) → Exit-Code 1 mit Hinweis auf `dpkg-perl`.
* **`codeforge-env repair`:** Ist im Repo ein Test-Rahmen für das Skript vorhanden (Stubs für `apt-get`/`dpkg` über `PATH`), dort testen; sonst ein kleiner Test mit Stub-Programmen anlegen. Fälle: Zustand sauber → keine Aktion; defekt durch `dpkg-perl` → nach Zustimmung entfernt; Paket außerhalb der Liste → bleibt unangetastet; Antwort „Nein“ → keine Änderung.
* **Workflow:** `actionlint`, falls im Repo verwendet; sonst Syntaxprüfung mit `bash -n` für geänderte Skripte.
* Geräte-Tests gehören in den Bericht unter **nicht getestet**, mit dieser Checkliste für den Inhaber: frische Installation → `apt-get check` still → `pkg install jq` → `codeforge-env setup --jdk 17 --ndk r29 --cmake 3.22.1` bis zum JDK-Schritt; Bestandsgerät mit defektem Zustand → `codeforge-env repair`.

## 45.7 Abgrenzung

* Nicht Teil des Tasks: fehlende Pakete der Gruppe D (`shellcheck`, `taplo`) und der `openssh`-Fehlschlag in `06-dev-core` (getrennte Fehlersuche über die Lauf-Logs). Fällt die Repo-Prüfung (45.3) wegen eines dieser Pakete durch, im Bericht vermerken und nicht stillschweigend ausnehmen.
* Kein Umbau des Hostings. Die Frage, ob das Repo langfristig nicht mehr auf GitHub Pages liegen soll (100-MB-Grenze, `large-packages`), ist ein eigener Entscheid.

## Abnahme
* Bericht beantwortet, ob `dpkg-perl` im Bootstrap bleibt (Lösung A oder B) und warum.

* `scripts/check-apt-consistency.py` mit Tests, eingebunden vor Veröffentlichung von Bootstrap und Repo.
* `codeforge-env` mit Vorabtest und `repair`.
* Bericht beantwortet die drei Fragen aus 45.1 mit Belegen.
* Bei bestätigter Bootstrap-Ursache: neuer Release, `gradle.properties` aktualisiert, `./gradlew :app:assembleDebug` grün.
