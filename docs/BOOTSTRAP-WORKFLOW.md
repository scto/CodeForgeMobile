# GitHub-Actions-Workflow: Bootstraps & APT-Repo

Datei: `.github/workflows/build-bootstrap-repo.yml`
Ruft `build_codeforge_repo.sh` auf (Termux-Docker-Builder, Prefix `com.codeforge.app`) und baut Bootstraps sowie das signierte APT-Repository.
Der Workflow startet nur manuell (Actions → *Build Bootstraps & APT-Repo* → *Run workflow*).
Für mehrere Etappen am Stück gibt es zusätzlich `.github/workflows/build-all-stages.yml` (Abschnitt „Gruppen und Kette“).

> Stand 2026-10-12: Der Workflow lief auf einem echten Runner durch (Bootstraps, 420 Pakete im APT-Pool, Release `bootstrap-2026.10.11`, `gh-pages` gepusht). **Nicht geprüft:** ob GitHub Pages `…/dists/stable/Release` ausliefert, `pkg install` gegen das Repo und der Test der App auf dem Gerät.

## Eingaben

| Eingabe | Standard | Bedeutung |
|---|---|---|
| `archs` | `aarch64,arm,x86_64` | Architekturen, durch Komma getrennt. Erlaubt: `aarch64`, `arm`, `i686`, `x86_64`. Das Gradle-Plugin bettet `aarch64`, `arm` und `x86_64` ein; `i686` wird nur gebaut. Duplikate und Leerzeichen werden bereinigt, alles andere bricht ab. |
| `extra_packages` | leer | Pakete **im Bootstrap** (vergrößert jede ZIP und die APK). Normalerweise leer, höchstens Kleinkram wie `zip`. |
| `repo_packages` | leer | Pakete **nur im APT-Repo**: werden gebaut und per `pkg install` nachgeladen, z. B. `openjdk-17,git,protobuf,aapt2,wget`. |
| `publish` | aus | Aus: nur Artefakte. An: Release anlegen und APT-Repo nach `gh-pages` pushen. |
| `target_repo` | `scto/terminal-packages-codeforge` | Ziel für Release und `gh-pages` (nur bei `publish`). |
| `release_tag` | leer | Leer = `bootstrap-JJJJ.MM.TT` (UTC). Existiert der Tag schon, werden die Assets ersetzt (`--clobber`) statt abzubrechen. Für unveränderliche Versionen besser einen neuen Tag wählen: `gradle.properties` verweist per Tag und SHA-256 darauf. |
| `apt_url` | leer | Öffentliche URL des APT-Repos. Leer = Standard des Scripts. |

## Ablauf

1. Eingaben prüfen (Whitelist für Architekturen, Regex für Pakete, Tag, Repo, URL).
2. Checkout, Secrets prüfen.
3. Speicherplatz auf dem Runner freigeben, Tools installieren.
4. Privaten Schlüssel aus dem Secret nach `.gpg/private/codeforge.gpg` schreiben.
5. `build_codeforge_repo.sh` ausführen (Fork reparieren und patchen, Build, strenge Verifikation der Bootstraps, APT-Repo, Signatur, Selbstprüfung). Details: `build_script_summary.md`.
6. Zusammenfassung mit fertigem `gradle.properties`-Block (Version und SHA-256 je ABI).
7. Artefakte hochladen (Bootstraps, `github_repo_ready/`, öffentlicher Key, Doku). **Nie** der private Schlüssel.
8. Nur bei `publish`: Release anlegen (oder bei vorhandenem Tag die Assets ersetzen), APT-Repo auf `gh-pages` pushen.
9. Aufräumen (`.gpg/private`, `tmp`, Docker-Cache), auch bei Fehlern.

## Einmalige Einrichtung

Der Runner darf keinen neuen Schlüssel erzeugen: Er wäre nach dem Lauf weg, und die Signatur würde sich bei jedem Build ändern. Der Schlüssel wird deshalb einmal lokal erzeugt (z. B. in Termux) und als Secret hinterlegt:

