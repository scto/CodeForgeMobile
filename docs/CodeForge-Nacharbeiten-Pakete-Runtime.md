# CodeForge: Nacharbeiten nach Task 36–47, Paket-Installation, Compiler-Runtime und Language Server

Stand: 2026-10-21 · Projekt CodeForgeMobile · Prefix `/data/data/com.codeforge.app/files/usr`

**Wie belastbar ist das?** Jede Aussage trägt eine Kennzeichnung:
- **[geprüft]** im Repo-Snapshot (Code, Skript, Dokument) oder in Ausgaben des Inhabers gesehen.
- **[abgeleitet]** aus dem Code geschlossen, nicht ausgeführt.
- **[offen]** nicht geprüft; steht noch aus.

Alle Befehle und Eingabewerte unten stammen aus dem Repo oder aus deinen Ausgaben. Nichts davon habe ich auf einem echten Runner oder Gerät ausgeführt.

---

## 0. Überblick: Welche Teile es gibt

```
                       ┌──────────────────────────────────────────┐
 GitHub Actions        │ build-bootstrap-repo.yml / build-all-    │
 (scto/CodeForgeMobile)│ stages.yml  →  build_codeforge_repo.sh   │
                       └───────┬──────────────┬───────────────────┘
                               │              │
        Release bootstrap-<v>  │              │  gh-pages (APT-Repo) + Release large-packages
        (ZIP je Architektur)   ▼              ▼
                 scto/terminal-packages-codeforge  (https://scto.github.io/terminal-packages-codeforge)

 Mirror scto/codeforge-tools:  Releases android-sdk-*, android-ndk-*, cmake-*, androidide-tools-*,
                               lzhiyong-sdk-tools-*, cmdline-tools-19.0, compiler-runtime-<bom>-k<kotlin>

 App (CodeForgeMobile)
   1. Bootstrap-ZIP (aus gradle.properties) → wird beim Erststart nach files/usr entpackt
   2. codeforge-env setup   → Pakete aus dem APT-Repo + Tools aus codeforge-tools
   3. Extensions-Manager    → Language Server (Download oder Termux-Paket)
   4. Compose-Preview       → Compiler-Runtime-ZIP aus codeforge-tools
```

| Quelle | Inhalt | Wer lädt es | Wie |
|---|---|---|---|
| **Bootstrap** (Release `bootstrap-<v>` in `terminal-packages-codeforge`) | Basissystem (apt, dpkg, bash, coreutils …) | Gradle-Plugin beim App-Build, App beim Erststart | in die APK eingebettet |
| **APT-Repo** (`gh-pages` desselben Repos, signiert) | Pakete wie `openjdk-17`, `git`, `nodejs` | `apt`/`pkg`, `codeforge-env pkg` | `apt update`, `pkg install` |
| **Release `large-packages`** (gleiches Repo) | `.deb` über 95 MiB (`gradle`, `libllvm-static`, `openjdk-21/25` …) | `codeforge-env` | über `large-packages.txt` |
| **Mirror `scto/codeforge-tools`** | build-tools, platform-tools, NDK, CMake, cmdline-tools, **Compiler-Runtime** | `codeforge-env`, Compose-Preview | GitHub-Release-Assets |
| **Language Server** | kotlin-language-server, jdtls, pyright, … | Extensions-Manager der App | Download oder Termux-Paket |

---

# Teil A – Nacharbeiten nach den agy-tasks 36 bis 47

## A1. Ausgangslage (was jetzt wirklich offen ist)

| Befund | Stand | Quelle |
|---|---|---|
| APT-Repo enthält 69 von 71 Paketen aus `apt-stages.conf`. **`shellcheck` (Stage 14-ide-tools) und `taplo` (Stage 12-lsp-misc) fehlen.** | **[geprüft]** gegen die PDF (447 Paketzeilen, Stand 2026-10-21 10:12 UTC) | PDF des Inhabers |
| Gruppe D (`gopls,taplo,ruff` + `neocmakelsp,esbuild,pnpm,shellcheck`) wurde am 2026-10-21 neu gestartet. Ergebnis | **[offen]** (Logs fehlen) | Eingabe des Inhabers |
| `openssh` (Stage `06-dev-core`, Gruppe `B-leicht-dev`) lief fehl, liegt aber im Repo | **[offen]**, Log fehlt | Inhaber |
| Arm-Pakete für `openjdk-21`, `openjdk-25`, `libjansi` in der PDF nicht erkannt | **[offen]** (Parser unsicher; große Pakete liegen im Release) | PDF-Auswertung |
| Die Abhängigkeiten der Pakete wurden bisher **nicht** auf Vollständigkeit geprüft, nur die Top-Level-Pakete | **[geprüft]** | Task 45 |
| Auf dem Gerät blockierte `dpkg-perl 1.22.6-5` (`Depends: perl, clang, make, dpkg`) mit fehlendem `clang` jede Installation | **[geprüft]** (`apt-cache show`, Gerät) | Inhaber |
| Aktuelle Bootstrap-Version in `gradle.properties`: `2026.10.19` (aarch64, arm, x86_64) | **[geprüft]** im Snapshot. `docs/BOOTSTRAP-WORKFLOW.md` und `libs/terminal-engine/BOOTSTRAP.md` nennen noch `2026.10.11`: **Dokumente veraltet** | Repo |
| Release `compiler-runtime-2025.09.00-k2.1.21`: ZIP 53,8 MB; Task 26/27a erwartet ~20,22 MiB ohne Compiler-Plugin | Abweichung **[geprüft]** (Screenshot und Task-Text); Ursache **[offen]**, siehe C1 | Screenshots, Task 26 |
| Der Editor stürzt beim Öffnen ab (NPE im `EditorViewModel`-`init`), Patch liegt vor | **[geprüft]** (Stacktrace), Fix noch nicht im Repo bestätigt | Task 46 |

