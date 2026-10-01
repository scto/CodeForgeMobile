# CodeForge Mobile: :core:resources Centralization & :libs:terminal-engine Build Resolution

## 1. Overview

This document details the centralization of hardcoded user-facing strings into a dedicated `:core:resources` module and the complete restoration of missing interfaces, implementations, and compiled dependencies within `:core:domain` and `:libs:terminal-engine`.

---

## 2. `:core:resources` String Centralization

### 2.1 Architecture & Setup
- Created module `:core:resources` with `namespace = "com.codeforge.core.resources"`.
- Added `include(":core:resources")` in `settings.gradle.kts`.
- Added dependency `api(project(":core:resources"))` in `:core:ui` and `implementation(project(":core:resources"))` in `:app`.

### 2.2 Centralized Resource Key Categories (`strings.xml`)
All hardcoded strings (titles, text labels, accessibility `contentDescription`s, dialog titles, buttons, and setting categories) were extracted and centralized into `core/resources/src/main/res/values/strings.xml`:

- **Actions & Common UI**: `action_save`, `action_cancel`, `action_delete`, `action_copy`, `action_share`, `action_apply`, `action_close`, `action_ok`, `action_search`, `action_format`.
- **Accessibility (`contentDescription`)**: `cd_navigate_up`, `cd_file_tree_item`, `cd_expand_folder`, `cd_collapse_folder`, `cd_search_clear`, `cd_editor_minimap`, `cd_close_tab`, `cd_more_options`, `cd_terminal_settings`.
- **Settings Categories & Labels**: `settings_title`, `settings_category_editor`, `settings_category_terminal`, `settings_category_theme`, `settings_category_extensions`, `settings_category_debug`, `settings_theme_mode`, `settings_font_size`, `settings_minimap_enabled`, `settings_autocompletion_enabled`.
- **File Tree & Workspace**: `filetree_empty_directory`, `filetree_new_file`, `filetree_new_folder`, `filetree_rename`, `filetree_delete_confirm`.
- **Dialogs & Messages**: `dialog_confirm_delete_title`, `dialog_confirm_delete_msg`, `dialog_unsaved_changes_title`, `dialog_unsaved_changes_msg`.
- **Editor UI**: `editor_tab_untitled`, `editor_formatting_success`, `editor_formatting_failed`, `editor_preview_title`.
- **Terminal & Engine**: `terminal_session_new`, `terminal_session_kill`, `terminal_wake_lock`, `terminal_status_ready`, `terminal_status_bootstrapping`.

---

## 3. `:libs:terminal-engine` & Domain Restoration

### 3.1 Domain Repositories Restored
- **`DistroBootstrapRepository` & `BootstrapProgress`**:
  - Defined `DistroBootstrapRepository` interface in `:core:domain` ([DistroBootstrapRepository.kt](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/core/domain/src/main/kotlin/com/codeforge/core/domain/repository/DistroBootstrapRepository.kt)).
  - Defined sealed class `BootstrapProgress` (`Downloading`, `Extracting`, `Finalizing`, `Completed`, `Failed`).
- **`TerminalSessionRepository` & `TerminalSession`**:
  - Defined `TerminalSessionRepository` interface in `:core:domain` ([TerminalSessionRepository.kt](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/core/domain/src/main/kotlin/com/codeforge/core/domain/repository/TerminalSessionRepository.kt)).
  - Defined data class `TerminalSession`.

### 3.2 Terminal Engine Components Restored
- **`DistroBootstrapRepositoryImpl.kt`**: Implemented PRoot rootfs downloader & native symlink extractor (`java.nio.file.Files.createSymbolicLink`) to fix PRoot warnings and syntax errors.
- **`RootfsDownloader.kt`**: Implemented robust HTTP streaming downloader supporting redirects (`301/302/307/308`).
- **`RootfsBootstrapper.kt`**: Managed idempotent rootfs bootstrapping before terminal process execution.
- **`JdkCatalog.kt` & `JdkInstaller.kt`**: Managed OpenJDK 17 and OpenJDK 21 installations inside the PRoot rootfs environment.
- **`AndroidRepoCrawler.kt`**: Fallback package catalog provider when `sdkmanager` CLI returns 0 packages.
- **`nyamux-terminal.jar`**: Created pre-built jar dependency containing full compiled bytecodes for `com.nyamux.terminal.*` (`TerminalEmulator`, `TerminalBuffer`, `TerminalSessionClient`, `KeyHandler`, `ByteQueue`, `JNI`).
- **`TerminalEngineModule.kt`**: Registered Hilt singleton bindings for `DistroBootstrapRepository`, `TerminalSessionRepository`, and `SdkRepository`.

---

## 4. Verification & Output

- **Gradle Build Command**:
  `bash .../gradle assembleDebug --no-daemon -Dorg.gradle.console=plain -Dkotlin.colors.enabled=false`
- **Output Artifact**:
  [`app/build/outputs/apk/debug/app-debug.apk`](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/app/build/outputs/apk/debug/app-debug.apk)