```bash
export GNUPGHOME="$(mktemp -d)"
printf '%s\n' "$CODEFORGE_GPG_PASSPHRASE" | gpg --batch --pinentry-mode loopback --passphrase-fd 0 \
  --quick-generate-key "Thomas Schmid <tschmid35@gmail.com>" rsa4096 sign never
printf '%s\n' "$CODEFORGE_GPG_PASSPHRASE" | gpg --batch --pinentry-mode loopback --passphrase-fd 0 \
  --armor --export-secret-keys tschmid35@gmail.com | base64 -w0 > key.b64
gh secret set GPG_PRIVATE_KEY_B64 < key.b64
gh secret set CODEFORGE_GPG_PASSPHRASE --body "$CODEFORGE_GPG_PASSPHRASE"
shred -u key.b64 2>/dev/null || rm -f key.b64
```

| Secret | Pflicht | Inhalt |
|---|---|---|
| `CODEFORGE_GPG_PASSPHRASE` | ja | Passphrase des Signaturschlüssels |
| `GPG_PRIVATE_KEY_B64` | ja | Privater Schlüssel, armored und base64 (einzeilig) |
| `PACKAGES_REPO_TOKEN` | nur bei `publish` | Fine-grained PAT mit *Contents: Read and write* auf dem Ziel-Repo (der Standard-`GITHUB_TOKEN` kennt nur das eigene Repo) |

Zusätzlich im Ziel-Repo: *Settings → Pages* auf Branch `gh-pages` stellen.

## Empfohlene Reihenfolge

1. Erster Lauf: `archs=aarch64,arm,x86_64`, keine Extras, `publish` aus. Prüft Docker-Build, Verifikation und Repo-Erzeugung (erledigt).
2. Zweiter Lauf: `publish` an, mit neuem `release_tag` und ggf. `repo_packages`.
3. Ergebnis in `gradle.properties` übernehmen (Block steht in der Job-Zusammenfassung), App neu bauen.

## Grenzen

- **Laufzeit:** GitHub bricht Jobs nach 6 Stunden ab (Workflow: 350 Minuten). Ein Lauf mit vier Architekturen hat rund 3,5 Stunden gedauert. `openjdk-17` kann sehr lange dauern; ggf. nur eine Architektur pro Lauf.
- **Repo wird ersetzt:** `publish` ersetzt den kompletten Inhalt von `gh-pages` durch das Ergebnis dieses Laufs (Pool eingeschlossen). Nicht mitgebaute Architekturen und Pakete fehlen danach. Für ein vollständiges Repo alle gewünschten Architekturen und Pakete im selben Lauf bauen.
- **Pages:** Im Ziel-Repo muss *Settings → Pages* auf Branch `gh-pages`, Ordner `/` stehen, und das Repo muss öffentlich sein. APT-URL: `https://scto.github.io/terminal-packages-codeforge`.
- **Dateigröße & Pool-Limit:** GitHub lehnt Einzeldateien über 100 MB ab. Pages-Seiten sind außerdem auf 1.000 MB (1 GB) begrenzt. Gemäß `apt-limits.conf` werden .deb-Dateien ab 30 MiB automatisch als Release-Assets ins Release `large-packages` ausgelagert. Der Größenwächter im Zusammenbau warnt ab 800 MB und verweigert die Veröffentlichung ab 950 MB (übersteuerbar nur per `force_publish=true`).
- **`extra_packages` vs. `repo_packages`:** `extra_packages` geht an `--add` des Termux-Builders und landet vermutlich im Bootstrap (Verhalten von `build-bootstraps.sh` im Fork nicht geprüft). `repo_packages` werden danach einzeln pro Architektur mit `build-package.sh -a <arch> -o output <paket>` gebaut, also auch samt Abhängigkeiten aus dem Quellcode. Jedes so gebaute Paket wird auf `com.termux` geprüft; ein Treffer bricht den Lauf ab. Unterpakete (`protobuf`, `aapt2`) werden über ihr Elternpaket gebaut. Der Build dieser Pakete ist in einem Lauf mit `repo_packages` noch nicht getestet.

## Welche Pakete?

Das Gradle-Plugin bettet die Bootstrap-ZIPs in die APK ein. Alles, was in der ZIP landet, vergrößert die App. Pakete, die `codeforge-env` per `pkg install` nachlädt, gehören deshalb in `repo_packages` (nur Repo), nicht in `extra_packages`.

`codeforge-env` bezieht Android-SDK-Teile so (Vorbild: `scto/termux-android-setup`):

