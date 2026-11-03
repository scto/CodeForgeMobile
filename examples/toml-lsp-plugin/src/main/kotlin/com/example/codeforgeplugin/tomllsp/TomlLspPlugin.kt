/**
 * Modul: :examples:toml-lsp-plugin
 * @author Thomas Schmid
 *
 * TOML Language Server (Taplo) — neues Plugin-Modell (apiVersion 2): Start des Servers erfolgt deklarativ über
 * `contributes.lspServers` in plugin.json; diese Klasse stellt nur sicher, dass der Server vorhanden ist.
 */
package com.example.codeforgeplugin.tomllsp

import com.example.codeforgeplugin.lspcommon.LspServerPlugin
import com.example.codeforgeplugin.lspcommon.LspServerSpec


class TomlLspPlugin : LspServerPlugin(
    LspServerSpec(
        tag = "TomlLspPlugin",
        displayName = "TOML Language Server (Taplo)",
        installedCheck = "command -v taplo >/dev/null 2>&1",
        missingHint = "taplo fehlt: unter Einstellungen → Plugins die Abhängigkeit „taplo“ bestätigen (oder: pkg install taplo)."
    )
)
