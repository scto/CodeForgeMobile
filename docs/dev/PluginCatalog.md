# Plugin-Liste

Alle 147 Plugins, die das Repository baut (erzeugt von `store-site/catalog_doc.py`; nicht von Hand ändern).
Deklarative Plugins gibt es im [Store](../store.md), Code-Plugins als Sideload-ZIP. Kategorien und Tags wie im Store.

## Code-Plugins (API 4, nur Sideload) (7)

Quelle: `code-plugins/`

| Name | ID | Version | Kategorien | Beschreibung |
|---|---|---|---|---|
| Daten-Betrachter (CSV, TSV, JSON) | `com.codeforge.plugin.data-viewer.code` | 1.0.0 | Vorschauen › Daten, Sprachen › Daten und Konfiguration | Zeigt CSV/TSV als sortierbare Tabelle und JSON als aufklappbaren Baum – direkt aus dem Editor-Inhalt. |
| Gradle-Aufgaben | `com.codeforge.plugin.gradle-tasks.code` | 1.0.0 | Build › Gradle, Sprachen › JVM › Kotlin | Bereich mit allen Modulen des Projekts und ihren üblichen Gradle-Aufgaben (assemble, test, lint, clean, dependencies) plus freier Eingabe … |
| Markdown-Vorschau | `com.codeforge.plugin.markdown-preview.code` | 1.0.0 | Vorschauen › Markdown, Sprachen › Auszeichnung | Vorschau für Markdown-Dateien (Überschriften, Listen, Aufgaben, Code, Zitate, Tabellen, Links) direkt aus dem Editor-Inhalt. |
| Projekt-Skripte (npm, Make, Cargo, Go, just) | `com.codeforge.plugin.script-tasks.code` | 1.0.0 | Build › Aufgaben, Sprachen › Web, Sprachen › Systemnah › Rust | Findet Aufgaben im geöffneten Projekt – npm/pnpm/yarn-Skripte aus package.json, Makefile-Ziele, Cargo, Go-Module und justfile-Rezepte – u … |
| Python-Tests (pytest) | `com.codeforge.plugin.python-tests.code` | 1.0.0 | Tests › Python, Sprachen › Python | Erkennt pytest-Tests (test_*-Funktionen, Test*-Klassen) in test_*.py und *_test.py, zeigt Start-Knöpfe an jeder Testzeile und meldet best … |
| REST-Client (.http) | `com.codeforge.plugin.rest-client.code` | 1.0.0 | Werkzeuge › HTTP/APIs, Sprachen › Web | Sendet HTTP-Anfragen aus .http/.rest-Dateien (Format wie REST Client für VS Code: ### als Trenner, @variable = wert, {{variable}}) und ze … |
| SQLite-Betrachter | `com.codeforge.plugin.sqlite-viewer.code` | 1.0.0 | Werkzeuge › Datenbanken, Vorschauen › Daten | Findet SQLite-Datenbanken im Projekt (.db, .sqlite, .sqlite3), zeigt Tabellen, Schema und Zeilen und führt lesende Abfragen aus (SELECT, … |

## Bundles und Sprachpakete (14)

Quelle: `tool-plugins/`, `content-plugins/`

| Name | ID | Version | Kategorien | Beschreibung |
|---|---|---|---|---|
| Alle Formatierer (Bundle) | `com.codeforge.plugin.all-formatters.bundle` | 1.0.0 | Bundles, Bundles › Sammlungen | Alle Formatierer von CodeForge als Bundle. Die Programme selbst müssen installiert werden. |
| Alle Grammatiken (Bundle) | `com.codeforge.plugin.all-grammars.bundle` | 1.0.0 | Bundles, Bundles › Sammlungen | Alle Grammatiken von CodeForge als Bundle (25 Pakete). |
| Alle Icon-Pakete (Bundle) | `com.codeforge.plugin.all-icons.bundle` | 1.0.0 | Bundles, Bundles › Sammlungen | Alle 11 Icon-Pakete von CodeForge als Bundle (3 klassisch hell, 3 klassisch dunkel, 3 modern hell, 2 modern dunkel). |
| Alle Language Server (Bundle) | `com.codeforge.plugin.all-lsp-servers.bundle` | 1.0.0 | Bundles, Bundles › Sammlungen | Alle Language Server von CodeForge als Bundle. Die Programme selbst müssen installiert werden. |
| Alle Snippets (Bundle) | `com.codeforge.plugin.all-snippets.bundle` | 1.0.0 | Bundles, Bundles › Sammlungen | Alle Snippets von CodeForge als Bundle (13 Pakete). |
| Alle Sprachpakete (Bundle) | `com.codeforge.plugin.all-languages.bundle` | 1.0.0 | Bundles, Bundles › Sammlungen | Alle Sprachpakete von CodeForge als Bundle (10 Pakete). |
| C/C++ (Bundle) | `com.codeforge.plugin.cpp-pack.bundle` | 1.0.0 | Bundles, Bundles › Sprachpakete | C/C++-Unterstützung: clangd und neocmakelsp für CMake. |
| Go (Bundle) | `com.codeforge.plugin.go-pack.bundle` | 1.0.0 | Bundles, Bundles › Sprachpakete | Go-Unterstützung: gopls und gofmt. |
| Konfigurationsdateien (YAML, TOML, XML) (Bundle) | `com.codeforge.plugin.config-pack.bundle` | 1.0.0 | Bundles, Bundles › Sprachpakete | YAML-, TOML- (Taplo) und XML-Server (LemMinX) samt Taplo-Formatierer. |
| Kotlin und Java (Bundle) | `com.codeforge.plugin.jvm-pack.bundle` | 1.0.0 | Bundles, Bundles › Sprachpakete | Kotlin Language Server, Eclipse JDT LS (Java) und LemMinX für Android-XML. |
| Python (Bundle) | `com.codeforge.plugin.python-pack.bundle` | 1.0.0 | Bundles, Bundles › Sprachpakete | Python-Unterstützung: Pyright (Typen, Navigation), Ruff (Linting) und Ruff Format. |
| Rust (Bundle) | `com.codeforge.plugin.rust-pack.bundle` | 1.0.0 | Bundles, Bundles › Sprachpakete | Rust-Unterstützung: rust-analyzer, rustfmt und Taplo für Cargo.toml. |
| Shell (Bundle) | `com.codeforge.plugin.shell-pack.bundle` | 1.0.0 | Bundles, Bundles › Sprachpakete | Shell-Skripte: Bash Language Server und shfmt. |
| Web (TypeScript, JavaScript, HTML, CSS) (Bundle) | `com.codeforge.plugin.web-pack.bundle` | 1.0.0 | Bundles, Bundles › Sprachpakete | Web-Entwicklung: TypeScript/JavaScript-Server, HTML/CSS/JSON-Server und Prettier. |

## Sprachserver (16)

Quelle: `tool-plugins/`

| Name | ID | Version | Kategorien | Beschreibung |
|---|---|---|---|---|
| Bash Language Server (Language Server) | `com.codeforge.plugin.bash-language-server.lsp` | 1.0.0 | Sprachserver, Sprachen › Shell | Bindet Bash Language Server ein. Fehlende Programme installiert die App nach Bestätigung (sonst: npm install -g bash-language-server (opt … |
| clangd (C/C++) (Language Server) | `com.codeforge.plugin.clangd.lsp` | 1.0.0 | Sprachserver, Sprachen › Systemnah › C/C++ | Bindet clangd (C/C++) ein. Fehlende Programme installiert die App nach Bestätigung (sonst: pkg install clang). |
| Dart Analysis Server (Language Server) | `com.codeforge.plugin.dart.lsp` | 1.0.0 | Sprachserver, Sprachen › Dart/Flutter | Bindet Dart Analysis Server ein. Fehlende Programme installiert die App nach Bestätigung (sonst: pkg install dart). |
| Eclipse JDT LS (Java) (Language Server) | `com.codeforge.plugin.jdtls.lsp` | 1.0.0 | Sprachserver, Sprachen › JVM › Java | Bindet Eclipse JDT LS (Java) ein. Fehlende Programme installiert die App nach Bestätigung (sonst: Download jdt-language-server 1.45.0 von … |
| gopls (Go) (Language Server) | `com.codeforge.plugin.gopls.lsp` | 1.0.0 | Sprachserver, Sprachen › Go | Bindet gopls (Go) ein. Fehlende Programme installiert die App nach Bestätigung (sonst: pkg install gopls). |
| HTML/CSS/JSON Language Servers (Language Server) | `com.codeforge.plugin.web-langservers.lsp` | 1.0.0 | Sprachserver, Sprachen › Web › HTML, Sprachen › Web › CSS | Bindet HTML/CSS/JSON Language Servers ein. Fehlende Programme installiert die App nach Bestätigung (sonst: npm install -g vscode-langserv … |
| kmp-lsp (Kotlin/Java/Swift) (Language Server) | `com.codeforge.plugin.kmp-lsp.lsp` | 1.0.2 | Sprachserver, Sprachen › JVM › Kotlin, Sprachen › JVM › Java | Bindet kmp-lsp (Kotlin/Java/Swift) ein. Fehlende Programme installiert die App nach Bestätigung (sonst: Download kmp-lsp termux-2 von Git … |
| Kotlin Language Server (Language Server) | `com.codeforge.plugin.kotlin-language-server.lsp` | 1.0.0 | Sprachserver, Sprachen › JVM › Kotlin | Bindet Kotlin Language Server ein. Fehlende Programme installiert die App nach Bestätigung (sonst: Download server.zip 1.3.11 von GitHub, … |
| LemMinX (XML) (Language Server) | `com.codeforge.plugin.lemminx.lsp` | 1.0.0 | Sprachserver, Sprachen › Daten und Konfiguration › XML | Bindet LemMinX (XML) ein. Fehlende Programme installiert die App nach Bestätigung (sonst: Download lemminx-uber.jar 0.31.2 von eclipse.or … |
| neocmakelsp (CMake) (Language Server) | `com.codeforge.plugin.neocmakelsp.lsp` | 1.0.0 | Sprachserver, Sprachen › Systemnah › CMake | Bindet neocmakelsp (CMake) ein. Fehlende Programme installiert die App nach Bestätigung (sonst: pkg install neocmakelsp). |
| Pyright (Python) (Language Server) | `com.codeforge.plugin.pyright.lsp` | 1.0.0 | Sprachserver, Sprachen › Python | Bindet Pyright (Python) ein. Fehlende Programme installiert die App nach Bestätigung (sonst: npm install -g pyright). |
| Ruff (Python-Linting) (Language Server) | `com.codeforge.plugin.ruff.lsp` | 1.0.0 | Sprachserver, Sprachen › Python | Bindet Ruff (Python-Linting) ein. Fehlende Programme installiert die App nach Bestätigung (sonst: pkg install ruff). |
| rust-analyzer (Rust) (Language Server) | `com.codeforge.plugin.rust-analyzer.lsp` | 1.0.0 | Sprachserver, Sprachen › Systemnah › Rust | Bindet rust-analyzer (Rust) ein. Fehlende Programme installiert die App nach Bestätigung (sonst: pkg install rust-analyzer). |
| Taplo (TOML) (Language Server) | `com.codeforge.plugin.taplo.lsp` | 1.0.0 | Sprachserver, Sprachen › Daten und Konfiguration › TOML | Bindet Taplo (TOML) ein. Fehlende Programme installiert die App nach Bestätigung (sonst: pkg install taplo). |
| TypeScript/JavaScript Language Server (Language Server) | `com.codeforge.plugin.typescript-language-server.lsp` | 1.0.0 | Sprachserver, Sprachen › Web › TypeScript, Sprachen › Web › JavaScript | Bindet TypeScript/JavaScript Language Server ein. Fehlende Programme installiert die App nach Bestätigung (sonst: npm install -g typescri … |
| YAML Language Server (Language Server) | `com.codeforge.plugin.yaml-language-server.lsp` | 1.0.0 | Sprachserver, Sprachen › Daten und Konfiguration › YAML | Bindet YAML Language Server ein. Fehlende Programme installiert die App nach Bestätigung (sonst: npm install -g yaml-language-server). |

## Formatierer (6)

Quelle: `tool-plugins/`

| Name | ID | Version | Kategorien | Beschreibung |
|---|---|---|---|---|
| gofmt (Formatierer) | `com.codeforge.plugin.gofmt.formatter` | 1.0.0 | Formatierer, Sprachen › Go | Formatiert go mit gofmt. Fehlende Programme installiert die App nach Bestätigung (sonst: pkg install golang). |
| Prettier (Formatierer) | `com.codeforge.plugin.prettier.formatter` | 1.0.0 | Formatierer, Sprachen › Web › JavaScript, Sprachen › Web › TypeScript | Formatiert js, jsx, mjs, cjs, ts, tsx … mit Prettier. Fehlende Programme installiert die App nach Bestätigung (sonst: npm install -g pret … |
| Ruff Format (Formatierer) | `com.codeforge.plugin.ruff-format.formatter` | 1.0.0 | Formatierer, Sprachen › Python | Formatiert py, pyi mit Ruff Format. Fehlende Programme installiert die App nach Bestätigung (sonst: pkg install ruff). |
| rustfmt (Formatierer) | `com.codeforge.plugin.rustfmt.formatter` | 1.0.0 | Formatierer, Sprachen › Systemnah › Rust | Formatiert rs mit rustfmt. Fehlende Programme installiert die App nach Bestätigung (sonst: pkg install rust). |
| shfmt (Formatierer) | `com.codeforge.plugin.shfmt.formatter` | 1.0.0 | Formatierer, Sprachen › Shell | Formatiert sh, bash, zsh mit shfmt. Fehlende Programme installiert die App nach Bestätigung (sonst: pkg install shfmt). |
| Taplo Format (Formatierer) | `com.codeforge.plugin.taplo-format.formatter` | 1.0.0 | Formatierer, Sprachen › Daten und Konfiguration › TOML | Formatiert toml mit Taplo Format. Fehlende Programme installiert die App nach Bestätigung (sonst: pkg install taplo). |

## Werkzeuge (PT-Menü) (9)

Quelle: `tool-plugins/`

| Name | ID | Version | Kategorien | Beschreibung |
|---|---|---|---|---|
| .gitignore-Generator (Tool) | `com.codeforge.plugin.gitignore.tool` | 1.0.0 | Werkzeuge, Werkzeuge › Projekt | Ergänzt .gitignore um fehlende Einträge für Android, Kotlin, Node, Python, Rust oder Go. Erscheint im PT-Menü des Dateibaums. Führt Skrip … |
| Alles formatieren (Tool) | `com.codeforge.plugin.format-all.tool` | 1.0.0 | Werkzeuge, Werkzeuge › Umbau | Formatiert oder prüft das ganze Projekt mit den installierten Formatierern (ktlint, shfmt, ruff, gofmt, rustfmt, taplo, prettier). Ersche … |
| AndroidX-Migration (Tool) | `com.codeforge.plugin.androidx-migrate.tool` | 1.0.0 | Werkzeuge, Werkzeuge › Umbau | Ersetzt android.support.* / android.arch.* durch AndroidX (Imports, Layout-XML, Gradle) und setzt useAndroidX/Jetifier. Erscheint im PT-M … |
| LICENSE-Generator (Tool) | `com.codeforge.plugin.license.tool` | 1.0.0 | Werkzeuge, Werkzeuge › Projekt | Schreibt eine LICENSE-Datei (MIT, BSD-2, BSD-3, ISC, GPL-2.0, GPL-3.0, Unlicense) – Auswahl per Dropdown. Der GPL-Volltext wird von gnu.o … |
| Projekt bereinigen (Tool) | `com.codeforge.plugin.project-clean.tool` | 1.0.0 | Werkzeuge, Werkzeuge › Projekt | Löscht Build-Artefakte (build/, .gradle/, .kotlin/, .cxx/, captures/, *.iml) und zeigt die freigegebene Größe. Erscheint im PT-Menü des D … |
| README-Generator (Tool) | `com.codeforge.plugin.readme.tool` | 1.0.0 | Werkzeuge, Werkzeuge › Projekt | Erzeugt eine README.md aus dem Projekt (Name, Module, Technik, SDK-Werte, Build-Befehle, Lizenz) auf Deutsch oder Englisch. Eine vorhande … |
| Unbenutzte Ressourcen (Tool) | `com.codeforge.plugin.unused-resources.tool` | 1.0.0 | Werkzeuge, Werkzeuge › Umbau | Findet nicht referenzierte Drawables, Layouts, Strings, Farben und mehr; löscht auf Wunsch Dateien. Erscheint im PT-Menü des Dateibaums. … |
| Version-Catalog-Migration (Tool) | `com.codeforge.plugin.catalog-migrate.tool` | 1.0.0 | Werkzeuge, Werkzeuge › Umbau | Verschiebt hartcodierte Abhängigkeiten aus build.gradle(.kts) in gradle/libs.versions.toml. Erscheint im PT-Menü des Dateibaums. Führt Sk … |
| VS Code Import (Tool) | `com.codeforge.plugin.vscode-import.tool` | 1.1.0 | Werkzeuge, Werkzeuge › Import | Konvertiert VS-Code-Extensions (.vsix, Ordner, Theme-JSON oder direkt von Open VSX) in ein CodeForge-Plugin: Farbschemata, Icon-Themes, G … |

## Projektvorlagen (6)

Quelle: `project-templates/`

| Name | ID | Version | Kategorien | Beschreibung |
|---|---|---|---|---|
| Compose Clean Architecture (Projektvorlage) | `com.codeforge.plugin.compose-clean.template` | 1.1.0 | Projektvorlagen, Projektvorlagen › Android | Projektvorlage: Jetpack Compose, MVI mit StateFlow, Hilt und Clean Architecture in mehreren Modulen (:app, :core:domain, :core:data, :fea … |
| Compose · Navigationsmenü (Drawer) (Projektvorlage) | `com.codeforge.plugin.compose-drawer.template` | 1.1.0 | Projektvorlagen, Projektvorlagen › Android | Projektvorlage: Jetpack Compose mit ModalNavigationDrawer und drei Bildschirmen (Start, Notizen, Einstellungen); MVI mit StateFlow, Hilt, … |
| Compose · Notizen-App (Projektvorlage) | `com.codeforge.plugin.compose-notes.template` | 1.0.0 | Projektvorlagen, Projektvorlagen › Android | Projektvorlage: Notizen-App mit eigenem Designsystem (:core:designsystem – Theme hell/dunkel, Akzentfarben, Formen, Verläufe, Komponenten … |
| Compose · Podcast-App (Projektvorlage) | `com.codeforge.plugin.compose-podcast.template` | 1.0.0 | Projektvorlagen, Projektvorlagen › Android | Projektvorlage: Podcast-App mit eigenem Designsystem (:core:designsystem – Theme hell/dunkel, Akzentfarben, Formen, Verläufe, Komponenten … |
| Compose · Untere Navigationsleiste (Projektvorlage) | `com.codeforge.plugin.compose-bottomnav.template` | 1.1.0 | Projektvorlagen, Projektvorlagen › Android | Projektvorlage: Jetpack Compose mit NavigationBar und drei Bildschirmen (Heute, Aufgaben, Profil); MVI mit StateFlow, Hilt, typsichere Na … |
| Konsolenprogramm JVM (Projektvorlage) | `com.codeforge.plugin.jvm-cli.template` | 1.1.0 | Projektvorlagen, Projektvorlagen › JVM | Projektvorlage: Konsolenprogramm für die JVM in Kotlin oder Java, Gradle mit Kotlin- oder Groovy-DSL, application-Plugin und JUnit-Test; … |

## Grammatiken (25)

Quelle: `content-plugins/`

| Name | ID | Version | Kategorien | Beschreibung |
|---|---|---|---|---|
| C (Grammatik) | `com.codeforge.plugin.c.grammar` | 1.0.0 | Grammatiken, Sprachen › Systemnah › C/C++ | Syntaxfärbung für C (vereinfachte TextMate-Grammatik, gilt für Dateien ohne eingebaute Grammatik). |
| C++ (Grammatik) | `com.codeforge.plugin.cpp.grammar` | 1.0.0 | Grammatiken, Sprachen › Systemnah › C/C++ | Syntaxfärbung für C++ (vereinfachte TextMate-Grammatik, gilt für Dateien ohne eingebaute Grammatik). |
| CMake (Grammatik) | `com.codeforge.plugin.cmake.grammar` | 1.0.0 | Grammatiken, Sprachen › Systemnah › CMake | Syntaxfärbung für CMake (vereinfachte TextMate-Grammatik, gilt für Dateien ohne eingebaute Grammatik). |
| CSS (Grammatik) | `com.codeforge.plugin.css.grammar` | 1.0.0 | Grammatiken, Sprachen › Web › CSS | Syntaxfärbung für CSS (vereinfachte TextMate-Grammatik, gilt für Dateien ohne eingebaute Grammatik). |
| Dart (Grammatik) | `com.codeforge.plugin.dart.grammar` | 1.0.0 | Grammatiken, Sprachen › Dart/Flutter | Syntaxfärbung für Dart (vereinfachte TextMate-Grammatik, gilt für Dateien ohne eingebaute Grammatik). |
| Dockerfile (Grammatik) | `com.codeforge.plugin.dockerfile.grammar` | 1.0.0 | Grammatiken, Sprachen › Shell | Syntaxfärbung für Dockerfile (vereinfachte TextMate-Grammatik, gilt für Dateien ohne eingebaute Grammatik). |
| Go (Grammatik) | `com.codeforge.plugin.go.grammar` | 1.0.0 | Grammatiken, Sprachen › Go | Syntaxfärbung für Go (vereinfachte TextMate-Grammatik, gilt für Dateien ohne eingebaute Grammatik). |
| Groovy / Gradle (Grammatik) | `com.codeforge.plugin.groovy.grammar` | 1.0.0 | Grammatiken, Sprachen › JVM › Groovy | Syntaxfärbung für Groovy / Gradle (vereinfachte TextMate-Grammatik, gilt für Dateien ohne eingebaute Grammatik). |
| HTML (Grammatik) | `com.codeforge.plugin.html.grammar` | 1.0.0 | Grammatiken, Sprachen › Web › HTML | Syntaxfärbung für HTML (vereinfachte TextMate-Grammatik, gilt für Dateien ohne eingebaute Grammatik). |
| JavaScript (Grammatik) | `com.codeforge.plugin.javascript.grammar` | 1.0.0 | Grammatiken, Sprachen › Web › JavaScript | Syntaxfärbung für JavaScript (vereinfachte TextMate-Grammatik, gilt für Dateien ohne eingebaute Grammatik). |
| Lua (Grammatik) | `com.codeforge.plugin.lua.grammar` | 1.0.0 | Grammatiken, Sprachen › Weitere › Lua | Syntaxfärbung für Lua (vereinfachte TextMate-Grammatik, gilt für Dateien ohne eingebaute Grammatik). |
| Makefile (Grammatik) | `com.codeforge.plugin.makefile.grammar` | 1.0.0 | Grammatiken, Sprachen › Shell | Syntaxfärbung für Makefile (vereinfachte TextMate-Grammatik, gilt für Dateien ohne eingebaute Grammatik). |
| Markdown (Grammatik) | `com.codeforge.plugin.markdown.grammar` | 1.0.0 | Grammatiken, Sprachen › Auszeichnung › Markdown | Syntaxfärbung für Markdown (vereinfachte TextMate-Grammatik, gilt für Dateien ohne eingebaute Grammatik). |
| PHP (Grammatik) | `com.codeforge.plugin.php.grammar` | 1.0.0 | Grammatiken, Sprachen › Weitere › Php | Syntaxfärbung für PHP (vereinfachte TextMate-Grammatik, gilt für Dateien ohne eingebaute Grammatik). |
| Properties / INI (Grammatik) | `com.codeforge.plugin.properties.grammar` | 1.0.0 | Grammatiken, Sprachen › Weitere › Properties | Syntaxfärbung für Properties / INI (vereinfachte TextMate-Grammatik, gilt für Dateien ohne eingebaute Grammatik). |
| Protocol Buffers (Grammatik) | `com.codeforge.plugin.protobuf.grammar` | 1.0.0 | Grammatiken, Sprachen › Weitere › Proto | Syntaxfärbung für Protocol Buffers (vereinfachte TextMate-Grammatik, gilt für Dateien ohne eingebaute Grammatik). |
| Python (Grammatik) | `com.codeforge.plugin.python.grammar` | 1.0.0 | Grammatiken, Sprachen › Python | Syntaxfärbung für Python (vereinfachte TextMate-Grammatik, gilt für Dateien ohne eingebaute Grammatik). |
| Ruby (Grammatik) | `com.codeforge.plugin.ruby.grammar` | 1.0.0 | Grammatiken, Sprachen › Weitere › Ruby | Syntaxfärbung für Ruby (vereinfachte TextMate-Grammatik, gilt für Dateien ohne eingebaute Grammatik). |
| Rust (Grammatik) | `com.codeforge.plugin.rust.grammar` | 1.0.0 | Grammatiken, Sprachen › Systemnah › Rust | Syntaxfärbung für Rust (vereinfachte TextMate-Grammatik, gilt für Dateien ohne eingebaute Grammatik). |
| Shell (Grammatik) | `com.codeforge.plugin.shellscript.grammar` | 1.0.0 | Grammatiken, Sprachen › Shell | Syntaxfärbung für Shell (vereinfachte TextMate-Grammatik, gilt für Dateien ohne eingebaute Grammatik). |
| SQL (Grammatik) | `com.codeforge.plugin.sql.grammar` | 1.0.0 | Grammatiken, Sprachen › Daten und Konfiguration › SQL | Syntaxfärbung für SQL (vereinfachte TextMate-Grammatik, gilt für Dateien ohne eingebaute Grammatik). |
| Swift (Grammatik) | `com.codeforge.plugin.swift.grammar` | 1.0.0 | Grammatiken, Sprachen › Weitere › Swift | Syntaxfärbung für Swift (vereinfachte TextMate-Grammatik, gilt für Dateien ohne eingebaute Grammatik). |
| TOML (Grammatik) | `com.codeforge.plugin.toml.grammar` | 1.0.0 | Grammatiken, Sprachen › Daten und Konfiguration › TOML | Syntaxfärbung für TOML (vereinfachte TextMate-Grammatik, gilt für Dateien ohne eingebaute Grammatik). |
| TypeScript (Grammatik) | `com.codeforge.plugin.typescript.grammar` | 1.0.0 | Grammatiken, Sprachen › Web › TypeScript | Syntaxfärbung für TypeScript (vereinfachte TextMate-Grammatik, gilt für Dateien ohne eingebaute Grammatik). |
| YAML (Grammatik) | `com.codeforge.plugin.yaml.grammar` | 1.0.0 | Grammatiken, Sprachen › Daten und Konfiguration › YAML | Syntaxfärbung für YAML (vereinfachte TextMate-Grammatik, gilt für Dateien ohne eingebaute Grammatik). |

## Snippets (13)

Quelle: `content-plugins/`

| Name | ID | Version | Kategorien | Beschreibung |
|---|---|---|---|---|
| Android XML (Snippets) | `com.codeforge.plugin.xml.snippets` | 1.0.0 | Snippets, Sprachen › Daten und Konfiguration › XML | 8 Code-Snippets für Android XML. |
| C (Snippets) | `com.codeforge.plugin.c.snippets` | 1.0.0 | Snippets, Sprachen › Systemnah › C/C++ | 6 Code-Snippets für C. |
| C++ (Snippets) | `com.codeforge.plugin.cpp.snippets` | 1.0.0 | Snippets, Sprachen › Systemnah › C/C++ | 8 Code-Snippets für C++. |
| Dart (Snippets) | `com.codeforge.plugin.dart.snippets` | 1.0.0 | Snippets, Sprachen › Dart/Flutter | 7 Code-Snippets für Dart. |
| Go (Snippets) | `com.codeforge.plugin.go.snippets` | 1.0.0 | Snippets, Sprachen › Go | 9 Code-Snippets für Go. |
| Java (Snippets) | `com.codeforge.plugin.java.snippets` | 1.0.0 | Snippets, Sprachen › JVM › Java | 8 Code-Snippets für Java. |
| JavaScript (Snippets) | `com.codeforge.plugin.javascript.snippets` | 1.0.0 | Snippets, Sprachen › Web › JavaScript | 9 Code-Snippets für JavaScript. |
| Jetpack Compose (Snippets) | `com.codeforge.plugin.compose.snippets` | 1.0.0 | Snippets, Sprachen › JVM › Kotlin | 11 Code-Snippets für Jetpack Compose. |
| Kotlin (Snippets) | `com.codeforge.plugin.kotlin.snippets` | 1.0.0 | Snippets, Sprachen › JVM › Kotlin | 12 Code-Snippets für Kotlin. |
| Python (Snippets) | `com.codeforge.plugin.python.snippets` | 1.0.0 | Snippets, Sprachen › Python | 10 Code-Snippets für Python. |
| Rust (Snippets) | `com.codeforge.plugin.rust.snippets` | 1.0.0 | Snippets, Sprachen › Systemnah › Rust | 9 Code-Snippets für Rust. |
| Shell (Snippets) | `com.codeforge.plugin.shellscript.snippets` | 1.0.0 | Snippets, Sprachen › Shell | 8 Code-Snippets für Shell. |
| TypeScript (Snippets) | `com.codeforge.plugin.typescript.snippets` | 1.0.0 | Snippets, Sprachen › Web › TypeScript | 8 Code-Snippets für TypeScript. |

## Sprachpakete der Oberfläche (10)

Quelle: `content-plugins/`

| Name | ID | Version | Kategorien | Beschreibung |
|---|---|---|---|---|
| Español (Sprachpaket) | `com.codeforge.plugin.lang-es.language` | 1.0.0 | Sprachpakete | Teilübersetzung der Oberfläche (80 Texte: Allgemeines und Dateibaum); übrige Texte bleiben in der App-Sprache. |
| Français (Sprachpaket) | `com.codeforge.plugin.lang-fr.language` | 1.0.0 | Sprachpakete | Teilübersetzung der Oberfläche (79 Texte: Allgemeines und Dateibaum); übrige Texte bleiben in der App-Sprache. |
| Italiano (Sprachpaket) | `com.codeforge.plugin.lang-it.language` | 1.0.0 | Sprachpakete | Teilübersetzung der Oberfläche (80 Texte: Allgemeines und Dateibaum); übrige Texte bleiben in der App-Sprache. |
| Nederlands (Sprachpaket) | `com.codeforge.plugin.lang-nl.language` | 1.0.0 | Sprachpakete | Teilübersetzung der Oberfläche (74 Texte: Allgemeines und Dateibaum); übrige Texte bleiben in der App-Sprache. |
| Polski (Sprachpaket) | `com.codeforge.plugin.lang-pl.language` | 1.0.0 | Sprachpakete | Teilübersetzung der Oberfläche (81 Texte: Allgemeines und Dateibaum); übrige Texte bleiben in der App-Sprache. |
| Português (Brasil) (Sprachpaket) | `com.codeforge.plugin.lang-pt-br.language` | 1.0.0 | Sprachpakete | Teilübersetzung der Oberfläche (80 Texte: Allgemeines und Dateibaum); übrige Texte bleiben in der App-Sprache. |
| Türkçe (Sprachpaket) | `com.codeforge.plugin.lang-tr.language` | 1.0.0 | Sprachpakete | Teilübersetzung der Oberfläche (82 Texte: Allgemeines und Dateibaum); übrige Texte bleiben in der App-Sprache. |
| Русский (Sprachpaket) | `com.codeforge.plugin.lang-ru.language` | 1.0.0 | Sprachpakete | Teilübersetzung der Oberfläche (82 Texte: Allgemeines und Dateibaum); übrige Texte bleiben in der App-Sprache. |
| 日本語 (Sprachpaket) | `com.codeforge.plugin.lang-ja.language` | 1.0.0 | Sprachpakete | Teilübersetzung der Oberfläche (82 Texte: Allgemeines und Dateibaum); übrige Texte bleiben in der App-Sprache. |
| 简体中文 (Sprachpaket) | `com.codeforge.plugin.lang-zh-cn.language` | 1.0.0 | Sprachpakete | Teilübersetzung der Oberfläche (82 Texte: Allgemeines und Dateibaum); übrige Texte bleiben in der App-Sprache. |

## Farbschemata (30)

Quelle: `theme-plugins/`, `textmate-plugins/`

| Name | ID | Version | Kategorien | Beschreibung |
|---|---|---|---|---|
| Ayu (Editor-Theme) | `com.codeforge.plugin.ayu.theme` | 1.0.0 | Themes, Themes › Dunkel, Themes › Hell | Ayu als Farbschema mit TextMate-Editor-Theme, Dunkel- und Hell-Variante (Palette nachempfunden). |
| Ayu Dark (Farbschema) | `com.codeforge.plugin.ayu-dark.theme` | 1.0.0 | Themes, Themes › Dunkel, Themes › Hell | Farbschema „Ayu Dark“ aus CodeForge, als Dunkel- und Hell-Variante. |
| Catppuccin (Editor-Theme) | `com.codeforge.plugin.catppuccin.theme` | 1.0.0 | Themes, Themes › Dunkel, Themes › Hell | Catppuccin als Farbschema mit TextMate-Editor-Theme, Dunkel- und Hell-Variante (Palette nachempfunden). |
| Cyberpunk Neon (Farbschema) | `com.codeforge.plugin.cyberpunk-neon.theme` | 1.0.0 | Themes, Themes › Dunkel, Themes › Hell | Farbschema „Cyberpunk Neon“ aus CodeForge, als Dunkel- und Hell-Variante. |
| Darcula (Farbschema) | `com.codeforge.plugin.darcula.theme` | 1.0.0 | Themes, Themes › Dunkel, Themes › Hell | Farbschema „Darcula“ aus CodeForge, als Dunkel- und Hell-Variante. |
| Dracula (Editor-Theme) | `com.codeforge.plugin.dracula.theme` | 1.0.0 | Themes, Themes › Dunkel, Themes › Hell | Dracula als Farbschema mit TextMate-Editor-Theme, Dunkel- und Hell-Variante (Palette nachempfunden). |
| Eclipse (Farbschema) | `com.codeforge.plugin.eclipse.theme` | 1.0.0 | Themes, Themes › Dunkel, Themes › Hell | Farbschema „Eclipse“ aus CodeForge, als Dunkel- und Hell-Variante. |
| Everforest (Editor-Theme) | `com.codeforge.plugin.everforest.theme` | 1.0.0 | Themes, Themes › Dunkel, Themes › Hell | Everforest als Farbschema mit TextMate-Editor-Theme, Dunkel- und Hell-Variante (Palette nachempfunden). |
| Forest (Farbschema) | `com.codeforge.plugin.forest.theme` | 1.0.0 | Themes, Themes › Dunkel, Themes › Hell | Farbschema „Forest“ aus CodeForge, als Dunkel- und Hell-Variante. |
| GitHub (Editor-Theme) | `com.codeforge.plugin.github.theme` | 1.0.0 | Themes, Themes › Dunkel, Themes › Hell | GitHub als Farbschema mit TextMate-Editor-Theme, Dunkel- und Hell-Variante (Palette nachempfunden). |
| GitHub Light (Farbschema) | `com.codeforge.plugin.github-light.theme` | 1.0.0 | Themes, Themes › Dunkel, Themes › Hell | Farbschema „GitHub Light“ aus CodeForge, als Dunkel- und Hell-Variante. |
| Gruvbox (Editor-Theme) | `com.codeforge.plugin.gruvbox.theme` | 1.0.0 | Themes, Themes › Dunkel, Themes › Hell | Gruvbox als Farbschema mit TextMate-Editor-Theme, Dunkel- und Hell-Variante (Palette nachempfunden). |
| Kanagawa (Editor-Theme) | `com.codeforge.plugin.kanagawa.theme` | 1.0.0 | Themes, Themes › Dunkel, Themes › Hell | Kanagawa als Farbschema mit TextMate-Editor-Theme, Dunkel- und Hell-Variante (Palette nachempfunden). |
| Material (Editor-Theme) | `com.codeforge.plugin.material.theme` | 1.0.0 | Themes, Themes › Dunkel, Themes › Hell | Material als Farbschema mit TextMate-Editor-Theme, Dunkel- und Hell-Variante (Palette nachempfunden). |
| Monochrome (Farbschema) | `com.codeforge.plugin.monochrome.theme` | 1.0.0 | Themes, Themes › Dunkel, Themes › Hell | Farbschema „Monochrome“ aus CodeForge, als Dunkel- und Hell-Variante. |
| Monokai (Farbschema) | `com.codeforge.plugin.monokai.theme` | 1.0.0 | Themes, Themes › Dunkel, Themes › Hell | Farbschema „Monokai“ aus CodeForge, als Dunkel- und Hell-Variante. |
| Night Owl (Editor-Theme) | `com.codeforge.plugin.night-owl.theme` | 1.0.0 | Themes, Themes › Dunkel, Themes › Hell | Night Owl als Farbschema mit TextMate-Editor-Theme, Dunkel- und Hell-Variante (Palette nachempfunden). |
| Nord (Editor-Theme) | `com.codeforge.plugin.nord.theme` | 1.0.0 | Themes, Themes › Dunkel, Themes › Hell | Nord als Farbschema mit TextMate-Editor-Theme, Dunkel- und Hell-Variante (Palette nachempfunden). |
| Notepad++ (Farbschema) | `com.codeforge.plugin.notepad-plus-plus.theme` | 1.0.0 | Themes, Themes › Dunkel, Themes › Hell | Farbschema „Notepad++“ aus CodeForge, als Dunkel- und Hell-Variante. |
| Ocean (Farbschema) | `com.codeforge.plugin.ocean.theme` | 1.0.0 | Themes, Themes › Dunkel, Themes › Hell | Farbschema „Ocean“ aus CodeForge, als Dunkel- und Hell-Variante. |
| One (Editor-Theme) | `com.codeforge.plugin.one.theme` | 1.0.0 | Themes, Themes › Dunkel, Themes › Hell | One als Farbschema mit TextMate-Editor-Theme, Dunkel- und Hell-Variante (Palette nachempfunden). |
| Quiet Light (Farbschema) | `com.codeforge.plugin.quiet-light.theme` | 1.0.0 | Themes, Themes › Dunkel, Themes › Hell | Farbschema „Quiet Light“ aus CodeForge, als Dunkel- und Hell-Variante. |
| Rosé Pine (Editor-Theme) | `com.codeforge.plugin.rose-pine.theme` | 1.0.0 | Themes, Themes › Dunkel, Themes › Hell | Rosé Pine als Farbschema mit TextMate-Editor-Theme, Dunkel- und Hell-Variante (Palette nachempfunden). |
| Solarized (Editor-Theme) | `com.codeforge.plugin.solarized.theme` | 1.0.0 | Themes, Themes › Dunkel, Themes › Hell | Solarized als Farbschema mit TextMate-Editor-Theme, Dunkel- und Hell-Variante (Palette nachempfunden). |
| Solarized Dark (Farbschema) | `com.codeforge.plugin.solarized-dark.theme` | 1.0.0 | Themes, Themes › Dunkel, Themes › Hell | Farbschema „Solarized Dark“ aus CodeForge, als Dunkel- und Hell-Variante. |
| Sunset (Farbschema) | `com.codeforge.plugin.sunset.theme` | 1.0.0 | Themes, Themes › Dunkel, Themes › Hell | Farbschema „Sunset“ aus CodeForge, als Dunkel- und Hell-Variante. |
| Tokyo Night (Editor-Theme) | `com.codeforge.plugin.tokyo-night.theme` | 1.0.0 | Themes, Themes › Dunkel, Themes › Hell | Tokyo Night als Farbschema mit TextMate-Editor-Theme, Dunkel- und Hell-Variante (Palette nachempfunden). |
| Tomorrow (Editor-Theme) | `com.codeforge.plugin.tomorrow.theme` | 1.0.0 | Themes, Themes › Dunkel, Themes › Hell | Tomorrow als Farbschema mit TextMate-Editor-Theme, Dunkel- und Hell-Variante (Palette nachempfunden). |
| Violet (Farbschema) | `com.codeforge.plugin.violet.theme` | 1.0.0 | Themes, Themes › Dunkel, Themes › Hell | Farbschema „Violet“ aus CodeForge, als Dunkel- und Hell-Variante. |
| VS Code Dark+ (Farbschema) | `com.codeforge.plugin.vscode-dark-plus.theme` | 1.0.0 | Themes, Themes › Dunkel, Themes › Hell | Farbschema „VS Code Dark+“ aus CodeForge, als Dunkel- und Hell-Variante. |

## Icon-Pakete (11)

Quelle: `icon-plugins/`

| Name | ID | Version | Kategorien | Beschreibung |
|---|---|---|---|---|
| Aurora (Icons) | `com.codeforge.plugin.aurora.icons` | 1.0.0 | Icon-Pakete | Modernes helles Icon-Paket: runde Kacheln, violette Ordner. |
| Coral (Icons) | `com.codeforge.plugin.coral.icons` | 1.0.0 | Icon-Pakete | Modernes helles Icon-Paket: runde Kacheln, korallenrote Ordner. |
| Ember (Icons) | `com.codeforge.plugin.ember.icons` | 1.0.0 | Icon-Pakete | Klassisches dunkles Icon-Paket mit glutroten Ordnern. |
| Frost (Icons) | `com.codeforge.plugin.frost.icons` | 1.0.0 | Icon-Pakete | Klassisches dunkles Icon-Paket mit eisblauen Ordnern. |
| Lagoon (Icons) | `com.codeforge.plugin.lagoon.icons` | 1.0.0 | Icon-Pakete | Modernes helles Icon-Paket: runde Kacheln, türkise Ordner. |
| Meadow (Icons) | `com.codeforge.plugin.meadow.icons` | 1.0.0 | Icon-Pakete | Klassisches helles Icon-Paket mit grünen Ordnern. |
| Neon (Icons) | `com.codeforge.plugin.neon.icons` | 1.0.0 | Icon-Pakete | Modernes dunkles Icon-Paket: Neon-Konturen auf dunklen Kacheln. |
| Obsidian (Icons) | `com.codeforge.plugin.obsidian.icons` | 1.0.0 | Icon-Pakete | Modernes dunkles Icon-Paket: ruhige Graustufen mit Akzenten. |
| Paper (Icons) | `com.codeforge.plugin.paper.icons` | 1.0.0 | Icon-Pakete | Klassisches helles Icon-Paket: warme Ordner, weiße Seiten. |
| Sky (Icons) | `com.codeforge.plugin.sky.icons` | 1.0.0 | Icon-Pakete | Klassisches helles Icon-Paket mit blauen Ordnern. |
| Slate (Icons) | `com.codeforge.plugin.slate.icons` | 1.0.0 | Icon-Pakete | Klassisches dunkles Icon-Paket mit grauen Ordnern. |
