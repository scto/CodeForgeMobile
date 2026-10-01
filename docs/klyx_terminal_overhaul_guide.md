# Terminal, Engine & Installation Overhaul Guide (klyx.txt Implementation)

## Overview
This document details the complete overhaul of the terminal system, terminal engine, and terminal settings in CodeForge Mobile based on the requirements in `klyx.txt`.

## Key Changes & Implementations

### 1. Ubuntu Only & Init Scripts Clean Up
- **Multi-distro removal**: Distro selection was consolidated to exclusively support **Ubuntu 24.04 LTS (Noble Numbat)**.
- **Init scripts**: Removed obsolete non-Ubuntu init scripts (`init-debian*.sh`, `init-host.sh`, `init-root.sh`, etc.) and established clean Ubuntu init scripts (`init-ubuntu.sh`, `init-ubuntu-host.sh`, `init-ubuntu-root.sh`).

### 2. Environment Variables Configuration
Configured `ProotCommandBuilder` and `TerminalSessionRepositoryImpl` to supply the full required environment specification:
- `COLORTERM=truecolor`
- `ROOTFS=/data/data/com.codeforgemobile/rootfs` (dynamically set to the app's guest OS path)
- `TERM_PROGRAM_VERSION=1.0.0`
- `HOSTNAME=codeforgemobile`
- `PWD=/root`
- `PROOT_TMP_DIR=/tmp`
- `SYSTEMSERVERCLASSPATH=...`
- `EXTERNAL_STORAGE=/sdcard`
- `CODEFORGEMOBILE=1`
- `HOME=/root`
- `LANG=C.UTF-8`
- `DEX2OATBOOTCLASSPATH=...`
- `TMPDIR=/tmp`
- `ANDROID_DATA=/data`
- `ANDROID_STORAGE=/storage`
- `TERM=xterm-256color`
- `ASEC_MOUNTPOINT=/mnt/asec`
- `ANDROID_I18N_ROOT=/apex/com.android.i18n`
- `SHLVL=1`
- `BASH_ENV=/root/.bashrc`
- `ANDROID_ROOT=/system`
- `BOOTCLASSPATH=...`
- `PS1=\[\e[38;5;141m\][codeforgemobile]\[\e[0m\] \[\e[38;5;245m\]\w\[\e[0m\] \$ `
- `ANDROID_TZDATA_ROOT=/apex/com.android.tzdata`
- `LC_ALL=C.UTF-8`
- `PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin`
- `ANDROID_ART_ROOT=/apex/com.android.art`
- `DEBUG=false`
- `ANDROID_ASSETS=/system/app`
- `DEBIAN_FRONTEND=noninteractive`
- `TERM_PROGRAM=codeforgemobile`
- `_=/usr/bin/env`

### 3. Bootstrap Update Check in Terminal Settings
- Added a button `"Auf Bootstrap-Updates prüfen"` in [TerminalSettingsScreen.kt](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/feature/settings/src/main/kotlin/com/codeforge/feature/settings/terminal/TerminalSettingsScreen.kt) which queries and verifies the status of remote Ubuntu releases.

### 4. Terminal / Ubuntu System Wipe Entry in Debug Settings
- Added a dedicated section `"Terminal & Guest OS Wartung (Wipe)"` in [DebugSettingsScreen.kt](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/feature/settings/src/main/kotlin/com/codeforge/feature/settings/debug/DebugSettingsScreen.kt).
- Allows completely erasing the installed Ubuntu rootfs from disk for a clean reinstall.

### 5. On-Demand Terminal Installation UI Component
- **Onboarding decoupling**: Removed terminal rootfs downloading from the initial onboarding flow in `OnboardingViewModel.kt`.
- **On-demand setup**: When the user opens the Terminal screen for the first time or after a wipe, `TerminalScreen.kt` displays a custom, dark-themed Installation UI component (`TerminalInstallationView`) matching the `klyx_term.mp4` design, featuring progress indicators, percentage count, and error retry options.

### 6. Simplification & Stabilization
- Removed JDK catalog and complex multi-tool downloads during terminal boot to ensure robust and fast Ubuntu startup.
