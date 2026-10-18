# GitHub-Actions-Workflow: Bootstraps & APT-Repo

Datei: `.github/workflows/build-bootstrap-repo.yml`
Ruft `build_codeforge_repo.sh` auf (Termux-Docker-Builder, Prefix `com.codeforge.app`) und baut Bootstraps sowie das signierte APT-Repository.
Der Workflow startet nur manuell (Actions → *Build Bootstraps & APT-Repo* → *Run workflow*).

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
- **Dateigröße:** GitHub lehnt Einzeldateien über 100 MB ab (das Script warnt ab 95 MB). Pages-Seiten sind außerdem auf etwa 1 GB begrenzt.
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

## Große Pakete (> 100 MB)

GitHub lehnt beim Push auf `gh-pages` Einzeldateien über 100 MB ab (`GH001`, z. B. `gradle_9.8.0_all.deb` mit ~142 MB; potenziell auch `openjdk-*` oder `libllvm`).

### Lösung im Workflow (`build-bootstrap-repo.yml`, Modus `add`)
1. **Abtrennen ab 95 MiB:** Vor dem Zusammenbau des APT-Pools verschiebt der `assemble`-Job alle `.deb`-Dateien über 95 MiB nach `big-debs/` (Doppelpunkte aus Epochen werden im Dateinamen durch `_` ersetzt).
2. **Release-Asset:** Bei `publish = true` werden diese Dateien an das GitHub-Release `large-packages` des Ziel-Repos (`scto/terminal-packages-codeforge`) hochgeladen (`gh release upload large-packages big-debs/* --clobber`).
3. **Manifest `large-packages.txt` (+ `large-packages.txt.asc`):** Im Root des APT-Repos (`<apt-url>/large-packages.txt`) wird ein Verzeichnis aller ausgelagerten Pakete gepflegt (je Paket + Architektur gewinnt der neueste Eintrag) und zusätzlich mit dem Repo-Schlüssel als detachte Signatur `large-packages.txt.asc` signiert:
   ```text
   name|version|arch|datei|sha256|bytes|url
   ```

### Installation auf dem Gerät (`codeforge-env`)
Da ausgelagerte Pakete nicht im `pool/` und damit nicht in `dists/stable/main/binary-*/Packages` stehen, kennt `apt`/`pkg` sie nicht direkt.
- `codeforge-env` prüft in `install_repo_pkg()` (aufgerufen von `pkg_install()`) vor jedem Paket-Installationsschritt `<apt-url>/large-packages.txt` (inkl. optionaler `gpgv`-Signaturprüfung gegen `$PREFIX/share/termux-keyring/codeforge.gpg` falls `large-packages.txt.asc` vorliegt).
- Steht das Paket für die Geräte-Architektur (oder `all`) in `large-packages.txt`, prüft `install_repo_pkg`, dass die URL mit `https://github.com/` beginnt, lädt die `.deb`-Datei mit Fortschrittsanzeige herunter, verifiziert die SHA-256-Prüfsumme und installiert sie per `apt install -y /pfad/zur/datei.deb` (wodurch reguläre Abhängigkeiten weiterhin aus dem APT-Pool aufgelöst werden).
- Steht das Paket nicht in `large-packages.txt`, fällt `install_repo_pkg` auf `pkg install -y <name>` zurück.
- Manuelle Installation im Terminal (auch für große Pakete):
  ```bash
  codeforge-env pkg gradle
  # oder:
  codeforge-env install gradle
  codeforge-env setup --gradle
  ```

