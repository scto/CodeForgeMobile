# Build-Logic, Convention-Plugins und Toolchain

Stand: 2026-10-06. **Nichts davon wurde mit Gradle gebaut** (Sandbox ohne Maven-/Google-Zugriff) – Struktur und
Syntax sind sorgfältig geprüft, ein echter `./gradlew help` / `:app:assembleDebug` steht aus (Task: `agy-tasks/12-…`).

## Was neu ist
- `build-logic/` (Included Build, Details: `build-logic/README.md`): 7 Convention-Plugins, `BuildConfig`, `DownloadUtils`.
- Alle 44 Modul-`build.gradle.kts` (außer Root) nutzen die Plugins; `compileSdk`, `minSdk`, `compileOptions`,
  `buildFeatures.compose` und Hilt/KSP-Wiring stehen nicht mehr in den Modulen.
- `gradle/libs.versions.toml`: ergänzt um `ktlint`, `detekt`, `freemarker`, `protobuf-kotlin`, die `*-gradlePlugin`-Libraries
  (für build-logic), `kotlin-serialization`/`ktlint`/`detekt`-Plugins und `codeforge-*`-Plugin-Aliase. Vorhandene
  Projekt-Versionen blieben unverändert (u. a. `protobuf = 3.25.3`; das Attachment hatte `4.35.1` – Datastore-/protoc-Skew
  vermeiden, bei Bedarf bewusst gemeinsam anheben).
- Neu im Root: `gradle.properties` (`android.useAndroidX=true`, `nonTransitiveRClass=true`, Parallel/Caching),
  `.editorconfig` (ktlint, `android_studio`-Stil), `.gitignore`, `gradlew`/`gradlew.bat`, Wrapper-Jar.

## Abweichungen vom gelieferten Attachment (bewusst)
| Thema | Attachment | Projekt jetzt | Grund |
|---|---|---|---|
| Gradle | 9.2.1 | **8.14.3** | AGP 8.x läuft nicht auf Gradle 9; AGP 9.0 braucht Gradle ≥ 9.1, ändert aber DSL (CommonExtension ohne Generics) und bringt Built-in-Kotlin → die gelieferten Plugins (`CommonExtension<*,*,*,*,*,*>`, `org.jetbrains.kotlin.android`) und Kotlin 2.1.21 wären dafür umzuschreiben |
| AGP | 8.6.0 | **8.13.2** | `compileSdk = 36` (BuildConfig) verlangt AGP ≥ 8.9.1; 8.13 braucht Gradle ≥ 8.13 |
| Bootstrap-Plugin | Download beim Konfigurieren | Task `embedTerminalBootstrap` an `preBuild` | Sync/IDE-Import ohne Netz; Properties zum Überschreiben, `-PcodeforgeBootstrapSkip=true` |
| Quality-Plugin | ktlint ohne Konfiguration | `ignoreFailures = true`, `android = true` | Bestandscode ist nie formatiert worden; sonst bricht `check` |
| Library/App-Plugin | – | verdrahtet `consumer-rules.pro` / `proguard-rules.pro`, wenn vorhanden | Teil der Modul-Build-Fähigkeit |

Der bisherige, händische `embedTerminalBootstrap`-Task in `:libs:termux-app` (Platzhalter-Checksummen, eigener Fork)
ist durch das Plugin ersetzt. **Wichtig:** Die Plugin-Standardpakete (AndroidIDE `bootstrap-16.12.2023`, Prüfsummen
gegen die echten Dateien verifiziert) sind für den Prefix `/data/data/com.itsaky.androidide/files/usr` gebaut
(`etc/profile`, `etc/apt/sources.list`). `:app` läuft als `com.codeforge.app` → für funktionierendes Terminal einen
passenden Fork per `codeforgeBootstrapUrlTemplate` einhängen (siehe `docs/sub/TERMUX-PORTING.md`, `agy-tasks/04-…`).

## Modul → Plugin
- App: `:app` → `application` (+ `kotlin.compose`, `hilt`, `quality`).
- Compose-Libraries (`core:ui`, `core:designsystem`, `core:resources`, alle `feature:*`) → `library.compose`.
- Daten-/Logik-Libraries (`core:common|data|domain|datastore|navigation|testing`, `libs:*-impl`, `terminal-engine`,
  `gradle-tooling-bridge`, `lsp-client`, `plugin-api`, `template-engine`) → `library` (+ `hilt` wo Hilt genutzt wurde).
- Reine JVM-Module (`libs:code-tools`, `*-api`, `examples:*`) → `kotlin.library`.
- `libs:termux-*` → nur `library` (Fremdcode, kein ktlint); `termux-app` zusätzlich `terminal.bootstrap`.

## Risiken / offen
- build-logic wurde nicht kompiliert. Heikelste Stellen: `consumerProguardFiles`/`getByName("release")` in den
  Library-/App-Plugins, `KtlintExtension` (compileOnly `org.jlleitschuh.gradle:ktlint-gradle`).
- Toolchain jetzt angehoben (für Material 3 Expressive): Kotlin 2.1.21, KSP 2.1.21-2.0.1, Hilt 2.56.2, Compose BOM 2025.09.00,
  material3-adaptive 1.1.0, activity-compose 1.10.1, composepreview-Embeddables 2.1.21. **Nicht gegen Maven/Google verifiziert**
  (Repos im Sandbox gesperrt): Versionen vor dem ersten Build prüfen; ggf. braucht eine neuere Compose-Version höheres compileSdk.
  Details: `docs/adaptive-edge-to-edge-expressive.md`.
- Gradle 9 / AGP 9 ist ein eigener Migrationsschritt (Built-in Kotlin, neue DSL).
- SDK-Platform 36 + Build-Tools müssen installiert sein.