## A2. Reihenfolge der Nacharbeiten

Die Reihenfolge ist eine Empfehlung. Schritte 1 bis 3 können sofort passieren.

### Schritt 1 – Gerät reparieren (falls `apt`/`pkg install` mit „not going to be installed“ scheitert)
```bash
dpkg --remove --force-depends dpkg-perl dpkg-scanpackages
apt-get check          # muss still bleiben
pkg install jq         # Test
```
Danach `codeforge-env setup --jdk 17 --ndk r29 --cmake 3.22.1`. Der Download von `openjdk-17` (91,6 MB) läuft ohne Fortschrittsanzeige; Fortschritt prüfen mit `ls -l $PREFIX/var/cache/apt/archives/partial/`. Dauerhafte Lösung: Task 45.

### Schritt 2 – Task 46 (Editor-Absturz) zuerst umsetzen
Ohne ihn öffnet kein Projekt im Editor. Das Patch `editor-viewmodel-init-order.patch` verschiebt `projectRoot` und `pendingUpdates` vor den `init`-Block.

### Schritt 3 – Fehlende APT-Pakete nachbauen (Gruppe D, ggf. B)
Verwende *Actions → Build All Stages (Kette) → Run workflow* mit:

| Eingabe | Wert | Grund |
|---|---|---|
| `preset` | `light` (egal, wird von `only` überschrieben) | – |
| `only` | `D-leicht-lsp` | Stage `12-lsp-misc` (gopls, taplo, ruff) plus Zusatz `neocmakelsp,esbuild,pnpm,shellcheck` |
| `mode` | `add` | ergänzt das bestehende Repo |
| `archs` | `aarch64,arm` | die Architekturen des Repos |
| `publish` | **an** | sonst nur Artefakte (14 Tage gültig) |
| `stop_on_error` | aus | |
| `keep_going` | an | ein fehlgeschlagenes Paket stoppt die Gruppe nicht |
| `target_repo` | `scto/terminal-packages-codeforge` | Standard |

Nicht gleichzeitig mit einem anderen Lauf starten (gemeinsame Concurrency-Gruppe; ein zusätzlicher Start verdrängt den wartenden Lauf).

**Danach im Log nach `shellcheck` und `taplo` suchen** (Schritt „Pakete bauen“ der Gruppe): Steht dort „gebaut“, „übersprungen“ oder ein Fehler? Zwei Ursachen sind aus früheren Läufen bekannt:
- Die Prüfung „`.deb` enthält `com.termux`“ schlägt bei `taplo` (Binary) und `openssh` (`var/service/ssh-agent/run`) an und **überspringt jetzt nur das Paket** (zeigt die Fundstellen). Ursache **[offen]**: Ein Binary mit `com.termux` im Pfad ist ein Fehler im Paketbau (zum Beispiel ein einkompilierter Pfad, der nicht durch den Prefix ersetzt wurde).
- Pakete in einer Etappe mit `nodeps=…` brauchen ihre Abhängigkeiten im Prefix; sonst bricht der Bau ab (so geschah es bei `python-pip` in Gruppe A).

Für `openssh` dasselbe mit `only=B-leicht-dev`.

**Einzelpaket gezielt nachbauen:** *Build Bootstraps & APT-Repo* → `mode=add`, `repo_packages=shellcheck` (oder `taplo`), `publish` an, `archs=aarch64,arm`, `keep_going` an. Die `nodeps`-Flags einer Etappe gelten für den ganzen Lauf, bei einem reinen `repo_packages`-Lauf ohne `stage` gibt es keine.

### Schritt 4 – Vollständigkeit der Abhängigkeiten prüfen (bis Task 45 den Check automatisiert)
Auf einem Gerät mit **konsistentem** apt (Schritt 1) und aktuellem `apt update`:
```bash
cd ~/CodeForgeMobile                       # Pfad zum Klon (enthält apt-stages.conf)
CONF=./apt-stages.conf
for p in $(awk -F'|' '!/^#/ && NF>=2 {print $2}' "$CONF" | tr ',' '\n' | tr -d ' ' | sort -u); do
  c=$(apt-cache policy "$p" 2>/dev/null | awk '/Candidate:/{print $2}')
  [ -z "$c" ] || [ "$c" = "(none)" ] && echo "FEHLT: $p"
done
for p in $(awk -F'|' '!/^#/ && NF>=2 {print $2}' "$CONF" | tr ',' '\n' | tr -d ' ' | sort -u); do
  apt-get -s install "$p" >/dev/null 2>&1 || echo "NICHT INSTALLIERBAR: $p"
done
```
Die erste Schleife prüft, ob ein Kandidat existiert. Die zweite simuliert die Installation (`-s`, ohne Root, ändert nichts) und findet unerfüllbare Abhängigkeiten. Das gilt nur für die Architektur des Geräts (`dpkg --print-architecture`).

