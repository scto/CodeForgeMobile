# Tool-Plugins: Formatierer, Language Server und PT-Werkzeuge

Plugins (`com.codeforge.plugin.<name>.formatter|lsp|tool`, Publisher `codeforge`), erzeugt von `generate.py`.

* **Formatierer** (6): Prettier, shfmt, Ruff Format, gofmt, rustfmt, Taplo Format. Bundle: `com.codeforge.plugin.all-formatters.bundle`.
* **Language Server** (16 Plugins, 18 Server): clangd, rust-analyzer, Taplo, gopls, Ruff, neocmakelsp, Dart, Bash, TypeScript/JavaScript, Pyright, HTML/CSS/JSON, YAML, Kotlin Language Server, Eclipse JDT LS, LemMinX, kmp-lsp. Bundle: `com.codeforge.plugin.all-lsp-servers.bundle`.
* **Sprachpakete** (8 Bundles wie die „Extension Packs“ von VS Code): Python, Rust, Go, Web, C/C++, Shell, Konfiguration (YAML/TOML/XML), Kotlin/Java – `com.codeforge.plugin.<sprache>-pack.bundle`.
* **Werkzeuge** (10, erscheinen im **PT**-Menü des Dateibaums, Store-Kategorie „Code-Tools“ oder Sideload, Berechtigung `shell`, Skripte in Python):
  * `vscode-import` – VS-Code-Extensions (.vsix / Ordner / Theme-JSON / `openvsx:<publisher>.<name>`) → CodeForge-Plugin-ZIP
    (`cf-plugin-out/`) mit Farbschemata, Icon-Themes, Grammatiken (+ Dateiendungen aus `languages`), Snippets (JSONC → JSON)
    und Extension-Packs (bekannte Extensions → CodeForge-Plugins, z. B. `redhat.vscode-yaml` → YAML-Server). Dazu ein
    Bericht `<id>.report.md` mit allem, was nicht übertragbar ist (JavaScript-Code, Befehle, Debugger …). SVG-Icons brauchen
    `librsvg`. Auch am Rechner: `python3 tool-plugins/scripts/vscode_import.py <ausgabeordner> <eingabe>`;
    Tests: `python3 -m unittest tool-plugins/scripts/test_vscode_import.py`.
  * `androidx-migrate` – `android.support.*`/`android.arch.*` → AndroidX (Imports, Layout-XML, Gradle, `gradle.properties`); Modus `check`/`apply`.
  * `agp9-migrate` – Umstellung auf Android Gradle Plugin 9.0: AGP-Version (Catalog, `plugins`, `buildscript`),
    Gradle-Wrapper ≥ 9.1.0, Kotlin ≥ 2.2.10, `buildToolsVersion` ≥ 36.0.0, eingebautes Kotlin (Plugin
    `org.jetbrains.kotlin.android`/`kotlin-android` entfernen, `android.kotlinOptions` → `kotlin.compilerOptions`),
    KSP ≥ 2.3.6, Hilt ≥ 2.59.2, `kapt(…)` → `ksp(…)` für KSP-fähige Prozessoren (bekannte Liste bzw. JAR im
    Gradle-Cache), übrige kapt → `com.android.legacy-kapt`, `buildFeatures.buildConfig = true` bei
    `buildConfigField`, Übergangsschalter (`builtInKotlin`, `uniquePackageNames`, `enableAppCompileTimeRClass`,
    `newDsl` ohne alte Variant-API) entfernen, `dexOptions` entfernen, `CommonExtension<…>` → `CommonExtension`, in AGP 9
    entfernte `gradle.properties`-Schalter, `resvalues=true` wenn `resValue()` genutzt wird. Meldet alte Variant-API,
    `BaseExtension`, `kotlin.sourceSets`, KMP + Android im selben Modul, `addCustomBuildConfigFields`, Paparazzi < 2.0.0-alpha05 und Opt-outs (Abgleich mit Googles Skill `agp-9-upgrade`, github.com/android/skills). Eingabe
    `check|apply [version]` (Standard 9.0.1); Tests: `python3 -m unittest tool-plugins/scripts/test_agp9_migrate.py`.
  * `catalog-migrate` – hartcodierte Abhängigkeiten → `gradle/libs.versions.toml`; Modus `check`/`apply`.
  * `unused-resources` – unbenutzte Ressourcen finden, Dateien auf Wunsch löschen.
  * `format-all` – Projekt mit ktlint, shfmt, ruff, gofmt, rustfmt, taplo, prettier prüfen/formatieren.
  * `gitignore` – `.gitignore` ergänzen (android, kotlin, node, python, rust, go).
  * `license` – LICENSE (MIT, BSD-2, BSD-3, ISC, Unlicense; Apache-2.0/GPL nicht enthalten).
  * `readme` – `README.md` aus dem Projekt erzeugen (Name, Module, Technik, SDK-Werte, Build-Befehle, Lizenz; Deutsch/Englisch). Eine vorhandene README wird nie überschrieben, es entsteht `README.generated.md`.
  * `project-clean` – Build-Artefakte löschen.
* **App-Funktionen** (1): `com.codeforge.plugin.debugger.feature` schaltet den eingebauten Debugger frei
  (`contributes.features: ["debugger"]`); standardmäßig nicht installiert.
* **`requires`:** Alle Plugins deklarieren ihre Programme; die App prüft und installiert sie nach Bestätigung (Plugins-Bildschirm bzw. PT-Menü).
* **Kotlin LS, JDT LS, LemMinX:** laden ihre Dateien als `requires`-Download (`manager: download`, SHA-256 Pflicht) nach `${deps}/<name>`; JVM-Optionen aus den Einstellungen über `${jvmArg}`/`${jvmArgs}`. Neue Server-Version = neue URL/SHA im Plugin, kein App-Release.
* **kmp-lsp:** Termux-Binary je Architektur (`requires[].arch`: aarch64/x86_64, `stripTopDir`) aus dem Release `termux-1` von `scto/kmp-lsp`. Konkurriert bei Kotlin/Java mit Kotlin LS/JDT LS – einen davon in Einstellungen → Sprachserver abschalten.
* **Bauen:** `python3 tool-plugins/generate.py [ausgabeordner]` (gibt je ZIP die SHA-256 aus; zusätzlich `codeforge-tool-plugins-alle.zip` mit allen Werkzeug-Plugins).
* Die Skripte (`scripts/`) sind mit Beispiel-Projekten im Sandkasten getestet, nicht auf einem Gerät. Änderungsmodi (`apply`/`fix`) vorher mit `check` prüfen und das Projekt committen.