- **Termux-Pakete aus diesem Repo:** JDK, Ninja und das Hilfswerkzeug `jq`; CMake nur als Alternative (`cmake;termux`). `p7zip` lädt das Skript nur bei `.7z`-Assets nach.
- **Mirror (`cmdline-tools-19.0`):** cmdline-tools (`sdkmanager`, reines Java). Darüber lädt Google noch `platforms;android-N` und die Java-Teile der build-tools.
- **Mirror `scto/codeforge-tools` (GitHub-Releases):** aarch64-Binaries für build-tools und platform-tools (`aapt2`, `aidl`, `zipalign`, `adb`), NDK und CMake. Der Mirror kopiert Drittanbieter-Builds (HomuHomu833, AndroidIDEOfficial, Lzhiyong); er läuft über eigenen Workflow und nicht über dieses Repo, siehe `docs/CODEFORGE-TOOLS.md`. Ist der Mirror leer oder nicht erreichbar, nutzt das Skript die HomuHomu833-Repos direkt.

| Paket | Empfehlung | Begründung |
|---|---|---|
| `openjdk-17` | ja (Repo) | Für Gradle/Kotlin und den `sdkmanager` nötig, sehr groß |
| `cmake` | optional | Nur für die Alternative `cmake;termux` (per Symlink als `$ANDROID_HOME/cmake/<version>`). Die CMake-Versionen kommen sonst als fertige aarch64-Builds aus dem Mirror. Die Abhängigkeiten werden mitgebaut, das verlängert den Lauf |
| `ninja` | ja | Ninja für die Mirror-CMake-Builds (die kein eigenes `ninja` mitbringen) und für `cmake;termux` |
| `jq` | ja | `codeforge-env` liest damit die GitHub-API (Versionslisten, Mirror) |
| `p7zip` | nicht nötig | Wird nur bei `.7z`-Assets nachgeladen; die Mirror-Assets sind `.tar.xz` und `.zip` |
| `git` | ja | Git-Funktionen im Terminal |
| `zip` | ja | Gehört meist nicht zur Basis |
| `protobuf` | optional | Liefert `protoc`. Das Gradle-Plugin lädt sonst ein x86-Binary, das auf Android nicht läuft |
| `aapt2` | nicht nötig | Das aarch64-`aapt2` kommt aus den SDK-Binaries des Mirrors (`scto/codeforge-tools`). Nur als Rückfall sinnvoll, falls diese Quelle ausfällt |
| `android-tools` | nicht nötig | `adb` kommt aus den platform-tools derselben Quelle |
| `wget` | optional | Komfort |
| `curl`, `nano`, `unzip`, `tar`, `xz-utils` | wahrscheinlich schon in der Basis | Vorher in der Paketliste des Bootstraps prüfen |
| `gradle` | optional | Mit dem Gradle-Wrapper (`gradlew`) nicht nötig; nur sinnvoll für Projekte ohne Wrapper |
| `kotlin` | optional | Das Kotlin-Gradle-Plugin bringt seinen Compiler selbst mit; nur für das CLI `kotlinc` |

Vorschlag: `extra_packages` leer, `repo_packages` = `openjdk-17,ninja,jq,git,zip` (plus `cmake`, wenn du `cmake;termux` anbieten willst)

Zum Einstieg reicht `openjdk-17,ninja,jq`. Ein späterer Lauf mit `publish` ersetzt `gh-pages` komplett: Wenn du `git` oder `zip` nachträglich willst, gib im neuen Lauf wieder **alle** gewünschten Pakete an, sonst verschwinden die früheren aus dem Repo.

**Nicht geprüft:** ob alle Paketnamen im Fork für alle Architekturen bauen (insbesondere `openjdk-17` und `cmake` auf `arm`) und ob die Binaries aus dem Mirror (HomuHomu833, AndroidIDEOfficial, Lzhiyong) unter dem Prefix `com.codeforge.app` laufen. Der bisher erfolgreiche Lauf hatte `repo_packages` leer.

## Repo in Etappen aufbauen (Modus add)

Ein einzelner Lauf schafft große Paketlisten nicht: GitHub bricht Jobs nach 6 Stunden ab, und OpenJDK, LLVM, Python oder Node.js
brauchen je Architektur Stunden. Der Workflow `build-bootstrap-repo.yml` hat deshalb zwei Modi:

| Modus | Was passiert |
|---|---|
| `full` | Bootstraps + Pakete + Repo in einem Job. Ersetzt Release-Assets **und** das gesamte Repo auf gh-pages. |
| `add` | Bootstraps bleiben unberührt. Die Pakete werden **pro Architektur parallel** gebaut (je Architektur ein eigener Job mit eigenen 6 Stunden). Danach werden die neuen `.deb` in das bestehende Repo auf gh-pages **ergänzt**, `Packages`/`Release` neu erzeugt und signiert. |
| `auto` (Standard) | `add`, wenn auf gh-pages des Ziel-Repos schon ein Repo liegt, sonst `full`. |

