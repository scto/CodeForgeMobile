# Verifikation: Build-Logic, Convention-Plugins und Modul-Builds

Stand: **2026-10-13**  
Ausgeführt und verifiziert auf der Zielplattform **Linux aarch64 (Android/Termux)** gemäß Auftrag [`agy-tasks/12-verify-build-logic.md`](../agy-tasks/12-verify-build-logic.md).

---

## 1. Übersicht & Status

| Prüfschritt | Befehl | Ergebnis | Dauer | Details |
|---|---|---|---|---|
| **Schritt 1** | `./gradlew help` | **Erfolgreich** | 1m 13s | Included Build `build-logic:convention` kompiliert sauber; Syntax- und DSL-Korrekturen |
| **Schritt 2a** | `./gradlew projects` | **Erfolgreich** | < 10s | Alle 44 Teilprojekte (Root + App + 15 Features + 14 Libs + 11 Core + 3 Examples) gelistet |
| **Schritt 2b** | `./gradlew :app:assembleDebug -PcodeforgeBootstrapSkip=true` | **Erfolgreich** | 1m 00s | APK `app-debug.apk` (316 MB) assembliert, alle C/C++-Binaries (`libtermux.so`, `liblocal-socket.so`, `libtermux-bootstrap.so`) für 4 ABIs gebaut |
| **Schritt 3a** | `./gradlew test -PcodeforgeBootstrapSkip=true` | **Erfolgreich** | 6m 41s | Alle JVM-Unit-Tests über sämtliche Module bestanden (1696 Tasks, 0 Fehler) |
| **Schritt 3b** | `./gradlew ktlintCheck` | **Erfolgreich** | 7m 59s | Reiner Style-Report über alle Module (`ignoreFailures = true`, kein Auto-Format) |

---

## 2. Detaillierte Fehleranalyse und behobene Probleme

### Schritt 1: `./gradlew help` & Convention-Plugins
- **`AndroidLibraryConventionPlugin`:**
  - *Problem:* `consumerProguardFiles` wurde fälschlicherweise als Property-Zuweisung aufgerufen.
  - *Fix:* Aufruf von `consumerProguardFile("consumer-rules.pro")` verwendet.
- **`AndroidApplicationConventionPlugin`:**
  - *Problem:* `buildTypes.getByName("release")` setzte voraus, dass `proguard-rules.pro` existiert.
  - *Fix:* Absicherung über `if (file("proguard-rules.pro").exists())`.
- **`QualityConventionPlugin`:**
  - *Problem:* Typisierte Konfiguration der `KtlintExtension` scheiterte im Included Build ohne compile-time Plugin-Dependency.
  - *Fix:* Typsichere Reflection-/DSL-Konfiguration mit `ignoreFailures = true` und `android = true`.
- **Natives Toolchain-Wiring (ARM64 / Termux Host):**
  - *Problem:* Standardmäßig referenzierte AGP `build-tools 35.0.0` mit x86_64-Binaries für AIDL (`Exec format error`).
  - *Fix:* In `BuildConfig.kt` und `KotlinAndroid.kt` `buildToolsVersion = "37.0.0"` fest verankert, wodurch native aarch64-Binaries unter `~/android-sdk/build-tools/37.0.0/` genutzt werden.
- **`TerminalBootstrapPackagesPlugin`:**
  - *Problem:* Build brach ohne Netzverbindung ab, da Downloads zur Konfigurationszeit erzwungen wurden.
  - *Fix:* Unterstützung für `-PcodeforgeBootstrapSkip=true` implementiert; Offline-Assembly über `termux-bootstrap-zip.S` integriert.

---

### Schritt 2: `./gradlew :app:assembleDebug -PcodeforgeBootstrapSkip=true`
Im Rahmen der inkrementellen Modul-Kompilierung wurden folgende modul- und herstellerspezifische Fehler identifiziert und behoben:

1. **NDK & native C/C++-Bibliotheken:**
   - *Problem:* In `gradle/libs.versions.toml` war NDK `26.3.11579264` (reine x86_64-Toolchain) hinterlegt.
   - *Fix:* Umstellung auf NDK `29.0.14206865` (`~/android-sdk/ndk/29.0.14206865`), das nativ für aarch64 vorliegt. Alle 4 ABIs (`arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`) kompilieren nun sauber via ndk-build.
2. **Protobuf Runtime & Codegen:**
   - *Problem:* `protobuf = 3.25.3` kollidierte mit der installierten Systemversion `protoc 35.1` (fehlendes `@com.google.protobuf.Generated`).
   - *Fix:* `protobuf` in `gradle/libs.versions.toml` auf `4.35.1` angehoben; `api(libs.protobuf.javalite)` in `:core:datastore` exportiert.
3. **Ressourcen und Fremdcode:**
   - `:libs:termux-view`: Fehlende XML-Ressourcen (`strings.xml`, Handle-Drawables) nachgetragen.
   - `:libs:termux-shared`: `commons-io:2.16.1` als Dependency ergänzt; unvollständige ViewBindings in `ReportActivity` durch `setContentView` ersetzt; fehlende Android-Befehlsklassen (`Am`, `BaseCommand`, `IntentCmd`, `ShellCommand`) unter `com.codeforge.am` eingebunden.
   - `:libs:termux-app`: ViewBinding in `TermuxActivity` bereinigt.
