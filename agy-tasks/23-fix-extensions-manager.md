# Task 23 – Extensions-Manager reparieren, an den LSP-Client anbinden, Repo aufräumen

**Auslöser:** Prüfung des Stands `adb101f` (alle Tasks 01–22 abgearbeitet, Build grün). Task 16 Punkt 4 (Extensions-Manager, `feature/settings/src/main/kotlin/com/codeforge/feature/settings/extensions/`) ist nur zur Hälfte brauchbar. Arbeitsverzeichnis = Projekt-Root. Relevante Dateien: `LspExtensionPackage.kt`, `ExtensionsManager.kt`, `ArchiveUtils.kt`, `libs/lsp-client/…/LspClientRepositoryImpl.kt`, `core/domain/…/LspClientRepository.kt`, `feature/editor/…/lsp/`.

## Befunde (belegt)

1. **SHA-256 im Katalog falsch.** Beim Nachrechnen der Original-Downloads (2026-10-16) ergaben sich andere Werte; die Installation würde immer mit „SHA-256 mismatch" abbrechen:
   | Paket | im Katalog | gemessen |
   |---|---|---|
   | kotlin-language-server 1.3.11 `server.zip` | `c08e8df5e8c1…` | `c65efea1c09d88dd…004dcd` |
   | clangd 18.1.3 `clangd-linux-18.1.3.zip` | `1d6c818fa8f8…` | `775f6ac798d9c599…a1be6` |
   | rust-analyzer 2024-08-19 `…aarch64-unknown-linux-gnu.gz` | `8f307ebc31bc…` | `793226b880c5d815…3a1e5` |
   Die Werte für jdtls, pyright und vscode-langservers-extracted ließen sich nicht prüfen (Host nicht erreichbar) und gelten als unbestätigt. **Die Messwerte oben nicht übernehmen, sondern selbst neu berechnen.**
2. **Archivtypen falsch.** `jdtls` (`.tar.gz`), `pyright` und `vscode-langservers-extracted` (`.tgz`) sind als `ZIP` eingetragen; `ArchiveUtils` kennt nur `ZipInputStream`. `rust-analyzer` ist eine einzelne `.gz`-Datei, wird aber als `BINARY` nur kopiert (nicht entpackt, nicht lauffähig).
3. **Falsche Plattform.** `clangd-linux-*.zip` (x86_64) und `rust-analyzer-aarch64-unknown-linux-gnu` (glibc) laufen unter Android/Termux (bionic) nicht.
4. **Kein Aufrufer.** `LspClientRepository.start(...)` wird im gesamten Projekt nirgends aufgerufen; installierte Extensions werden vom Editor nicht genutzt. Die KDoc in `LspClientRepositoryImpl` verweist noch auf PRoot.
5. Die vorhandenen Unit-Tests laufen mit Testdaten und fangen 1–3 nicht ab.

## Aufgaben

