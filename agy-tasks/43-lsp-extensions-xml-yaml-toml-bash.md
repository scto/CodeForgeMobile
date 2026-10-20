# Task 43 – LSP-Erweiterungen: XML, YAML, TOML, Bash

**Ziel:** Im Extensions-Manager (`:feature:settings`) vier weitere Language Server anbieten, installieren und beim Öffnen passender Dateien im Editor starten: **XML** (LemMinX), **YAML** (`yaml-language-server`), **TOML** (`taplo`), **Bash** (`bash-language-server`).
**Bewusst nicht Teil dieses Tasks:** Ein Server für Groovy-Gradle (`*.gradle`). Der verfügbare `groovy-language-server` ist kaum gepflegt, `.gradle`-Dateien bleiben ohne LSP (Tree-sitter-Highlighting bleibt). `*.gradle.kts` läuft schon über den Kotlin-Server. JSON hat bereits einen Server (`vscode-langservers-extracted`, Eintrag `htmlCssJson`); dort ist nur zu prüfen, dass `json` korrekt angebunden bleibt.

**Gemeinsame Regeln (gelten für jeden Task):**
* Clean Architecture wie im Rest des Repos: Schnittstellen und Modelle in `:core:domain`, Implementierung in `:libs:*` bzw. im Feature-Modul, UI in `:feature:*` (MVVM/MVI mit Hilt, `@Immutable`-UI-States, `StateFlow`; Material 3; `modifier: Modifier = Modifier` als erster optionaler Parameter; keine Logik in Composables). Gradle Kotlin DSL, Versionen im Version Catalog, Convention-Plugins aus `build-logic`.
* Alle UI-Texte über `:core:resources` (Englisch Standard, Deutsch in `values-de`); der String-Check (Task 17) bleibt grün.
* Nichts erfinden: Was du nicht selbst ausgeführt hast, steht im Bericht unter **nicht getestet** (insbesondere Gerätetests; Zielgerät des Inhabers: Pixel 9 Pro XL, Android 17). Aussagen dieses Tasks über den Bestand stammen aus Stichwortsuchen; prüfe sie vor dem Start selbst und korrigiere sie im Bericht.
* Prüfsummen, Versionen und Download-URLs **nicht aus dem Gedächtnis eintragen**: Artefakt herunterladen, SHA-256 selbst berechnen, Datum der Prüfung im KDoc-Kommentar festhalten (wie bei den bestehenden Einträgen).
* Geheimnisse nie ins Log, nie in Dateien im Projektordner, nie in Berichte.
* Bericht in `docs/lsp-extensions-xml-yaml-toml-bash-report.md` mit Abschnitten **getestet / nicht getestet / offene Punkte**; `agy-tasks/TASKS-STATUS.md` und `agy-tasks/README.md` ergänzen (Commit-Hash eintragen).
* Build grün: `./gradlew :app:assembleDebug` und `./gradlew :feature:settings:testDebugUnitTest :feature:editor:testDebugUnitTest`.

## 43.1 Ausgangslage (erst selbst prüfen)

* `feature/settings/.../extensions/LspExtensionPackage.kt`: `LspExtensionPackage`, `ExtensionSource` (`Download(url, sha256, archiveType)` und `TermuxPackage(packageName, binaryName)`), `ExtensionArchiveType` (`ZIP`, `TAR_GZ`, `GZ`, `JAR`, `BINARY`), `LspExtensionCatalog` mit `ALL` (Kotlin, jdtls, clangd, pyright, rust-analyzer, `htmlCssJson`).
* `feature/settings/.../extensions/LspServerResolverImpl.kt`: ein `when` über die Paket-`id` baut den Startbefehl; eine zweite Zuordnung ordnet `languageId` einem Katalogeintrag zu (`"html","css","json" -> htmlCssJson` usw.).
* `feature/editor/.../EditorViewModel.kt`, Funktion `languageIdFor(path)`: kennt `kotlin`, `java`, `xml`, `json`, `python`, `c`, `cpp`, `rust`, `html`, `css`, sonst `plaintext`. **YAML, TOML und Shell fehlen**, `xml` hat keinen Katalogeintrag.
* `feature/editor/.../treesitter/TreeSitterLanguageSupport.kt` kennt bereits `XML`, `BASH`, `TOML`, `YAML` (nur Highlighting).
* `LspSessionController` hält **einen** aktiven Server und beendet ihn beim Wechsel der Sprache oder des Workspaces. Das bleibt in diesem Task unverändert; erwähne im Bericht, wenn der Wechsel zwischen z. B. Kotlin- und YAML-Tabs dadurch spürbar Neustarts auslöst (Verbesserung wäre ein eigener Task).
* Task 23 (`docs/extensions-manager-fix-report.md`) beschreibt, wie der Extensions-Manager Pakete installiert. **Lies ihn zuerst**, besonders, wie `pyright` und `vscode-langservers-extracted` (npm-Tarballs) ohne `npm install` laufen.
* APT-Gruppen im Repo: `taplo` steht in `apt-stages.conf` Stage `12-lsp-misc`, `shellcheck` in Stage `14-ide-tools`. Ob beide Pakete im veröffentlichten APT-Repo liegen, ist **nicht** bestätigt (Läufe der Gruppen stehen aus, Stand 2026-10-20).