Ablauf:

1. Einmal `full` (oder `auto` bei leerem Repo) mit `publish` – erzeugt Bootstraps und das erste Repo. `repo_packages` kann dabei leer bleiben oder eine kleine Liste enthalten.
2. Danach pro Lauf eine **Etappe** (Eingabe `stage`, Namen stehen in `apt-stages.conf`) und `publish` – jede ergänzt das Repo. Reihenfolge egal.
3. Zusätzliche Einzelpakete über `repo_packages` (Elternpakete: `libllvm` statt `clang`, `libcurl` statt `curl`, `liblzma` statt `xz-utils`, `libprotobuf` statt `protobuf`, `aapt` statt `aapt2`/`aidl`).

Wichtige Regeln:

- **Gleicher Schlüssel:** `GPG_PRIVATE_KEY_B64` muss derselbe sein wie beim ersten Lauf. Im Modus `add` bricht der Lauf ab, wenn der Fingerprint nicht zum bestehenden `codeforge.gpg` passt.
- **Abhängigkeiten:** Sie werden in jedem Lauf neu aus dem Quellcode gebaut (im Container ist nichts vom vorigen Lauf vorhanden). Pakete ohne BUILD_DEPENDS (gradle, kotlin, …) stehen deshalb mit `nodeps` in der Etappe und werden mit `build-package.sh -s` gebaut. Sonst würde z. B. jeder Lauf OpenJDK 21 mitbauen, nur weil `kotlin` davon abhängt.
- **Architekturunabhängige Pakete** (`*_all.deb`) werden einmal gebaut (`single-arch`) und gelten für alle Architekturen im Repo.
- **`keep_going`:** Ein fehlgeschlagenes Paket stoppt den Job nicht; es steht in der Zusammenfassung. Sind in einer Architektur alle Pakete fehlgeschlagen, bricht der Lauf ab.
- **Eine Leg schlägt fehl** (Timeout, Docker-Fehler): der Schritt „assemble“ wird übersprungen, das Repo bleibt unverändert. Etappe erneut starten oder kleiner schneiden.
- **gh-pages-Historie:** Beim Veröffentlichen wird der Branch als einzelner Commit per Force-Push ersetzt (der Stand ist immer vollständig). Das hält das Repo klein.
- **Größe:** GitHub Pages ist auf etwa 1 GB je Seite begrenzt. Das Skript warnt ab 900 MB Pool. Für ein sehr großes Repo ist anderes Hosting nötig.
- **Paketnamen:** `p7zip` gibt es im Fork nicht, nur `7zip` (stellt `p7zip` bereit). Das Skript ersetzt `p7zip` automatisch durch `7zip`.

Lokal (ohne GitHub): `PHASE=build ARCHS=aarch64 REPO_PACKAGES=... ./build_codeforge_repo.sh` baut nur Pakete nach `terminal-packages-codeforge/output/`.
`PHASE=assemble BASE_REPO_DIR=<gh-pages-Checkout> EXTRA_DEBS_DIR=<Ordner mit .deb> ./build_codeforge_repo.sh` ergänzt das Repo.

## Gruppen und Kette (`build-all-stages.yml`)

Ein Lauf von `build-bootstrap-repo.yml` baut eine Etappe. Damit nicht jede Etappe einzeln gestartet werden muss, startet
`.github/workflows/build-all-stages.yml` mehrere **Gruppen** nacheinander. Eine Gruppe ist eine Etappe aus `apt-stages.conf` plus
optional einzelne Zusatzpakete. Die Gruppen stehen in `apt-groups.json` (Repo-Wurzel), die Matrix baut `.github/scripts/plan-apt-groups.sh`.

### Welchen Workflow wann starten?

| Ziel | Workflow |
|---|---|
| Erstaufbau (Bootstraps, Release, erstes Repo) | `build-bootstrap-repo.yml`, Modus `full` (erledigt: `bootstrap-2026.10.11`) |
| Mehrere Etappen/Gruppen am Stück ergänzen | `build-all-stages.yml` |
| Ein einzelnes Paket oder eine Etappe, Wiederholung nach Fehler | `build-bootstrap-repo.yml`, Modus `add` mit `stage` oder `repo_packages` |

