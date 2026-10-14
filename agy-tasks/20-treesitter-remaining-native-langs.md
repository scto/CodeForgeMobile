> **ERLEDIGT (Stand 2026-10-14):** Umgesetzt mit reproduzierbarem Build-Skript `scripts/build-treesitter-lib.sh`, NDK 29 Clang für alle 4 ABIs (`arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86`). Bericht: `docs/treesitter-native-libs-verification.md`. Nur erneut ausführen, wenn sich der Gegenstand ändert (Regression).

# Task 20 – Tree-sitter: native Bibliotheken für Bash, CMake, TOML, YAML

**Ausgangslage (Task 02, Commit `2d66ecc`):** Kotlin, Java, JSON, XML, C++ und C sind über Maven-AARs (`com.itsaky.androidide.treesitter`) nativ eingebunden; für **Bash, CMake, TOML, YAML** liegen nur die `highlights.scm`-Queries unter
`feature/editor/src/main/assets/treesitter/<sprache>/`, die native `.so` fehlt – `TreeSitterLanguageSupport` fällt auf TextMate zurück. Lies `docs/treesitter-native-libs-verification.md`, `feature/editor/TREESITTER.md`, `TreeSitterLanguageSupport.kt`.

## Aufgabe

1. Pro Sprache prüfen, ob es ein fertiges Maven-/Release-Artefakt gibt (`tree-sitter-bash`, `-cmake`, `-toml`, `-yaml` unter `com.itsaky.androidide.treesitter` bzw. im Projekt `AndroidIDEOfficial/androidide-tree-sitter`, passend zur Core-Version `4.3.1`). Gibt es eines: Dependency über `gradle/libs.versions.toml` ergänzen und Test erweitern.
2. Sonst `.so` selbst bauen: Grammar-Repo (`tree-sitter-grammars/...`, `tree-sitter/tree-sitter-bash`, …) auf einen zur Core-Version **ABI-kompatiblen** Tag auschecken, mit NDK (`ndk` in `libs.versions.toml`, aktuell 29.0.14206865) für `arm64-v8a`, `armeabi-v7a` (Projekt zielt auf aarch64/arm) bauen; Ablage `feature/editor/src/main/jniLibs/<abi>/libtree-sitter-<sprache>.so`. Parser-ABI-Version gegen die Core-Library prüfen (inkompatible Parser **nicht** einbauen).
3. Lizenzen in `feature/editor/ASSETS.md`/`TREESITTER.md` ergänzen (alle MIT). Build-Skript (`scripts/build-treesitter-lib.sh`) nur anlegen, wenn Schritt 2 nötig war; Skript dokumentiert Tag, Compiler, Flags.
4. Test: Ladelogik je Sprache (vorhanden/fehlend → Fallback), Dateiendungen `.sh/.bash`, `CMakeLists.txt/.cmake`, `.toml`, `.yml/.yaml`.

## Grenzen / Akzeptanz

* Native Binaries nicht ungeprüft committen: SHA-256 und Quelle in `TREESITTER.md` notieren. Wenn eine Sprache nicht sauber lösbar ist, TextMate-Fallback belassen und im Bericht begründen.
* `./gradlew :feature:editor:testDebugUnitTest :feature:editor:assembleDebug` grün; Bericht als Abschnitt in `docs/treesitter-native-libs-verification.md`.
