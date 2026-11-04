/**
 * Modul: :examples:kmp-lsp-plugin
 * @author Thomas Schmid
 *
 * kmp-lsp (Hessesian) — Kotlin-Multiplatform-Language-Server für Kotlin, Java und Swift, geschrieben in Rust
 * (tree-sitter, ohne JVM). Neues Plugin-Modell (apiVersion 2): Start deklarativ über `contributes.lspServers`;
 * diese Klasse lädt beim ersten Laden das fertige Termux-Binary aus dem Release `termux-1` des Forks
 * `scto/kmp-lsp` (gegen Bionic gebaut, aarch64 und x86_64), prüft die SHA-256-Summe und legt es unter
 * `~/.local/share/kmp-lsp/bin` ab. Kein Rust-Build auf dem Gerät mehr nötig.
 */
package com.example.codeforgeplugin.kmplsp

import com.example.codeforgeplugin.lspcommon.LspServerPlugin
import com.example.codeforgeplugin.lspcommon.LspServerSpec

private const val INSTALL_DIR = "\$HOME/.local/share/kmp-lsp"
private const val RELEASE_URL = "https://github.com/scto/kmp-lsp/releases/download/termux-1"
private const val SHA256_AARCH64 = "228a955d5a712445683963ef6cc6775855969dbb63e013b07fbb7a08b7c1e352"
private const val SHA256_X86_64 = "63ea6880a36705ae4123a10d6e747eaae78c93006fcf542cfecde2a7cec8be73"

/** Lädt das Release-Archiv passend zur Architektur, prüft die Summe und installiert Server und JAR-Indexer. */
private val INSTALL_SCRIPT = listOf(
    "set -e",
    "case \"\$(uname -m)\" in aarch64|arm64) A=aarch64; H=$SHA256_AARCH64;; x86_64) A=x86_64; H=$SHA256_X86_64;; " +
        "*) echo \"Architektur nicht unterstützt: \$(uname -m)\"; exit 1;; esac",
    "command -v curl >/dev/null 2>&1 || { echo 'curl fehlt'; exit 1; }",
    "T=\"\$(mktemp -d)\"; trap 'rm -rf \"\$T\"' EXIT",
    "curl -fsSL -o \"\$T/k.tgz\" \"$RELEASE_URL/kmp-lsp-termux-\$A.tar.gz\"",
    "echo \"\$H  \$T/k.tgz\" | sha256sum -c - >/dev/null || { echo 'SHA-256 stimmt nicht'; exit 1; }",
    "tar xzf \"\$T/k.tgz\" -C \"\$T\"",
    "mkdir -p \"$INSTALL_DIR/bin\"",
    "cp \"\$T/kmp-lsp-termux-\$A/kmp-lsp\" \"\$T/kmp-lsp-termux-\$A/kmp-jar-indexer.jar\" \"$INSTALL_DIR/bin/\"",
    "chmod 755 \"$INSTALL_DIR/bin/kmp-lsp\""
).joinToString("; ")

class KmpLspPlugin : LspServerPlugin(
    LspServerSpec(
        tag = "KmpLspPlugin",
        displayName = "kmp-lsp (Kotlin Multiplatform)",
        installDir = INSTALL_DIR,
        installedCheck = "test -x \"$INSTALL_DIR/bin/kmp-lsp\"",
        installScript = INSTALL_SCRIPT
    )
)
