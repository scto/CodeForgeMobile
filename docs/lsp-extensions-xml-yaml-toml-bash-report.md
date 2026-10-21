# LSP-Erweiterungen (XML, YAML, TOML, Bash) – Bericht zu Task 43

> **Datum:** 21. Oktober 2026  
> **Status:** Erfolgreich implementiert & verifiziert  
> **Module:** `:feature:settings`, `:feature:editor`, `:core:resources`  

---

## 1. Belegte Quellen, Versionen & Prüfsummen

Alle Versionen und Prüfsummen wurden am **21. Oktober 2026** direkt von den offiziellen Upstream-Quellen bezogen, heruntergeladen und mittels `sha256sum` verifiziert:

| ID | Version | Quelle (URL) | Archivtyp | SHA-256 (Prüfdatum 2026-10-21) |
|---|---|---|---|---|
| **lemminx** | 0.31.2 | `https://download.eclipse.org/lemminx/releases/0.31.2/org.eclipse.lemminx-uber.jar` | `JAR` | `f4fde164e785c635e5f86361dbf4b993bfe2c5f83fdb52891d058b7dfc9bcfc8` |
| **yaml-language-server** | 2.0.0 | `https://registry.npmjs.org/yaml-language-server/-/yaml-language-server-2.0.0.tgz` | `TAR_GZ` | `8dcd68cc8bcdde719def62e651de9744576d3227de2e4ab85961603c92a52423` |
| **taplo** | 0.10.0 | Termux APT (`taplo`) | `TermuxPackage` | Native Binary `$PREFIX/bin/taplo` |
| **bash-language-server** | 5.8.1 | `https://registry.npmjs.org/bash-language-server/-/bash-language-server-5.8.1.tgz` | `TAR_GZ` | `2ae910de8c50f4ec148222bdc580c121f2c621bdacc1225716218ca700f8ec79` |

- **npm dist.integrity:**
  - `yaml-language-server` 2.0.0: `sha512-vT6ZEMTkXJA9Ixo07ByLgPkkt/L22fJdVDGAoB3EpPq3DCfKkMYeEeZdK5ECU6alwFUofhETo94/OV8GUxGuBg==`
  - `bash-language-server` 5.8.1: `sha512-F3g0hKZhCiKbLUf9wkrST6+ZypsHvJqaJxw/+J7WK2ywhFyc8+7jSHSTU1UDYncZkyWgbKV6+eZy5qWTZtoXww==`

---

## 2. Entscheidung zur Lieferform der npm-Server (Abhängigkeiten)

Die Untersuchung der npm-Tarballs ergab:
- **`yaml-language-server` 2.0.0:** Enthält keinen `node_modules`-Ordner. Ein Aufruf von `node package/out/src/server.js` scheiterte unmittelbar mit `Cannot find package 'vscode-languageserver'`.
- **`bash-language-server` 5.8.1:** Enthält ebenfalls keinen `node_modules`-Ordner. Ein Aufruf scheiterte mit `Cannot find module 'vscode-languageserver/node'`.

### Entscheidung & Umsetzung:
In Übereinstimmung mit den Vorgaben aus Task 43.2 wurde ein neuer Installationsstatus `ExtensionStatus.INSTALLING_DEPENDENCIES` im `ExtensionsManager` eingeführt.
1. Nach dem erfolgreichen Download, der SHA-256-Prüfung und dem Entpacken prüft `ExtensionsManager.install(pkg)` das Flag `pkg.needsNpmInstall`.
2. Ist `needsNpmInstall == true`, signalisiert der Manager den Zustand `INSTALLING_DEPENDENCIES` an die UI.
3. Im Paketverzeichnis (`File(extensionDir, "package")`) wird `npm install --omit=dev` via `ProcessBuilder` unter Verwendung von `$PREFIX/bin/npm` ausgeführt.
4. Schlägt `npm install` fehl (z. B. fehlendes Netzwerk oder Termux-`npm`), wird das Verzeichnis sauber aufgeräumt und der Installationsschritt bricht kontrolliert mit einer verständlichen Fehlermeldung ab.
5. In Unit-Tests wird `customNpmRunner` injectet, sodass Tests offline und deterministisch ohne Netzwerkzugriff ablaufen.

---

## 3. Befund zu `taplo lsp` und Verfügbarkeit im APT-Repository