## 43.2 Die vier Server

| id | Sprache / Endungen | Quelle | Start | Voraussetzungen |
|---|---|---|---|---|
| `lemminx` | XML: `xml`, `xsd`, `xsl`, `xslt`, `svg`, `plist` (Android-Layouts und Manifest eingeschlossen) | Download, **Uber-JAR** (`ExtensionArchiveType.JAR`) von Eclipse LemMinX | `java -jar <jar>` | `openjdk-17` |
| `yaml-language-server` | `yaml`, `yml` | Download, npm-Tarball (Red Hat) | `node <entry> --stdio` | `nodejs` |
| `taplo` | `toml` | `TermuxPackage` (`taplo`, Binary `taplo`) | `taplo lsp stdio` | APT-Paket `taplo` |
| `bash-language-server` | `sh`, `bash`, `zsh` (und Dateien mit `#!/bin/bash`/`#!/bin/sh`-Shebang, wenn der Editor Shebangs schon auswertet; sonst nur Endungen) | Download, npm-Tarball | `node <entry> start` | `nodejs`; **optional** `shellcheck` für Diagnosen |

**Zu prüfen und im Bericht zu belegen, vor dem Eintragen:**

* **LemMinX:** Die vorgefertigten Native-Binaries sind nach meinem Kenntnisstand für Linux x86_64 gebaut und laufen nicht auf Android aarch64 (nicht verifiziert). Nimm das JAR, prüfe, wo es offiziell bezogen wird (Eclipse Maven-Repository bzw. Release-Seite des Projekts), und notiere die exakte URL. Prüfe, ob der Extensions-Manager `JAR` ohne Entpacken ablegt und der Resolver den Pfad korrekt in `launchCommand` einsetzt (so wie bei den anderen Download-Einträgen).
* **npm-Server (YAML, Bash):** Prüfe in der `package.json` des Tarballs, ob die **Laufzeit-Abhängigkeiten gebündelt** sind. `pyright` ist es; bei `yaml-language-server` und `bash-language-server` ist das **nicht sicher**. Sind sie nicht gebündelt, braucht der Server ein `npm install --omit=dev` im Erweiterungsordner (Termux-`npm`, Netzwerk nötig) oder eine andere Lieferform. Entscheide nach dem, was Task 23 für den Extensions-Manager vorsieht, und dokumentiere die Entscheidung. Erfinde keinen Installationsweg, den der Manager nicht kann: Braucht es einen neuen Schritt „Abhängigkeiten installieren“, baue ihn mit Fortschritt und Fehlerbehandlung wie die anderen Schritte (`DOWNLOADING → VERIFYING → EXTRACTING → INSTALLED/ERROR`).
* **taplo:** Der Befehl `taplo lsp stdio` gibt es nur, wenn das Paket mit dem Cargo-Feature `lsp` gebaut wurde. Prüfe `packages/taplo/build.sh` im APT-Fork (`terminal-packages-codeforge`) auf `--features lsp` (bzw. gleichwertig). Fehlt es, ist das ein Befund für den Bericht und für die Paket-Rezeptur, nicht stillschweigend zu umgehen. Prüfe außerdem, ob `taplo` im veröffentlichten APT-Repo liegt (`apt policy taplo` bzw. Verzeichnis `pool/main/t/taplo/` im `gh-pages`-Branch von `scto/terminal-packages-codeforge`).
* **bash-language-server:** Starten mit dem Unterbefehl `start` (nicht `--stdio`). Prüfe das in der README des Pakets. `shellcheck` ist optional: Ist es nicht installiert, muss der Server trotzdem laufen (dann ohne Diagnosen); der Manager zeigt einen Hinweis „shellcheck nicht installiert“, installiert es aber nicht automatisch.
* **Versionen:** Immer die zum Zeitpunkt der Umsetzung aktuelle stabile Version nehmen, nicht die aus diesem Text, und prüfen, dass sie mit der vorhandenen Node- und JDK-Version in Termux läuft (`node --version`, `java -version`).