### Schritt 5 – Bootstrap prüfen, ggf. neu bauen
Der Bootstrap auf deinem Gerät hatte 164 Pakete. Auffällig sind `dpkg-perl`, `dpkg-scanpackages`, `doxygen`, `docbook-*`, `apt-transport-tor`, `tor`, `ca-certificates-java`, `python` samt Tk. Ob sie aus dem Bootstrap kamen oder später hinzukamen, klärt Task 45 (Frage 1).
Du hast sie **nicht selbst installiert [geprüft, Aussage des Inhabers]**.

Bootstrap neu bauen **nur**, wenn Task 45 ihn als Ursache belegt. Bevor du es tust:

> **Warnung: Modus `full` ersetzt das gesamte Repo auf `gh-pages` (Pool eingeschlossen) und ersetzt die Release-Assets.** Alle bisher ergänzten Gruppen fehlen danach. **[geprüft: `docs/BOOTSTRAP-WORKFLOW.md`, Abschnitte „Grenzen“ und „Modus add“]**
> Sichere vorher den Branch (`git clone --branch gh-pages --depth 1 …` oder ein Tag) und plane danach die Gruppen ein zweites Mal ein (`preset=all`).

| Eingabe (`build-bootstrap-repo.yml`) | Wert |
|---|---|
| `mode` | `full` |
| `archs` | `aarch64,arm,x86_64` (das Gradle-Plugin bettet alle drei ein; fehlt x86_64, muss das Plugin angepasst werden, siehe Task 04) |
| `extra_packages` | **leer** (vergrößert jede ZIP und die APK) |
| `repo_packages` | leer oder `openjdk-17,ninja,jq,git,zip` |
| `release_tag` | **neu wählen**, z. B. `bootstrap-2026.10.22`. Ein vorhandener Tag wird überschrieben; `gradle.properties` verweist per Tag und SHA-256 |
| `publish` | an |
| `apt_url` | leer (Standard `https://scto.github.io/terminal-packages-codeforge`) |
| `target_repo` | `scto/terminal-packages-codeforge` |

Danach: Block aus der Job-Zusammenfassung in `gradle.properties` übernehmen:
```properties
codeforgeBootstrapVersion=<neue Version>
codeforgeBootstrapSha256.aarch64=<…>
codeforgeBootstrapSha256.arm=<…>
codeforgeBootstrapSha256.x86_64=<…>
```
Werte **mit den `.sha256`-Dateien des Releases vergleichen** (nicht aus dem Log abschreiben), App neu bauen. Bestandsgeräte entpacken den Bootstrap nicht erneut: App-Daten löschen oder `codeforge-env repair` (Task 45).

Secrets (einmalig, derselbe Schlüssel für alle Läufe):

| Secret | Pflicht | Inhalt |
|---|---|---|
| `GPG_PRIVATE_KEY_B64` | ja | armored und base64, einzeilig; erzeugt `scripts/setup-gpg-secrets.sh` |
| `CODEFORGE_GPG_PASSPHRASE` | ja | Passphrase |
| `PACKAGES_REPO_TOKEN` | bei `publish` | Fine-grained PAT, *Contents: Read and write* auf dem Ziel-Repo |

Im Modus `add` bricht der Lauf ab, wenn der Fingerprint nicht zum bestehenden `codeforge.gpg` im Repo passt. Der aktuelle Signaturschlüssel hat den Fingerprint `CF4FDFDB5161D88D51A868BF8F2802E996A25022` **[geprüft, Setup-Skript-Lauf vom 2026-10-19]**. Das Setup-Skript räumt den temporären GnuPG-Ordner auf; der Schlüssel wird **nicht** nach `~/.gnupg` kopiert. Das Backup (`BACKUP_DIR`) bewahrst du selbst auf.
Im Ziel-Repo: *Settings → Pages* → Branch `gh-pages`, Ordner `/`, Repo öffentlich.

### Schritt 6 – Nacharbeiten je Task (36 bis 47)

