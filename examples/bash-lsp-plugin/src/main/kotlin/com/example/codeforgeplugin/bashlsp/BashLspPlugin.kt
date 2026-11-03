/**
 * Modul: :examples:bash-lsp-plugin
 * @author Thomas Schmid
 *
 * Bash Language Server — neues Plugin-Modell (apiVersion 2): Start des Servers erfolgt deklarativ über
 * `contributes.lspServers` in plugin.json; diese Klasse stellt nur sicher, dass der Server vorhanden ist.
 */
package com.example.codeforgeplugin.bashlsp

import com.example.codeforgeplugin.lspcommon.LspServerPlugin
import com.example.codeforgeplugin.lspcommon.LspServerSpec


class BashLspPlugin : LspServerPlugin(
    LspServerSpec(
        tag = "BashLspPlugin",
        displayName = "Bash Language Server",
        installedCheck = "command -v bash-language-server >/dev/null 2>&1",
        missingHint = "bash-language-server fehlt: unter Einstellungen → Plugins die Abhängigkeiten bestätigen (oder: npm i -g bash-language-server)."
    )
)
