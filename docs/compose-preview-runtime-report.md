# Compose-Preview: Jar-Bundle für den On-Device-Compiler – Report (Task 26)

**Datum:** 18. Oktober 2026  
**Autor:** Thomas Schmid / CodeForge Mobile Team  
**Status:** Erfolgreich implementiert und verifiziert (JVM-Unit-Tests & Gesamtsystem-Build)

---

## 1. Übersicht & Zielsetzung

Task 26 löst die fundamentale Einschränkung der On-Device Compose Preview auf Android:
Kompilierte `@Composable`-Dateien können vom eingebetteten Kotlin-Compiler (`K2JVMCompiler`) nur dann typgeprüft und mit Composer-Aufrufen augmentiert werden, wenn die passenden Klassenpfad-JARs für Kotlin-Stdlib, Compose-Runtime, Compose-UI, Compose-Foundation, Material 3 und das `kotlin-compose-compiler-plugin-embeddable` zur Compile-Zeit zur Verfügung stehen.

Da Android-Apps ihre eigenen Abhängigkeiten im APK ausschließlich als gemergtes DEX (`classes.dex`) ausliefern, fehlen diese Original-JARs im Android-Dateisystem.

### Architektur-Entscheidung: Release-Asset-Download vs. APK-Assets
Ein direktes Bundling des Bundles unter `assets/compiler-runtime/` in der APK wurde **ausdrücklich verworfen**:
- Das Standard-Runtime-Bundle umfasst **31 JAR-Dateien** mit **23.18 MB (22.10 MiB)** unkomprimiert bzw. **20.52 MiB (21.52 MB)** als ZIP-Archiv.
- Da `.jar`-Dateien bereits standardmäßig deflate-komprimiert sind, führt das Verpacken in eine APK zu einer unausweichlichen Vergrößerung des Basis-Downloads um **~20.5 MiB** – selbst für Nutzer, die niemals die Compose-Vorschau verwenden.
- **Gewählte Lösung:** Bereitstellung als GitHub Release Asset (`scto/codeforge-tools`) via Gradle-Task `packCompilerRuntime` und CI-Workflow `.github/workflows/build-compiler-runtime.yml`. Die App lädt das Bundle on-demand bei Erstnutzung über `CompilerRuntimeProvider` nach `filesDir/compiler-runtime/` herunter.

---

## 2. Zusammensetzung des Bundles (BOM 2025.09.00 / Kotlin 2.1.21)

| JAR-Dateiname | Größe (Bytes) | Größe (KiB / MiB) |
|---|---:|---:|
| `androidx_annotation-annotation-experimental-1.4.1.jar` | 6.304 | 6.16 KiB |
| `androidx_annotation-annotation-jvm-1.9.1.jar` | 60.577 | 59.16 KiB |
| `androidx_arch_core-core-common-2.2.0.jar` | 11.657 | 11.38 KiB |
| `androidx_collection-collection-jvm-1.5.0.jar` | 877.998 | 857.42 KiB |
| `androidx_compose_animation-animation-android-1.9.1.jar` | 490.958 | 479.45 KiB |
| `androidx_compose_animation-animation-core-android-1.9.1.jar` | 374.164 | 365.40 KiB |
| `androidx_compose_foundation-foundation-android-1.9.1.jar` | 4.443.227 | 4.24 MiB |
| `androidx_compose_foundation-foundation-layout-android-1.9.1.jar` | 638.328 | 623.37 KiB |
| `androidx_compose_material-material-icons-core-android-1.7.8.jar` | 869.838 | 849.45 KiB |
| `androidx_compose_material-material-ripple-android-1.9.1.jar` | 96.581 | 94.32 KiB |
| `androidx_compose_material3-material3-android-1.3.2.jar` | 3.985.473 | 3.80 MiB |
| `androidx_compose_runtime-runtime-android-1.9.1.jar` | 1.479.570 | 1.41 MiB |
| `androidx_compose_runtime-runtime-annotation-android-1.9.1.jar` | 4.378 | 4.28 KiB |
| `androidx_compose_runtime-runtime-saveable-android-1.9.1.jar` | 50.859 | 49.67 KiB |
| `androidx_compose_ui-ui-android-1.9.1.jar` | 3.375.873 | 3.22 MiB |
| `androidx_compose_ui-ui-geometry-android-1.9.1.jar` | 55.846 | 54.54 KiB |
| `androidx_compose_ui-ui-graphics-android-1.9.1.jar` | 731.286 | 714.15 KiB |
| `androidx_compose_ui-ui-text-android-1.9.1.jar` | 846.398 | 826.56 KiB |
| `androidx_compose_ui-ui-tooling-preview-android-1.9.1.jar` | 18.308 | 17.88 KiB |
| `androidx_compose_ui-ui-unit-android-1.9.1.jar` | 98.788 | 96.47 KiB |
| `androidx_compose_ui-ui-util-android-1.9.1.jar` | 17.710 | 17.30 KiB |
| `androidx_lifecycle-lifecycle-common-jvm-2.9.0.jar` | 60.804 | 59.38 KiB |
| `androidx_lifecycle-lifecycle-runtime-android-2.9.0.jar` | 53.133 | 51.89 KiB |
| `androidx_lifecycle-lifecycle-runtime-compose-android-2.9.0.jar` | 42.889 | 41.88 KiB |
| `androidx_lifecycle-lifecycle-runtime-ktx-android-2.9.0.jar` | 218 | 0.21 KiB |
| `kotlin-compose-compiler-plugin-embeddable.jar` | 859.101 | 838.97 KiB |
| `org_jetbrains-annotations-23.0.0.jar` | 29.371 | 28.68 KiB |
| `org_jetbrains_kotlin-kotlin-stdlib-2.1.21.jar` | 1.724.058 | 1.64 MiB |
| `org_jetbrains_kotlinx-kotlinx-coroutines-android-1.9.0.jar` | 19.247 | 18.79 KiB |
| `org_jetbrains_kotlinx-kotlinx-coroutines-core-jvm-1.9.0.jar` | 1.463.587 | 1.40 MiB |
| `org_jetbrains_kotlinx-kotlinx-serialization-core-jvm-1.7.3.jar` | 390.987 | 381.82 KiB |
| **Gesamt entpackt (31 JARs)** | **23.177.516** | **22.634 KiB (~22,10 MiB)** |
| **Release-ZIP (`compiler-runtime-2025.09.00-k2.1.21.zip`)** | **21.515.885** | **21.011 KiB (~20,52 MiB)** |

