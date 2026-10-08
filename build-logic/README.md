# build-logic

Included Build (`includeBuild("build-logic")` in `settings.gradle.kts`) mit den Convention-Plugins
des Projekts. Version Catalog: `../gradle/libs.versions.toml` (auch hier als `libs` eingebunden).

| Plugin-ID | Klasse | Zweck |
|---|---|---|
| `codeforge.android.application` | `AndroidApplicationConventionPlugin` | `com.android.application` + Kotlin; compileSdk/minSdk/targetSdk, Java 17, optional `proguard-rules.pro` |
| `codeforge.android.library` | `AndroidLibraryConventionPlugin` | `com.android.library` + Kotlin; compileSdk/minSdk, Java 17, optional `consumer-rules.pro`/`proguard-rules.pro` |
| `codeforge.android.library.compose` | `AndroidLibraryComposeConventionPlugin` | wie `…library` + Compose-Compiler-Plugin, `buildFeatures.compose`, BOM, `ui`, `material3`, `lifecycle-viewmodel-compose` |
| `codeforge.android.hilt` | `AndroidHiltConventionPlugin` | KSP + Hilt-Plugin, `hilt-android`, `hilt-compiler` |
| `codeforge.kotlin.library` | `KotlinLibraryConventionPlugin` | reines JVM-Modul (`kotlin("jvm")`), Java/JVM-Target 17 |
| `codeforge.quality` | `QualityConventionPlugin` | ktlint (`.editorconfig`), derzeit `ignoreFailures = true` |
| `codeforge.terminal.bootstrap` | `TerminalBootstrapPackagesPlugin` | Task `embedTerminalBootstrap`: lädt Bootstrap-Zips, schreibt `termux-bootstrap-zip.S` (nur `:libs:termux-app`) |

Zentrale Konstanten: `com.codeforge.buildlogic.BuildConfig` (compileSdk 36, minSdk 26, targetSdk 35, Java 17).
Verwendung im Modul: `alias(libs.plugins.codeforge.android.library.compose)` usw. (Aliase in der toml).
Vendorte Termux-Module nutzen nur `…android.library` (kein ktlint auf GPL-Fremdcode).
