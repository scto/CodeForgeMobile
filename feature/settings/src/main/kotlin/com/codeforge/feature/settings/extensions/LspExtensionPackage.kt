// Modul: :feature:settings
package com.codeforge.feature.settings.extensions

import androidx.annotation.StringRes
import com.codeforge.core.resources.R

enum class ExtensionArchiveType(val extension: String) {
    ZIP("zip"),
    TAR_GZ("tar.gz"),
    GZ("gz"),
    JAR("jar"),
    BINARY("bin")
}

enum class ExtensionStatus {
    NOT_INSTALLED,
    DOWNLOADING,
    VERIFYING,
    EXTRACTING,
    INSTALLING_DEPENDENCIES,
    INSTALLED,
    ERROR
}

sealed interface ExtensionSource {
    data class Download(
        val url: String,
        val sha256: String,
        val archiveType: ExtensionArchiveType
    ) : ExtensionSource

    data class TermuxPackage(
        val packageName: String,
        val binaryName: String
    ) : ExtensionSource
}

data class LspExtensionPackage(
    val id: String,
    @StringRes val nameRes: Int,
    @StringRes val descriptionRes: Int,
    val version: String,
    val language: String,
    val source: ExtensionSource,
    val requires: List<String> = emptyList(),
    val optionalRequires: List<String> = emptyList(),
    val needsNpmInstall: Boolean = false,
    val launchCommand: List<String>,
    val executableRelativePath: String
) {
    // Secondary constructor for backwards compatibility with tests and callers
    constructor(
        id: String,
        @StringRes nameRes: Int,
        @StringRes descriptionRes: Int,
        version: String,
        language: String,
        downloadUrl: String,
        expectedSha256: String,
        archiveType: ExtensionArchiveType,
        executableRelativePath: String,
        requires: List<String> = emptyList(),
        optionalRequires: List<String> = emptyList(),
        needsNpmInstall: Boolean = false,
        launchCommand: List<String> = listOf(executableRelativePath)
    ) : this(
        id = id,
        nameRes = nameRes,
        descriptionRes = descriptionRes,
        version = version,
        language = language,
        source = ExtensionSource.Download(downloadUrl, expectedSha256, archiveType),
        requires = requires,
        optionalRequires = optionalRequires,
        needsNpmInstall = needsNpmInstall,
        launchCommand = launchCommand,
        executableRelativePath = executableRelativePath
    )

    val downloadUrl: String? get() = (source as? ExtensionSource.Download)?.url
    val expectedSha256: String? get() = (source as? ExtensionSource.Download)?.sha256
    val archiveType: ExtensionArchiveType? get() = (source as? ExtensionSource.Download)?.archiveType
    val isTermuxPackage: Boolean get() = source is ExtensionSource.TermuxPackage
    val isDownloadable: Boolean get() = source is ExtensionSource.Download
}

object LspExtensionCatalog {
    /**
     * Kotlin Language Server (fwcd/kotlin-language-server)
     * Download: GitHub Releases asset server.zip
     * SHA-256 verifiziert 2026-10-16: c65efea1c09d88ddb1fe227d8e2c5dbb3d8dd568453bccbbf2e20dfa2f004dcd
     * Benötigt: openjdk-17 (Java Runtime)
     */
    val kotlinLanguageServer = LspExtensionPackage(
        id = "kotlin-language-server",
        nameRes = R.string.extension_kotlin_lsp_name,
        descriptionRes = R.string.extension_kotlin_lsp_desc,
        version = "1.3.11",
        language = "Kotlin",
        source = ExtensionSource.Download(
            url = "https://github.com/fwcd/kotlin-language-server/releases/download/1.3.11/server.zip",
            sha256 = "c65efea1c09d88ddb1fe227d8e2c5dbb3d8dd568453bccbbf2e20dfa2f004dcd",
            archiveType = ExtensionArchiveType.ZIP
        ),
        requires = listOf("openjdk-17"),
        launchCommand = listOf("server/bin/kotlin-language-server"),
        executableRelativePath = "server/bin/kotlin-language-server"
    )

    /**
     * Eclipse JDT Language Server (Eclipse Foundation)
     * Download: Eclipse Milestones (1.45.0)
     * SHA-256 verifiziert 2026-10-16: c09e79e958beb5ce41fb3b5097aec0455298bc88a14c8e41462b57edb2812797
     * Benötigt: openjdk-17, python
     */
    val jdtls = LspExtensionPackage(
        id = "jdtls",
        nameRes = R.string.extension_jdtls_name,
        descriptionRes = R.string.extension_jdtls_desc,
        version = "1.45.0",
        language = "Java",
        source = ExtensionSource.Download(
            url = "https://download.eclipse.org/jdtls/milestones/1.45.0/jdt-language-server-1.45.0-202502271238.tar.gz",
            sha256 = "c09e79e958beb5ce41fb3b5097aec0455298bc88a14c8e41462b57edb2812797",
            archiveType = ExtensionArchiveType.TAR_GZ
        ),
        requires = listOf("openjdk-17", "python"),
        launchCommand = listOf("bin/jdtls"),
        executableRelativePath = "bin/jdtls"
    )