## 43.3 Umsetzung

1. **Katalog:** Je ein Eintrag in `LspExtensionCatalog` im Stil der bestehenden (KDoc mit Quelle, Prüfdatum, SHA-256, Voraussetzungen), und in `ALL` aufnehmen. Strings `extension_<name>_name` und `extension_<name>_desc` in `:core:resources` (en und de).
2. **Resolver:** `LspServerResolverImpl` um die vier ids ergänzen (Startbefehl wie in 43.2, Node- und Java-Pfade wie bei den bestehenden Einträgen aus `TermuxEnvironment.prefix` ermitteln, nicht fest verdrahten) und die `languageId`-Zuordnung erweitern: `xml → lemminx`, `yaml → yaml-language-server`, `toml → taplo`, `shellscript` bzw. `bash → bash-language-server`. Nutze dieselben Sprach-IDs wie die LSP-Spezifikation (`xml`, `yaml`, `toml`, `shellscript`), sofern der Rest des Codes das zulässt; sonst konsequent die interne ID verwenden und im Bericht begründen.
3. **Editor:** `languageIdFor` um `yaml/yml`, `toml`, `sh/bash/zsh` (und die XML-Endungen aus 43.2) ergänzen; das `when` über `EditorLanguageType` muss weiter vollständig bleiben.
4. **Extensions-Manager-UI:** Neue Einträge erscheinen automatisch über `ALL`. Prüfe Abhängigkeitsanzeige (`requires`), Statusanzeige und Fehlertexte; neue Voraussetzungen (z. B. `taplo`, optional `shellcheck`) müssen sinnvoll angezeigt werden.
5. **Dokumentation:** Kurzer Abschnitt in `docs/` (z. B. `docs/lsp-extensions.md`, falls vorhanden ergänzen): welche Sprache welchen Server nutzt, Voraussetzungen, bekannte Grenzen (kein Groovy-Server; einer aktiv gleichzeitig).

## 43.4 Tests (Pflicht, JVM)

* **Katalog:** Alle Einträge haben nicht-leere Felder; `launchCommand` enthält den Binary- bzw. Entry-Namen; `findById` für die vier neuen ids; die SHA-256 ist 64 Hex-Zeichen und **ungleich Platzhalter**; `requires` passt zur Quelle.
* **Resolver:** Für jede neue `languageId` kommt der erwartete Befehl heraus (mit Fake-`TermuxEnvironment`/Temp-Verzeichnis); unbekannte oder nicht installierte Erweiterung liefert `null` (kein Absturz).
* **`languageIdFor`:** Tabelle der Endungen (`.xml`, `.xsd`, `.yaml`, `.yml`, `.toml`, `.sh`, `.bash`, `.zsh`, `.gradle` → kein Server) und der bisherigen Endungen (keine Regression).
* **Installation:** Der Manager-Test für Download und Verifizieren mit lokalem Testserver oder Fixture-Datei, falls das im bestehenden `ExtensionsManagerTest` schon so gelöst ist; Fehlerfall „falsche Prüfsumme“ bleibt abgedeckt.
* **Nicht in JVM-Tests prüfbar** und im Bericht entsprechend auszuweisen (nicht getestet, falls nicht auf dem Gerät erprobt): echter Start der vier Server in Termux, Antworten auf `initialize`, Vervollständigung/Diagnosen im Editor, Verhalten auf dem Pixel 9 Pro XL.

## Abnahme

Build und Tests grün; Bericht mit (1) belegten Quellen und Prüfsummen mit Prüfdatum, (2) der Entscheidung zur Lieferform der npm-Server (Abhängigkeiten gebündelt oder nachinstalliert), (3) dem Befund zu `taplo lsp` und zur Verfügbarkeit von `taplo` im APT-Repo, (4) ehrlicher Aussage, welche Server auf dem Gerät gestartet wurden (sonst „nicht getestet“).
