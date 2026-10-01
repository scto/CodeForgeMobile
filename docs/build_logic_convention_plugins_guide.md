# CodeForgeMobile Build-Logic & Convention Plugins Comprehensive Guide

## Overview
This document details the composite build architecture (`build-logic`) introduced to centralize, standardize, and streamline Gradle build scripts across all modules (`:app`, `:core:*`, `:feature:*`, `:libs:*`) of **CodeForgeMobile**.

---

## 1. Architecture & Directory Structure

`build-logic` is implemented as a Gradle composite build (`includeBuild("build-logic")` in root `settings.gradle.kts`).

```
build-logic/
├── settings.gradle.kts          # Version catalog mapping & module inclusion (:convention)
├── build.gradle.kts             # Root build file for composite build
└── convention/
    ├── build.gradle.kts         # Kotlin DSL plugin registration
    └── src/main/kotlin/
        ├── AndroidApplicationConventionPlugin.kt     # Plugin ID: codeforge.android.application
        ├── AndroidLibraryConventionPlugin.kt         # Plugin ID: codeforge.android.library
        ├── AndroidLibraryComposeConventionPlugin.kt # Plugin ID: codeforge.android.library.compose
        ├── AndroidHiltConventionPlugin.kt           # Plugin ID: codeforge.android.hilt
        ├── KotlinLibraryConventionPlugin.kt          # Plugin ID: codeforge.kotlin.library
        ├── QualityConventionPlugin.kt                # Plugin ID: codeforge.quality
        ├── TerminalBootstrapPackagesPlugin.kt       # Plugin ID: codeforge.terminal.bootstrap
        └── com/codeforge/buildlogic/
            ├── BuildConfig.kt                       # Single source of truth for build parameters
            ├── DownloadUtils.kt                      # Package downloader with SHA-256 verification
            └── KotlinAndroid.kt                     # Helper for common SDK & JVM compiler options
```

---

## 2. Centralized `BuildConfig.kt`

Adapted from `assets/BuildConfig.kt`, the `com.codeforge.buildlogic.BuildConfig` object acts as the single source of truth for build configuration parameters across all convention plugins:

```kotlin
package com.codeforge.buildlogic

import org.gradle.api.JavaVersion

object BuildConfig {
    const val packageName = "com.codeforge"
    const val compileSdk = 36
    const val minSdk = 26
    const val targetSdk = 35
    val javaVersion = JavaVersion.VERSION_17
    const val kotlinJvmTarget = "17"
}
```

### Applied Standard Configurations:
- **Compile SDK**: `36`
- **Min SDK**: `26`
- **Target SDK**: `35`
- **Java Compatibility**: `JavaVersion.VERSION_17`
- **Kotlin JVM Target**: `17`

---

## 3. Convention Plugins Catalog

| Plugin ID | Description | Features & Dependencies Applied |
| :--- | :--- | :--- |
| `codeforge.android.application` | Application Module Plugin | `com.android.application`, `kotlin.android`, applies `BuildConfig` SDK & JVM targets |
| `codeforge.android.library` | Standard Library Plugin | `com.android.library`, `kotlin.android`, applies `BuildConfig` SDK & JVM targets |
| `codeforge.android.library.compose` | Compose-enabled Library Plugin | `codeforge.android.library`, `kotlin.plugin.compose`, `buildFeatures.compose = true`, Compose BOM, UI & Material3 dependencies |
| `codeforge.android.hilt` | Dagger Hilt Injection Plugin | `google.devtools.ksp`, `dagger.hilt.android`, Hilt runtime & compiler KSP dependencies |
| `codeforge.kotlin.library` | Pure Kotlin JVM Library Plugin | `kotlin.jvm`, Java 17 compatibility |
| `codeforge.quality` | Code Quality & Linting | `org.jlleitschuh.gradle.ktlint` |
| `codeforge.terminal.bootstrap` | Terminal Package Downloader | Downloads SHA-256 verified Termux bootstrap zips (`aarch64`, `arm`, `x86_64`) & generates C++ assembly `termux-bootstrap-zip.S` |

---

## 4. Module-Wide Migration Summary

All module `build.gradle.kts` files have been migrated to use convention plugins, removing redundant SDK and compiler declarations:

- **`:app`**: Uses `codeforge.android.application` and `codeforge.android.hilt`.
- **`:core:*`** (`common`, `data`, `datastore`, `designsystem`, `domain`, `navigation`, `ui`): Migrated to `codeforge.android.library`, `codeforge.android.library.compose`, and `codeforge.android.hilt`.
- **`:feature:*`** (`editor`, `composepreview`, `projectwizard`, `filetree`, `terminal`, etc.): Migrated to `codeforge.android.library.compose` and `codeforge.android.hilt`.
- **`:libs:*`** (`terminal-engine`, `lsp-client`, etc.): Migrated to `codeforge.android.library` and `codeforge.android.hilt`.

---

## 5. Execution Strategy Optimization for Termux / Android

To prevent daemon file-locking and `FastJarFileSystem` I/O exceptions when building on Android/Termux fuse storage, `gradle.properties` was updated:

```properties
kotlin.compiler.execution.strategy=out-of-process
```
This ensures Kotlin compilation tasks spawn separate processes, releasing jar handles cleanly after execution.