| Task | Braucht aus APT / Bootstrap / Mirror | Gruppe / Quelle | Nacharbeit |
|---|---|---|---|
| **35** APK-Analyse | `aapt` (optional), `apksigner` | `C-leicht-android` (Stage `05-android-tools`), `A-leicht-jvm` (`04-jvm-tools`) | Prüfen, dass beide im Repo liegen (`apt-cache policy aapt apksigner`). Fehlt `apksigner`, blendet die UI den Signaturabschnitt aus. |
| **36** Test-Läufer | JDK + Gradle (Wrapper des Projekts) | `H1-jdk17` (`openjdk-17`), Wrapper lädt Gradle selbst | Bei Projekten ohne Wrapper `codeforge-env pkg gradle` (liegt als großes Paket im Release `large-packages`). |
| **37a** Debugger (Spike) | `openjdk-17` mit `jdk.jdi`, `jdk.jdwp.agent` | `H1-jdk17` | `java --list-modules \| grep -E "jdk.jdi\|jdk.jdwp.agent"` zeigte beide in 17.0.20 **[geprüft, Inhaber 2026-10-19]**. Nach jedem neuen JDK-Build wiederholen. |
| **37b** Debugger-UI | wie 37a | – | – |
| **37c** Debugger anhängen | `adb` aus `android-tools` | `C-leicht-android` (Zusatzpaket `android-tools`) | Task 29.5 (drahtloses ADB) vorher erledigen. Prüfen: `apt-cache policy android-tools`. |
| **38** KI-Assistent | keine Pakete | – | API-Schlüssel nie ins Log/Projekt; nur Hinweisregeln des Tasks. |
| **39** Git-Konflikte | JGit in der App; `git` im Terminal optional | `B-leicht-dev` | – |
| **40** Abhängigkeitsgraph | `./gradlew :<modul>:dependencies` über den Build-Läufer (Task 28) | `H1-jdk17` | Gradle-Läufe brauchen JDK und Netz. |
| **41** Layout-Designer | keine | – | – |
| **42** Hardcoded Strings | keine | – | – |
| **43** LSP-Erweiterungen (XML, YAML, TOML, Bash) | `openjdk-17` (LemMinX), `nodejs` (YAML, Bash), **`taplo`** (TOML), `shellcheck` (optional) | `H1-jdk17`, `E-leicht-py-node`, **`D-leicht-lsp`** | **`taplo` und `shellcheck` fehlen im Repo (Schritt 3).** Vor Task 43: prüfen, ob `packages/taplo/build.sh` mit Feature `lsp` gebaut wird (`taplo lsp stdio`). |
| **44** LSP mehrere Server | – | – | Quellkompatibilität für Plugins prüfen. Ergänzung in Task 43 um `serverId`, falls 44 zuerst kommt. |
| **45** Bootstrap/APT-Konsistenz | Workflows, `codeforge-env` | siehe Schritte 4 und 5 | Prüfskript `scripts/check-apt-consistency.py`; Lösung für `dpkg-perl → clang`. |
| **46** Editor-Absturz | – | – | Schritt 2. |
| **47** Einstellungen | Gradle-Init-Script (47.4), `keytool`/`apksigner` (JDK) für Schlüssel | `H1-jdk17`, `A-leicht-jvm` | 47.5 vor 47.4. Frühere Version des Editors/Willkommensbildschirms vom Inhaber anfordern (47.2/47.3). |

## A3. Workflows: alle Einstellungen im Überblick

### `build-bootstrap-repo.yml` (Name: *Build Bootstraps & APT-Repo*)

| Eingabe | Standard | Bedeutung |
|---|---|---|
| `mode` | `auto` | `full`: Bootstraps + Pakete + Repo, **ersetzt Repo und Release-Assets**. `add`: ergänzt Pakete, Bootstraps unberührt. `auto`: `add`, wenn auf `gh-pages` schon ein Repo liegt, sonst `full` |
| `stage` | leer | **genau eine** Etappe aus `apt-stages.conf`. Eine Liste mit Komma lehnt der Planer ab (Regex in `.github/scripts/plan-apt-build.sh`) |
| `archs` | `aarch64,arm,x86_64` | erlaubt: `aarch64`, `arm`, `i686`, `x86_64` |
| `repo_packages` | leer | Pakete nur im Repo (Elternpakete: `libllvm` statt `clang`, `libcurl` statt `curl`, `liblzma` statt `xz-utils`, `libprotobuf` statt `protobuf`, `aapt` statt `aapt2`/`aidl`) |
| `extra_packages` | leer | Pakete **im Bootstrap**; nur Modus `full` |
| `keep_going` | an | nur Modus `add` |
| `publish` | aus | an: Release + `gh-pages` |
| `target_repo` | `scto/terminal-packages-codeforge` | |
| `release_tag` | `bootstrap-JJJJ.MM.TT` | nur Modus `full` |
| `apt_url` | leer | |

Regeln: Flags einer Etappe (`single-arch`, `nodeps=…`) gelten für den **ganzen Lauf**. Paketnamen: `^[a-z0-9+._-]+(,[a-z0-9+._-]+)*$`. 6-Stunden-Grenze je Job (Workflow-Limit 350 Minuten); schwere Pakete (`openjdk-*`, `libllvm`, `dart`) in eigenen Läufen. Pakete über 95 MiB gehen als Release-Assets nach `large-packages`; Pages ist auf etwa 1 GB begrenzt (Warnung ab 900 MB Pool).

### `build-all-stages.yml` (Kette von Gruppen)

| Eingabe | Standard | Bedeutung |
|---|---|---|
| `preset` | `light` | `light` = A–F, `heavy` = H1–H6, `all` = beides |
| `only` | leer | Gruppen, Komma getrennt; überschreibt `preset` |
| `mode` | `add` | |
| `archs` | `aarch64,arm` | |
| `publish` | aus | an: jede Gruppe wird nach ihrem Lauf veröffentlicht |
| `stop_on_error` | aus | Kette bricht bei der ersten fehlgeschlagenen Gruppe |
| `keep_going` | an | |
| `target_repo` | `scto/terminal-packages-codeforge` | |

