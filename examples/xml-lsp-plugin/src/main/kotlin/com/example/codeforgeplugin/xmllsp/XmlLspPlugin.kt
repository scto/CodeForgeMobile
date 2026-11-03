/**
 * Modul: :examples:xml-lsp-plugin
 * @author Thomas Schmid
 *
 * XML Language Server (LemMinX) — neues Plugin-Modell (apiVersion 2): Start des Servers erfolgt deklarativ über
 * `contributes.lspServers` in plugin.json; diese Klasse stellt nur sicher, dass der Server vorhanden ist.
 */
package com.example.codeforgeplugin.xmllsp

import com.example.codeforgeplugin.lspcommon.LspServerPlugin
import com.example.codeforgeplugin.lspcommon.LspServerSpec
import com.example.codeforgeplugin.lspcommon.ServerDownload

class XmlLspPlugin : LspServerPlugin(
    LspServerSpec(
        tag = "XmlLspPlugin",
        displayName = "XML Language Server (LemMinX)",
        installDir = "\$HOME/.local/share/lemminx",
        installedCheck = "test -s \$HOME/.local/share/lemminx/org.eclipse.lemminx-uber.jar",
        download = ServerDownload(
            "https://repo.eclipse.org/content/repositories/lemminx-releases/org/eclipse/lemminx/org.eclipse.lemminx/0.28.0/org.eclipse.lemminx-0.28.0-uber.jar",
            fileName = "org.eclipse.lemminx-uber.jar"
        ),
        requiredJdk = "17"
    )
)
