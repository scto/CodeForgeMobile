# Task 25 – Große Pakete (> 100 MB) per Release-Asset installieren

**Anlass:** Der Push auf `gh-pages` von `scto/terminal-packages-codeforge` wurde abgelehnt (`gradle_9.8.0_all.deb` = 142 MB, GitHub-Limit 100 MB je Datei, GH001). Betroffen sind voraussichtlich auch `openjdk-*` und `libllvm` (Größe nicht geprüft).
**Lösung (Workflow-Seite ist fertig, `build-bootstrap-repo.yml`):** Im Modus `add` werden `.deb` über 95 MiB nicht ins Repo übernommen, sondern als Asset am Release `large-packages` des Ziel-Repos veröffentlicht. Der APT-Root enthält `large-packages.txt` mit Zeilen `name|version|arch|datei|sha256|bytes|url`. `apt` kennt diese Pakete deshalb **nicht**; die App muss sie selbst laden.

## Auftrag
1. `libs/terminal-engine/snippets/large-pkg-snippet.sh` in `libs/terminal-engine/src/main/assets/codeforge-env` einbauen, direkt **nach** der Definition von `download()` (benötigt `say`, `err`, `download`, `$TMP`, `$PREFIX`). Das Snippet ist mit Mock-Funktionen getestet (`large-pkg-snippet.test.sh`, drei Fälle: Manifest-Treffer, normales Paket, falsche Prüfsumme).
2. Jede Stelle, die ein Repo-Paket per `pkg install -y NAME` installiert (u. a. Kind `pkg` in `do_install`, Zeilen ~197, 223, 333, 428, 475, 516), auf `install_repo_pkg NAME` umstellen. Pakete mit mehreren Namen in einer Schleife aufrufen. Mit Task 24.5 (`pkg_install`-Hilfsfunktion, sichtbare Fehler) zusammenführen: `pkg_install` ruft `install_repo_pkg` auf.
3. `apt-get`/`pkg` anderswo im Code (Kotlin, z. B. `:feature:settings` Extensions-Manager, `:feature:sdkmanager`) per `grep -rn "pkg install\|apt install"` prüfen und ebenso auf das Manifest umstellen oder im Bericht begründen, warum nicht.
4. Sicherheit: nur `https://github.com/`-URLs akzeptieren, SHA-256 aus dem Manifest prüfen (im Snippet enthalten). Das Manifest selbst ist **nicht** GPG-signiert (es liegt auf demselben Pages-Host wie die signierte Paketliste). Im Bericht als Restrisiko festhalten; optional eine detachte Signatur `large-packages.txt.asc` im Workflow ergänzen und mit `codeforge.gpg` prüfen.
5. Test: Auf dem Gerät `codeforge-env setup` mit einer Komponente, die `gradle` installiert, sobald das Release existiert. Bis dahin nur die Mock-Tests ausführen; „nicht auf Gerät getestet“ ausweisen.
6. `docs/BOOTSTRAP-WORKFLOW.md` und `README` um den Abschnitt „Große Pakete“ ergänzen; `TASKS-STATUS.md` Task 25 eintragen.

## Nicht Teil dieses Tasks
Der erste Lauf, der `gradle` wirklich als Asset hochlädt (Gruppe A, `publish = true`). Ablauf: Workflow-Datei übernehmen, Gruppe A starten, danach prüfen: Release `large-packages` existiert und `large-packages.txt` liegt auf `https://scto.github.io/terminal-packages-codeforge/`.
