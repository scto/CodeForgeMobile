// Modul: :feature:settings
package com.codeforge.feature.settings.extensions

import com.codeforge.core.domain.extension.ContributionRepository
import com.codeforge.core.domain.model.LspServerLaunch
import com.codeforge.core.domain.repository.LspServerResolver
import com.codeforge.libs.terminal_engine.TermuxEnvironment
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LspServerResolverImpl @Inject constructor(
    private val extensionsManager: ExtensionsManager,
    private val contributionRepository: ContributionRepository
) : LspServerResolver {

    override suspend fun resolve(languageId: String): LspServerLaunch? {
        val pkg = findPackageForLanguage(languageId)
        if (pkg == null || !extensionsManager.isInstalled(pkg)) return resolveContributed(languageId)

        val env = TermuxEnvironment.environment()
        val extensionDir = extensionsManager.getExtensionDirectory(pkg)
        val termuxBin = File(TermuxEnvironment.prefix, "bin")

        val command: List<String> = when (pkg.id) {
            "kotlin-language-server" -> {
                val exe = extensionsManager.getExecutableFile(pkg) ?: return null
                listOf(exe.absolutePath)
            }
            "jdtls" -> {
                val exe = extensionsManager.getExecutableFile(pkg) ?: return null
                val pythonBin = File(termuxBin, "python3").takeIf { it.canExecute() }?.absolutePath
                    ?: File(termuxBin, "python").takeIf { it.canExecute() }?.absolutePath
                if (pythonBin != null) {
                    listOf(pythonBin, exe.absolutePath)
                } else {
                    listOf(exe.absolutePath)
                }
            }
            "pyright" -> {
                val nodeBin = File(termuxBin, "node").takeIf { it.canExecute() }?.absolutePath ?: "node"
                val script = File(extensionDir, "package/langserver.index.js").absolutePath
                listOf(nodeBin, script, "--stdio")
            }
            "vscode-langservers-extracted" -> {
                val nodeBin = File(termuxBin, "node").takeIf { it.canExecute() }?.absolutePath ?: "node"
                val relScript = when (languageId.lowercase()) {
                    "css" -> "package/bin/vscode-css-language-server"
                    "json" -> "package/bin/vscode-json-language-server"
                    else -> "package/bin/vscode-html-language-server"
                }
                val script = File(extensionDir, relScript).absolutePath
                listOf(nodeBin, script, "--stdio")
            }
            "clangd" -> {
                val exe = extensionsManager.getExecutableFile(pkg)
                    ?: File(termuxBin, "clangd").takeIf { it.canExecute() }
                    ?: return null
                listOf(exe.absolutePath)
            }
            "rust-analyzer" -> {
                val exe = extensionsManager.getExecutableFile(pkg)
                    ?: File(termuxBin, "rust-analyzer").takeIf { it.canExecute() }
                    ?: return null
                listOf(exe.absolutePath)
            }
            "lemminx" -> {
                val jar = extensionsManager.getExecutableFile(pkg) ?: return null
                val javaBin = File(termuxBin, "java").takeIf { it.canExecute() }?.absolutePath ?: "java"
                listOf(javaBin, "-jar", jar.absolutePath)
            }
            "yaml-language-server" -> {
                val nodeBin = File(termuxBin, "node").takeIf { it.canExecute() }?.absolutePath ?: "node"
                val script = File(extensionDir, "package/bin/yaml-language-server").absolutePath
                listOf(nodeBin, script, "--stdio")
            }
            "taplo" -> {
                val exe = extensionsManager.getExecutableFile(pkg)
                    ?: File(termuxBin, "taplo").takeIf { it.canExecute() }
                    ?: return null
                listOf(exe.absolutePath, "lsp", "stdio")
            }
            "bash-language-server" -> {
                val nodeBin = File(termuxBin, "node").takeIf { it.canExecute() }?.absolutePath ?: "node"
                val script = File(extensionDir, "package/out/cli.js").absolutePath
                listOf(nodeBin, script, "start")
            }
            else -> {
                val exe = extensionsManager.getExecutableFile(pkg) ?: return null
                listOf(exe.absolutePath)
            }
        }

        val serverId = when (pkg.id) {
            "vscode-langservers-extracted" -> when (languageId.lowercase()) {
                "css" -> "vscode-css-language-server"
                "json" -> "vscode-json-language-server"
                else -> "vscode-html-language-server"
            }
            else -> pkg.id
        }

        return LspServerLaunch(command = command, env = env, serverId = serverId)
    }

    /**
     * Language Server, den ein aktiviertes Plugin deklarativ beisteuert (`contributes.lspServers`).
     * Platzhalter `${pluginDir}` und `${home}` im Kommando werden ersetzt; existiert die ausführbare
     * Datei nicht (und ist es ein absoluter Pfad), gibt es keinen Server.
     */
    private fun resolveContributed(languageId: String): LspServerLaunch? {
        val wanted = languageId.lowercase()
        val hit = contributionRepository.contributions.value.lspServers
            .firstOrNull { r -> r.item.languages.any { it.equals(wanted, ignoreCase = true) } }
            ?: return null
        val command = hit.item.command.map {
            it.replace("${'$'}{pluginDir}", hit.pluginDir).replace("${'$'}{home}", TermuxEnvironment.home)
        }
        val exe = command.first()
        if (exe.startsWith("/") && !File(exe).exists()) return null
        return LspServerLaunch(command = command, env = TermuxEnvironment.environment(), serverId = hit.item.id)
    }

    private fun findPackageForLanguage(languageId: String): LspExtensionPackage? = when (languageId.lowercase()) {
        "kotlin" -> LspExtensionCatalog.kotlinLanguageServer
        "java" -> LspExtensionCatalog.jdtls
        "python" -> LspExtensionCatalog.pyright
        "html", "css", "json" -> LspExtensionCatalog.htmlCssJson
        "c", "cpp", "c_cpp" -> LspExtensionCatalog.clangd
        "rust" -> LspExtensionCatalog.rustAnalyzer
        "xml" -> LspExtensionCatalog.lemminx
        "yaml", "yml" -> LspExtensionCatalog.yamlLanguageServer
        "toml" -> LspExtensionCatalog.taplo
        "shellscript", "bash", "sh" -> LspExtensionCatalog.bashLanguageServer
        else -> null
    }
}