Die Gruppen laufen strikt nacheinander (`max-parallel: 1`). Artefakte: `debs-<Gruppe>-<Arch>` (tar) und `codeforge-apt-repo-<Gruppe>`; sie verfallen nach 14 Tagen. Es gibt **keinen** Workflow, der Artefakte nachträglich veröffentlicht: nur `publish=true` im Lauf selbst.

### Gruppen (`apt-groups.json`) und Etappen (`apt-stages.conf`)

| Gruppe | Etappe | Zusatzpakete | Hinweis |
|---|---|---|---|
| `A0-leicht-libjansi` | `04a-libjansi` (libjansi, alle Architekturen; vor A) | – |
| `A-leicht-jvm` | `04-jvm-tools` (gradle, kotlin, maven, ant, apksigner, d8, dx, ecj; `single-arch nodeps=all`) | codeforge-env, yarn, autoconf, automake | `gradle` > 95 MiB → `large-packages` |
| `B-leicht-dev` | `06-dev-core` (git, git-lfs, zip, unzip, tar, libcurl, wget, liblzma, 7zip, jq, ninja, make, cmake, openssh) | pkg-config, libtool, flex, bison, ccache, patchelf, strace | `openssh` **[offen]** |
| `C-leicht-android` | `05-android-tools` (libprotobuf, aapt) | gdb, android-tools, gh, rsync, xmlstarlet | |
| `D-leicht-lsp` | `12-lsp-misc` (gopls, taplo, ruff; `nodeps=gopls`) | neocmakelsp, esbuild, pnpm, shellcheck | **`taplo`, `shellcheck` fehlen** |
| `E-leicht-py-node` | `08-python` (python, python-pip) | nodejs, npm | |
| `F-leicht-utils` | `07-utils` | – | |
| `H1-jdk17` / `H2-jdk21` / `H3-jdk25` | `01-jdk17` / `02-jdk21` / `03-jdk25` | – | `openjdk-21/25` > 95 MiB → `large-packages` |
| `H4-llvm-clang` | `10-llvm-clang` (libllvm) | – | `libllvm-static` (arm) in `large-packages` |
| `H5-dart`, `H6-rust-analyzer` | `11-dart`, `15-rust-analyzer` | – | |

Stages `00-codeforge`, `09-nodejs`, `13-pure-tools`, `14-ide-tools`, `16-native-dev`, `17-debug-misc` sind keine eigenen Gruppen; ihre Pakete stehen als Zusatzpakete in A–E. `shellcheck` (Stage `14-ide-tools`) liegt als Zusatzpaket in Gruppe D.

## A4. Prüfliste nach jedem Lauf

1. Job-Zusammenfassung lesen: Welche Pakete „gebaut“, „übersprungen“, „FEHLER“? (Bei `keep_going` wird der Job auch bei Einzelfehlern grün.)
2. Prüfen, ob `https://scto.github.io/terminal-packages-codeforge/dists/stable/Release` (und `InRelease`) erreichbar ist und die neue Zeit zeigt. **[offen]**: GitHub Pages braucht oft einige Minuten.
3. Auf dem Gerät: `apt update`, dann die Schleife aus Schritt 4. Bei großen Paketen zusätzlich `curl -fsSL https://scto.github.io/terminal-packages-codeforge/large-packages.txt`.
4. Neue Gruppen in `agy-tasks/TASKS-STATUS.md` und `docs/BOOTSTRAP-WORKFLOW.md` eintragen (die Tabelle „Stand der ersten Läufe“ ist veraltet).

---

# Teil B – Wie Pakete und Artefakte installiert werden

## B1. Automatisch (Erststart der App)

**[geprüft: `libs/terminal-engine/BOOTSTRAP.md`, `OnboardingViewModel`, `TermuxInstaller`]**

1. **Bootstrap entpacken:** `TermuxInstaller.setupBootstrapIfNeeded` entpackt die in die APK eingebettete ZIP (Version und SHA-256 aus `gradle.properties`) nach `/data/data/com.codeforge.app/files/usr`. Der Bootstrap enthält das Basissystem, **nicht** `openjdk-17` und nicht `codeforge-env` (Letzteres wird sonst vom `ScriptInstaller` aus `assets/codeforge-env` nach `$PREFIX/bin` kopiert).
2. **APT-Quelle:** `$PREFIX/etc/apt/sources.list` zeigt auf `deb https://scto.github.io/terminal-packages-codeforge stable main`; `termux-keyring` installiert zusätzlich `codeforge.gpg` nach `$PREFIX/share/termux-keyring/`. Beides patcht `build_codeforge_repo.sh` beim Bau.
3. **Onboarding fertig:** Die App öffnet das Terminal und führt sichtbar aus:
   ```bash
   codeforge-env setup --jdk 17 --ndk r29 --cmake 3.22.1
   ```
