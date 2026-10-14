# Verifikationsbericht: Tree-sitter Core-Library, native Sprachbibliotheken & Query-Assets (Task 02)

**Datum:** 2026-10-14  
**Bearbeiter:** Antigravity AI  
**Status:** Erfolgreich verifiziert ✅

---

## 1. Übersicht & Ziel

Ziel dieses Auftrags (`agy-tasks/02-treesitter-native-libs.md`) war die Vervollständigung der Tree-sitter-Unterstützung in `:feature:editor` für die zehn Ziel-Sprachen:
**Kotlin, Java, JSON, XML, C++, C, Bash, CMake, TOML und YAML**.

Hierbei waren folgende Kernkomponenten einzubinden und zu verifizieren:
1. **Core-Library (`android-tree-sitter`)**: Bereitstellung der JNI-Bridge und C-Runtime als Gradle-Dependency via Versionskatalog (`gradle/libs.versions.toml`).
2. **Pro-Sprache-Bibliotheken (`tree-sitter-*`)**:
   - Vorkompilierte AARs von Maven Central für Kotlin, Java, JSON, XML, C++ sowie C.
   - Klärung des Status für die restlichen 4 Sprachen (Bash, CMake, TOML, YAML).
3. **Query-Dateien (`highlights.scm`)**: Vorhaltung der Highlight-Queries unter `feature/editor/src/main/assets/treesitter/<sprache>/highlights.scm` für alle 10 Sprachen.
4. **Resilientes Laden & Fallbacks**: Prüfung von `TreeSitterLanguageSupport`, `TreeSitterGrammar` und `EditorLanguageFactory` auf sauberen Fallback zu TextMate/Plaintext, falls eine native Bibliothek auf der Ziel-Plattform nicht vorhanden ist.

---

## 2. Durchgeführte Maßnahmen & Ergebnisse

### 2.1 Core-Library & Maven-Sprachartefakte (Schritt 1 & 2a)

Auf Maven Central (`com.itsaky.androidide.treesitter`) wurden folgende native AAR-Artefakte in der aktuellen stabilen Version `4.3.1` identifiziert:
* `com.itsaky.androidide.treesitter:android-tree-sitter:4.3.1` (Core-Library, JNI-Bridge + Runtime)
* `com.itsaky.androidide.treesitter:tree-sitter-kotlin:4.3.1`
* `com.itsaky.androidide.treesitter:tree-sitter-java:4.3.1`
* `com.itsaky.androidide.treesitter:tree-sitter-json:4.3.1`
* `com.itsaky.androidide.treesitter:tree-sitter-xml:4.3.1`
* `com.itsaky.androidide.treesitter:tree-sitter-cpp:4.3.1`
* `com.itsaky.androidide.treesitter:tree-sitter-c:4.3.1` (ebenfalls auf Maven Central verfügbar!)

**Prüfung der nativen Bibliotheken (`unzip -l`):**
- In `android-tree-sitter-4.0.1.aar` und `4.3.1.aar` heißt die native Core-Bibliothek exakt `libandroid-tree-sitter.so` für alle vier ABIs (`arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`). Die Konstante `CORE_LIBRARY_NAME = "android-tree-sitter"` in `TreeSitterLanguageSupport.kt` ist somit exakt zutreffend.
- In den Sprach-AARs heißen die nativen Bibliotheken `libtree-sitter-<lang>.so` (z. B. `libtree-sitter-kotlin.so`).
- Jede AAR liefert eine dedizierte Wrapper-Klasse mit JNI-Bindung (`com.itsaky.androidide.treesitter.<lang>.TSLanguage<Lang>.getInstance()`), wodurch das Laden und Erzeugen der `TSLanguage`-Instanzen ohne Abhängigkeit von Dateipfaden in `nativeLibraryDir` erfolgt.

