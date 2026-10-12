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
- **Google:** cmdline-tools (`sdkmanager`, reines Java), darüber `platforms;android-N` und die Java-Teile der build-tools.
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