4. **Was `setup` installiert:**
   - JDK 17 (oder 21) und Ninja per `pkg`/`apt` aus dem APT-Repo; große Pakete über `large-packages.txt`.
   - `jq` (für die GitHub-API).
   - cmdline-tools (`sdkmanager`) aus dem Mirror-Release `cmdline-tools-19.0` (SHA-256 fest im Skript); Rückfall: aus `android-sdk-37.0.0`.
   - build-tools und platform-tools (`aapt2`, `aidl`, `zipalign`, `adb`), NDK und CMake aus `scto/codeforge-tools`; Rückfall: HomuHomu833-Repos direkt, dann feste Liste.
   - `platforms;android-N` und die Java-Teile der build-tools per `sdkmanager` von Google.
5. **SDK-Manager in der App** ruft `codeforge-env --machine list|install|uninstall` auf; er ist nur die Oberfläche.

## B2. Manuell im Terminal

```bash
apt update                                 # Paketlisten
pkg install openjdk-17 git zip            # normale Repo-Pakete
codeforge-env pkg gradle                   # große Pakete (> 95 MiB) über large-packages.txt
codeforge-env install gradle               # gleichbedeutend
codeforge-env setup --gradle               # Setup mit Gradle
codeforge-env list                         # alle Komponenten mit Status
codeforge-env install ndk\;r29             # Mirror: NDK
codeforge-env install cmake\;3.22.1
codeforge-env install build-tools\;37.0.0
codeforge-env install platform-tools\;37.0.0
codeforge-env install platforms\;android-36
codeforge-env uninstall <id>
codeforge-env env                          # Umgebungsdatei (ANDROID_HOME, JAVA_HOME …)
codeforge-env doctor                       # Umgebung prüfen
```
(Das `;` in der Shell maskieren: `ndk\;r29` oder in Anführungszeichen setzen.)

Nützliche Umgebungsvariablen **[geprüft im Skript]**: `CODEFORGE_TOOLS_REPO` (Mirror, leer = aus), `ASSET_VARIANT` (Asset-Variante nach `uname -m`), `CODEFORGE_SDK_ROOT`, `CODEFORGE_CMDLINE_REV`, `CODEFORGE_CMDLINE_TOOLS_URL/_SHA256`, `GITHUB_TOKEN` (ohne Token: 60 API-Anfragen pro Stunde).

**Ein großes Paket ohne `codeforge-env` von Hand installieren** (Ablauf laut `docs/BOOTSTRAP-WORKFLOW.md`, Dateiformat `name|version|arch|datei|sha256|bytes|url`):
```bash
curl -fsSL https://scto.github.io/terminal-packages-codeforge/large-packages.txt -o ~/lp.txt
grep '^gradle|' ~/lp.txt                      # Zeile mit URL und SHA-256
curl -fL "<url aus der Zeile>" -o ~/gradle.deb
sha256sum ~/gradle.deb                        # muss zur Zeile passen
apt install -y ~/gradle.deb                   # Abhängigkeiten kommen aus dem Pool
```
Die Signatur der Liste prüft `codeforge-env` mit `gpgv --keyring $PREFIX/share/termux-keyring/codeforge.gpg large-packages.txt.asc large-packages.txt` **[abgeleitet]**.

**APT-Quelle von Hand setzen** (nur wenn die Datei fehlt; **[abgeleitet]** aus dem Patch in `build_codeforge_repo.sh`):
```bash
printf '%s\n' '# CodeForge package repository (Prefix com.codeforge.app)' \
  'deb https://scto.github.io/terminal-packages-codeforge stable main' > $PREFIX/etc/apt/sources.list
```
Der öffentliche Schlüssel liegt im Bootstrap unter `$PREFIX/share/termux-keyring/codeforge.gpg` (aus dem Paket `termux-keyring`). Ob die Datei zusätzlich unter `$PREFIX/etc/apt/trusted.gpg.d/` abgelegt werden muss, ist **[offen]**.

## B3. Artefakte aus Actions-Läufen (ohne Veröffentlichung)

`debs-<Gruppe>-<Arch>` (tar) und `codeforge-apt-repo-<Gruppe>` verfallen nach 14 Tagen. Manuell installieren (nach Download aus dem Lauf):
```bash
tar xf debs-D-leicht-lsp-aarch64.tar -C ~/debs
apt install ~/debs/taplo_*.deb          # dpkg-Abhängigkeiten müssen vorhanden sein
```
Ein `.deb`, das nicht im Repo veröffentlicht wurde, ist **nicht signiert**; nur für eigene Tests.

## B4. Typische Fehlerbilder

| Symptom | Ursache / Maßnahme |
|---|---|
| `pkg install` meldet bei **allen** Paketen „not going to be installed“, `apt-get check` zeigt `X : Depends: Y but it is not installed` | defekter dpkg-Zustand (der Fall `dpkg-perl` → `clang`). `dpkg --remove --force-depends <X>` und, falls nötig, das abhängige Paket (`dpkg-scanpackages`). Task 45. |
| Setup bleibt bei „[ 5%] Installiere Paket …“ | Download ohne Fortschrittsanzeige. `ls -l $PREFIX/var/cache/apt/archives/partial/` zweimal im Abstand von 20 s; wächst die Datei, läuft er. |
| GitHub-API „Rate-Limit“ | `export GITHUB_TOKEN=…` |
| SHA-256-Abweichung | Download wiederholen; sonst Release neu erzeugen, `.sha256` prüfen |
| „… enthält `com.termux`“ beim Bau | Paketbau-Fehler (Prefix nicht ersetzt); Paket wird übersprungen |
| Pages liefert alte Pakete | Pages-Verzögerung abwarten; `apt update` wiederholen |