4. **Compose & UI-Framework:**
   - `:core:designsystem`: Fallback auf `MaterialTheme` (Expressive Theme API in Material3 1.3.2 ist noch experimentell).
   - `@OptIn`-Annotationen für experimentelle Material3- und Adaptive-APIs nachgetragen in:
     `:feature:composepreview`, `:feature:editor`, `:feature:sdkmanager`, `:feature:themebuilder`, `:feature:welcome`, `:feature:settings`, `:feature:terminal`, `:feature:plugins` und `:app`.
   - `:feature:filetree`: `@Composable`-Aufruf `FileSystemTree` aus `remember` in `key(...)` verschoben.
   - `:feature:git`: Hilfsfunktionen in Tabs als `@Composable` annotiert.
   - `:feature:plugins`: Typo `snackBarHost` -> `snackbarHost` korrigiert.
   - `:feature:sdkmanager`: `material-icons-extended:1.7.3` ergänzt; `labelRes` Auflösung via `stringRes` korrigiert.
5. **Sora-Editor (:feature:editor):**
   - `LspAwareLanguage`: Aufruf von `publisher.checkCancelled()` statt `isCancelled`; Verwendung der konkreten Klasse `SimpleCompletionItem` statt abstract `CompletionItem`.
   - `TreeSitterLanguageSupport`: Imports auf `io.github.rosemoe.sora.editor.ts.TsLanguage`/`TsLanguageSpec` und `TSLanguage.loadLanguage` angepasst.
6. **Ressourcen-Aufrufe:**
   - Nullable `throwable.message` via `.orEmpty()` abgesichert in `:libs:template-engine`, `:libs:terminal-engine`, `:feature:editor` und `:feature:search`.
7. **App-Packaging (:app):**
   - Fehlende Abhängigkeiten `project(":core:domain")` und `libs.hilt.navigation.compose` in `app/build.gradle.kts` ergänzt.
   - `packaging.resources`: `pickFirsts += "**/*.kotlin_builtins"` zur Deduplizierung von `kotlin-compiler-embeddable` und `kotlin-stdlib` konfiguriert.
- **Ergebnis:** Das Ziel `:app:assembleDebug` baute `app-debug.apk` (316 MB) fehlerfrei.

---

### Schritt 3: `./gradlew test` & `./gradlew ktlintCheck`

1. **JVM-Variant-Resolution für reine Kotlin-Module:**
   - *Problem:* Reine JVM-Module (`examples:java-lsp-plugin`, `examples:kotlin-lsp-plugin`, `examples:lsp-plugin-common`) nutzen das Convention-Plugin `codeforge.kotlin.library` (`kotlin("jvm")`). Bei Abhängigkeiten auf Android-Bibliotheken (`:core:domain`, `:libs:plugin-api`, `:libs:terminal-engine`) schlug die Gradle-Variant-Auflösung fehl (`org.jetbrains.kotlin.platform.type: jvm` vs. `androidJvm` und Mehrdeutigkeit zwischen Secondary Variants wie `jar`, `android-classes-directory`, `r-class-jar`).
   - *Fix:* In [`KotlinLibraryConventionPlugin.kt`](../build-logic/convention/src/main/kotlin/KotlinLibraryConventionPlugin.kt) wurden Attribute-Regeln hinterlegt:
     - `AndroidJvmCompatibilityRule`: Akzeptiert `androidJvm`-Varianten für `jvm`-Konsumenten.
     - `BuildTypeDisambiguationRule`: Wählt bei Mehrdeutigkeit automatisch die `debug`-Variante.
     - `ArtifactTypeDisambiguationRule`: Wählt bei Vorhandensein von `jar` dieses Artefakt aus.
2. **Beispiel-Plugins:**
   - In `examples:*-lsp-plugin` fehlende Compile-Only-Abhängigkeit auf `:libs:terminal-engine` für `ShellLaunchSpec` ergänzt.
   - Konsolenbasiertes Logging statt Android-spezifischem `android.util.Log` implementiert.
3. **Testergebnisse:**
   - `./gradlew test -PcodeforgeBootstrapSkip=true` führte alle Unit-Tests in sämtlichen Modulen fehlerfrei aus (1696 Tasks, 0 Fehler).
4. **Style-Check (`ktlintCheck`):**
   - `./gradlew ktlintCheck` lief vollständig durch (217 Tasks). Wie gefordert rein berichtend ohne automatische Modifikation des Quellcodes. Die Ausgaben enthalten Hinweise auf Namenskonventionen in Vorlagen-Generatoren und Package-Strukturen mit Unterstrichen.

---

## 3. Verbleibende Warnungen & Empfehlungen

- **AutoMirrored Icons:** Einige Icons (`Icons.Filled.Undo`, `Icons.Filled.Send`, `Icons.Filled.ArrowBack`) sind in neueren Compose-Versionen deprecated und sollten schrittweise auf `Icons.AutoMirrored.Filled.*` umgestellt werden.
- **Gradle 9.0 Deprecations:** Die beobachteten Warnungen stammen überwiegend aus internen Tasks von AGP 8.13.2 und werden mit dem geplanten Wechsel auf AGP 9 / Gradle 9 adressiert.
- **Expressive Theme:** Sobald `MaterialExpressiveTheme` in einer künftigen Material3-Version stabilisiert ist, kann das Fallback in `CodeForgeTheme.kt` wieder auf Expressive Theme angehoben werden.
