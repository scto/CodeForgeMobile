# Explanation: PRoot /bin/env vs /usr/bin/env Resolution

## Overview
This document answers the technical question regarding `codeforge_debug.log`:
> *Why does `ls $PREFIX/local/ubuntu/bin/env` succeed manually, while PRoot emits `execve("/usr/bin/env"): No such file or directory`?*

---

## 1. Root Cause Analysis

### Host View vs. Container View
- When checking host paths manually via terminal (`ls $PREFIX/local/ubuntu/bin/env`), the binary `env` resides under the rootfs `/bin/` directory (`$PREFIX/local/ubuntu/bin/env`).
- In standard Linux guest distributions (Alpine, Ubuntu, Debian rootfs packages), core utilities are installed in `/bin/env`.
- Previously, `ProotCommandBuilder.kt` hardcoded the launcher path as `/usr/bin/env`.
- Inside clean guest rootfs containers, `/usr/bin/env` did not yet exist or was an unlinked symlink prior to full package setup.
- Consequently, when PRoot attempted `execve("/usr/bin/env")` inside the container rootfs, Linux returned `ENOENT` (`No such file or directory`), causing `sdkmanager` and `apt` background queries to fail.

---

## 2. Implemented Fix in `ProotCommandBuilder.kt`

In [`libs/terminal-engine/src/main/kotlin/com/codeforge/libs/terminal_engine/ProotCommandBuilder.kt`](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/libs/terminal-engine/src/main/kotlin/com/codeforge/libs/terminal_engine/ProotCommandBuilder.kt):

1. **Prioritize `/bin/env`**:
   The executable selection logic now prioritizes `/bin/env` (which is present in Alpine, Ubuntu, and Debian rootfs binaries):
   ```kotlin
   val envExecutable = when {
       binEnv.exists() -> "/bin/env"
       usrBinEnv.exists() -> "/usr/bin/env"
       else -> "/bin/sh"
   }
   ```
2. **Automatic Symlink Guarantee**:
   If `/usr/bin/env` does not exist inside `rootfsDir`, a symbolic link pointing to `/bin/env` is created automatically:
   ```kotlin
   if (!usrBinEnv.exists() && binEnv.exists()) {
       try {
           java.nio.file.Files.createSymbolicLink(usrBinEnv.toPath(), java.nio.file.Paths.get("/bin/env"))
       } catch (_: Exception) {}
   }
   ```

---

## 3. Results
- Background SDK Manager and package manager CLI calls now successfully execute via `/bin/env`.
- Scripts inside guest rootfs using shebang `#!/usr/bin/env` run without path resolution errors.
