# GitHub-Actions-Workflow: Bootstraps & APT-Repo

Datei: `.github/workflows/build-bootstrap-repo.yml`
Ruft `build_codeforge_repo.sh` auf (Termux-Docker-Builder, Prefix `com.codeforge.app`) und baut Bootstraps sowie das signierte APT-Repository.
Der Workflow startet nur manuell (Actions → *Build Bootstraps & APT-Repo* → *Run workflow*).

> Stand: YAML geparst und die Eingabeprüfung lokal getestet. Der Lauf auf einem echten Runner (Docker-Builder, `apt-ftparchive`, Dateirechte) ist **nicht** getestet.

## Eingaben

| Eingabe | Standard | Bedeutung |
|---|---|---|
| `archs` | `aarch64,arm` | Architekturen, durch Komma getrennt. Erlaubt: `aarch64`, `arm`, `i686`, `x86_64`. Duplikate und Leerzeichen werden bereinigt, alles andere bricht ab. |
| `extra_packages` | leer | Pakete **im Bootstrap** (vergrößert jede ZIP und die APK). Normalerweise leer, höchstens Kleinkram wie `zip`. |
| `repo_packages` | leer | Pakete **nur im APT-Repo**: werden gebaut und per `pkg install` nachgeladen, z. B. `openjdk-17,git,protobuf,aapt2,wget`. |
| `publish` | aus | Aus: nur Artefakte. An: Release anlegen und APT-Repo nach `gh-pages` pushen. |
| `target_repo` | `scto/terminal-packages-codeforge` | Ziel für Release und `gh-pages` (nur bei `publish`). |
| `release_tag` | leer | Leer = `bootstrap-JJJJ.MM.TT` (UTC). |
| `apt_url` | leer | Öffentliche URL des APT-Repos. Leer = Standard des Scripts. |

## Ablauf

1. Eingaben prüfen (Whitelist für Architekturen, Regex für Pakete, Tag, Repo, URL).
2. Checkout, Secrets prüfen.
3. Speicherplatz auf dem Runner freigeben, Tools installieren.
4. Privaten Schlüssel aus dem Secret nach `.gpg/private/codeforge.gpg` schreiben.
5. `build_codeforge_repo.sh` ausführen (Build, harte Verifikation der ELF-Dateien, APT-Repo, Signatur, Selbstprüfung).
6. Zusammenfassung mit fertigem `gradle.properties`-Block (Version und SHA-256 je ABI).
7. Artefakte hochladen (Bootstraps, `github_repo_ready/`, öffentlicher Key, Doku). **Nie** der private Schlüssel.
8. Nur bei `publish`: Release anlegen, APT-Repo auf `gh-pages` pushen.
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

1. Erster Lauf: `archs=aarch64,arm`, keine Extras, `publish` aus. Prüft Docker-Build, Verifikation und Repo-Erzeugung.
2. Zweiter Lauf: mit Extra-Paketen, `publish` an.
3. Ergebnis in `gradle.properties` übernehmen (Block steht in der Job-Zusammenfassung), App neu bauen.

## Grenzen

- **Laufzeit:** GitHub bricht Jobs nach 6 Stunden ab (Workflow: 350 Minuten). `openjdk-17` kann sehr lange dauern; ggf. nur eine Architektur pro Lauf.
- **Repo wird ersetzt:** `publish` ersetzt den kompletten Inhalt von `gh-pages` durch das Ergebnis dieses Laufs (Pool eingeschlossen). Nicht mitgebaute Architekturen und Pakete fehlen danach. Für ein vollständiges Repo alle gewünschten Architekturen und Pakete im selben Lauf bauen.
- **Dateigröße:** GitHub lehnt Einzeldateien über 100 MB ab (das Script warnt ab 95 MB). Pages-Seiten sind außerdem auf etwa 1 GB begrenzt.
- **`extra_packages` vs. `repo_packages`:** `extra_packages` geht an `--add` des Termux-Builders und landet vermutlich im Bootstrap (Verhalten von `build-bootstraps.sh` im Fork nicht geprüft). `repo_packages` werden danach einzeln pro Architektur mit `build-package.sh -a <arch> -o output <paket>` gebaut, also auch samt Abhängigkeiten aus dem Quellcode. Jedes so gebaute Paket wird auf `com.termux` geprüft; ein Treffer bricht den Lauf ab. Die Aufrufoptionen von `build-package.sh` im Fork sind nicht verifiziert.

## Welche Pakete?

Das Gradle-Plugin bettet die Bootstrap-ZIPs in die APK ein. Alles, was in der ZIP landet, vergrößert die App. Pakete, die `codeforge-env` per `pkg install` nachlädt, gehören deshalb in `repo_packages` (nur Repo), nicht in `extra_packages`.

| Paket | Empfehlung | Begründung |
|---|---|---|
| `openjdk-17` | ja (Repo) | Für Gradle/Kotlin nötig, sehr groß |
| `git` | ja | Git-Funktionen im Terminal |
| `protobuf` | ja | Liefert `protoc`. Das Gradle-Plugin lädt sonst ein x86-Binary, das auf Android nicht läuft |
| `aapt2` | ja | Gleiches Prinzip: AGP lädt sonst ein x86-`aapt2`; über `android.aapt2FromMavenOverride` auf das Termux-Binary zeigen |
| `zip` | ja | Gehört meist nicht zur Basis |
| `wget` | optional | Komfort |
| `curl`, `nano`, `unzip` | wahrscheinlich schon in der Basis | Vorher in der Paketliste des Bootstraps prüfen |
| `gradle` | optional | Mit dem Gradle-Wrapper (`gradlew`) nicht nötig; nur sinnvoll für Projekte ohne Wrapper |
| `kotlin` | optional | Das Kotlin-Gradle-Plugin bringt seinen Compiler selbst mit; nur für das CLI `kotlinc` |

Vorschlag: `extra_packages` leer, `repo_packages` = `openjdk-17,git,protobuf,aapt2,zip,wget`

Ob alle Paketnamen im Fork unter genau diesem Namen existieren und für alle Architekturen bauen (insbesondere `openjdk-17` auf `arm`), ist nicht geprüft.