### A. Katalog korrigieren (`LspExtensionPackage.kt`)
1. Für jeden Eintrag Quelle, Version und Asset-Namen **gegen die Upstream-Release-Seite prüfen** (`curl -fsSLI <url>`; bei 404 aktuelle Version/URL suchen). Sicherheits- und Funktionsprüfung: nichts erfinden; wenn ein Paket nicht sauber lösbar ist, aus dem Katalog nehmen und im Bericht begründen.
2. SHA-256 **aus dem tatsächlichen Download berechnen** (`sha256sum`), bei npm-Paketen zusätzlich `dist.integrity` (sha512) der Registry gegenprüfen. Prüfsumme, Quelle und Datum je Eintrag als Kommentar/KDoc festhalten.
3. Plattform: Nur Binaries für Android/bionic oder plattformunabhängige Pakete (Jar/JS) direkt laden. Für **clangd** (Teil des Termux-Pakets `clang`) und **rust-analyzer** zuerst prüfen, ob ein Termux-Paket (im CodeForge-APT-Repo, siehe `apt-stages.conf`/`docs/apt-stages-verification.md`) existiert; wenn ja, neuen Typ `ExtensionSource.TERMUX_PACKAGE(name)` einführen (Erkennung über `$PREFIX/bin/<exe>`, in der UI Hinweis „`pkg install <name>` im Terminal" statt Download). Wenn nein, aus dem Katalog nehmen.
4. Laufzeitvoraussetzungen als Feld `requires` (z. B. `openjdk-17` für Kotlin-LS/jdtls, `nodejs` für pyright und vscode-langservers) ergänzen; die UI zeigt „Voraussetzung fehlt", wenn `$PREFIX/bin/java` bzw. `node` nicht vorhanden ist (Texte zweisprachig, siehe Abschnitt E).
5. Startkommando als Katalogfeld `launchCommand: List<String>` (Pfade relativ zum Extension-Verzeichnis, `node`/`java` aus dem Termux-Prefix) statt nur `executableRelativePath`; Einstiegspunkte nach dem Entpacken im echten Archiv prüfen (z. B. `pyright-langserver`, `vscode-html-language-server`, jdtls-Launcher).

### B. Entpacken reparieren (`ArchiveUtils.kt`, `ExtensionsManager.kt`)
1. `ExtensionArchiveType` um `TAR_GZ` und `GZ` erweitern; `TAR_GZ` per `GZIPInputStream` + Tar-Reader (vorhandene Abhängigkeit prüfen, sonst kleinen ustar-Reader schreiben statt neue Library einzuführen), `GZ` per `GZIPInputStream` in Zieldatei. Beides mit denselben Schutzmaßnahmen wie Zip (Zip-Slip/Pfad-Traversal, absolute Pfade, Symlink-Ziele außerhalb des Zielordners ablehnen) und Erhalt des Ausführungsbits (Tar-Mode bzw. Heuristik wie bisher).
2. Fehlerfall: halb entpackte Verzeichnisse aufräumen; Download-Archiv nach Erfolg löschen.
3. `FileDownloader`: Redirects (GitHub-Releases) und Resume nicht nötig; Timeout und `Content-Length`-Abgleich prüfen.

### C. Anbindung an den Editor (Architektur: keine Feature→Feature-Abhängigkeit)
1. In `:core:domain` ein Interface `LspServerResolver` (`suspend fun resolve(languageId: String): LspServerLaunch?`; `LspServerLaunch(command: List<String>, env: Map<String,String>)`) anlegen. Implementierung in `:feature:settings` (nutzt `ExtensionsManager`, `TermuxEnvironment` für `PATH`/`JAVA_HOME`/`ANDROID_HOME`, vgl. `$PREFIX/etc/profile.d/codeforge-android.sh`), per Hilt in `:app` gebunden.
2. In `:feature:editor/lsp/` einen `LspSessionController` (Hilt) anlegen: Beim Öffnen einer Datei die Sprache bestimmen (`EditorLanguageFactory`), über `LspServerResolver` das Startkommando holen, `LspClientRepository.start(command, workspaceRoot, env)` **einmal pro Workspace und Sprache** aufrufen, bei Projektwechsel/Schließen `stop()`; Zustand (`serverState`) in der Statusleiste des Editors anzeigen; Diagnosen und Completion laufen bereits über `LspDiagnosticsBridge`/`LspCompletionProvider` – prüfen, dass sie nach `start` tatsächlich Daten erhalten.
3. Fehler (Server fehlt, Start schlägt fehl, Timeout) nie crashen lassen: Zustand `ERROR`, Snackbar mit Hinweis auf „Einstellungen → Extensions".
4. KDoc von `LspClientRepositoryImpl` (PRoot-TODO) auf den Termux-Pfad aktualisieren.
5. Ohne installierte Extension verhält sich der Editor wie bisher (kein Server, TextMate/Tree-sitter).

### D. Tests
* `ArchiveUtilsTest`: ZIP, TAR_GZ, GZ mit erzeugten Fixtures; Pfad-Traversal und Symlink-Ausbruch werden abgelehnt; Ausführungsbit bleibt.
* `LspExtensionCatalogTest` (neu, offline): Jeder Eintrag hat 64-stelligen Hex-SHA-256, `https`-URL, Archivtyp passend zur URL-Endung (`.zip`/`.tar.gz`/`.tgz`/`.gz`), nicht-leeren `launchCommand`, eindeutige `id`.
* `ExtensionsManagerTest`: Install mit gefaktem Downloader pro Archivtyp, Mismatch-Fall löscht das Archiv.
* `LspSessionControllerTest`: Resolver liefert null → kein Start; mit Kommando → genau ein `start`; Wechsel → `stop`.
* Skript `scripts/verify-extension-catalog.sh` (nicht in CI): lädt jeden Katalogeintrag, vergleicht SHA-256 und gibt je Eintrag OK/FEHLER aus; Ergebnis im Bericht festhalten.

### E. Texte
Neue Strings in `core/resources/src/main/res/values/strings.xml` (Englisch) **und** `values-de/strings.xml` (Deutsch), identische Schlüssel/Platzhalter. Prüfen mit `python3 scripts/check_string_locales.py` und `python3 scripts/find_hardcoded_strings.py --check` (je Exit 0).

### F. Repo aufräumen
1. `plan-apt-build.sh` im Repo-Root ist byte-identisch mit `.github/scripts/plan-apt-build.sh`: Verweise suchen (`grep -rn plan-apt-build`), den Root-Duplikat entfernen und Verweise auf `.github/scripts/` umstellen.
2. `mirror-integration.patch` ist eingecheckt, obwohl `*.patch` in `.gitignore` steht: `git rm --cached mirror-integration.patch` (Datei nur löschen, wenn nichts darauf verweist).
3. Stringanzahl in Doku vereinheitlichen auf den tatsächlichen Wert (aktuell **825** je Sprache): `docs/resources-and-strings.md` (816), `docs/restore-lost-editor-features.md` (818), `agy-tasks/17-externalize-hardcoded-strings.md`-Banner (816), `agy-tasks/TASKS-STATUS.md` (818). Nach dieser Task den Wert neu mit `check_string_locales.py` ermitteln.
4. `docs/…`-Verweise auf `values-en` entfernen (`docs/project-wizard-and-template-engine.md`, `agy-tasks/13-verify-resources.md`, `STATUS.md` Punkt 8).
5. `STATUS.md`, `agy-tasks/README.md`, `agy-tasks/TASKS-STATUS.md` und `libs/terminal-engine/BOOTSTRAP.md` wurden vom Inhaber bereits aktualisiert bereitgestellt – **nicht überschreiben**, nur den Eintrag zu Task 23 auf ✅ setzen und Zahlen angleichen.
6. Hinweis (nicht in diesem Repo erledigbar): Die Paketrezepte `flutter`, `typescript`, `jdtls`, `lemminx`, `kotlin-lsp` unter `agy-output/packages/` gehören in den Fork `scto/terminal-packages-codeforge` (`packages/`) und müssen dort committet werden – im Bericht als offenen Punkt für den Inhaber nennen.

## Grenzen

* Keine Prüfsummen raten oder aus dem Gedächtnis eintragen; jeder Wert muss aus einem realen Download stammen. Nicht auffindbare Pakete aus dem Katalog nehmen.
* Keine Feature→Feature-Abhängigkeiten; Zustände immutable; `modifier` als erster optionaler Parameter; keine harten Strings.
* Kein Push, keine Releases, kein Eingriff in GitHub-Einstellungen.
* `targetSdk = 28` bleibt unverändert.

## Akzeptanz

* `./gradlew :feature:settings:testDebugUnitTest :feature:editor:testDebugUnitTest :libs:lsp-client:testDebugUnitTest assembleDebug` grün.
* `scripts/verify-extension-catalog.sh` meldet für alle verbleibenden Download-Einträge OK (Ausgabe im Bericht).
* Kotlin-Datei öffnen mit installierter Extension: Server startet (`serverState` RUNNING), mindestens Diagnosen oder Completion treffen im Editor ein (manuell, Ergebnis im Bericht oder als „ungeprüft" markieren).
* Bericht `docs/extensions-manager-fix-report.md`: je Katalogeintrag alt/neu, entfernte Einträge mit Begründung, Architektur der Anbindung, offene Punkte.
* `CHANGELOG.md` (`[Unreleased]`) und `STATUS.md` (Task-16-Punkt 4, LSP-Zeile) ergänzt.
