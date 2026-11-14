/**
 * Modul: :examples:java-lsp-plugin
 * @author Thomas Schmid
 *
 * Java Language Server — neues Plugin-Modell (apiVersion 2): Start des Servers erfolgt deklarativ über
 * `contributes.lspServers` in plugin.json; diese Klasse stellt nur sicher, dass der Server vorhanden ist.
 */
package com.example.codeforgeplugin.javalsp

import com.example.codeforgeplugin.lspcommon.LspServerPlugin
import com.example.codeforgeplugin.lspcommon.LspServerSpec
import com.example.codeforgeplugin.lspcommon.ServerDownload

class JavaLspPlugin : LspServerPlugin(
    LspServerSpec(
        tag = "JavaLspPlugin",
        displayName = "Java Language Server",
        installDir = "\$HOME/.local/share/jdt-language-server",
        installedCheck = "ls \$HOME/.local/share/jdt-language-server/plugins/org.eclipse.equinox.launcher_*.jar >/dev/null 2>&1",
        download = ServerDownload("https://download.eclipse.org/jdtls/snapshots/jdt-language-server-latest.tar.gz", stripComponents = 0),
        // Aktuelle JDT-LS-Snapshots laufen nur noch mit Java 21.
        jdkVersions = listOf(21)
    )
)
