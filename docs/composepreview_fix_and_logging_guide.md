# Compose Preview Fix, Logging & Build Overhaul Guide

## Overview
This document details the diagnosis and resolution of the Compose Preview compilation error identified in `assets/preview.png`, the integration of conditional excessive logging across `:feature:composepreview`, `:feature:editor`, and `:feature:projectwizard`, as well as module dependency adjustments to achieve a clean build.

---

## 1. Analysis & Fix of Compose Preview Error (`preview.png`)

### Root Cause
As shown in `assets/preview.png`, the preview compilation failed with:
```
Kompilierung fehlgeschlagen:
null:null java.lang.IllegalStateException: Resource not found: /org/jetbrains/kotlin/utils/PathUtil.class
at org.jetbrains.kotlin.utils.PathUtil.getResourcePathForClass(PathUtil.kt:169)
at org.jetbrains.kotlin.utils.PathUtil.getPathUtilJar(PathUtil.kt:164)
at org.jetbrains.kotlin.utils.PathUtil.getKotlinPathsForCompiler(PathUtil.kt:124)
at org.jetbrains.kotlin.cli.common.ArgumentsKt.computeKotlinPaths(arguments.kt:132)
at org.jetbrains.kotlin.cli.common.CLICompiler.execImpl(CLICompiler.kt:91)
at com.codeforge.feature.composepreview.PreviewCompiler.compile(PreviewCompiler.kt:70)
```
When running the embedded Kotlin compiler (`K2JVMCompiler`) on Android ART/Dalvik, `PathUtil` attempts to locate `/org/jetbrains/kotlin/utils/PathUtil.class` via Java classloader resource streams. On Android, compiled classes reside inside DEX files, causing `getResource()` to return `null` and throw `IllegalStateException`.

### Implementation Fix
In [PreviewCompiler.kt](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/feature/composepreview/src/main/kotlin/com/codeforge/feature/composepreview/PreviewCompiler.kt):
- Set `kotlinHome` explicitly on `K2JVMCompilerArguments`:
  ```kotlin
  val homeDir = outputDir.parentFile?.absolutePath ?: outputDir.absolutePath
  System.setProperty("kotlin.compiler.home", homeDir)

  val arguments = K2JVMCompilerArguments().apply {
      ...
      kotlinHome = homeDir
  }
  ```
This prevents `PathUtil.getKotlinPathsForCompiler()` from attempting classpath resource resolution and resolves the preview build failure.

---

## 2. Excessive Logging Integration

Excessive step-by-step tracing and debug logging were added across three key modules. All logging calls are strictly guarded by `AppLogger.isEnabled` (and `AppLogger.excessiveTracingEnabled` where appropriate), ensuring zero overhead when logging is disabled in settings.

### Modules Updated:
1. **`:feature:composepreview`**:
   - [PreviewCompiler.kt](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/feature/composepreview/src/main/kotlin/com/codeforge/feature/composepreview/PreviewCompiler.kt): Logs compiler argument setup, classpath JARs, message collector outputs, and exit codes.
   - [PreviewDexer.kt](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/feature/composepreview/src/main/kotlin/com/codeforge/feature/composepreview/PreviewDexer.kt): Logs d8 binary commands, class files, and process execution results.
   - [ComposePreviewRendererImpl.kt](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/feature/composepreview/src/main/kotlin/com/codeforge/feature/composepreview/ComposePreviewRendererImpl.kt): Logs the full pipeline execution from source parsing to reflective invocation and bitmap rendering.

2. **`:feature:editor`**:
   - [EditorViewModel.kt](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/feature/editor/src/main/kotlin/com/codeforge/feature/editor/EditorViewModel.kt): Logs file loading, tab switching, buffer modifications, LSP requests, formatting, and Gradle build executions.

3. **`:feature:projectwizard`**:
   - [ProjectWizardViewModel.kt](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/feature/projectwizard/src/main/kotlin/com/codeforge/feature/projectwizard/ProjectWizardViewModel.kt): Logs template listing, parameter validation, project file generation, and recent project persistence.

---

## 3. Component Contract & Module Build Fixes

### A. Editor ViewModel Compose Analyzer Contract Alignment
In [EditorViewModel.kt](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/feature/editor/src/main/kotlin/com/codeforge/feature/editor/EditorViewModel.kt):
- Updated `publishActiveFileToBridge()` to match the exact signature of `ComposeSourceAnalyzer.findComposables(sourceCode: String): List<ComposableCandidate>` defined in `:core:domain`:
  ```kotlin
  val composables = if (isKotlinFile) {
      composeSourceAnalyzer.findComposables(active.content).map { it.functionName }
  } else emptyList()
  ```

### B. Project Wizard Dependency Injection & Logging Setup
In [feature/projectwizard/build.gradle.kts](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/feature/projectwizard/build.gradle.kts):
- Added `implementation(project(":core:common"))` to resolve references to `AppLogger`.

---

## 4. Verification Results

The entire codebase was compiled using Gradle:
```bash
./gradlew assembleDebug
```
**Result**:
```
BUILD SUCCESSFUL in 6m 51s
921 actionable tasks: 26 executed, 895 up-to-date
```
All modules compiled cleanly with zero errors.
