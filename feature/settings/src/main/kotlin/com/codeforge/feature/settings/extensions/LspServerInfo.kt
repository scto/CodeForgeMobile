// Modul: :feature:settings
package com.codeforge.feature.settings.extensions

import androidx.annotation.StringRes
import com.codeforge.core.resources.R

/** Zusatzinformationen zu einem Sprachserver für die Detailansicht (Autor, Links, Texte, Update-Quelle). */
data class LspServerInfo(
    val packageId: String,
    val author: String,
    val homepageUrl: String,
    val repoSlug: String,
    val license: String,
    val update: LspUpdateSource,
    @StringRes val longDescriptionRes: Int,
    @StringRes val capabilitiesRes: Int,
    @StringRes val limitationsRes: Int,
) {
    val repoUrl: String get() = "https://github.com/$repoSlug"
}

object LspServerInfoCatalog {
    val ALL: List<LspServerInfo> = listOf(
        LspServerInfo(
            packageId = "kotlin-language-server",
            author = "fwcd",
            homepageUrl = "https://github.com/fwcd/kotlin-language-server",
            repoSlug = "fwcd/kotlin-language-server",
            license = "MIT",
            update = LspUpdateSource.GitHubRelease("fwcd/kotlin-language-server"),
            longDescriptionRes = R.string.ext_kotlin_ls_long,
            capabilitiesRes = R.string.ext_kotlin_ls_caps,
            limitationsRes = R.string.ext_kotlin_ls_limits,
        ),
        LspServerInfo(
            packageId = "jdtls",
            author = "Eclipse Foundation",
            homepageUrl = "https://projects.eclipse.org/projects/eclipse.jdt.ls",
            repoSlug = "eclipse-jdtls/eclipse.jdt.ls",
            license = "EPL-2.0",
            update = LspUpdateSource.GitHubTag("eclipse-jdtls/eclipse.jdt.ls"),
            longDescriptionRes = R.string.ext_jdtls_long,
            capabilitiesRes = R.string.ext_jdtls_caps,
            limitationsRes = R.string.ext_jdtls_limits,
        ),
        LspServerInfo(
            packageId = "clangd",
            author = "LLVM Project",
            homepageUrl = "https://clangd.llvm.org",
            repoSlug = "clangd/clangd",
            license = "Apache-2.0 mit LLVM-Ausnahmen",
            update = LspUpdateSource.TermuxApt("clang"),
            longDescriptionRes = R.string.ext_clangd_long,
            capabilitiesRes = R.string.ext_clangd_caps,
            limitationsRes = R.string.ext_clangd_limits,
        ),
        LspServerInfo(
            packageId = "pyright",
            author = "Microsoft",
            homepageUrl = "https://github.com/microsoft/pyright",
            repoSlug = "microsoft/pyright",
            license = "MIT",
            update = LspUpdateSource.Npm("pyright"),
            longDescriptionRes = R.string.ext_pyright_long,
            capabilitiesRes = R.string.ext_pyright_caps,
            limitationsRes = R.string.ext_pyright_limits,
        ),
        LspServerInfo(
            packageId = "rust-analyzer",
            author = "rust-lang",
            homepageUrl = "https://rust-analyzer.github.io",
            repoSlug = "rust-lang/rust-analyzer",
            license = "MIT / Apache-2.0",
            update = LspUpdateSource.TermuxApt("rust-analyzer"),
            longDescriptionRes = R.string.ext_rust_analyzer_long,
            capabilitiesRes = R.string.ext_rust_analyzer_caps,
            limitationsRes = R.string.ext_rust_analyzer_limits,
        ),
        LspServerInfo(
            packageId = "vscode-langservers-extracted",
            author = "hrsh7th",
            homepageUrl = "https://github.com/hrsh7th/vscode-langservers-extracted",
            repoSlug = "hrsh7th/vscode-langservers-extracted",
            license = "MIT",
            update = LspUpdateSource.Npm("vscode-langservers-extracted"),
            longDescriptionRes = R.string.ext_html_css_json_long,
            capabilitiesRes = R.string.ext_html_css_json_caps,
            limitationsRes = R.string.ext_html_css_json_limits,
        ),
        LspServerInfo(
            packageId = "lemminx",
            author = "Eclipse LemMinX",
            homepageUrl = "https://github.com/eclipse-lemminx/lemminx",
            repoSlug = "eclipse-lemminx/lemminx",
            license = "EPL-2.0",
            update = LspUpdateSource.GitHubTag("eclipse-lemminx/lemminx"),
            longDescriptionRes = R.string.ext_lemminx_long,
            capabilitiesRes = R.string.ext_lemminx_caps,
            limitationsRes = R.string.ext_lemminx_limits,
        ),
        LspServerInfo(
            packageId = "yaml-language-server",
            author = "Red Hat",
            homepageUrl = "https://github.com/redhat-developer/yaml-language-server",
            repoSlug = "redhat-developer/yaml-language-server",
            license = "MIT",
            update = LspUpdateSource.Npm("yaml-language-server"),
            longDescriptionRes = R.string.ext_yaml_ls_long,
            capabilitiesRes = R.string.ext_yaml_ls_caps,
            limitationsRes = R.string.ext_yaml_ls_limits,
        ),
        LspServerInfo(
            packageId = "taplo",
            author = "tamasfe",
            homepageUrl = "https://taplo.tamasfe.dev",
            repoSlug = "tamasfe/taplo",
            license = "MIT",
            update = LspUpdateSource.TermuxApt("taplo"),
            longDescriptionRes = R.string.ext_taplo_long,
            capabilitiesRes = R.string.ext_taplo_caps,
            limitationsRes = R.string.ext_taplo_limits,
        ),
        LspServerInfo(
            packageId = "bash-language-server",
            author = "bash-lsp",
            homepageUrl = "https://github.com/bash-lsp/bash-language-server",
            repoSlug = "bash-lsp/bash-language-server",
            license = "MIT",
            update = LspUpdateSource.Npm("bash-language-server"),
            longDescriptionRes = R.string.ext_bash_ls_long,
            capabilitiesRes = R.string.ext_bash_ls_caps,
            limitationsRes = R.string.ext_bash_ls_limits,
        ),
    )

    fun of(packageId: String): LspServerInfo? = ALL.firstOrNull { it.packageId == packageId }
}