### Analyse von `material-icons-extended`
- `androidx.compose.material:material-icons-extended` liegt als AAR vor.
- Die darin enthaltene `classes.jar` besitzt alleine eine Größe von **37.425.981 Bytes (35,69 MiB)**.
- Würde `material-icons-extended` in das Standardbundle aufgenommen, stiege die Gesamtgröße entpackt auf **57,79 MiB**.
- **Entscheidung:** Wie gefordert bleibt `material-icons-extended` aus dem Standard-Bundle ausgeschlossen und ist optional über den Gradle-Parameter `-PwithExtendedIcons=true` konfigurierbar.

---

## 3. Implementierte Komponenten

1. **Gradle-Task `packCompilerRuntime` (`:feature:composepreview`):**
   - Löst die transitiven Compose-BOM- und Kotlin-Abhängigkeiten auf.
   - Extrahiert `classes.jar` aus allen AARs und benennt sie kollisionsfrei nach Maven-Koordinaten.
   - Legt `kotlin-compose-compiler-plugin-embeddable.jar` isoliert ab.
   - Prüft jede JAR-Datei auf Integrität (analog `unzip -t`).
   - Schreibt `manifest.json` mit Dateinamen, Dateigröße und SHA-256-Prüfsummen.
   - Erzeugt die Release-ZIP-Datei und deren `.sha256`-Prüfsumme.
2. **GitHub Actions Workflow `.github/workflows/build-compiler-runtime.yml`:**
   - Manuell via `workflow_dispatch` triggerbar (mit optionalem `with_extended_icons`).
   - Führt `./gradlew :feature:composepreview:packCompilerRuntime` aus.
   - Veröffentlicht die ZIP-, Prüfsummen- und Manifest-Dateien unter dem Release-Tag `compiler-runtime-<bom>-k<kotlin>` im Repository `scto/codeforge-tools`.
   - Validiert und fehlerfrei geprüft mit `actionlint`.
3. **`CompilerRuntimeProvider` & `CompilerRuntimeProviderImpl`:**
   - Interface im Core-Domain-Layer (`:core:domain`).
   - Implementierung in `:feature:composepreview`.
   - Integritätsprüfung gegen lokales `manifest.json` und Abgleich mit `BuildConfig.COMPOSE_BOM_VERSION` und `BuildConfig.KOTLIN_VERSION`.
   - Bei Mismatch oder Erststart: Download des ZIPs und `.sha256`-Prüfsumme, SHA-256-Validierung des ZIPs.
   - Zip-Slip-sichere Extraktion (keine relativen Pfade, keine Verzeichnisse).
   - Validierung jeder einzelnen entpackten Datei gegen `manifest.json` vor dem Aktivieren.
   - Atomarer Tausch des Runtime-Verzeichnisses: Bei Netzwerkabbruch oder Mismatch bleibt eine vorhandene Installation unbeschädigt erhalten.
