# Auftrag: Tree-sitter Core-Library + native Parser-Bibliotheken + Query-Assets

## Kontext

Repo: CodeForgeMobile (Android, Kotlin, Gradle-Multi-Module). Arbeitsverzeichnis:
Projekt-Root (`CodeForgeMobile/`).

Lies zuerst `feature/editor/TREESITTER.md` im Repo — das ist der vollständige,
bereits recherchierte Hintergrund inkl. Maven-Artefakt-Koordinaten. Lies außerdem
`feature/editor/src/main/kotlin/com/codeforge/feature/editor/treesitter/TreeSitterLanguageSupport.kt`
(bereits um Core-Library-Ladelogik und zehn Sprach-Einträge erweitert) und
`feature/editor/build.gradle.kts` (dort fehlt die eigentliche Dependency noch).

**Wichtige Korrektur gegenüber einer früheren Version dieses Auftrags:** Es reicht
NICHT, nur Pro-Sprache-Bibliotheken (`libtree-sitter-<sprache>.so`) zu bauen/zu
beschaffen. sora-editors `language-treesitter`-Modul bringt die eigentliche
JNI-Bridge/Tree-sitter-Runtime nicht automatisch mit — diese muss zusätzlich als
**Core-Library** aus dem Projekt `AndroidIDEOfficial/android-tree-sitter`
(Maven-Gruppe `com.itsaky.androidide.treesitter`) bezogen werden. Ohne sie schlägt
jeder `System.loadLibrary("tree-sitter-<sprache>")`-Aufruf fehl (ungelöste Symbole).

## Sprachen in diesem Auftrag (zehn insgesamt)

Kotlin, Java, JSON (bereits im ursprünglichen Scope), zusätzlich: **XML, C++, C,
Bash, CMake, TOML, YAML** (von Thomas ergänzt).

## Aufgabe

### 1. Core-Library als Gradle-Dependency ergänzen (zuerst, alles andere hängt davon ab)

In `feature/editor/build.gradle.kts`:
```kotlin
implementation("com.itsaky.androidide.treesitter:android-tree-sitter:<aktuelle-version>")
```
Finde die aktuelle Version auf Maven Central
(`https://central.sonatype.com/artifact/com.itsaky.androidide.treesitter/android-tree-sitter`)
oder den Releases der GitHub-Repo. Trage die Version im Versions-Catalog
(`gradle/libs.versions.toml`) ein statt sie hart im Modul zu verdrahten (Konvention
dieses Projekts — siehe andere Einträge in derselben Datei).

**Nach dem Dependency-Sync:** per `unzip -l` auf die im Gradle-Cache aufgelöste
`.aar`-Datei (Pfad z. B. unter
`~/.gradle/caches/modules-2/files-2.1/com.itsaky.androidide.treesitter/android-tree-sitter/...`)
prüfen, wie die enthaltene `.so`-Datei tatsächlich heißt (`libandroid-tree-sitter.so`
oder `libtree-sitter.so` o. ä.). Falls der Name nicht `android-tree-sitter` ist, die
Konstante `CORE_LIBRARY_NAME` in `TreeSitterLanguageSupport.kt` entsprechend auf den
tatsächlichen Namen korrigieren (ohne `lib`-Präfix und `.so`-Endung, wie
`System.loadLibrary` es erwartet).

### 2. Pro-Sprache-Bibliotheken — Maven-Artefakt zuerst prüfen, erst dann manuell bauen

Für jede der zehn Sprachen in dieser Reihenfolge vorgehen:

**a) Bestätigt als vorkompiliertes Artefakt verfügbar (laut Recherche in TREESITTER.md):**
Kotlin, Java, JSON, XML, C++. Für diese einfach die passende Dependency ergänzen,
z. B.:
```kotlin
implementation("com.itsaky.androidide.treesitter:tree-sitter-kotlin:<version>")
implementation("com.itsaky.androidide.treesitter:tree-sitter-java:<version>")
implementation("com.itsaky.androidide.treesitter:tree-sitter-json:<version>")
implementation("com.itsaky.androidide.treesitter:tree-sitter-xml:<version>")
implementation("com.itsaky.androidide.treesitter:tree-sitter-cpp:<version>")
```
Kein manueller NDK-Build nötig — die `.aar`-Dateien bringen die `.so` pro ABI bereits
fertig mit.

**b) Nicht bestätigt (C, Bash, CMake, TOML, YAML):** ZUERST auf Maven Central bzw.
der GitHub-Releases-Seite von `AndroidIDEOfficial/android-tree-sitter` nachschauen,
ob es inzwischen doch `tree-sitter-c`, `tree-sitter-bash`, `tree-sitter-cmake`,
`tree-sitter-toml`, `tree-sitter-yaml`-Artefakte gibt (das Projekt generiert laut
eigener Doku Module aus einer `grammars.json` und erweitert sein Angebot
gelegentlich). Falls ja: wie in (a) einfach als Dependency ergänzen.

Falls NICHT vorhanden, manuell bauen (Android NDK erforderlich — in Termux prüfen,
ob `ndk-build`/`clang` mit Android-Target verfügbar ist; falls nicht, diesen Teil
auf einen Host mit NDK verschieben und das in der Commit-Message vermerken):
   - Grammar-Quelle je Sprache:
     - C: `tree-sitter/tree-sitter-c`
     - Bash: `tree-sitter/tree-sitter-bash`
     - CMake: `uyha/tree-sitter-cmake` (oder `tree-sitter-grammars/tree-sitter-cmake`,
       falls dort aktiver gepflegt — prüfen, welches Repo aktueller ist)
     - TOML: `tree-sitter-grammars/tree-sitter-toml`
     - YAML: `tree-sitter-grammars/tree-sitter-yaml`
   - `tree-sitter-cli generate` im jeweiligen Grammar-Repo, um `src/parser.c` zu
     erzeugen (falls nicht bereits im Repo enthalten). Bei C, Bash und CMake prüfen,
     ob ein externer Scanner (`src/scanner.c`) existiert — falls ja, mitkompilieren.
   - Als Build-Vorlage **zuerst** das Build-Setup von
     `AndroidIDEOfficial/android-tree-sitter` selbst heranziehen (hat die
     Android/NDK/ABI-Spezifika bereits gelöst, inkl. Verlinkung gegen dieselbe
     Core-Runtime wie oben) — nur falls das nicht direkt wiederverwendbar ist,
     ersatzweise ein minimales `Android.mk`/`CMakeLists.txt` analog zu
     `libs/termux-emulator/src/main/jni/Android.mk` in diesem Repo schreiben, das
     `parser.c` (+ `scanner.c` falls vorhanden) gegen dieselbe Tree-sitter-Runtime
     linkt wie die Core-Library aus Schritt 1 (nicht gegen eine zweite,
     eingebettete Kopie der Runtime — sonst Symbol-Duplikate/ABI-Mismatch mit der
     Core-Library).
   - Für alle vom Projekt unterstützten ABIs bauen (`arm64-v8a`, `armeabi-v7a`,
     `x86_64`, siehe `gradle/libs.versions.toml`/`app/build.gradle.kts` für
     eventuelle Einschränkungen).
   - Ablage: `feature/editor/src/main/jniLibs/<abi>/libtree-sitter-<sprache>.so`

### 3. Query-Dateien für alle zehn Sprachen

