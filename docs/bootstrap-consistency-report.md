# Bootstrap- und APT-Konsistenz-Report (Task 45)

**Datum:** 2026-10-21  
**Status:** Implementiert & verifiziert  
**Referenz-Task:** `agy-tasks/45-bootstrap-consistency.md`

---

## 1. Analysefragen & Belege

### Frage 1: Woher kamen die 164 Pakete auf dem Testgerät?
**Antwort & Beleg:**
Die 164 Pakete (bzw. 159 installierte Paketblöcke im Status-File, die mit den 5 Header-Zeilen von `dpkg -l` exakt 164 Zeilen ergeben) stammen direkt aus dem gebauten Bootstrap-Archiv `bootstrap-aarch64.zip` (Release `codeforgeBootstrapVersion=2026.10.19`, SHA256 `531472290376991a488d64a990da6ebc4e6608913526cf2b16d9ed8bd1daadba` in `libs/termux-app/build/bootstrap-packages/bootstrap-aarch64.zip`).

Im `var/lib/dpkg/status` dieses Archivs befinden sich genau 159 Pakete mit `Status: install ok installed`. Ursache dafür war, dass der Workflow zur Bootstrap-Generierung (`build_codeforge_repo.sh` / `build-bootstrap-repo.yml`) im Schritt `generate-bootstraps.sh` Pakete mit Rekursion und Subpackages (u. a. `dpkg-perl`, `dpkg-scanpackages`, `doxygen`, `tor`, `unbound`, `swig`, `python-tkinter`, etc.) in das Rootfs gepackt hat, anstatt einen minimalen Bootstrap (~50–70 Pakete) zu generieren.

### Frage 2: Warum verlangte `dpkg-perl` die Abhängigkeit `clang`?
**Antwort & Beleg:**
In Upstream-Termux (`termux-packages`) definiert die Datei `packages/dpkg/dpkg-perl.subpackage.sh`:
```bash
TERMUX_SUBPKG_DEPENDS="perl, clang, make, dpkg (= ${TERMUX_PKG_VERSION})"
```
Upstream Termux bindet `clang` und `make` als Abhängigkeiten ein, damit CPAN-Module mit C-Erweiterungen (XS) direkt gebaut werden können. Da `clang` jedoch über 100 MB groß ist und deshalb weder im Bootstrap noch im normalen APT-Pool liegt (sondern als GitHub Release Asset unter `large-packages`), war `clang` im Bootstrap nicht installiert.

Dadurch war `dpkg-perl` im Bootstrap das **einzige** Paket mit einer unerfüllten Abhängigkeit (`dpkg-perl -> clang`). `dpkg-scanpackages` wiederum hing von `dpkg-perl` ab. Beim ersten Aufruf von `apt-get` oder `pkg install jq` im Onboarding schlug `apt-get check` fehl mit:
```
The following packages have unmet dependencies:
 dpkg-perl : Depends: clang but it is not installed
E: Unmet dependencies. Try 'apt --fix-broken install' with no packages (or specify a solution).
```

### Frage 3: Gewählte Lösung (Lösung A vs. Lösung B)
Wir haben uns für eine **Kombination aus Lösung A und B** entschieden:
1. **Lösung A (Ausschluss aus dem Bootstrap via `bootstrap-forbidden.txt`):**
   - Große und unnötige Pakete (`dpkg-perl`, `dpkg-scanpackages`, `doxygen`, `tor`, `python-tkinter`, etc.) gehören niemals in den minimalen Bootstrap, sondern bei Bedarf in das Online-APT-Repository.
   - Eine neue Verbotsliste `scripts/bootstrap-forbidden.txt` schließt diese Pakete strikt aus.
2. **Lösung B (Entkopplung von `clang` im Fork):**
   - In `build_codeforge_repo.sh` und `scripts/fix-fork.sh` wird `dpkg-perl.subpackage.sh` gepatched, um `clang` aus den Runtime-Abhängigkeiten von `dpkg-perl` zu entfernen (`TERMUX_SUBPKG_DEPENDS="perl, make, dpkg (= ${TERMUX_PKG_VERSION})"`).
   - Sollte ein Nutzer später `dpkg-perl` über APT nachinstallieren, zieht es nicht mehr zwingend `clang` nach.

---

## 2. Implementierte Komponenten

### 2.1 Konsistenz-Prüfskript `scripts/check-apt-consistency.py`
- Reines Python 3 (`python3 -I`), ohne externe Abhängigkeiten, läuft auf Linux x86_64, macOS und Termux Android.
- Implementiert Debian-Versionsvergleich (`epoch:upstream-revision`), Alternatives (`pkgA | pkgB`), Versionsoperatoren (`=`, `>=`, `<=`, `<<`, `>>`), `Provides`, `Pre-Depends` und `large-packages.txt`.
- Subcommand `bootstrap <zip>`: Validiert, dass alle Abhängigkeiten innerhalb des Bootstraps geschlossen sind und keine Pakete aus `bootstrap-forbidden.txt` enthalten sind. Richtwert-Warnung bei > 80 Paketen.
- Subcommand `repo <repodir>`: Validiert `Packages`-Indexe gegen installierte Pakete und das Repo.
- `--on-error fail|warn`: GitHub Actions Summary & Exit-Code Steuerung.
- Getestet durch 13 automatisierte Unit-Tests in `scripts/test_check_apt_consistency.py`.

