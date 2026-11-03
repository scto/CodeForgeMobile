/**
 * Modul: :examples:cpp-lsp-plugin
 * @author Thomas Schmid
 *
 * C/C++ Language Server (clangd) — neues Plugin-Modell (apiVersion 2): Start des Servers erfolgt deklarativ über
 * `contributes.lspServers` in plugin.json; diese Klasse stellt nur sicher, dass der Server vorhanden ist.
 */
package com.example.codeforgeplugin.cpplsp

import com.example.codeforgeplugin.lspcommon.LspServerPlugin
import com.example.codeforgeplugin.lspcommon.LspServerSpec


class CppLspPlugin : LspServerPlugin(
    LspServerSpec(
        tag = "CppLspPlugin",
        displayName = "C/C++ Language Server (clangd)",
        installedCheck = "command -v clangd >/dev/null 2>&1",
        missingHint = "clangd fehlt: unter Einstellungen → Plugins die Abhängigkeit „clang“ bestätigen (oder: pkg install clang)."
    )
)