`build-all-stages.yml` ruft `build-bootstrap-repo.yml` selbst auf (`workflow_call`); beide Dateien müssen im Repo liegen. Nie beide parallel starten: Beide
teilen sich eine Concurrency-Gruppe (ein laufender, ein wartender Lauf); ein zusätzlicher Start verdrängt den wartenden Lauf.

### Gruppen (`apt-groups.json`)

Die Gruppen sind nach ihren internen Abhängigkeiten geordnet (geprüft mit `scripts/check-group-order.py`):
1. `B-leicht-dev` liefert `m4`, `make`, `jq` und Basis-Entwicklerwerkzeuge.
2. `E-leicht-py-node` liefert `python` (für `gdb` in C) und `nodejs`/`npm` (für `yarn` in A und `pnpm` in D).
3. `C-leicht-android` liefert Android-Tools und `gdb`.
4. `D-leicht-lsp` liefert LSP-Tools, `pnpm`, `taplo` und `shellcheck`.
5. `F-leicht-utils` liefert Hilfswerkzeuge (`vim`, `tmux`, etc.).
6. `A-leicht-jvm` liefert `gradle`, `kotlin`, `maven`, `codeforge-env`, `yarn`, `autoconf`, `automake`.

| Gruppe | Etappe | Zusatzpakete | Art |
|---|---|---|---|
| `B-leicht-dev` | `06-dev-core` (git, git-lfs, zip, unzip, tar, libcurl, wget, liblzma, 7zip, jq, ninja, make, cmake, openssh) | m4, pkg-config, libtool, flex, bison, ccache, patchelf, strace | leicht |
| `E-leicht-py-node` | `08-python` (python, python-pip) | nodejs, npm | leicht |
| `C-leicht-android` | `05-android-tools` (libprotobuf, aapt) | gdb, android-tools, gh, rsync, xmlstarlet | leicht |
| `D-leicht-lsp` | `12-lsp-misc` (gopls, taplo, ruff) | neocmakelsp, esbuild, pnpm, shellcheck | leicht |
| `F-leicht-utils` | `07-utils` (vim, nano, ripgrep, fd, tmux, patch, diffutils, findutils, binutils, which, libmagic, libsqlite, bash-completion) | – | leicht |
| `A-leicht-jvm` | `04-jvm-tools` (gradle, kotlin, maven, libjansi, ant, apksigner, d8, dx, ecj) | codeforge-env, yarn, autoconf, automake | leicht |
| `H1-jdk17` | `01-jdk17` (openjdk-17) | – | schwer |
| `H2-jdk21` | `02-jdk21` (openjdk-21) | – | schwer |
| `H3-jdk25` | `03-jdk25` (openjdk-25) | – | schwer |
| `H4-llvm-clang` | `10-llvm-clang` (libllvm) | – | schwer |
| `H5-dart` | `11-dart` (dart) | – | schwer |
| `H6-rust-analyzer` | `15-rust-analyzer` (rust-analyzer) | – | schwer |

Die Etappen `00-codeforge`, `09-nodejs`, `13-pure-tools`, `14-ide-tools`, `16-native-dev` und `17-debug-misc` in `apt-stages.conf` sind **nicht** einzeln als Gruppe
eingetragen; ihre Pakete sind in den Zusatzpaketen von A–E enthalten. Jedes Paket aus `apt-stages.conf` kommt damit in mindestens einer Gruppe vor (geprüft per Skript, 2026-10-18).
`python-pip` steht bewusst in `08-python` und nicht in `13-pure-tools`, siehe „Regeln beim Ändern“.

### Start

*Actions → Build All Stages (Kette) → Run workflow*:

| Eingabe | Standard | Bedeutung |
|---|---|---|
| `preset` | `light` | `light` = Gruppen B, E, C, D, F, A · `heavy` = H1–H6 · `all` = beides (leichte zuerst) |
| `only` | leer | Nur diese Gruppen, Komma getrennt (z. B. `A-leicht-jvm`); überschreibt `preset` |
| `mode` | `add` | `add` ergänzt das bestehende Repo |
| `archs` | `aarch64,arm` | Architekturen; Gruppen mit eigenem `archs` im JSON nutzen dieses |
| `publish` | aus | An: jede Gruppe wird nach ihrem Lauf veröffentlicht |
| `stop_on_error` | an (true) | An: Kette bricht bei der ersten fehlgeschlagenen Gruppe sofort ab (kein Veröffentlichen nach Abbruch) |
| `keep_going` | an | Innerhalb einer Gruppe bei fehlgeschlagenem Paket weiterbauen |
| `fail_on_skipped` | aus | An: bricht ab / schlägt fehl, wenn Pakete verworfen oder übersprungen wurden |
| `target_repo` | `scto/terminal-packages-codeforge` | Ziel für `gh-pages` |

