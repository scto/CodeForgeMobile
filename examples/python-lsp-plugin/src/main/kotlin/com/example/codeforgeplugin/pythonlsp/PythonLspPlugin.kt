/**
 * Modul: :examples:python-lsp-plugin
 * @author Thomas Schmid
 *
 * Python Language Server (Pyright) — neues Plugin-Modell (apiVersion 2): Start des Servers erfolgt deklarativ über
 * `contributes.lspServers` in plugin.json; diese Klasse stellt nur sicher, dass der Server vorhanden ist.
 */
package com.example.codeforgeplugin.pythonlsp

import com.example.codeforgeplugin.lspcommon.LspServerPlugin
import com.example.codeforgeplugin.lspcommon.LspServerSpec


class PythonLspPlugin : LspServerPlugin(
    LspServerSpec(
        tag = "PythonLspPlugin",
        displayName = "Python Language Server (Pyright)",
        installedCheck = "command -v pyright-langserver >/dev/null 2>&1",
        missingHint = "pyright fehlt: unter Einstellungen → Plugins die Abhängigkeiten bestätigen (oder: npm i -g pyright)."
    )
)
