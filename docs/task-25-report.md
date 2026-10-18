# Task 25 – Bericht: Große Pakete (> 100 MB) per Release-Asset installieren

**Datum:** 2026-10-18  
**Auftrag:** `agy-tasks/25-large-packages-release-assets.md`

---

## 1. Anlass & Architekturüberblick

GitHub lehnt beim Push auf den Branch `gh-pages` von `scto/terminal-packages-codeforge` Einzeldateien über 100 MB ab (`GH001`, z. B. `gradle_9.8.0_all.deb` mit ~142 MB; potenziell auch `openjdk-*` oder `libllvm`).

Im Workflow `.github/workflows/build-bootstrap-repo.yml` (Modus `add`, Job `assemble`) werden `.deb`-Dateien über 95 MiB deshalb vor der Pool-Erzeugung abgetrennt:
1. Sie werden als Assets an das GitHub-Release `large-packages` des Ziel-Repos (`scto/terminal-packages-codeforge`) hochgeladen.
2. Im Root des APT-Repos (`<apt-url>/large-packages.txt`) wird ein Manifest mit dem Format
   ```text
   name|version|arch|datei|sha256|bytes|url
   ```
   geschrieben und zusätzlich mit dem Repository-Schlüssel als detachte Signatur `large-packages.txt.asc` signiert.
3. Da diese Pakete nicht im `pool/` und somit nicht in `dists/stable/main/binary-*/Packages` stehen, kennt `apt`/`pkg` sie nicht direkt. `codeforge-env` übernimmt daher die Auflösung über `large-packages.txt`.

---

## 2. Durchgeführte Änderungen

### 2.1 Snippet-Wiederherstellung & Einbau in `codeforge-env` (Auftrag 1 & 2)
* **`libs/terminal-engine/snippets/large-pkg-snippet.sh`**:
  * Enthielt im Commit `d4dd00c` versehentlich eingefügten Markdown-Text statt Shell-Code. Wiederhergestellt mit `apt_base_url()` und `install_repo_pkg()`.
* **`libs/terminal-engine/src/main/assets/codeforge-env`**:
  * `apt_base_url()` und `install_repo_pkg()` direkt **nach** `download()` eingefügt.
  * `apt_repo_url()`, `pkg_update_once()` und `pkg_install()` (aus Task 24.5) direkt dahinter angeordnet und zusammengeführt:
    * `pkg_install()` iteriert in einer Schleife über alle übergebenen Paketnamen (`for _pkg in "$@"; do ...`) und ruft für jedes Paket `PKG_INSTALL_OUT="$out_file" install_repo_pkg "$_pkg"` auf.
    * Dadurch bleiben die Fortschrittsanzeige (`download` / `progress`) und Statusmeldungen (`say`) beim Herunterladen großer Pakete auf der Konsole bzw. im `--machine`-Protokoll sichtbar, während die `apt install -y "$deb"`- bzw. `pkg install -y "$want"`-Ausgabe in `$LOG_FILE` (`~/.codeforge/logs/setup-YYYY-MM-DD.log`) protokolliert und bei Fehlern (inkl. der letzten 20 Zeilen, `apt-cache policy` und Hinweis bei `"Unable to locate package"`) ausgegeben wird.
  * Alle Paketinstallationen in `codeforge-env` (`need_tools`, `extract` für `p7zip`, `do_install` für `kind=pkg`, `cmake`/`ninja`, `jq`) laufen über `pkg_install` und damit automatisch über `install_repo_pkg`.
  * Zusätzliche CLI-Unterstützung in `codeforge-env`:
    * `codeforge-env pkg <paket> [paket …]` installiert beliebige Repo-Pakete unter Berücksichtigung von `large-packages.txt`.
    * `codeforge-env install <paket>` akzeptiert neben Manifest-IDs (`jdk;17`) auch direkte Paketnamen ohne Semikolon (z. B. `codeforge-env install gradle`).
    * `codeforge-env setup --gradle` installiert optional `gradle` im Rahmen des Setups mit.
    * `installed_path()` unterstützt für `kind=pkg` neben `jdk;*` nun auch allgemeine Pakete über `command -v "$src"`.