    /**
     * Clangd C/C++ Language Server
     * Bereitstellung über das Termux-Paket 'clang' im CodeForge APT-Repository (apt-stages.conf Stage 10)
     * Binary: $PREFIX/bin/clangd
     */
    val clangd = LspExtensionPackage(
        id = "clangd",
        nameRes = R.string.extension_clangd_name,
        descriptionRes = R.string.extension_clangd_desc,
        version = "18.1.3",
        language = "C/C++",
        source = ExtensionSource.TermuxPackage(
            packageName = "clang",
            binaryName = "clangd"
        ),
        requires = listOf("clang"),
        launchCommand = listOf("clangd"),
        executableRelativePath = "clangd"
    )

    /**
     * Pyright (Microsoft)
     * Download: npm registry tarball pyright-1.1.377.tgz
     * SHA-256 verifiziert 2026-10-16: b11dc22e92560b38ebad99a892544d9ce81e2ce995db430b3683e86db57d7710
     * dist.integrity (sha512): y6ENYuyZXTczPnPWZnqx78pE+ZgyIotEas2M/LFRTq3EfbgVk84EcvuSKLIy2DJeDKjKDxVP/LVmDNHabljD3g==
     * Benötigt: nodejs
     */
    val pyright = LspExtensionPackage(
        id = "pyright",
        nameRes = R.string.extension_pyright_name,
        descriptionRes = R.string.extension_pyright_desc,
        version = "1.1.377",
        language = "Python",
        source = ExtensionSource.Download(
            url = "https://registry.npmjs.org/pyright/-/pyright-1.1.377.tgz",
            sha256 = "b11dc22e92560b38ebad99a892544d9ce81e2ce995db430b3683e86db57d7710",
            archiveType = ExtensionArchiveType.TAR_GZ
        ),
        requires = listOf("nodejs"),
        launchCommand = listOf("node", "package/langserver.index.js", "--stdio"),
        executableRelativePath = "package/langserver.index.js"
    )

    /**
     * Rust Analyzer
     * Bereitstellung über das Termux-Paket 'rust-analyzer' im CodeForge APT-Repository (apt-stages.conf Stage 15)
     * Binary: $PREFIX/bin/rust-analyzer
     */
    val rustAnalyzer = LspExtensionPackage(
        id = "rust-analyzer",
        nameRes = R.string.extension_rust_analyzer_name,
        descriptionRes = R.string.extension_rust_analyzer_desc,
        version = "1.79.0",
        language = "Rust",
        source = ExtensionSource.TermuxPackage(
            packageName = "rust-analyzer",
            binaryName = "rust-analyzer"
        ),
        requires = listOf("rust-analyzer"),
        launchCommand = listOf("rust-analyzer"),
        executableRelativePath = "rust-analyzer"
    )

    /**
     * VS Code Language Servers Extracted (HTML / CSS / JSON / ESLint)
     * Download: npm registry tarball vscode-langservers-extracted-4.8.0.tgz
     * SHA-256 verifiziert 2026-10-16: 8c7102927a32fa145077f0406b66bb3a7d85f5b0a208d4efeb40702b5a6cd2e1
     * dist.integrity (sha512): kWqVdmdcf9Ez1P3hZpvCHomxVqrdK2awwZkezSW3SuXLX0kXS4x8WoA0GkF54SA0CiXtJWszaFpQghUNVlepuQ==
     * Benötigt: nodejs
     */
    val htmlCssJson = LspExtensionPackage(
        id = "vscode-langservers-extracted",
        nameRes = R.string.extension_html_css_json_name,
        descriptionRes = R.string.extension_html_css_json_desc,
        version = "4.8.0",
        language = "HTML / CSS / JSON",
        source = ExtensionSource.Download(
            url = "https://registry.npmjs.org/vscode-langservers-extracted/-/vscode-langservers-extracted-4.8.0.tgz",
            sha256 = "8c7102927a32fa145077f0406b66bb3a7d85f5b0a208d4efeb40702b5a6cd2e1",
            archiveType = ExtensionArchiveType.TAR_GZ
        ),
        requires = listOf("nodejs"),
        launchCommand = listOf("node", "package/bin/vscode-html-language-server", "--stdio"),
        executableRelativePath = "package/bin/vscode-html-language-server"
    )

