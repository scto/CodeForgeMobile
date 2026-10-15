# Task 21 – APT-Repo-Etappen-Pipeline prüfen und Paketliste abgleichen

**Ausgangslage:** Commit `9315c7a` brachte `apt-stages.conf`, `.github/workflows/build-bootstrap-repo.yml` (Jobs plan / build_full / build_add / assemble), `.github/scripts/plan-apt-build.sh`, die Composite-Actions `prepare-runner`/`publish-pages` und `docs/BOOTSTRAP-WORKFLOW.md` (Abschnitt „Repo in Etappen aufbauen (Modus add)"). Gebaut wird im Fork `scto/terminal-packages-codeforge` (Prefix `com.codeforge.app`); das APT-Repo liegt auf `gh-pages`. Lies zuerst `docs/BOOTSTRAP-WORKFLOW.md` und `build_codeforge_repo.md`.

**Was geprüft ist:** Skriptlogik lokal mit Mocks (Docker-/apt-ftparchive-Stubs). **Nicht geprüft:** echter GitHub-Actions-Lauf, echte Build-Zeiten, Pages-Größe.

## Aufgaben (ohne Zugriff auf GitHub Actions: Vorbereitung und Bericht)

1. `bash -n` und `shellcheck` für `build_codeforge_repo.sh` und `.github/scripts/plan-apt-build.sh`; YAML des Workflows mit `actionlint` (falls installierbar) prüfen.
2. `apt-stages.conf` gegen den Fork abgleichen: Jeder Paketname muss als `packages/<name>/build.sh` im Fork existieren (oder per `TERMUX_PKG_PROVIDES`/Unterpaket erreichbar sein). Liste fehlender Namen melden (Erwartung: `libllvm`/`dart`/`gopls`-Namen und `7zip` prüfen). Keine Namen raten.
3. Offene Paketwünsche aus dem Projekt gegen die Etappen halten und Lücken melden: JDK 17/21/25, Gradle, Kotlin, git, git-lfs, zip/unzip/tar, protobuf/protoc, apksigner, r8/d8, aidl, Python, Clang, npm, Dart, Flutter, TypeScript, Java-/XML-/Kotlin-LSP, curl, wget, xz, jq, p7zip, ninja. Für **Flutter, TypeScript (npm-Paket), jdtls, lemminx, kotlin-lsp** existieren keine Termux-Pakete: als Vorschlag je ein `build.sh` (Download eines fertigen Release, Prüfsumme, `TERMUX_PKG_PLATFORM_INDEPENDENT=true`) im Stil von `packages/kotlin` entwerfen – nur im Fork-Verzeichnis `agy-output/packages/`, nicht ins Hauptrepo.
4. Risiko-Etappen markieren (Laufzeit > 5 h auf GitHub-Runner: `openjdk-*`, `libllvm`, `dart`) und je Etappe Rückfallstrategie vorschlagen (nur eine Architektur pro Lauf, `skip_deps`, Selbst-Hosted-Runner). Pages-Limit (~1 GB) gegen die erwartete Pool-Größe abschätzen (Größen der bereits vorhandenen 420 Debs aus `gh-pages` zählen).
5. Bericht `docs/apt-stages-verification.md` mit Ergebnissen, offenen Namen, Laufreihenfolge-Empfehlung (Etappen 01→12) und einer Checkliste für den ersten echten Lauf.

## Grenzen

* Keine Veröffentlichung, kein Push in den Fork, keine Änderung an Releases/Secrets. GPG-Passphrase nie ausgeben oder ins Repo schreiben (nur `CODEFORGE_GPG_PASSPHRASE` aus der Umgebung); `.gpg/` und `.gpg/private/` nie committen.
* Das Bauen der Pakete selbst ist **nicht** Teil dieses Tasks; es läuft im GitHub-Actions-Workflow des Forks.

## Status: Abgeschlossen

- Skripte `build_codeforge_repo.sh`, `plan-apt-build.sh`, `.github/scripts/plan-apt-build.sh` und Workflow `.github/workflows/build-bootstrap-repo.yml` mit `bash -n`, `shellcheck` und `actionlint` geprüft und bereinigt (Exit Code 0).
- Alle 12 Etappen und Pakete aus `apt-stages.conf` gegen den Fork abgeglichen (100% Übereinstimmung, keine fehlenden Elternpakete).
- Lücken (Flutter, TypeScript, jdtls, lemminx, kotlin-lsp) identifiziert und Entwürfe im Fork unter `agy-output/packages/` bereitgestellt.
- Risiken, GitHub Pages Kapazitäten (420 Debs = 181,6 MB) und Laufzeiten analysiert.
- Ausführlicher Bericht erstellt in `docs/apt-stages-verification.md`.