---

# Teil C – Compiler-Runtime und Language Server

## C1. Compiler-Runtime (Compose-Preview)

**Was ist das?** Ein ZIP mit Jars (Compose-Runtime/-UI, `kotlin-stdlib`, `kotlinx-coroutines-core-jvm` …) und `manifest.json` (`bom`, `kotlin`, `files[]` mit Name, SHA-256, Größe). Es liegt als Release-Asset in `scto/codeforge-tools`.
Der Tag ergibt sich **aus dem Versionskatalog**: `compiler-runtime-<composeBomVersion>-k<kotlinVersion>`, aktuell `compiler-runtime-2025.09.00-k2.1.21` **[geprüft: `feature/composepreview/build.gradle.kts`, Screenshot des Releases]**.

**Automatisch (App):** `ComposePreviewRendererImpl` ruft `CompilerRuntimeProvider.ensureInstalled` vor dem Kompilieren auf **[geprüft]**:
1. Ist `files/compiler-runtime/` vorhanden und stimmen `manifest.json` (`bom`, `kotlin`) und alle SHA-256 der Dateien, passiert nichts.
2. Sonst lädt die App `<zip>.sha256` und `<zip>` von `https://github.com/scto/codeforge-tools/releases/download/<tag>/…` (nur `https://github.com/…`, Weiterleitungen geprüft), prüft die Summe, entpackt in einen Temp-Ordner (Zip-Slip-Schutz), verifiziert das Manifest und verschiebt es nach `files/compiler-runtime/`.
3. Fortschritt zeigt der UI-Zustand `PreparingRuntime(progress)`.
Ändert sich BOM oder Kotlin im Versionskatalog, **muss vorher** das passende Bundle unter dem neuen Tag veröffentlicht sein; sonst scheitert der Download.

**Manuell (im App-Terminal; der Ordner liegt im App-Speicher):**
```bash
cd ~/..                                   # = /data/data/com.codeforge.app/files
TAG=compiler-runtime-2025.09.00-k2.1.21
BASE=https://github.com/scto/codeforge-tools/releases/download/$TAG
mkdir -p tmp-cr && cd tmp-cr
curl -fL "$BASE/$TAG.zip" -o "$TAG.zip"
curl -fL "$BASE/$TAG.zip.sha256" -o "$TAG.zip.sha256"
sha256sum "$TAG.zip"; cat "$TAG.zip.sha256"      # müssen übereinstimmen
mkdir x && unzip -q "$TAG.zip" -d x
ls x; cat x/manifest.json | head -30
rm -rf ../compiler-runtime && mv x ../compiler-runtime
```
Danach prüft die App beim nächsten Start nur noch das Manifest. **[abgeleitet; nicht ausgeführt]** (Pfad `COMPILER_RUNTIME_DIR_NAME = compiler-runtime` unter `filesDir`).

**Bauen und veröffentlichen:** Workflow *Build Compose Preview Compiler Runtime* (`.github/workflows/build-compiler-runtime.yml`) ruft `./gradlew :feature:composepreview:packCompilerRuntime` auf.

| Eingabe | Standard | Bedeutung |
|---|---|---|
| `publish` | an | Release in `target_repo` anlegen (Secret `PACKAGES_REPO_TOKEN`) |
| `with_extended_icons` | **aus** | `material-icons-extended` einschließen: **+35,7 MiB** |
| `target_repo` | `scto/codeforge-tools` | |

**Auffälligkeit [offen]:** Das veröffentlichte ZIP hat 53,8 MB, Task 26/27a erwartet ~20,22 MiB (30 JARs, ohne `kotlin-compose-compiler-plugin-embeddable.jar`). Das passt zu einem Lauf mit `with_extended_icons=true`; **das ist eine Vermutung**. Prüfen:
```bash
unzip -l compiler-runtime-2025.09.00-k2.1.21.zip | tail -40
grep -c '"name"' manifest.json
unzip -l …zip | grep -i "icons-extended\|compose-compiler"
```
Task 27a ändert das Bundle: Das Plugin-Jar entfällt, es kommt aus dem Termux-Paket `kotlin`. Beachte die Versionsabweichung: Das Paket `kotlin` im Fork steht auf **2.4.21**, die App auf **2.1.21** (Spike aus Task 27a.0 klärt, ob das läuft).

## C2. Language Server

**Wo und wie:** *Einstellungen → Erweiterungen* (`ExtensionsScreen`, `ExtensionsManager`). Installationsort: `/data/data/com.codeforge.app/files/usr/opt/extensions/<id>/` **[geprüft: `resolveBaseDirectory`]**. Beim Öffnen einer passenden Datei startet `LspServerResolverImpl` den Server **[geprüft]**.

