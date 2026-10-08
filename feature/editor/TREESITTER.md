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

| Sprache | Grammar-Repo (falls manueller Build nötig) | Vorkompiliertes Artefakt? |
|---|---|---|
| Kotlin | `fwcd/tree-sitter-kotlin` | `tree-sitter-kotlin` ✅ |
| Java | `tree-sitter/tree-sitter-java` | `tree-sitter-java` ✅ |
| JSON | `tree-sitter/tree-sitter-json` | `tree-sitter-json` ✅ |
| XML | `tree-sitter-grammars/tree-sitter-xml` | `tree-sitter-xml` ✅ |
| C++ | `tree-sitter/tree-sitter-cpp` | `tree-sitter-cpp` ✅ |
| C | `tree-sitter/tree-sitter-c` | ❓ ungeprüft, vermutlich manueller Build |
| Bash | `tree-sitter/tree-sitter-bash` | ❓ ungeprüft, vermutlich manueller Build |
| CMake | `uyha/tree-sitter-cmake` bzw. `tree-sitter-grammars/tree-sitter-cmake` | ❓ ungeprüft, vermutlich manueller Build |
| TOML | `tree-sitter-grammars/tree-sitter-toml` | ❓ ungeprüft, vermutlich manueller Build |
| YAML | `tree-sitter-grammars/tree-sitter-yaml` | ❓ ungeprüft, vermutlich manueller Build |

Empfohlener Build-Weg für die ❓-Sprachen, falls kein vorkompiliertes Artefakt existiert:
`tree-sitter-cli generate` generiert aus `grammar.js` die `parser.c`; diese wird zusammen
mit der Tree-sitter-Runtime über ein minimales `Android.mk`/`CMakeLists.txt` je Sprache zu
`libtree-sitter-<sprache>.so` kompiliert — als Vorlage eignet sich das Build-Setup von
`AndroidIDEOfficial/android-tree-sitter` selbst besser als ein komplett eigenes
`Android.mk` (das Projekt generiert laut eigener Doku Gradle-Module aus einer
`grammars.json`-Konfiguration und hat damit die Android/NDK-Spezifika bereits gelöst),
alternativ strukturell analog zum bereits vorhandenen `src/main/jni/Android.mk` in
`libs/termux-emulator` (dort für `termux.c`/PTY, hier für den Parser).

## Aktueller Stand in diesem Repository

- Kotlin-Integrationscode (`TreeSitterLanguageSupport`, `EditorLanguageFactory`): **fertig**,
  erweitert um die Core-Library-Ladelogik und die sieben neuen Sprach-Einträge (XML, C++, C,
  Bash, CMake, TOML, YAML).
- Core-Library-Dependency (`com.itsaky.androidide.treesitter:android-tree-sitter`) in
  `feature/editor/build.gradle.kts`: **noch nicht ergänzt** — siehe `agy-tasks/02-treesitter-native-libs.md`.
- Native `.so`-Dateien (weder Core noch Pro-Sprache) und `.scm`-Query-Assets: **nicht
  enthalten** — dieses Sandbox-Environment hat weder NDK-Toolchain noch zuverlässigen
  Netzwerkzugriff, um Maven-Artefakte aufzulösen oder Grammar-Repos zu klonen. Bis diese
  Artefakte ergänzt werden, läuft der Editor automatisch mit TextMate-Highlighting
  (funktional vollständig, nur weniger präzise als AST-basiertes Highlighting) — für die
  sieben neuen Sprachen ohne eigene TextMate-Grammar-Datei (siehe `ASSETS.md`) fällt das
  Highlighting für diese Dateitypen bis dahin auf Plain-Text zurück, nicht auf TextMate.
