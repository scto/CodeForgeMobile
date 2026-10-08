Act as an expert Bash developer and Termux build system engineer. Your task is to write a comprehensive, automated Bash script that sets up a custom Termux build environment, modifies the app package name, builds the bootstraps via binary patching for all architectures, and generates a fully signed APT repository ready for GitHub Pages.

Please generate a script (e.g., `build_codeforge_repo.sh`) that sequentially executes the following requirements:

1. **Environment Setup & Cloning:**
   - Clone the repository: `https://github.com/scto/terminal-packages-codeforge` (Do NOT clone the official termux-packages repo).
   - Install required build dependencies (like `apt-ftparchive`, `gnupg`, `wget`, `curl`, `rsync`).
   - Append the following entries to the project's `.gitignore` (idempotent, each only once): `.gpg/`, `.gpg/private/`, `output/`, `github_repo_ready/`, `tmp/`, `terminal-packages-codeforge/`, so that key material and build artefacts are never committed.
   - Do NOT use `|| true` when installing dependencies; a failed install must abort the script. Also require `unzip` (used for the bootstrap verification).

2. **Namespace & Prefix Refactoring:**
   - Programmatically patch the Termux build environment to replace all instances of `com.termux` with `com.codeforge.app`.
   - Specifically, ensure `TERMUX_APP_PACKAGE` is set to `com.codeforge.app` and the `TERMUX_PREFIX` is changed from `/data/data/com.termux/files/usr` to `/data/data/com.codeforge.app/files/usr` in the relevant configuration files (e.g., `properties.sh` or build scripts).

3. **GPG Key Generation & Checksum:**
   - Create a `.gpg` directory and a `.gpg/private` directory in the project root (`$WORKSPACE_DIR/.gpg` and `$WORKSPACE_DIR/.gpg/private`).
   - Generate a new local GPG key in batch mode. The Key Name is `codeforge.gpg`, User Name is `Thomas Schmid`, and E-Mail is `tschmid35@gmail.com`.
   - The GPG passphrase is already stored by the user in `~/.bashrc` as `CODEFORGE_GPG_PASSPHRASE`. **Never prompt the user for it.** Load it automatically with a function `load_gpg_passphrase`: (1) use the environment variable if it is already set and non-empty; (2) otherwise read ONLY the last line matching `^[[:space:]]*(export[[:space:]]+)?CODEFORGE_GPG_PASSPHRASE=` from `"$HOME/.bashrc"` with `grep` and strip one pair of surrounding single or double quotes. Do NOT `source ~/.bashrc` (it returns early in non-interactive shells and may have side effects). Abort with a clear message (without printing any value) if the file is unreadable, the line is missing or the value is empty. Never hardcode the passphrase and never print or log it. Set `umask 077` and create the GPG home directory with mode 700 inside `$WORKSPACE_DIR/tmp` (removed via `trap ... EXIT`).
   - Pass the passphrase to every `gpg` call that needs it (secret-key export, signing) via `printf '%s\n' "$CODEFORGE_GPG_PASSPHRASE" | gpg --pinentry-mode loopback --passphrase-fd 0 ...`, never as `--passphrase <value>` on the command line. Set the exported private key to mode 600.
   - Export the generated GPG public key and save it to `<Project Root>/.gpg/codeforge.gpg`.
   - Export the generated GPG private key and save it to `<Project Root>/.gpg/private/codeforge.gpg`.
   - Calculate the SHA256 checksum of the public key and save the hash to `<Project Root>/.gpg/sha256.txt`.
   - Calculate the SHA256 checksum of the private key and save the hash to `<Project Root>/.gpg/private/sha256.txt`.

4. **Patching the Build Environment (Direct File Substitution):**
   - Dynamically extract the key ID / fingerprint of the newly generated GPG key for Thomas Schmid.
   - Copy the exported public GPG key to `packages/termux-keyring/codeforge_pub.gpg` and `packages/termux-keyring/codeforge.gpg` inside the cloned repository.
   - Inject the command `install -Dm600 $TERMUX_PKG_BUILDER_DIR/codeforge.gpg $GPG_SHARE_DIR` directly into `packages/termux-keyring/build.sh` so it executes during the package build.
   - Locate the dependency PGP key setup block in `build-package.sh` (around line 631). Remove the old `gpg --list-keys` logic blocks entirely using sed/awk.
   - Inject the following clean Bash code directly into `build-package.sh`:
     ```bash
     gpg --list-keys <YOUR_DYNAMIC_KEY_ID> >/dev/null 2>&1 || {
         gpg --import "$TERMUX_SCRIPTDIR/packages/termux-keyring/codeforge_pub.gpg"
         gpg --no-tty --command-file <(echo -e "trust\n5\ny") --edit-key <YOUR_DYNAMIC_KEY_ID>
     }
     ```
   - Ensure that the placeholder `<YOUR_DYNAMIC_KEY_ID>` in the injected block is programmatically replaced with the actual newly extracted fingerprint/key ID.
   - In `scripts/properties.sh` replace `com.termux` → `com.codeforge.app` and then collapse `com.codeforge.app.app` → `com.codeforge.app`. Do NOT use a rule such as `s/com.codeforge /com.codeforge.app/` (it matches a trailing space and corrupts text).

