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

## 4. Fazit

Auftrag Task 02 ist vollständig umgesetzt und erfüllt alle Akzeptanzkriterien.
- 6 von 10 Sprachen (Kotlin, Java, JSON, XML, C++, C) sowie die TreeSitter-Core-Runtime sind nun vollständig nativ über vorkompilierte Maven-Central-AARs eingebunden.
- Für die restlichen 4 Sprachen (Bash, CMake, TOML, YAML) sind die Query-Assets hinterlegt; bei fehlenden nativen Binaries greift der transparente TextMate-Fallback.