Für jede Sprache unter `feature/editor/src/main/assets/treesitter/<sprache>/`
(Verzeichnisnamen exakt wie in `TreeSitterGrammar.queryAssetDir` in
`TreeSitterLanguageSupport.kt`: `kotlin`, `java`, `json`, `xml`, `cpp`, `c`, `bash`,
`cmake`, `toml`, `yaml`):
- `highlights.scm` (Pflicht) — orientiere dich an den `queries/highlights.scm`-
  Dateien, die praktisch jedes offizielle Tree-sitter-Grammar-Repo selbst im
  Verzeichnis `queries/` mitliefert (das ist der Standard-Ort in den
  `tree-sitter/tree-sitter-*`- und `tree-sitter-grammars/*`-Repos — meist direkt
  verwendbar, ggf. leicht an sora-editors Scope-Namen anpassen).
- `blocks.scm` (optional) — nur wenn eine sinnvolle Quelle existiert, sonst
  auslassen statt zu raten.

### 4. `TreeSitterGrammar.fromPath` für CMake prüfen

`CMakeLists.txt` hat keine klassische Dateiendung. Aktuell deckt `fromPath` nur
`.cmake`-Dateien ab (siehe Kommentar im Code). Prüfe, ob `EditorLanguageFactory`
bzw. die aufrufende Stelle in `:feature:editor` den vollen Dateinamen (nicht nur
die Endung) kennt, und ergänze bei Bedarf eine Sonderbehandlung für den exakten
Dateinamen `CMakeLists.txt` (z. B. ein zusätzlicher Parameter `fileName` an
`fromPath`, oder ein Vorab-Check auf `path.substringAfterLast('/') == "CMakeLists.txt"`),
damit CMake-Projektdateien ohne Endung ebenfalls erkannt werden. Nur umsetzen,
wenn die aufrufende Stelle das unterstützt, ohne andere Call-Sites zu brechen.

## Akzeptanzkriterien

- `feature/editor/build.gradle.kts` enthält die Core-Library-Dependency plus je
  eine Pro-Sprache-Dependency für Kotlin, Java, JSON, XML, C++ (Maven-Weg).
- Für C, Bash, CMake, TOML, YAML: entweder ebenfalls per Maven-Dependency gelöst,
  oder `.so`-Dateien unter `src/main/jniLibs/<abi>/` vorhanden — pro Sprache
  dokumentiert, welcher der beiden Wege gewählt wurde.
- `./gradlew :feature:editor:assembleDebug` baut erfolgreich.
- `TreeSitterLanguageSupport.isAvailable(...)` liefert zur Laufzeit `true` für
  mindestens die fünf Maven-basierten Sprachen auf einem Testgerät/Emulator mit
  passender ABI.
- Für jede der zehn Sprachen existiert mindestens `highlights.scm` unter dem
  korrekten Asset-Pfad — oder ist explizit in der Commit-Message als "noch offen"
  vermerkt.
- `feature/editor/TREESITTER.md`, Abschnitt "Aktueller Stand in diesem Repository",
  aktualisiert auf den tatsächlich erreichten Stand (welche Sprachen per Maven,
  welche manuell, welche noch offen).
- Falls eine Sprache aus Zeit-/Werkzeuggründen nicht fertiggestellt werden kann:
  kein Build-Fehler (`EditorLanguageFactory` fällt automatisch auf TextMate bzw.
  Plain-Text zurück) — dokumentieren, welche fehlt und warum.

## Hinweis zur NDK-Umgebung in Termux

Ein vollständiges Android-NDK in Termux auf dem Smartphone zu betreiben ist nicht
in jeder Termux-Installation Standard — prüfe zuerst, ob `ndk-build` oder `clang`
mit Android-Target verfügbar ist. Falls nicht, beschränke den manuellen-Build-Teil
(Schritt 2b) auf das Beschaffen/Schreiben der `.scm`-Dateien und dokumentiere, dass
der native Build einen Host mit installiertem NDK erfordert — die Maven-basierten
Sprachen (Schritt 2a) sind davon nicht betroffen und sollten auch in Termux
funktionieren, da dort nur Gradle-Dependency-Resolution nötig ist, kein lokaler
NDK-Build.