**Eintrag im Versionskatalog:**
In [gradle/libs.versions.toml](file:///data/data/com.termux/files/home/CodeForgeMobile/gradle/libs.versions.toml) eingepflegt:
```toml
[versions]
treesitter = "4.3.1"

[libraries]
treesitter-core = { group = "com.itsaky.androidide.treesitter", name = "android-tree-sitter", version.ref = "treesitter" }
treesitter-kotlin = { group = "com.itsaky.androidide.treesitter", name = "tree-sitter-kotlin", version.ref = "treesitter" }
treesitter-java = { group = "com.itsaky.androidide.treesitter", name = "tree-sitter-java", version.ref = "treesitter" }
treesitter-json = { group = "com.itsaky.androidide.treesitter", name = "tree-sitter-json", version.ref = "treesitter" }
treesitter-xml = { group = "com.itsaky.androidide.treesitter", name = "tree-sitter-xml", version.ref = "treesitter" }
treesitter-cpp = { group = "com.itsaky.androidide.treesitter", name = "tree-sitter-cpp", version.ref = "treesitter" }
treesitter-c = { group = "com.itsaky.androidide.treesitter", name = "tree-sitter-c", version.ref = "treesitter" }
```
Und in [feature/editor/build.gradle.kts](file:///data/data/com.termux/files/home/CodeForgeMobile/feature/editor/build.gradle.kts) eingebunden.

### 2.2 Status von C, Bash, CMake, TOML, YAML (Schritt 2b)

- **C:** Entgegen der ursprünglichen Annahme in `TREESITTER.md` ist `tree-sitter-c` ebenfalls als vorgefertigtes Maven-Artefakt verfügbar und nun direkt eingebunden.
- **Bash, CMake, TOML, YAML:** Für diese vier Grammatiken existieren im Maven Central keine vorgefertigten AARs von `AndroidIDEOfficial`.
  - Die NDK-Toolchain auf Termux (`HomuHomu833/llvm-custom`) bricht bei isolierten Backend-Kompilierungen ohne komplettes Sysroot/Toolchain-Environment ab (`Instruction Combining seems stuck in an infinite loop`).
  - Gemäß den Anweisungen im Auftrag wurde der native Bau dieser vier Bibliotheken für einen Host mit regulärem Android-NDK dokumentiert.
  - Alle vier Sprachen besitzen nun jedoch die vollständigen `highlights.scm`-Query-Dateien. Fehlt die `.so`-Bibliothek auf dem Gerät, meldet `TreeSitterLanguageSupport.isAvailable(...)` zur Laufzeit sicher `false` und `EditorLanguageFactory` greift transparent auf TextMate bzw. Plaintext zurück.

### 2.3 Query-Dateien für alle 10 Sprachen (Schritt 3)

Aus den offiziellen Upstream-Grammar-Repositories wurden die `highlights.scm`-Query-Dateien bezogen und unter `feature/editor/src/main/assets/treesitter/<sprache>/highlights.scm` abgelegt:

| Sprache | Upstream-Quelle | Highlights-Query-Größe |
|---|---|---|
| `kotlin` | `fwcd/tree-sitter-kotlin` | 6.385 Bytes |
| `java` | `tree-sitter/tree-sitter-java` | 2.064 Bytes |
| `json` | `tree-sitter/tree-sitter-json` | 172 Bytes |
| `xml` | `tree-sitter-grammars/tree-sitter-xml` | 2.281 Bytes |
| `cpp` | `tree-sitter/tree-sitter-cpp` | 1.032 Bytes |
| `c` | `tree-sitter/tree-sitter-c` | 1.432 Bytes |
| `bash` | `tree-sitter/tree-sitter-bash` | 577 Bytes |
| `cmake` | `uyha/tree-sitter-cmake` | 10.554 Bytes |
| `toml` | `tree-sitter-grammars/tree-sitter-toml` | 501 Bytes |
| `yaml` | `tree-sitter-grammars/tree-sitter-yaml` | 962 Bytes |

Alle 10 Query-Dateien wurden im erzeugten Debug-AAR (`editor-debug.aar`) verifiziert.

### 2.4 Resiliente Ladelogik in `TreeSitterLanguageSupport`

Die Disassemblierung von `TSLanguage.loadLanguage` im Bytecode ergab, dass `loadLanguage(context, langName)` den Pfad `/libtree-sitter-<langName>.so` und den Symbolnamen `tree_sitter_<langName>` erwartet.
1. `TreeSitterGrammar` wurde um das Property `langName` (`"kotlin"`, `"java"` etc.) erweitert, während `libraryName` abwärtskompatibel auf `"tree-sitter-$langName"` (`System.loadLibrary`) zeigt.
2. In `TreeSitterLanguageSupport.loadTsLanguage()` wird vorrangig die generierte Wrapper-Klasse (`com.itsaky.androidide.treesitter.<lang>.TSLanguage<Lang>.getInstance()`) aufgerufen. Falls keine Wrapper-Klasse vorliegt, greift der Fallback auf `TSLanguage.loadLanguage(context, grammar.langName)`.

---

## 3. Test- & Build-Ergebnisse

- **Unit-Tests:** `./gradlew :feature:editor:testDebugUnitTest`
  - `TreeSitterGrammarTest`: 4/4 Tests erfolgreich (inkl. Verifikation aller 10 Grammatiken, Bibliotheksnamen und Asset-Pfade).
  - `ColorLiteralScannerTest`: 5/5 Tests erfolgreich.
  - Gesamt: **9/9 Tests bestanden**.
- **AAR-Build:** `./gradlew :feature:editor:assembleDebug`
  - Erfolgreich gebaut (`BUILD SUCCESSFUL in 2m 58s`).
  - `unzip -l editor-debug.aar` bestätigt die vollständige Aufnahme aller 10 `assets/treesitter/<sprache>/highlights.scm`.
- **Statische Codeanalyse / Hardcoded Strings:**
  - `python3 scripts/find_hardcoded_strings.py --check`: 0 Fundstellen.

---

---

## 4. Phase 2 / Task 20: Native Bibliotheken für Bash, CMake, TOML, YAML

In Task 20 (`agy-tasks/20-treesitter-remaining-native-langs.md`) wurden die verbleibenden 4 Sprachen nativ kompiliert und vollständig integriert.

### 4.1 Reverse Engineering & ABI-Kompatibilität

Eine Disassemblierung der Methode `ts_parser_set_language` in `libandroid-tree-sitter.so` ergab die exakte ABI-Bedingung:
```arm64
ldr  w8, [x20]       ; Version aus TSLanguage-Header
sub  w8, w8, #0xd    ; subtrahiere 13
cmp  w8, #0x1        ; prüfe ob Differenz <= 1
b.ls 0x3b9e8         ; OK nur für 13 und 14
```
Die Core-Library akzeptiert ausschließlich Tree-sitter Language Version **13 und 14**. Neuere Git-HEAD-Commits generieren teilweise bereits Version 15, was zu einem Laufzeitfehler geführt hätte.

Daher wurden exakt jene stabilen Upstream-Release-Tags ausgewählt, die ABI 14 implementieren:
- **Bash (`tree-sitter/tree-sitter-bash`):** Tag `v0.23.3` (Commit `487734f`, `LANGUAGE_VERSION 14`)
- **CMake (`uyha/tree-sitter-cmake`):** Tag `v0.7.5` (Commit `e997bd0`, `LANGUAGE_VERSION 14`)
- **TOML (`tree-sitter-grammars/tree-sitter-toml`):** Tag `v0.7.0` (Commit `64b5683`, `LANGUAGE_VERSION 14`)
- **YAML (`tree-sitter-grammars/tree-sitter-yaml`):** Tag `v0.7.2` (Commit `7708026`, `LANGUAGE_VERSION 14`)

### 4.2 Build-Skript `scripts/build-treesitter-lib.sh`

Das Skript ermittelt automatisch das Android NDK (Version `29.0.14206865` aus `gradle/libs.versions.toml`) und kompiliert die C/C++-Sourcen für alle vier ABIs (`arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86`) mittels Clang 21:
- Compiler: `aarch64-linux-android24-clang`, `armv7a-linux-androideabi24-clang`, `x86_64-linux-android24-clang`, `i686-linux-android24-clang`
- Flags: `-O3 -fPIC -shared -Wl,-soname,libtree-sitter-<lang>.so -Isrc`
  - YAML zusätzlich: `-std=c11 -DYAML_SCHEMA=core`
- Strip: `llvm-strip --strip-unneeded`

### 4.3 SHA-256 Prüfsummen

| ABI | Datei | SHA-256 |
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

### 4.4 Verifikation der Symbole & dynamischen Abhängigkeiten

Mit `llvm-readelf --dyn-symbols` wurde nachgewiesen, dass jede Bibliothek das für `TSLanguage.loadLanguage(context, langName)` notwendige Exportsymbol bereitstellt:
- `tree_sitter_bash` (FUNC GLOBAL DEFAULT)
- `tree_sitter_cmake` (FUNC GLOBAL DEFAULT)
- `tree_sitter_toml` (FUNC GLOBAL DEFAULT)
- `tree_sitter_yaml` (FUNC GLOBAL DEFAULT)

### 4.5 Packaging & AAR-Verifikation

`unzip -l feature/editor/build/outputs/aar/editor-debug.aar` bestätigt:
- Alle 16 nativen Shared Libraries (`jni/<abi>/libtree-sitter-<lang>.so` für 4 ABIs × 4 Sprachen) sind im AAR enthalten.
- Alle 10 `assets/treesitter/<sprache>/highlights.scm` sind im AAR enthalten.

### 4.6 Unit-Tests & Code-Qualität

- `./gradlew :feature:editor:testDebugUnitTest`:
  - `TreeSitterGrammarTest`:
    - `cmakeListsRecognizedDirectlyAndInPath` ✅
    - `standardExtensionsRecognized` ✅
    - `unsupportedFilesReturnNull` ✅
    - `grammarPropertiesMatchConvention` ✅
    - `queryAssetsExistForEveryGrammar` ✅
    - `nativeLibsExistForManuallyBuiltGrammars` ✅
    - `isAvailableHandlesMissingRuntimeGracefully` ✅
  - `ColorLiteralScannerTest`: 5/5 ✅
  - Gesamt: **12/12 Tests bestanden**.
- `./gradlew :feature:editor:assembleDebug`: BUILD SUCCESSFUL.
- `python3 scripts/find_hardcoded_strings.py --check`: 0 Fundstellen.

---

## 5. Fazit

**10 von 10 Sprachen** (Kotlin, Java, JSON, XML, C++, C, Bash, CMake, TOML, YAML) sind nun vollständig nativ in CodeForgeMobile integriert.
- 6 Sprachen via Maven-Central AARs (`com.itsaky.androidide.treesitter:*:4.3.1`).
- 4 Sprachen via reproduzierbar und automatisiert mit NDK 29 gebauter Shared Libraries (`libtree-sitter-*.so`).
- Alle 10 Sprachen verfügen über vollständige `highlights.scm`-Query-Dateien.
- Alle Akzeptanzkriterien aus `agy-tasks/02-treesitter-native-libs.md` und `agy-tasks/20-treesitter-remaining-native-langs.md` sind zu 100% erfüllt.

