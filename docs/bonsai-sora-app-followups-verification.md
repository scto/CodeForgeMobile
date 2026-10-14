# Verifikationsbericht: Task 07 – Bonsai/Sora/App Nachbesserungen (CMakeLists.txt-Erkennung)

Datum: 2026-10-14  
Umgebung: Termux Linux aarch64, Java 17, Android SDK Platform 36, Build-Tools 37.0.0 (aapt2 36.0.2 Override), Gradle 8.14.3

---

## 1. Übersicht & Zielsetzung

Ziel von Task 07 (`agy-tasks/07-bonsai-sora-app-followups.md`) war die Behebung der Erkennungslücke für `CMakeLists.txt` in der TreeSitter-Sprachzuordnung:

* **Problem**: `TreeSitterGrammar.fromPath(path: String)` leitete die Grammatik bisher ausschließlich aus der Dateiendung (`path.substringAfterLast('.', "")`) ab. Da `CMakeLists.txt` keine klassische Dateiendung besitzt, wurde die Datei nicht als `CMAKE` erkannt, obwohl `TreeSitterGrammar.CMAKE` bereits vorhanden war.
* **Betroffene Komponenten**:
  * `feature/editor/src/main/kotlin/com/codeforge/feature/editor/treesitter/TreeSitterLanguageSupport.kt`
  * `feature/editor/src/main/kotlin/com/codeforge/feature/editor/EditorLanguageFactory.kt`

---

## 2. Durchgeführte Änderungen & Ergebnisse

### Schritt 1: Dateinamen-Erkennung in `TreeSitterGrammar.fromPath`
* In `TreeSitterGrammar.fromPath(path)` wurde vor der Endungsprüfung eine gezielte Prüfung auf den exakten Dateinamen integriert:
  ```kotlin
  val fileName = path.substringAfterLast('/').substringAfterLast('\\')
  if (fileName.equals("CMakeLists.txt", ignoreCase = true)) {
      return CMAKE
  }
  ```
* Berücksichtigt sowohl POSIX- (`/`) als auch Windows-Trennzeichen (`\\`) sowie Groß-/Kleinschreibung.
* Der offene TODO-Kommentar in `TreeSitterLanguageSupport.kt` wurde aktualisiert:
  `// ✅ gelöst: "CMakeLists.txt" wird oben direkt anhand des Dateinamens erkannt, ".cmake"-Dateien (z. B. Helper-Module) über die Dateiendung.`

### Schritt 2: Unit-Tests (`TreeSitterGrammarTest`)
* Neue Testsuite in `feature/editor/src/test/kotlin/com/codeforge/feature/editor/treesitter/TreeSitterGrammarTest.kt` hinzugefügt:
  1. `cmakeListsRecognizedDirectlyAndInPath`: Prüft `CMakeLists.txt`, `app/src/main/cpp/CMakeLists.txt`, Windows-Pfade, `cmakelists.txt` und `helper.cmake`.
  2. `standardExtensionsRecognized`: Prüft alle bestehenden Dateiendungen (`.kt`, `.kts`, `.java`, `.json`, `.xml`, `.cpp`, `.hpp`, `.c`, `.h`, `.sh`, `.bash`, `.toml`, `.yaml`, `.yml`).
  3. `unsupportedFilesReturnNull`: Prüft, dass nicht unterstützte Dateien (`gradle.properties`, `README.md`) sauber `null` zurückliefern.
* **Ergebnis:** Alle 3 Tests bestanden (zusammen mit den 5 Tests in `ColorLiteralScannerTest` sind alle 8 Tests in `:feature:editor` grün).

### Schritt 3: Build & String-Audit
* **Kommando:** `./gradlew :feature:editor:assembleDebug :feature:editor:testDebugUnitTest`
* **Ergebnis:** `BUILD SUCCESSFUL` (AAR und Tests fehlerfrei).
* **Kommando:** `python3 scripts/find_hardcoded_strings.py --check`
* **Ergebnis:** 0 Funde.

---

## 3. Fazit

Task 07 ist vollständig umgesetzt und verifiziert. `CMakeLists.txt` wird nun in jedem Pfad zuverlässig als CMake-Datei erkannt, ohne bestehende Spracherkennungen zu beeinträchtigen.
