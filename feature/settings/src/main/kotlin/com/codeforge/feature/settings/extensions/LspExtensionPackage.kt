// Modul: :feature:settings
package com.codeforge.feature.settings.extensions

import androidx.annotation.StringRes
import com.codeforge.core.resources.R

enum class ExtensionArchiveType(val extension: String) {
    ZIP("zip"),
    JAR("jar"),
    BINARY("bin")
}

enum class ExtensionStatus {
    NOT_INSTALLED,
    DOWNLOADING,
    VERIFYING,
    EXTRACTING,
    INSTALLED,
    ERROR
}

data class LspExtensionPackage(
    val id: String,
    @StringRes val nameRes: Int,
    @StringRes val descriptionRes: Int,
    val version: String,
    val language: String,
    val downloadUrl: String,
    val expectedSha256: String,
    val archiveType: ExtensionArchiveType,
    val executableRelativePath: String
)

object LspExtensionCatalog {
    val kotlinLanguageServer = LspExtensionPackage(
        id = "kotlin-language-server",
        nameRes = R.string.extension_kotlin_lsp_name,
        descriptionRes = R.string.extension_kotlin_lsp_desc,
        version = "1.3.11",
        language = "Kotlin",
        downloadUrl = "https://github.com/fwcd/kotlin-language-server/releases/download/1.3.11/server.zip",
        expectedSha256 = "c08e8df5e8c1591f8615b1c5a932d0ce7ce6a17b876f185ef3db1c1c7aeceebf",
        archiveType = ExtensionArchiveType.ZIP,
        executableRelativePath = "bin/kotlin-language-server"
    )

    val jdtls = LspExtensionPackage(
        id = "jdtls",
        nameRes = R.string.extension_jdtls_name,
        descriptionRes = R.string.extension_jdtls_desc,
        version = "1.38.0",
        language = "Java",
        downloadUrl = "https://download.eclipse.org/jdtls/milestones/1.38.0/jdt-language-server-1.38.0.tar.gz",
        expectedSha256 = "09462fc74805ef952f44227f2f111f1ae59902506eef14115e51084d50f8ad91",
        archiveType = ExtensionArchiveType.ZIP,
        executableRelativePath = "bin/jdtls"
    )

    val clangd = LspExtensionPackage(
        id = "clangd",
        nameRes = R.string.extension_clangd_name,
        descriptionRes = R.string.extension_clangd_desc,
        version = "18.1.3",
        language = "C/C++",
        downloadUrl = "https://github.com/clangd/clangd/releases/download/18.1.3/clangd-linux-18.1.3.zip",
        expectedSha256 = "1d6c818fa8f8aa48c8b41ca85786ba113501a1d1ca22ee3a2c5ce82e6ff93414",
        archiveType = ExtensionArchiveType.ZIP,
        executableRelativePath = "bin/clangd"
    )

    val pyright = LspExtensionPackage(
        id = "pyright",
        nameRes = R.string.extension_pyright_name,
        descriptionRes = R.string.extension_pyright_desc,
        version = "1.1.377",
        language = "Python",
        downloadUrl = "https://registry.npmjs.org/pyright/-/pyright-1.1.377.tgz",
        expectedSha256 = "91a826bcddbeff64eec2489cbe3600f688e1467406a6b83f33cf2b66cbcf22ca",
        archiveType = ExtensionArchiveType.ZIP,
        executableRelativePath = "package/index.js"
    )

    val rustAnalyzer = LspExtensionPackage(
        id = "rust-analyzer",
        nameRes = R.string.extension_rust_analyzer_name,
        descriptionRes = R.string.extension_rust_analyzer_desc,
        version = "2024-08-19",
        language = "Rust",
        downloadUrl = "https://github.com/rust-lang/rust-analyzer/releases/download/2024-08-19/rust-analyzer-aarch64-unknown-linux-gnu.gz",
        expectedSha256 = "8f307ebc31bcfbbdcb06bc455a04fb68d6f9a0d8e202521c7e2c949755ca7b6c",
        archiveType = ExtensionArchiveType.BINARY,
        executableRelativePath = "rust-analyzer"
    )

    val htmlCssJson = LspExtensionPackage(
        id = "vscode-langservers-extracted",
        nameRes = R.string.extension_html_css_json_name,
        descriptionRes = R.string.extension_html_css_json_desc,
        version = "4.8.0",
        language = "HTML / CSS / JSON",
        downloadUrl = "https://registry.npmjs.org/vscode-langservers-extracted/-/vscode-langservers-extracted-4.8.0.tgz",
        expectedSha256 = "2f6c096abeccecae2b1b369ba0f0f5b9d29ba029f626155ee2dfebc469b76e27",
        archiveType = ExtensionArchiveType.ZIP,
        executableRelativePath = "package/bin/vscode-html-language-server"
    )

    val ALL: List<LspExtensionPackage> = listOf(
        kotlinLanguageServer,
        jdtls,
        clangd,
        pyright,
        rustAnalyzer,
        htmlCssJson
    )

    fun findById(id: String): LspExtensionPackage? = ALL.find { it.id == id }
}