Die Gruppen laufen **strikt nacheinander** (`max-parallel: 1`): `gh-pages` darf nicht parallel beschrieben werden. Artefakte heißen `debs-<Gruppe>-<Arch>` (tar-Archiv) und
`codeforge-apt-repo-<Gruppe>`; `run_label` setzt der Aufrufer, damit sich die Gruppen nicht überschreiben.

### Nachhol-Lauf für einzelne Gruppen
Sollte eine Gruppe fehlschlagen oder nachgeholt werden müssen (z.B. nach Fertigstellung der JDKs für Gruppe A):
- Workflow `build-all-stages.yml` starten mit `only=A-leicht-jvm`, `mode=add`, `publish=true`.
- Alternativ direkt `build-bootstrap-repo.yml` mit `stage=04-jvm-tools`, `repo_packages=codeforge-env,yarn,autoconf,automake`, `mode=add`, `publish=true`.
- Der Modus `add` verändert die bereits vorhandenen und veröffentlichten Pakete im Pool nicht, sondern fügt nur die neu gebauten `.deb` hinzu und aktualisiert `Packages` und `Release`.

### Regeln beim Ändern von `apt-groups.json`

- **Flags gelten für den ganzen Lauf.** Die Flags der Etappe (`single-arch`, `nodeps=…`) gelten für **alle** Pakete des Laufs, auch für `repo_packages`. Pakete, die ihre Abhängigkeiten im Build-Prefix brauchen, gehören in eine Gruppe ohne `nodeps=all`. Beispiel: `python-pip` scheiterte in Gruppe A mit „`python3.14` does not exist“, weil `04-jvm-tools` ohne Abhängigkeiten baut; seitdem steht es in `08-python`.
- **Etappenname muss in `apt-stages.conf` stehen**, Paketnamen müssen `^[a-z0-9+._-]+(,[a-z0-9+._-]+)*$` entsprechen; Gruppennamen sind eindeutig. Der Planer prüft das und bricht sonst mit Meldung ab.
- **Elternpakete verwenden** (`libllvm` statt `clang`, `libcurl` statt `curl`, `aapt` statt `aapt2`).
- **Laufzeit im Blick behalten:** Gemessen (Logs 2026-10-17, je Architektur): A ca. 8 Min, C ca. 1 h 45, E 2 h bis 2 h 45; bei B und D brach der Lauf vorzeitig ab (siehe unten), also keine vollständigen Zeiten.

### Stand der ersten Läufe (2026-10-17, laut Logs)

| Gruppe | Ergebnis | Ursache | Stand |
|---|---|---|---|
| A, C, E | Pakete gebaut, Upload scheiterte | Dateiname mit `:` (Epoche, z. B. `ca-certificates-java_1:2026.09.25_all.deb`) wird von `upload-artifact@v4` abgelehnt | behoben: `.deb` werden als tar hochgeladen (im Repo) |
| A | `python-pip` fehlgeschlagen | in `nodeps`-Etappe gebaut, `python` fehlte im Prefix | behoben: `python-pip` jetzt in `08-python` |
| B, D | Lauf brach ab | Prüfung „.deb enthält `com.termux`“ bei `openssh` (`var/service/ssh-agent/run`) und `taplo` (Binary); brach früher den ganzen Lauf ab | Prüfung zeigt jetzt Fundstellen und überspringt nur das Paket; **Ursache offen**, `openssh` und `taplo` fehlen vorerst im Repo |
| (nach Push) | `gradle_9.8.0_all.deb` 142 MB | GitHub-Limit 100 MB je Datei | behoben über Release-Asset und `large-packages.txt`, siehe „Große Pakete“ |
| F, H1–H6 | noch nicht gelaufen | – | offen |

Die Läufe selbst sind nach den Korrekturen **nicht erneut ausgeführt** worden; der Stand „behoben“ bezieht sich auf die Änderungen im Repo, nicht auf einen erfolgreichen neuen Lauf.