5. **Bootstrap Generation (On-Device Binary Patching):**
   - Use `generate-bootstraps.sh` instead of `build-bootstraps.sh`. Since this script runs directly on an Android device inside a PRoot/Termux environment, compiling the entire toolchain from C/C++ source is not feasible.
   - You must inject a Python or `sed` script that patches `generate-bootstraps.sh` on the fly before executing it.
   - This patch must do the following inside `generate-bootstraps.sh`:
     1. Intercept the extraction phase of the downloaded `.deb` packages.
     2. **Important:** The repository `terminal-packages-codeforge` already contains logic to rename the rootfs from `com.termux` to `com.codeforge.app`. Do **NOT** run a blanket "search & replace" over the shell script itself for `com.termux` -> `com.codeforge.app`, as this breaks the existing `cp`/`mv` logic.
     3. Run a recursive `sed` command over all extracted **text** files in the rootfs (select them with `find … -exec grep -Iq . {} \;`; escape the dots in the pattern):
        - `com\.termux` -> `com.codeforge.app`
        - Fix double extensions if they occur (e.g., replacing `com.codeforge.app.app` with `com.codeforge.app`).
        - Apply the same replacement to the symlink targets and to `var/lib/dpkg/info/*.list`.
        - **Do NOT run the length-changing `sed` over ELF binaries**: `com.termux` is 10 bytes, `com.codeforge.app` is 16 bytes, so string tables and offsets would be corrupted. Binaries must come from packages that were already built with the prefix `/data/data/com.codeforge.app/files/usr`.
     4. After generation, extract every `bootstrap-*.zip` into `$WORKSPACE_DIR/tmp/verify` and **fail immediately** (exit 1, list the files) if any ELF file still contains the string `com.termux`.
     5. Prevent the script from attempting to touch or write to system root paths like `/data/TERMUX_ARCH`: replace that path with `$WORKSPACE_DIR/tmp/TERMUX_ARCH`. Also route temporary directories below `$WORKSPACE_DIR/tmp` instead of `/tmp` by replacing the `/tmp` string. **Do NOT inject absolute path templates or `-p` into `mktemp -d` commands**, as Termux's `mktemp` does not support absolute templates alongside `--tmpdir`. Replacing `/tmp` is sufficient.
   - **CRITICAL INSTRUCTION:** Do NOT include any fallback logic, error suppression (`|| true`, `2>/dev/null` on commands that can fail), or dummy archive generation. The script MUST run the generator script explicitly and fail immediately if the build process fails.
   - Move the generated and patched bootstrap archives (.zip or .tar.xz) into a designated `output/bootstraps/` directory.

6. **Checksum Generation:**
   - Iterate over the compiled bootstrap archives and generate a `sha256sum` file for each one. 

7. **GitHub APT Repository Generation:**
   - Create a standard Debian APT repository structure (`pool/main/` and `dists/stable/main/binary-{aarch64,arm,i686,x86_64}`).
   - Use `apt-ftparchive` to generate the `Packages`, `Packages.gz`, and `Release` files.
   - Sign the `Release` file with the generated GPG key to create `Release.gpg` and `InRelease`. Ensure you use a secure piping method (e.g., `printf '%s\n' "$KEY_PASS" | gpg --passphrase-fd 0 --pinentry-mode loopback`) instead of exposing the passphrase as a direct command-line argument.
   - Organize all files in a `github_repo_ready/` directory so it can be directly committed and pushed to a GitHub Pages branch.

8. **Documentation Generation:**
   - Create a `docs/` directory in the project root.
   - Generate a Markdown file named `overview_and_summary.md` inside `docs/`.
   - Write a detailed summary into this file containing the execution date, built architectures, paths to all generated outputs (bootstraps, APT repo), and paths to the public/private GPG keys and their respective checksums.

Ensure the final script is robust, includes error handling (`set -eo pipefail`), securely handles secrets, and prints clear status messages for each step of the build pipeline.

9. **Integration with the CodeForgeMobile app (context):**
   - `applicationId`, `TERMUX_PACKAGE_NAME` and the prefix must be exactly `com.codeforge.app` / `/data/data/com.codeforge.app/files/usr` (the Android namespace of the `:libs:termux-app` module is separate: `com.codeforge.termux`).
   - The Gradle plugin `codeforge.terminal.bootstrap` downloads the ABI archives (`aarch64`, `arm`, `x86_64`) and verifies SHA-256. After publishing, set `codeforgeBootstrapUrlTemplate` (with `%1$s` = version, `%2$s` = ABI) and `codeforgeBootstrapSha256.<abi>` from the generated `.sha256` files; the `i686` archive is produced but not embedded.
   - Document in `docs/overview_and_summary.md` that the passphrase was loaded from the environment / `~/.bashrc` and was not stored or written anywhere.