### 2.2 Prüfung weiterer `pkg install` / `apt install`-Stellen im Code (Auftrag 3)
Per `grep -rn --exclude-dir=build --exclude-dir=.gradle "pkg install\|apt install\|apt-get install"` wurden alle Vorkommen im Repository geprüft:

| Fundstelle | Kontext | Maßnahme / Begründung |
|---|---|---|
| `libs/terminal-engine/src/main/assets/codeforge-env` | Alle Paketinstallationen (`jdk;*`, `cmake`, `ninja`, `jq`, `coreutils`, `p7zip`, `gradle`, …) | ✅ Laufen vollständig über `pkg_install()` → `install_repo_pkg()`. |
| `feature/settings/.../ExtensionsManager.kt` & `core/resources/.../strings.xml` (`extensions_termux_install_hint`) | Hinweis für Termux-basierte LSP-Pakete (`clang`, `rust-analyzer`) im Extensions-Manager | ✅ Umgestellt von `pkg install <pkg>` auf `codeforge-env pkg <pkg>` (DE & EN), damit auch große Pakete aus `large-packages.txt` (z. B. falls `clang`/`libllvm` > 95 MiB ist) berücksichtigt werden. |
| `:feature:sdkmanager` (`SdkManagerViewModel.kt`) | SDK-Manager-UI | ✅ Ruft über `SdkRepository` ausschließlich `codeforge-env --machine install <id>` auf; nutzt damit automatisch `install_repo_pkg()`. |
| `.github/actions/prepare-runner/action.yml:40` | `sudo apt-get install -y ...` | Unverändert – läuft auf dem Ubuntu-x86_64-Runner von GitHub Actions zur Vorbereitung der Build-Umgebung, nicht auf dem Android-Gerät. |

### 2.3 Sicherheitsanalyse & GPG-Signatur für `large-packages.txt` (Auftrag 4)
1. **URL-Beschränkung (`https://github.com/`):**
   * `install_repo_pkg()` akzeptiert ausschließlich Download-URLs, die mit `https://github.com/` beginnen (`case "$url" in https://github.com/*) ;; *) err ...; return 1 ;; esac`). Andere Hosts oder unverschlüsseltes `http://` werden vor dem Download abgewiesen.
2. **SHA-256-Prüfung:**
   * Vor dem Download wird geprüft, dass das SHA-256-Feld im Manifest exakt 64 hexadezimale Zeichen (`^[0-9a-f]{64}$`) umfasst.
   * Nach dem Download wird die `.deb`-Datei mit `sha256sum` gegen den Manifest-Wert geprüft; bei Abweichung wird die Datei sofort gelöscht (`rm -f "$deb"`) und die Installation mit Fehlercode `1` abgebrochen.
3. **Restrisiko eines unsignierten Manifests & Härtung per `large-packages.txt.asc`:**
   * *Risiko ohne Signatur:* `large-packages.txt` liegt auf demselben GitHub-Pages-Host (`https://scto.github.io/terminal-packages-codeforge`) wie die GPG-signierte APT-`InRelease`-Datei. Bei einer Kompromittierung des `gh-pages`-Branches oder des Pages-Hostings ohne Zugriff auf den privaten GPG-Schlüssel könnte ein Angreifer zwar keine manipulierten Pakete über `apt` unterschieben (da `InRelease` GPG-geprüft wird), aber in einem unsignierten `large-packages.txt` auf ein beliebiges fremdes Release unter `https://github.com/<angreifer>/...` samt passender SHA-256 verweisen.
   * *Umgesetzte Härtung:*
     * In `.github/workflows/build-bootstrap-repo.yml` (Schritt `Manifest großer Pakete schreiben`) wird `github_repo_ready/large-packages.txt` mit dem privaten Repository-Schlüssel (`.gpg/private/codeforge.gpg`) detacht signiert (`github_repo_ready/large-packages.txt.asc`) und direkt per `gpg --verify` geprüft.
     * In `install_repo_pkg()` lädt das Gerät (sofern `$PREFIX/share/termux-keyring/codeforge.gpg` und `gpgv` vorhanden sind) zusätzlich `<apt-url>/large-packages.txt.asc` herunter und prüft die Signatur mit `gpgv --keyring "$keyring" "$man.asc" "$man"`. Schlägt die Signaturprüfung fehl, wird `large-packages.txt` verworfen und die Installation abgebrochen.