## Große Pakete (> 30 MiB / Release-Assets & Seitenlimit)

GitHub lehnt beim Push auf `gh-pages` Einzeldateien über 100 MB ab (`GH001`, z. B. `gradle_9.8.0_all.deb` mit ~142 MB). Zudem ist die gesamte veröffentlichte GitHub-Pages-Seite auf 1.000 MB (1 GB) begrenzt. Um das Limit dauerhaft einzuhalten, werden Pakete ab **30 MiB** (definiert in `apt-limits.conf`) als Release-Assets ausgelagert.

### Lösung im Workflow (`build-bootstrap-repo.yml`, Modus `add`)
1. **Abtrennen ab 30 MiB:** Vor dem Zusammenbau des APT-Pools verschiebt der `assemble`-Job alle `.deb`-Dateien über `LARGE_PKG_THRESHOLD_MIB` (30 MiB aus `apt-limits.conf`) nach `big-debs/` (Doppelpunkte aus Epochen werden im Dateinamen durch `_` ersetzt).
2. **Release-Asset:** Bei `publish = true` werden diese Dateien an das GitHub-Release `large-packages` des Ziel-Repos (`scto/terminal-packages-codeforge`) hochgeladen (`gh release upload large-packages big-debs/* --clobber`).
3. **Manifest `large-packages.txt` (+ `large-packages.txt.asc`):** Im Root des APT-Repos (`<apt-url>/large-packages.txt`) wird ein Verzeichnis aller ausgelagerten Pakete gepflegt (je Paket + Architektur gewinnt der neueste Eintrag) und zusätzlich mit dem Repo-Schlüssel als detachte Signatur `large-packages.txt.asc` signiert:
   ```text
   name|version|arch|datei|sha256|bytes|url
   ```
4. **Größenwächter im Zusammenbau:** Vor der Veröffentlichung misst der Assemble-Job die Größe des Repos (ohne `.git`). Erzeugt eine Warnung in `GITHUB_STEP_SUMMARY` ab 800 MB (`POOL_WARN_LIMIT_MB`) und verweigert die Veröffentlichung ab 950 MB (`POOL_MAX_LIMIT_MB`), sofern nicht `force_publish=true` übergeben wird.

### Bestands-Umzug (`scripts/rehome-large-packages.sh` / Workflow `rehome-large`)
Für Pakete, die vor der Absenkung der Schwelle bereits im Pool gelandet sind (z. B. `openjdk-17` aarch64/arm, `openjdk-*-source`), existiert das Werkzeug `rehome-large-packages.sh`:
- Standardmäßig sicherer Trockenlauf (`--dry-run`), berechnet Ersparnis und geplante Pool-Größe.
- Mit `--execute` wird jedes Asset hochgeladen, vor dem Löschen auf GitHub verifiziert, aus dem Pool entfernt, `Packages`/`Release` neu generiert, signiert und konsistenzgeprüft.

### Installation auf dem Gerät (`codeforge-env`)
Da ausgelagerte Pakete nicht im `pool/` und damit nicht in `dists/stable/main/binary-*/Packages` stehen, kennt `apt`/`pkg` sie nicht direkt.
- `codeforge-env` prüft in `install_repo_pkg()` (aufgerufen von `pkg_install()`) vor jedem Paket-Installationsschritt `<apt-url>/large-packages.txt` (inkl. optionaler `gpgv`-Signaturprüfung gegen `$PREFIX/share/termux-keyring/codeforge.gpg` falls `large-packages.txt.asc` vorliegt).
- Steht das Paket für die Geräte-Architektur (oder `all`) in `large-packages.txt`, prüft `install_repo_pkg`, dass die URL mit `https://github.com/` beginnt, lädt die `.deb`-Datei mit Fortschrittsanzeige herunter, verifiziert die SHA-256-Prüfsumme und installiert sie per `apt install -y /pfad/zur/datei.deb` (wodurch reguläre Abhängigkeiten weiterhin aus dem APT-Pool aufgelöst werden).
- Steht das Paket nicht in `large-packages.txt`, fällt `install_repo_pkg` auf `pkg install -y <name>` zurück.
- Manuelle Installation im Terminal (auch für große Pakete):
  ```bash
  codeforge-env pkg openjdk-17
  codeforge-env pkg gradle
  # oder:
  codeforge-env install gradle
  codeforge-env setup --gradle
  ```

