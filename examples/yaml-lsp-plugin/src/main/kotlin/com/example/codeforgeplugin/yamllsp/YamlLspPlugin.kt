/**
 * Modul: :examples:yaml-lsp-plugin
 * @author Thomas Schmid
 *
 * YAML Language Server — neues Plugin-Modell (apiVersion 2): Start des Servers erfolgt deklarativ über
 * `contributes.lspServers` in plugin.json; diese Klasse stellt nur sicher, dass der Server vorhanden ist.
 */
package com.example.codeforgeplugin.yamllsp

import com.example.codeforgeplugin.lspcommon.LspServerPlugin
import com.example.codeforgeplugin.lspcommon.LspServerSpec


class YamlLspPlugin : LspServerPlugin(
    LspServerSpec(
        tag = "YamlLspPlugin",
        displayName = "YAML Language Server",
        installedCheck = "command -v yaml-language-server >/dev/null 2>&1",
        missingHint = "yaml-language-server fehlt: unter Einstellungen → Plugins die Abhängigkeiten bestätigen (oder: npm i -g yaml-language-server)."
    )
)