1. **Verfügbarkeit im APT-Repo:**
   - Eine Überprüfung mittels `apt-cache policy taplo` zeigte, dass `taplo` in Version `0.10.0-2` im offiziellen Termux-Haupt-Repository (`https://is.mirror.flokinet.net/termux/termux-main stable/main aarch64`) vorliegt.
2. **Funktionsumfang `taplo lsp`:**
   - Das `.deb`-Paket wurde heruntergeladen und entpackt.
   - Der Befehlstest `taplo lsp --help` bestätigte, dass das Cargo-Feature `lsp` aktiv einkompiliert ist:
     ```
     Commands:
       tcp    Run the language server and listen on a TCP address
       stdio  Run the language server over the standard input and output
     ```
   - Der Startbefehl `taplo lsp stdio` funktioniert direkt ohne zusätzliche Parameter.
3. **Einbindung:**
   - `taplo` wird im Katalog als `ExtensionSource.TermuxPackage(packageName = "taplo", binaryName = "taplo")` geführt.

---

## 4. Getestet vs. Nicht getestet

### In JVM-Tests und Termux CLI verifiziert:
- [x] **Katalog-Integrität (`LspExtensionCatalogTest`):** Alle 10 Pakete (inklusive der 4 neuen) verfügen über eindeutige IDs, korrekte Lokalisierungs-Ressourcen, valide Versionen, SHA-256-Prüfsummen und passende `launchCommand`-Signaturen.
- [x] **Resolver-Zuordnung (`LspServerResolverTest`):**
  - `xml` -> `lemminx` (`java -jar .../org.eclipse.lemminx-uber.jar`)
  - `yaml` & `yml` -> `yaml-language-server` (`node .../yaml-language-server --stdio`)
  - `toml` -> `taplo` (`.../taplo lsp stdio`)
  - `shellscript`, `bash` & `sh` -> `bash-language-server` (`node .../cli.js start`)
  - Unbekannte oder nicht installierte Erweiterungen liefern sauber `null`.
- [x] **Sprach-Erkennung (`EditorLanguageIdTest`):**
  - Vollständige Endungs-Tabelle: `.xml`, `.xsd`, `.xsl`, `.xslt`, `.svg`, `.plist`, `.yaml`, `.yml`, `.toml`, `.sh`, `.bash`, `.zsh`.
  - `.gradle` wird ohne LSP als `plaintext` geöffnet (keine Regression).
  - Skripte ohne Endung mit Shebang (`#!/bin/bash`, `#!/bin/sh`, `#!/usr/bin/env bash`) werden als `shellscript` identifiziert.
- [x] **Manager-Lifecycle & Post-Install (`ExtensionsManagerTest`):**
  - JAR-Archiv-Installation (`lemminx`) ohne Entpacken.
  - `needsNpmInstall`-Workflow inklusive Status-Übergang zu `INSTALLING_DEPENDENCIES` und Fehlerbereinigung.
  - Termux-Paketerkennung für `taplo`.
  - Optionale Anforderung `shellcheck` wird korrekt als `missingOptionalRequirements` erfasst.
- [x] **Manuelle CLI-Startprobe in Termux:**
  - `lemminx` 0.31.2 startet erfolgreich mit OpenJDK 17 (`java -jar ...`).
  - `taplo` 0.10.0-2 startet den LSP-Server via `taplo lsp stdio`.
  - `yaml-language-server` 2.0.0 läuft nach `npm install --omit=dev` fehlerfrei unter Node.js v26.4.0.
  - `bash-language-server` 5.8.1 läuft nach `npm install --omit=dev` fehlerfrei unter Node.js v26.4.0.
- [x] **String-Check & Builds:**
  - `python3 scripts/find_hardcoded_strings.py` bestätigt 0 neue unlokalisierte Strings.
  - `./gradlew :feature:settings:testDebugUnitTest` (Grün).
  - `./gradlew :feature:editor:testDebugUnitTest` (Grün).
  - `./gradlew :app:assembleDebug` (Grün).

### Nicht getestet (auf physischem Android-Zielgerät / Pixel 9 Pro XL):
- Echte interaktive Code-Vervollständigung und Diagnosen in der Editor-UI auf einem physischen Android-Gerät mit geladenem Android-Layout (`res/layout/*.xml`), komplexem Helm/Kubernetes-YAML oder Cargo.toml.
- Speicherverbrauch und Reaktionszeit des `lemminx`-Java-Prozesses bei großen XML-Schemas auf Android.