4. **Classpath-Trennung & Android-SDK-Plattform:**
   - `kotlin-compose-compiler-plugin-embeddable.jar` wird strikt aus dem User-Classpath (`runtimeClasspathJars`) herausgefiltert und nur als Compiler-Plugin übergeben.
   - Über `SdkRepository` wird die höchste installierte Android-Plattform (`platforms;android-N`) ermittelt und deren `android.jar` an den Classpath angehängt.
   - Fehlt eine Android-Plattform, liefert der Renderer eine verständliche Lokalisierungsfehlermeldung (`composepreview_android_plattform_fehlt`).
5. **UI & State-Handling:**
   - Neuer `PreviewRenderState.PreparingRuntime(progress)` mit `LinearProgressIndicator` und Prozentanzeige.
   - "Erneut versuchen"-Button im `Failed`-Zustand zur Wiederholung nach Behebung von Offline- oder Speicherplatzproblemen.
   - Korrektur der String-Ressource `composepreview_compose_compiler_plugin_jar_fehlt` (de/en), um auf das Runtime-Paket statt auf APK-Assets zu verweisen.

---

## 4. Getestet / Nicht getestet

### Was wurde getestet (Erfolgreich verifiziert):
- [x] **Gradle-Packer:** `./gradlew :feature:composepreview:packCompilerRuntime` erzeugt 31 valide JARs, `manifest.json` und die Release-ZIP.
- [x] **CI-Workflow:** `.github/workflows/build-compiler-runtime.yml` mit `actionlint` geprüft (0 Fehler, 0 Warnungen).
- [x] **Manifest-Parser:** JVM-Unit-Tests in `CompilerRuntimeProviderTest` für gültiges JSON, ungültige Formate, leere Arrays und Typvalidierung.
- [x] **SHA-256-Prüfung:** Unit-Test verifiziert, dass SHA-Mismatch den Vorgang abbricht und eine bestehende Vorinstallation unangetastet lässt.
- [x] **Zip-Slip-Schutz:** Unit-Test verifiziert, dass Pfadmanipulationen (`../evil.jar`) abgefangen werden.
- [x] **Versions-Mismatch:** Unit-Test verifiziert, dass veraltete Bundles erkannt, neu geladen und atomar aktualisiert werden, während aktuelle Versionen ohne erneuten Download wiederverwendet werden.
- [x] **Plugin-Jar-Exklusion:** Unit-Test verifiziert, dass `resolveRuntimeJars()` das Compiler-Plugin nicht im User-Classpath belässt.
- [x] **Release-Manifest-Integrität:** Unit-Test verifiziert jede der 31 erzeugten JARs im Build-Verzeichnis gegen `build/compiler-runtime/manifest.json` und die `.sha256`-Datei.
- [x] **Versions-Sortierung:** Unit-Test für `VersionComparator` zur Ermittlung der höchsten Android-SDK-Plattform.
- [x] **Lokalisierung & String-Audits:** `check_string_locales.py` (846/846 Parität) und `find_hardcoded_strings.py --check` (0 Fehler).
- [x] **Code-Qualität & Gesamtsystem:** `ktlintCheck` und `assembleDebug` auf `:feature:composepreview` sowie `:app:assembleDebug` erfolgreich durchgelaufen.

### Was wurde NICHT getestet (Einschränkungen dieser Testumgebung):
- [ ] **On-Device K2JVMCompiler-Ausführung:** Die tatsächliche Ausführung des eingebetteten Kotlin-Compilers (`org.jetbrains.kotlin.cli.jvm.K2JVMCompiler`) innerhalb der Android Runtime (ART) auf einem physischen Android-Gerät oder Emulator.
- [ ] **Offscreen-ComposeView-Rendering:** Das tatsächliche Erzeugen eines Bitmaps aus einer reflektiv in eine Komposition eingehängten `@Composable`-Funktion über `ComposeViewBitmapRenderer` auf einem echten Gerät.

---

## 5. Konkrete Folge-Tasks

1. **Task 27a (On-Device Compiler & DEX Pipeline Verification):**
   - Verifikation von `PreviewCompiler` mit `K2JVMCompiler` auf ART (physisches Android-Gerät / Android 14+).
   - Test des Zusammenspiels mit `PreviewDexer` und `d8` aus dem installierten Android-SDK (`build-tools`).
   - Speicher- und Laufzeitprofiling der Kompilierung direkt auf dem Gerät.

2. **Task 27b (Compose Offscreen Bitmap Rendering & Environment Support):**
   - Verifikation von `ComposeViewBitmapRenderer` und `ReflectiveComposableHost` auf physischen Geräten.
   - Unterstützung für MaterialTheme-Wrapper, LocalContext-Injektion und dynamische Preview-Parameter (`@PreviewParameter`).
   - Optimierung des Renderings für Geräte mit unterschiedlichen Pixeldichten und Themes (Light/Dark-Mode).