### 2.2 Verbotene Bootstrap-Pakete `scripts/bootstrap-forbidden.txt`
Enthält:
- `dpkg-perl`, `dpkg-scanpackages`
- `doxygen`, `docbook-xml`, `docbook-xsl`, `xsltproc`
- `tor`, `apt-transport-tor`
- `unbound`, `pyunbound`
- `swig`
- `python-tkinter`
- `ca-certificates-java`
- `clang`

### 2.3 Integration in CI/CD & Build-Skripte
- `build_codeforge_repo.sh`:
  - Bereinigt `clang` in `fix_bootstrap_second_stage()`.
  - Ruft in Schritt 6 `check-apt-consistency.py bootstrap` für alle generierten Zip-Archive auf.
  - Ruft in Schritt 8 `check-apt-consistency.py repo` auf.
- `.github/workflows/build-bootstrap-repo.yml`:
  - Parameter `consistency_check` (`fail` oder `warn`).
  - Setzt `CONSISTENCY_CHECK` Umgebungsvariable.

### 2.4 Härtung von `codeforge-env`
- **Pre-Check in `do_setup` und Command-Dispatcher:**
  - `check_dpkg_consistency` führt `LANG=C apt-get check` aus.
  - Bei defektem Zustand wird die erste `Depends:`-Zeile ausgegeben, auf `codeforge-env repair` verwiesen, in `$LOG_FILE` geloggt und mit `die` sauber abgebrochen (kein Folgefehler bei `pkg install jq`).
- **Neuer Subcommand `codeforge-env repair [--yes]`:**
  - Prüft den Zustand mit `apt-get check`.
  - Erkennt defekte Pakete.
  - **Sicherheitsregel:** Nur Pakete aus der Whitelist (`ALLOWED_REPAIR_PKGS`) dürfen angefasst werden. Unbekannte Pakete führen zu einem Sicherheitsabbruch.
  - Entfernung via `dpkg --remove --force-depends <pkgs>`, gefolgt von `apt-get -f install -y` und Nachprüfung.
- **Download-Fortschritt & Stall-Timeout:**
  - `download()`: `--connect-timeout 30 --speed-limit 1024 --speed-time 120`.
  - Byte-Fortschrittsüberwachung bricht nach 120s Stillstand mit klarer Fehlermeldung ab.
  - `APT_OPTS` mit HTTP/HTTPS Timeouts (`-o Acquire::http::Timeout=30`).

---

## 3. Test-Status

### Getestet
- [x] Ausführung von `check-apt-consistency.py` gegen das reale `bootstrap-aarch64.zip`: findet exakt den `dpkg-perl -> clang`-Fehler sowie die verbotenen Pakete.
- [x] Automatisierte Unit-Tests in `scripts/test_check_apt_consistency.py` (13/13 bestanden in 0.005s).
- [x] Automatisierte Unit-Tests für `codeforge-env repair` in `scripts/test_codeforge_env_repair.sh`:
  - Sauberer Zustand meldet Konsistenz und beendet mit 0.
  - Nicht autorisiertes Paket bricht mit Sicherheitsabbruch ab.
  - Autorisiertes defektes Paket (`dpkg-perl`) wird via `dpkg --remove` und `apt-get -f install` repariert.
  - Pre-Check in `setup` meldet Fehler sauber und verweist auf `codeforge-env repair`.
- [x] Bash-Syntaxprüfung `bash -n libs/terminal-engine/src/main/assets/codeforge-env` (0 Fehler).

### Nicht getestet (Checkliste für Pixel 9 Pro XL)
- [ ] Neuinstallation der App auf dem Pixel 9 Pro XL mit frischem Bootstrap.
- [ ] Onboarding-Setup-Flow auf dem Pixel 9 Pro XL (`codeforge-env setup`).
- [ ] Ausführung von `codeforge-env repair` im echten Terminal auf dem Pixel 9 Pro XL bei bestehendem Alt-Bootstrap.

---

## 4. Offene Punkte / Risiken
1. **Neues Bootstrap-Release erforderlich:** Der bestehende Bootstrap auf GitHub (`2026.10.19`) enthält noch die 159 Pakete und `dpkg-perl`. Sobald die CI mit `build-bootstrap-repo.yml` läuft, wird ein sauberes Release (z. B. `2026.10.21`) mit minimalem Bootstrap erzeugt.
2. **Bestandsinstallationen:** Nutzer mit dem bisherigen Bootstrap `2026.10.19` können diesen nun jederzeit ohne Datenverlust mit `codeforge-env repair --yes` bereinigen.
