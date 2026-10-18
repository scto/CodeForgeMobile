### 📥 Übernommene Aktualisierungen

1. **Neuer Task 23:**
   - Umfassende Reparatur und Anbindung des Extensions-Managers an den LSP-Client (Prüfsummen, Archiv-Typen `.tar.gz`/`.gz`, Termux-Pakete `clangd`/`rust-analyzer`, Resolver-Architektur, Editor-Anbindung und Repo-Bereinigung).

2. **APT-Etappen erweitert (`apt-stages.conf`):**
   - `00-codeforge` (`codeforge-env`)
   - `13-pure-tools` (`python-pip`, `yarn`, `autoconf`, `automake`)
   - `14-ide-tools` (`neocmakelsp`, `esbuild`, `pnpm`, `shellcheck`)
   - `15-rust-analyzer` (`rust-analyzer`)
   - `16-native-dev` (`pkg-config`, `libtool`, `flex`, `bison`, `ccache`, `patchelf`, `strace`)
   - `17-debug-misc` (`gdb`, `android-tools`, `gh`, `rsync`, `xmlstarlet`)

3. **CI/CD & Workflows:**
   - Workflow für alle Etappen: `build-all-stages.yml`
   - Gruppen-Planungsskript: `plan-apt-groups.sh` & `apt-groups.json`
   - Aktualisierungen in `build-bootstrap-repo.yml`

4. **Hilfsskript:**
   - `sync-codeforge-env-package.sh`: Synchronisiert das `codeforge-env`-Skript mit dem Fork-Paketverzeichnis.

5. **Doku-Synchronisation:**
   - `STATUS.md`, `TASKS-STATUS.md` und `BOOTSTRAP.md` auf den neuesten Stand gebracht.

Der Arbeitsbaum ist sauber (*working tree clean*). Soll ich direkt mit der Umsetzung von Task 23 beginnen?
