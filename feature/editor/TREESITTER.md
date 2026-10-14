# TreeSitter-Sprachunterstützung — native Abhängigkeiten

`:feature:editor` bindet `io.github.Rosemoe.sora-editor:language-treesitter` ein, um
präziseres, inkrementelles Highlighting und Code-Block-Indicators auf AST-Basis (statt
regelbasiertem TextMate) anzubieten — umschaltbar über `EditorConfig.use_tree_sitter`
(`:feature:settings` → "Tree-sitter verwenden").

## Warum das nicht "einfach funktioniert"

TreeSitter-Parser sind in C geschrieben und werden pro Sprache als eigene native
Bibliothek (`libtree-sitter-<sprache>.so`) kompiliert — anders als TextMate-Grammatiken
(reines JSON) gibt es hier **keine** rein-JVM-Lösung. Das ist dieselbe Klasse von Problem
wie bei `proot`/`libtalloc.so.2` in `:libs:terminal-engine` (siehe dessen `BOOTSTRAP.md`):
eine native Abhängigkeit, die nicht im Gradle-Dependency-Graphen auflösbar ist, sondern
separat pro ABI gebaut/bezogen und gebunden werden muss.

`TreeSitterLanguageSupport.isAvailable()` prüft das zur Laufzeit per `System.loadLibrary`
und meldet `false`, solange die Bibliothek fehlt — `EditorLanguageFactory` fällt dann
transparent auf TextMate zurück. Die App bleibt damit in jedem Zustand lauffähig.

## WICHTIG — zusätzliche Core-Bibliothek nötig (nicht nur Pro-Sprache-Libs)

**Ergänzung nach Rückfrage von Thomas, recherchiert:** Neben den Pro-Sprache-Bibliotheken
(`libtree-sitter-<sprache>.so`) wird zusätzlich eine gemeinsame **Core-/Runtime-Bibliothek**
benötigt — die eigentliche JNI-Bridge zwischen `io.github.rosemoe.sora.langs.treesitter.*`
(`TsLanguage`/`TsLanguageSpec`, das sora-editor selbst mitbringt) und der nativen
Tree-sitter-C-Runtime. Laut sora-editor-eigener Dokumentation
(project-sora.github.io/sora-editor-docs/guide/using-language) müssen die
"Sprachimplementierungen aus `android-tree-sitter` bezogen werden" — sora-editors
`language-treesitter`-Modul bringt diese Core-Runtime also **nicht** automatisch mit, man
muss sie selbst als Dependency ergänzen.

