// Modul: :feature:settings
package com.codeforge.feature.settings.extensions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LspExtensionCatalogTest {

    private val hexRegex = Regex("^[a-f0-9]{64}$")

    @Test
    fun `catalog has unique non-blank IDs`() {
        val catalog = LspExtensionCatalog.ALL
        assertTrue("Catalog should contain at least 10 packages", catalog.size >= 10)

        val ids = catalog.map { it.id }
        assertEquals("Catalog IDs must be unique", ids.size, ids.distinct().size)

        for (pkg in catalog) {
            assertTrue("Package ID must not be blank", pkg.id.isNotBlank())
            assertTrue("Package nameRes must be valid", pkg.nameRes != 0)
            assertTrue("Package descriptionRes must be valid", pkg.descriptionRes != 0)
            assertTrue("Package version must not be blank", pkg.version.isNotBlank())
            assertTrue("Package language must not be blank", pkg.language.isNotBlank())
            assertFalse("Package launchCommand must not be empty", pkg.launchCommand.isEmpty())
            assertTrue("Package executableRelativePath must not be blank", pkg.executableRelativePath.isNotBlank())
        }
    }

    @Test
    fun `download packages have valid https URLs, 64-char hex SHA256, and matching archive types`() {
        val downloadPackages = LspExtensionCatalog.ALL.filter { it.isDownloadable }
        assertTrue("Must have at least 7 downloadable packages", downloadPackages.size >= 7)

        for (pkg in downloadPackages) {
            val source = pkg.source as ExtensionSource.Download
            assertTrue("URL must start with https:// for ${pkg.id}", source.url.startsWith("https://"))
            assertTrue(
                "SHA-256 must be exactly 64 hex characters for ${pkg.id} (got: ${source.sha256})",
                hexRegex.matches(source.sha256)
            )
            assertFalse(
                "SHA-256 must not be a zero placeholder for ${pkg.id}",
                source.sha256 == "0".repeat(64)
            )

            when (source.archiveType) {
                ExtensionArchiveType.ZIP -> {
                    assertTrue("Archive type ZIP must end with .zip for ${pkg.id}", source.url.endsWith(".zip"))
                }
                ExtensionArchiveType.TAR_GZ -> {
                    assertTrue(
                        "Archive type TAR_GZ must end with .tar.gz or .tgz for ${pkg.id}",
                        source.url.endsWith(".tar.gz") || source.url.endsWith(".tgz")
                    )
                }
                ExtensionArchiveType.GZ -> {
                    assertTrue("Archive type GZ must end with .gz for ${pkg.id}", source.url.endsWith(".gz"))
                }
                ExtensionArchiveType.JAR -> {
                    assertTrue("Archive type JAR must end with .jar for ${pkg.id}", source.url.endsWith(".jar"))
                }
                ExtensionArchiveType.BINARY -> {
                    // Raw binary
                }
            }
        }
    }

    @Test
    fun `termux packages have valid package and binary names and launch commands`() {
        val termuxPackages = LspExtensionCatalog.ALL.filter { it.isTermuxPackage }
        assertEquals("Must have 3 Termux packages (clangd, rust-analyzer, taplo)", 3, termuxPackages.size)

        for (pkg in termuxPackages) {
            val source = pkg.source as ExtensionSource.TermuxPackage
            assertTrue("PackageName must not be blank for ${pkg.id}", source.packageName.isNotBlank())
            assertTrue("BinaryName must not be blank for ${pkg.id}", source.binaryName.isNotBlank())
            assertTrue(
                "LaunchCommand must contain binaryName for ${pkg.id}",
                pkg.launchCommand.contains(source.binaryName)
            )
        }
    }

    @Test
    fun `findById retrieves expected catalog entries`() {
        assertNotNull(LspExtensionCatalog.findById("kotlin-language-server"))
        assertNotNull(LspExtensionCatalog.findById("jdtls"))
        assertNotNull(LspExtensionCatalog.findById("clangd"))
        assertNotNull(LspExtensionCatalog.findById("pyright"))
        assertNotNull(LspExtensionCatalog.findById("rust-analyzer"))
        assertNotNull(LspExtensionCatalog.findById("vscode-langservers-extracted"))
        assertNotNull(LspExtensionCatalog.findById("lemminx"))
        assertNotNull(LspExtensionCatalog.findById("yaml-language-server"))
        assertNotNull(LspExtensionCatalog.findById("taplo"))
        assertNotNull(LspExtensionCatalog.findById("bash-language-server"))
    }
}