| id | Sprachen | Quelle | Voraussetzung (`requires`) | Startbefehl |
|---|---|---|---|---|
| `kotlin-language-server` 1.3.11 | Kotlin | Download ZIP (fwcd, SHA-256 fest) | `openjdk-17` | `server/bin/kotlin-language-server` |
| `jdtls` 1.45.0 | Java | Download TAR.GZ (Eclipse, SHA-256 fest) | `openjdk-17`, `python` | `python bin/jdtls` |
| `clangd` 18.1.3 | C/C++ | Termux-Paket `clang` (Binary `clangd`) | `clang` (`libllvm`, Gruppe H4) | `$PREFIX/bin/clangd` |
| `pyright` 1.1.377 | Python | Download npm-Tarball | `nodejs` | `node package/langserver.index.js --stdio` |
| `rust-analyzer` 1.79.0 | Rust | Termux-Paket `rust-analyzer` | `rust-analyzer` (Gruppe H6) | `$PREFIX/bin/rust-analyzer` |
| `vscode-langservers-extracted` 4.8.0 | HTML, CSS, JSON | Download npm-Tarball | `nodejs` | `node package/bin/vscode-{html,css,json}-language-server --stdio` |

**Geplant mit Task 43 (noch nicht umgesetzt):** `lemminx` (XML; Uber-JAR, `java -jar`, `openjdk-17`), `yaml-language-server` (npm, `nodejs`), `taplo` (TOML; Termux-Paket `taplo`, **fehlt im Repo**, braucht `taplo lsp stdio` und damit das Cargo-Feature `lsp`), `bash-language-server` (npm, Unterbefehl `start`; `shellcheck` optional, **fehlt im Repo**).

**Automatisch installieren:**
- *Download-Server:* In der App „Installieren“. Ablauf: laden, SHA-256 prüfen (`Sha256Utils`), nach `…/opt/extensions/<id>/` entpacken, Startdatei ausführbar machen. Fehlen `requires`, zeigt die App die fehlenden Pakete an (`checkMissingRequirements`).
- *Termux-Paket-Server (`clangd`, `rust-analyzer`, später `taplo`):* Die App installiert sie **nicht selbst**; `ExtensionsManager.install` wirft den Hinweis „… must be installed via `codeforge-env pkg <paket>`“. Im Terminal:
  ```bash
  codeforge-env pkg clang          # liefert clangd
  codeforge-env pkg rust-analyzer
  ```

**Voraussetzungen im Terminal installieren:**
```bash
pkg install openjdk-17       # Kotlin LS, jdtls, später LemMinX
pkg install nodejs           # pyright, vscode-langservers, später YAML und Bash
pkg install python           # jdtls-Startskript
```
(`python` und `nodejs` liegen in Gruppe `E-leicht-py-node`, `openjdk-17` in `H1-jdk17`.)

**Manuell installieren (Beispiel Kotlin Language Server)** — gleiche Schritte wie die App, **[abgeleitet; nicht ausgeführt]**:
```bash
EXT=/data/data/com.codeforge.app/files/usr/opt/extensions
mkdir -p $EXT/kotlin-language-server && cd $EXT/kotlin-language-server
curl -fL https://github.com/fwcd/kotlin-language-server/releases/download/1.3.11/server.zip -o server.zip
sha256sum server.zip   # erwartet: c65efea1c09d88ddb1fe227d8e2c5dbb3d8dd568453bccbbf2e20dfa2f004dcd
unzip -q server.zip && rm server.zip
chmod +x server/bin/kotlin-language-server
```
Für `jdtls` (`https://download.eclipse.org/jdtls/milestones/1.45.0/jdt-language-server-1.45.0-202502271238.tar.gz`, SHA-256 `c09e79e9…2797`), `pyright` (`https://registry.npmjs.org/pyright/-/pyright-1.1.377.tgz`, SHA-256 `b11dc22e…7710`) und `vscode-langservers-extracted` (`…-4.8.0.tgz`, SHA-256 `8c710292…d2e1`) gilt dasselbe mit `tar xzf` und den Startdateien aus der Tabelle. Die vollständigen Prüfsummen stehen in `LspExtensionPackage.kt`.

**Offene Prüfungen [offen]:** Startet `jdtls` (Python-Skript) und der Kotlin LS auf dem Gerät mit `openjdk-17`? Laufen die npm-Server mit der Node-Version aus `E-leicht-py-node`? Beides ist auf dem Gerät nicht belegt (Task 43 verlangt es im Bericht).

---

## Anhang: Befehle zum Prüfen auf dem Gerät

```bash
dpkg --print-architecture                      # aarch64 oder arm
apt-cache policy openjdk-17 taplo shellcheck   # Kandidat vorhanden?
apt-get check                                  # Zustand konsistent?
codeforge-env doctor                           # Umgebung
ls $PREFIX/opt/extensions                      # installierte Language Server
ls ~/../compiler-runtime | head                # Compiler-Runtime
cat ~/crash_log.md                             # Crash-Report der App (Datei kann leer/fehlen)
logcat -d | grep -E "FATAL|AndroidRuntime" | tail -n 80
```
