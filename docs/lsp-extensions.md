# Language Server Extensions in CodeForge Mobile

Dieses Dokument beschreibt die im Extensions-Manager (`:feature:settings`) verfügbaren Language Server, deren Sprach-Zuordnung, Startbefehle und Voraussetzungen.

---

## 1. Übersicht der Language Server

| ID | Sprache / Dateiendungen | Quelle | Startbefehl | Voraussetzungen |
|---|---|---|---|---|
| `kotlin-language-server` | Kotlin (`.kt`, `.kts`, `build.gradle.kts`) | GitHub Release ZIP | `server/bin/kotlin-language-server` | `openjdk-17` |
| `jdtls` | Java (`.java`) | Eclipse Milestones TAR.GZ | `python3 bin/jdtls` | `openjdk-17`, `python3` |
| `clangd` | C / C++ (`.c`, `.h`, `.cpp`, `.cc`, `.cxx`, `.hpp`, `.hh`) | Termux APT (`clang`) | `clangd` | Termux-Paket `clang` |
| `pyright` | Python (`.py`) | npm Registry TAR.GZ | `node package/langserver.index.js --stdio` | `nodejs` |
| `rust-analyzer` | Rust (`.rs`) | Termux APT (`rust-analyzer`) | `rust-analyzer` | Termux-Paket `rust-analyzer` |
| `vscode-langservers-extracted` | HTML, CSS, JSON (`.html`, `.htm`, `.css`, `.json`) | npm Registry TAR.GZ | `node package/bin/vscode-<lang>-language-server --stdio` | `nodejs` |
| `lemminx` | XML (`.xml`, `.xsd`, `.xsl`, `.xslt`, `.svg`, `.plist`) | Eclipse LemMinX JAR | `java -jar org.eclipse.lemminx-uber.jar` | `openjdk-17` |
| `yaml-language-server` | YAML (`.yaml`, `.yml`) | npm Registry TAR.GZ | `node package/bin/yaml-language-server --stdio` | `nodejs`, npm (`npm install --omit=dev`) |
| `taplo` | TOML (`.toml`, `Cargo.toml`, `pyproject.toml`) | Termux APT (`taplo`) | `taplo lsp stdio` | Termux-Paket `taplo` |
| `bash-language-server` | Shell / Bash (`.sh`, `.bash`, `.zsh`, Shebangs) | npm Registry TAR.GZ | `node package/out/cli.js start` | `nodejs`, npm (`npm install --omit=dev`), optional `shellcheck` |

---

## 2. Installation & Abhängigkeiten

- **Download-Archive:** Werden über `ExtensionsManager` in den privaten App-Speicher (`filesDir/extensions/<id>`) geladen und anhand ihrer SHA-256 verifiziert.
- **npm-Server (YAML, Bash):** Da `yaml-language-server` und `bash-language-server` im npm-Tarball ungebündelte Laufzeit-Abhängigkeiten haben, führt der `ExtensionsManager` nach dem Entpacken automatisch ein `npm install --omit=dev` im Erweiterungsverzeichnis aus.
- **Termux-Pakete (`clangd`, `rust-analyzer`, `taplo`):** Werden nativ über das Termux-Paketverwaltungssystem (`pkg install <name>`) bereitgestellt.
- **Optionale Tools:** `shellcheck` ist für `bash-language-server` optional; ohne `shellcheck` startet der Server normal, bietet jedoch keine linter-gestützten Diagnosen.

---

## 3. Bekannte Grenzen

- **Kein Groovy-LSP:** Für traditionelle Gradle-Dateien (`*.gradle`) existiert kein stabiler und gepflegter Language Server. Diese Dateien werden ohne LSP geöffnet (Syntax-Highlighting über Sora-Editor / Tree-sitter bleibt aktiv). Für `*.gradle.kts` steht der vollständige Kotlin Language Server zur Verfügung.
- **Multi-Session-Verhalten:** Seit Task 44 können bis zu 3 Server gleichzeitig laufen (`maxConcurrentSessions`, konfigurierbar 1–10). Ältere Sitzungen werden nach LRU verdrängt, sobald das Limit erreicht ist.