### 2.4 Dokumentation & Statusdateien (Auftrag 6)
* `docs/BOOTSTRAP-WORKFLOW.md`: Abschnitt **„Große Pakete (> 100 MB)“** ergänzt.
* `README.md` & `README_DE.md`: Abschnitt unter **„Termux Bootstrap & Large Packages (`> 100 MB`)“** ergänzt.
* `agy-tasks/TASKS-STATUS.md`, `agy-tasks/README.md`, `STATUS.md` und `CHANGELOG.md` um Task 25 aktualisiert.

---

## 3. Getestet vs. Nicht getestet (Auftrag 5)

### ✅ Getestet
1. **Mock-Testsuite `libs/terminal-engine/snippets/large-pkg-snippet.test.sh`:**
   * `apt_base_url()` liest `https://scto.github.io/terminal-packages-codeforge` korrekt aus `$PREFIX/etc/apt/sources.list`.
   * **Fall 1 (Manifest-Treffer):** `install_repo_pkg gradle` erkennt `gradle` (`all`) in `large-packages.txt`, lädt `gradle.deb` herunter, verifiziert die SHA-256-Prüfsumme und führt `apt install -y .../gradle.deb` aus.
   * **Fall 2 (Normales Paket):** `install_repo_pkg clang` findet keinen Eintrag in `large-packages.txt` und fällt auf `pkg install -y clang` zurück.
   * **Fall 3 (Falsche Prüfsumme):** Nach Manipulation von `srv/gradle.deb` bricht `install_repo_pkg gradle` mit `err: Prüfsumme für gradle.deb stimmt nicht ...` und Exit-Code `rc=1` ab und löscht die temporäre `.deb`-Datei.
2. **Shell-Syntaxprüfung:**
   * `bash -n libs/terminal-engine/src/main/assets/codeforge-env` (0 Fehler).
   * `bash -n libs/terminal-engine/snippets/large-pkg-snippet.sh` (0 Fehler).
3. **Lokalisierungs- & Qualitätsprüfungen + Android-Build:**
   * `python3 scripts/check_string_locales.py` (844 Strings synchron in `values/` und `values-de/`).
   * `python3 scripts/find_hardcoded_strings.py --check` (0 Funde).
   * `./gradlew :feature:settings:testDebugUnitTest :libs:terminal-engine:testDebugUnitTest --no-daemon`.
   * `./gradlew :app:assembleDebug --no-daemon`.

### ⚠️ Nicht auf Gerät getestet
* **End-to-End-Installation von `gradle` auf dem Gerät (`codeforge-env setup --gradle` / `codeforge-env pkg gradle`):**
  * **Nicht auf Gerät getestet**, da der erste Workflow-Lauf von Gruppe A mit `publish = true` (der das Release `large-packages` auf `scto/terminal-packages-codeforge` anlegt und `large-packages.txt` auf `https://scto.github.io/terminal-packages-codeforge/large-packages.txt` veröffentlicht) laut Auftrag ausdrücklich nicht Teil dieses Tasks ist. Sobald das Release `large-packages` existiert, kann auf dem Gerät `codeforge-env pkg gradle` bzw. `codeforge-env setup --gradle` ausgeführt werden.