    /**
     * Eclipse LemMinX XML Language Server (Eclipse Foundation)
     * Download: Eclipse LemMinX Releases (0.31.2 uber-jar)
     * URL: https://download.eclipse.org/lemminx/releases/0.31.2/org.eclipse.lemminx-uber.jar
     * SHA-256 verifiziert 2026-10-21: f4fde164e785c635e5f86361dbf4b993bfe2c5f83fdb52891d058b7dfc9bcfc8
     * Benötigt: openjdk-17
     */
    val lemminx = LspExtensionPackage(
        id = "lemminx",
        nameRes = R.string.extension_lemminx_name,
        descriptionRes = R.string.extension_lemminx_desc,
        version = "0.31.2",
        language = "XML",
        source = ExtensionSource.Download(
            url = "https://download.eclipse.org/lemminx/releases/0.31.2/org.eclipse.lemminx-uber.jar",
            sha256 = "f4fde164e785c635e5f86361dbf4b993bfe2c5f83fdb52891d058b7dfc9bcfc8",
            archiveType = ExtensionArchiveType.JAR
        ),
        requires = listOf("openjdk-17"),
        launchCommand = listOf("java", "-jar", "org.eclipse.lemminx-uber.jar"),
        executableRelativePath = "org.eclipse.lemminx-uber.jar"
    )

    /**
     * YAML Language Server (Red Hat)
     * Download: npm registry tarball yaml-language-server-2.0.0.tgz
     * URL: https://registry.npmjs.org/yaml-language-server/-/yaml-language-server-2.0.0.tgz
     * SHA-256 verifiziert 2026-10-21: 8dcd68cc8bcdde719def62e651de9744576d3227de2e4ab85961603c92a52423
     * dist.integrity (sha512): vT6ZEMTkXJA9Ixo07ByLgPkkt/L22fJdVDGAoB3EpPq3DCfKkMYeEeZdK5ECU6alwFUofhETo94/OV8GUxGuBg==
     * Benötigt: nodejs, npm
     */
    val yamlLanguageServer = LspExtensionPackage(
        id = "yaml-language-server",
        nameRes = R.string.extension_yaml_lsp_name,
        descriptionRes = R.string.extension_yaml_lsp_desc,
        version = "2.0.0",
        language = "YAML",
        source = ExtensionSource.Download(
            url = "https://registry.npmjs.org/yaml-language-server/-/yaml-language-server-2.0.0.tgz",
            sha256 = "8dcd68cc8bcdde719def62e651de9744576d3227de2e4ab85961603c92a52423",
            archiveType = ExtensionArchiveType.TAR_GZ
        ),
        requires = listOf("nodejs"),
        needsNpmInstall = true,
        launchCommand = listOf("node", "package/bin/yaml-language-server", "--stdio"),
        executableRelativePath = "package/bin/yaml-language-server"
    )

    /**
     * Taplo TOML Language Server
     * Bereitstellung über das Termux-Paket 'taplo' (APT-Paket 'taplo', Binary 'taplo')
     * Binary: $PREFIX/bin/taplo
     * Befehl: taplo lsp stdio
     */
    val taplo = LspExtensionPackage(
        id = "taplo",
        nameRes = R.string.extension_taplo_name,
        descriptionRes = R.string.extension_taplo_desc,
        version = "0.10.0",
        language = "TOML",
        source = ExtensionSource.TermuxPackage(
            packageName = "taplo",
            binaryName = "taplo"
        ),
        requires = listOf("taplo"),
        launchCommand = listOf("taplo", "lsp", "stdio"),
        executableRelativePath = "taplo"
    )

    /**
     * Bash Language Server
     * Download: npm registry tarball bash-language-server-5.8.1.tgz
     * URL: https://registry.npmjs.org/bash-language-server/-/bash-language-server-5.8.1.tgz
     * SHA-256 verifiziert 2026-10-21: 2ae910de8c50f4ec148222bdc580c121f2c621bdacc1225716218ca700f8ec79
     * dist.integrity (sha512): F3g0hKZhCiKbLUf9wkrST6+ZypsHvJqaJxw/+J7WK2ywhFyc8+7jSHSTU1UDYncZkyWgbKV6+eZy5qWTZtoXww==
     * Benötigt: nodejs; Optional: shellcheck für Diagnosen
     */
    val bashLanguageServer = LspExtensionPackage(
        id = "bash-language-server",
        nameRes = R.string.extension_bash_lsp_name,
        descriptionRes = R.string.extension_bash_lsp_desc,
        version = "5.8.1",
        language = "Shell / Bash",
        source = ExtensionSource.Download(
            url = "https://registry.npmjs.org/bash-language-server/-/bash-language-server-5.8.1.tgz",
            sha256 = "2ae910de8c50f4ec148222bdc580c121f2c621bdacc1225716218ca700f8ec79",
            archiveType = ExtensionArchiveType.TAR_GZ
        ),
        requires = listOf("nodejs"),
        optionalRequires = listOf("shellcheck"),
        needsNpmInstall = true,
        launchCommand = listOf("node", "package/out/cli.js", "start"),
        executableRelativePath = "package/out/cli.js"
    )

    val ALL: List<LspExtensionPackage> = listOf(
        kotlinLanguageServer,
        jdtls,
        clangd,
        pyright,
        rustAnalyzer,
        htmlCssJson,
        lemminx,
        yamlLanguageServer,
        taplo,
        bashLanguageServer
    )

    fun findById(id: String): LspExtensionPackage? = ALL.find { it.id == id }
}