**Quelle:** [AndroidIDEOfficial/android-tree-sitter](https://github.com/AndroidIDEOfficial/android-tree-sitter)
(Maven-Gruppe `com.itsaky.androidide.treesitter`). Bestätigt verfügbare Artefakte:

| Artefakt | Zweck |
|---|---|
| `com.itsaky.androidide.treesitter:android-tree-sitter` | **Core-Library** (JNI-Bridge + Tree-sitter-Runtime) — Pflicht-Dependency, bevor irgendeine Pro-Sprache-Bibliothek lädt |
| `com.itsaky.androidide.treesitter:tree-sitter-java` | Pro-Sprache: Java |
| `com.itsaky.androidide.treesitter:tree-sitter-json` | Pro-Sprache: JSON |
| `com.itsaky.androidide.treesitter:tree-sitter-kotlin` | Pro-Sprache: Kotlin |
| `com.itsaky.androidide.treesitter:tree-sitter-xml` | Pro-Sprache: XML |
| `com.itsaky.androidide.treesitter:tree-sitter-cpp` | Pro-Sprache: C++ |
| (laut Projekt-README zusätzlich vorhanden) | Python, "Logs" (AndroidIDE-eigenes Format) |

**Nicht bestätigt** als vorkompilierte Artefakte dieser Gruppe (Stand der Recherche,
unbedingt zuerst selbst auf Maven Central/der GitHub-Releases-Seite des Projekts
nachprüfen, bevor manuell gebaut wird): **Bash, C (eigenständig, nicht nur als Teil von
C++), CMake, TOML, YAML**. Für diese vier/fünf Sprachen vermutlich manueller Build nötig
(s. u., "Vorgehen falls kein vorkompiliertes Artefakt existiert").

Der genaue Name der nativen `.so`-Datei der Core-Library (`libandroid-tree-sitter.so` vs.
schlicht `libtree-sitter.so`, je nach Artefakt-Version) ist beim Recherchieren nicht
hundertprozentig sicher geklärt worden — **vor dem ersten Build per `unzip -l` auf die vom
Gradle-Cache aufgelöste `.aar`-Datei prüfen**, welcher Dateiname tatsächlich drinsteckt, und
`TreeSitterLanguageSupport.CORE_LIBRARY_NAME`
(`feature/editor/src/main/kotlin/com/codeforge/feature/editor/treesitter/TreeSitterLanguageSupport.kt`)
entsprechend korrigieren, falls er nicht zu `android-tree-sitter` passt.

## Benötigte Artefakte pro Sprache

1. **Native Bibliothek** `libtree-sitter-<sprache>.so` für jede unterstützte ABI
   (`arm64-v8a`, `armeabi-v7a`, `x86_64`) unter `src/main/jniLibs/<abi>/` **ODER** als
   transitive Dependency über die oben genannten `com.itsaky.androidide.treesitter:tree-sitter-*`-
   Gradle-Artefakte (empfohlener Weg, falls für die Sprache vorhanden — kein manueller
   NDK-Build nötig, siehe unten).
2. **Query-Dateien** (`.scm`, Tree-sitter-Query-Syntax) unter
   `src/main/assets/treesitter/<sprache>/`:
   - `highlights.scm` — mappt AST-Knoten auf Highlight-Scopes (analog zu TextMate-Scopes).
   - `blocks.scm` (optional) — definiert faltbare/markierbare Code-Blöcke für die
     Block-Line-Indicators.

## Unterstützte Sprachen in diesem Projekt (Stand nach Erweiterung)

`TreeSitterGrammar` (`TreeSitterLanguageSupport.kt`) deckt jetzt zehn Sprachen ab:
Kotlin, Java, JSON, XML, C++, C, Bash, CMake, TOML, YAML.

| Sprache | Grammar-Repo | Bezugsweg | Status in CodeForgeMobile |
|---|---|---|---|
| Kotlin | `fwcd/tree-sitter-kotlin` | `com.itsaky.androidide.treesitter:tree-sitter-kotlin:4.3.1` | Eingebunden via Maven ✅ |
| Java | `tree-sitter/tree-sitter-java` | `com.itsaky.androidide.treesitter:tree-sitter-java:4.3.1` | Eingebunden via Maven ✅ |
| JSON | `tree-sitter/tree-sitter-json` | `com.itsaky.androidide.treesitter:tree-sitter-json:4.3.1` | Eingebunden via Maven ✅ |
| XML | `tree-sitter-grammars/tree-sitter-xml` | `com.itsaky.androidide.treesitter:tree-sitter-xml:4.3.1` | Eingebunden via Maven ✅ |
| C++ | `tree-sitter/tree-sitter-cpp` | `com.itsaky.androidide.treesitter:tree-sitter-cpp:4.3.1` | Eingebunden via Maven ✅ |
| C | `tree-sitter/tree-sitter-c` | `com.itsaky.androidide.treesitter:tree-sitter-c:4.3.1` | Eingebunden via Maven ✅ |
| Bash | `tree-sitter/tree-sitter-bash` (@ `v0.23.3`, ABI 14) | NDK 29 Clang (`scripts/build-treesitter-lib.sh`) | Nativ in `jniLibs/<abi>/` ✅ |
| CMake | `uyha/tree-sitter-cmake` (@ `v0.7.5`, ABI 14) | NDK 29 Clang (`scripts/build-treesitter-lib.sh`) | Nativ in `jniLibs/<abi>/` ✅ |
| TOML | `tree-sitter-grammars/tree-sitter-toml` (@ `v0.7.0`, ABI 14) | NDK 29 Clang (`scripts/build-treesitter-lib.sh`) | Nativ in `jniLibs/<abi>/` ✅ |
| YAML | `tree-sitter-grammars/tree-sitter-yaml` (@ `v0.7.2`, ABI 14) | NDK 29 Clang (`scripts/build-treesitter-lib.sh`) | Nativ in `jniLibs/<abi>/` ✅ |

### Nativer Build für Bash, CMake, TOML, YAML (`scripts/build-treesitter-lib.sh`)

Da für diese vier Grammatiken keine vorgefertigten AARs auf Maven Central existieren, werden sie über das automatisierte Build-Skript [`scripts/build-treesitter-lib.sh`](file:///data/data/com.termux/files/home/CodeForgeMobile/scripts/build-treesitter-lib.sh) mit dem Android NDK (`ndk = "29.0.14206865"` aus `gradle/libs.versions.toml`) für alle vier ABIs (`arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86`) kompiliert und unter `feature/editor/src/main/jniLibs/<abi>/` abgelegt.

**ABI-Kompatibilitätsprüfung:**
Die Tree-sitter Core-Library (`libandroid-tree-sitter.so:4.3.1`) validiert in `ts_parser_set_language` strikt die Language-Version:
`version >= 13 && version <= 14`.
Neuere Releases auf `main` erzeugen teilweise ABI 15. Daher sind exakt jene Upstream-Tags eingecheckt, die `LANGUAGE_VERSION == 14` implementieren:
- **Bash:** `v0.23.3` (Commit `487734f`)
- **CMake:** `v0.7.5` (Commit `e997bd0`)
- **TOML:** `v0.7.0` (Commit `64b5683`)
- **YAML:** `v0.7.2` (Commit `7708026`)

**Compiler & Flags:**
- Compiler: NDK 29 Clang (`<target>-linux-android24-clang`)
- Flags: `-O3 -fPIC -shared -Wl,-soname,libtree-sitter-<lang>.so -Isrc`
  - YAML zusätzlich: `-std=c11 -DYAML_SCHEMA=core`
- Strip: `llvm-strip --strip-unneeded`

### SHA-256 Prüfsummen der nativen Bibliotheken

| ABI | Bibliothek | SHA-256 Prüfsumme |
|---|---|---|
| `arm64-v8a` | `libtree-sitter-bash.so` | `c10ef9fb65b87dd7ccc916d120c726b649e6ea001ce7f23ec30a5715881a1318` |
| `arm64-v8a` | `libtree-sitter-cmake.so` | `899f6d592f002864ae79ee661d8e3c9a41281d9086153d0633e8ef1ceb6b5743` |
| `arm64-v8a` | `libtree-sitter-toml.so` | `695625bddffb65d3342e0964bd19bafeab9bc616f6f707d55425a6ac7b3a883f` |
| `arm64-v8a` | `libtree-sitter-yaml.so` | `243af42ba2f84278caf68ab69bc83748a1339dac16e2a06c98ce892af3aee691` |
| `armeabi-v7a` | `libtree-sitter-bash.so` | `74f0b70245cf73f85258ba6c439551c201662f44ffdd6d7cda0344c793605654` |
| `armeabi-v7a` | `libtree-sitter-cmake.so` | `5eca052dd0414d07ec799e40e1031212045e034292041a65a9560a393d6100fa` |
| `armeabi-v7a` | `libtree-sitter-toml.so` | `d63672287a105fe1e54bde07fb2fdf8e3cfff1cbf8361443b1ee9de3abfd607a` |
| `armeabi-v7a` | `libtree-sitter-yaml.so` | `6b9b6788df46e4fa92ec50c90c848b1fbec795cfa52920e04ddaddd8ef300b88` |
| `x86_64` | `libtree-sitter-bash.so` | `f9976065d0efb47d50f4cfab41da64b454661c57c756ce5d4817d38c39fad9c1` |
| `x86_64` | `libtree-sitter-cmake.so` | `81434c07c101e358f99792d44f4ebd590adcf15c2db55a2b2c775aff4a9c3403` |
| `x86_64` | `libtree-sitter-toml.so` | `fcd79eaca174f537110ed5765487e75d64c2012c435ce84316633d3e556e6bc8` |
| `x86_64` | `libtree-sitter-yaml.so` | `eb1f63e1d62478c1cc127eef9e00336cea8a69d7a8154240bbaed75b04e6982f` |
| `x86` | `libtree-sitter-bash.so` | `85fca31a6878cf7ca4be5f73241f1e2e2ff212f5ba696b7a56db89fc401a73f8` |
| `x86` | `libtree-sitter-cmake.so` | `20f98d3cbdbe2c46fad89d953c891fba26430f2f88d496280956dba562e2787d` |
| `x86` | `libtree-sitter-toml.so` | `61febe3f9c9372d0f384eefea9c1f8eb3615e8dee528d327abdfd3795753febb` |
| `x86` | `libtree-sitter-yaml.so` | `42b01b0770b4b834d9ab6f0fc7068795c6186fef4ae5ecd2fc6ba89670682e77` |

## Aktueller Stand in diesem Repository

- **Alle zehn Sprachen sind nun vollständig und nativ unterstützt** ✅:
  - Kotlin, Java, JSON, XML, C++, C über Maven-AARs (`com.itsaky.androidide.treesitter:*:4.3.1`).
  - Bash, CMake, TOML, YAML über vorkompilierte, ABI-kompatible native Shared Libraries in `feature/editor/src/main/jniLibs/<abi>/`.
- Kotlin-Integrationscode (`TreeSitterLanguageSupport`, `EditorLanguageFactory`): **vollständig**,
  erweitert um Core-Library-Ladelogik, Reflektions-Lookup für AAR-Klassen und dynamischen Fallback via `TSLanguage.loadLanguage(context, langName)`.
- Core-Library-Dependency (`com.itsaky.androidide.treesitter:android-tree-sitter:4.3.1`) in
  `gradle/libs.versions.toml` und `feature/editor/build.gradle.kts` **eingebunden** ✅.
- Query-Dateien (`highlights.scm`) für **alle zehn Sprachen** unter
  `feature/editor/src/main/assets/treesitter/<sprache>/highlights.scm` **vorhanden und verifiziert** ✅.
- Erkennung von `CMakeLists.txt` und aller 10 Spracherweiterungen in `TreeSitterGrammar.fromPath(path)` **implementiert und unit-getestet** ✅.
- Automatisches Build-Skript `scripts/build-treesitter-lib.sh` dokumentiert, idempotent und funktionsfähig ✅.


