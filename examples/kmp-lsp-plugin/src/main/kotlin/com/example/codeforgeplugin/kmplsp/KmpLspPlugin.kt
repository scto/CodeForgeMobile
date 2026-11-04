/**
 * Modul: :examples:kmp-lsp-plugin
 * @author Thomas Schmid
 *
 * kmp-lsp (Hessesian) — Kotlin-Multiplatform-Language-Server für Kotlin, Java und Swift, geschrieben in Rust
 * (tree-sitter, ohne JVM). Neues Plugin-Modell (apiVersion 2): Start deklarativ über `contributes.lspServers`;
 * diese Klasse baut den Server beim ersten Laden per `cargo install` (die fertigen Linux-Binaries sind gegen glibc
 * gelinkt und laufen nicht unter Termux/Bionic).
 */
package com.example.codeforgeplugin.kmplsp

import com.example.codeforgeplugin.lspcommon.LspServerPlugin
import com.example.codeforgeplugin.lspcommon.LspServerSpec

private const val INSTALL_DIR = "\$HOME/.local/share/kmp-lsp"

class KmpLspPlugin : LspServerPlugin(
    LspServerSpec(
        tag = "KmpLspPlugin",
        displayName = "kmp-lsp (Kotlin Multiplatform)",
        installDir = INSTALL_DIR,
        installedCheck = "test -x \"$INSTALL_DIR/bin/kmp-lsp\"",
        installScript = "command -v cargo >/dev/null 2>&1 || { echo 'cargo fehlt: Paket rust bestätigen (Einstellungen → Plugins)'; exit 1; }; " +
            "command -v clang >/dev/null 2>&1 || { echo 'clang fehlt: Paket clang bestätigen (Einstellungen → Plugins)'; exit 1; }; " +
            "cargo install kmp-lsp --locked --root \"$INSTALL_DIR\" 2>&1"
    )
)
