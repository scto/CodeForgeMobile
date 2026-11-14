# Tool-Plugins: Formatierer, Language Server und PT-Werkzeuge

Plugins (`com.codeforge.plugin.<name>.formatter|lsp|tool`, Publisher `codeforge`), erzeugt von `generate.py`.

* **Formatierer** (6): Prettier, shfmt, Ruff Format, gofmt, rustfmt, Taplo Format. Bundle: `com.codeforge.plugin.all-formatters.bundle`.
* **Language Server** (15 Plugins, 17 Server): clangd, rust-analyzer, Taplo, gopls, Ruff, neocmakelsp, Dart, Bash, TypeScript/JavaScript, Pyright, HTML/CSS/JSON, YAML, Kotlin Language Server, Eclipse JDT LS, LemMinX. Bundle: `com.codeforge.plugin.all-lsp-servers.bundle`.
* **Werkzeuge** (9, erscheinen im **PT**-Menü des Dateibaums, Store-Kategorie „Code-Tools“ oder Sideload, Berechtigung `shell`, Skripte in Python):
  * `vscode-import` – VS-Code-Themes und Icon-Packs (.vsix / Ordner / Theme-JSON) → CodeForge-Plugin-ZIP (`cf-plugin-out/`); SVG-Icons brauchen `librsvg`.
  * `androidx-migrate` – `android.support.*`/`android.arch.*` → AndroidX (Imports, Layout-XML, Gradle, `gradle.properties`); Modus `check`/`apply`.
  * `catalog-migrate` – hartcodierte Abhängigkeiten → `gradle/libs.versions.toml`; Modus `check`/`apply`.
  * `unused-resources` – unbenutzte Ressourcen finden, Dateien auf Wunsch löschen.
  * `format-all` – Projekt mit ktlint, shfmt, ruff, gofmt, rustfmt, taplo, prettier prüfen/formatieren.
  * `gitignore` – `.gitignore` ergänzen (android, kotlin, node, python, rust, go).
  * `license` – LICENSE (MIT, BSD-2, BSD-3, ISC, Unlicense; Apache-2.0/GPL nicht enthalten).
  * `readme` – `README.md` aus dem Projekt erzeugen (Name, Module, Technik, SDK-Werte, Build-Befehle, Lizenz; Deutsch/Englisch). Eine vorhandene README wird nie überschrieben, es entsteht `README.generated.md`.
  * `project-clean` – Build-Artefakte löschen.
* **`requires`:** Alle Plugins deklarieren ihre Programme; die App prüft und installiert sie nach Bestätigung (Plugins-Bildschirm bzw. PT-Menü).
* **Kotlin LS, JDT LS, LemMinX:** laden ihre Dateien als `requires`-Download (`manager: download`, SHA-256 Pflicht) nach `${deps}/<name>`; JVM-Optionen aus den Einstellungen über `${jvmArg}`/`${jvmArgs}`. Neue Server-Version = neue URL/SHA im Plugin, kein App-Release.
* **Bauen:** `python3 tool-plugins/generate.py [ausgabeordner]` (gibt je ZIP die SHA-256 aus; zusätzlich `codeforge-tool-plugins-alle.zip` mit allen Werkzeug-Plugins).
* Die Skripte (`scripts/`) sind mit Beispiel-Projekten im Sandkasten getestet, nicht auf einem Gerät. Änderungsmodi (`apply`/`fix`) vorher mit `check` prüfen und das Projekt committen.
