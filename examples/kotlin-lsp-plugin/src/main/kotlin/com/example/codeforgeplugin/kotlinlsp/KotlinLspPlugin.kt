/**
 * Modul: :examples:kotlin-lsp-plugin
 * @author Thomas Schmid
 *
 * Kotlin Language Server — neues Plugin-Modell (apiVersion 2): Start des Servers erfolgt deklarativ über
 * `contributes.lspServers` in plugin.json; diese Klasse stellt nur sicher, dass der Server vorhanden ist.
 */
package com.example.codeforgeplugin.kotlinlsp

import com.example.codeforgeplugin.lspcommon.LspServerPlugin
import com.example.codeforgeplugin.lspcommon.LspServerSpec
import com.example.codeforgeplugin.lspcommon.ServerDownload

class KotlinLspPlugin : LspServerPlugin(
    LspServerSpec(
        tag = "KotlinLspPlugin",
        displayName = "Kotlin Language Server",
        installDir = "\$HOME/.local/share/kotlin-language-server",
        installedCheck = "test -x \"\$HOME/.local/share/kotlin-language-server/bin/kotlin-language-server\"",
        download = ServerDownload("https://github.com/fwcd/kotlin-language-server/releases/download/1.3.9/server.tar.gz", stripComponents = 1),
        requiredJdk = "17"
    )
)
