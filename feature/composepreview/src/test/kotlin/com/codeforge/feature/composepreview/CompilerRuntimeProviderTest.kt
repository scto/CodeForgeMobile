/**
 * Modul: :feature:composepreview
 * @author Thomas Schmid
 */
package com.codeforge.feature.composepreview

import com.codeforge.core.domain.repository.CompilerRuntimeProvider
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CompilerRuntimeProviderTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `manifest parser parses valid manifest json`() {
        val sha1 = "a".repeat(64)
        val sha2 = "b".repeat(64)
        val raw = """
            {
              "bom": "2025.09.00",
              "kotlin": "2.1.21",
              "files": [
                { "name": "kotlin-stdlib-2.1.21.jar", "sha256": "$sha1", "size": 1024 },
                { "name": "kotlin-compose-compiler-plugin-embeddable.jar", "sha256": "$sha2", "size": 2048 }
              ]
            }
        """.trimIndent()

        val manifest = CompilerRuntimeManifest.parse(raw)
        assertEquals("2025.09.00", manifest.bom)
        assertEquals("2.1.21", manifest.kotlin)
        assertEquals(2, manifest.files.size)
        assertEquals("kotlin-stdlib-2.1.21.jar", manifest.files[0].name)
        assertEquals(sha1, manifest.files[0].sha256)
        assertEquals(1024L, manifest.files[0].size)
        assertEquals("kotlin-compose-compiler-plugin-embeddable.jar", manifest.files[1].name)
        assertEquals(sha2, manifest.files[1].sha256)
        assertEquals(2048L, manifest.files[1].size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `manifest parser rejects missing bom`() {
        val raw = """
            {
              "kotlin": "2.1.21",
              "files": [
                { "name": "a.jar", "sha256": "abc", "size": 10 }
              ]
            }
        """.trimIndent()
        CompilerRuntimeManifest.parse(raw)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `manifest parser rejects empty files array`() {
        val raw = """
            {
              "bom": "2025.09.00",
              "kotlin": "2.1.21",
              "files": []
            }
        """.trimIndent()
        CompilerRuntimeManifest.parse(raw)
    }

    @Test
    fun `sha256 mismatch fails and keeps existing installation intact`() = runBlocking {
        val filesDir = tempFolder.newFolder("files")

        // Vorhandene (veraltete) Installation anlegen, die bei fehlerhaftem Update intakt bleiben muss
        val existingRuntimeDir = File(filesDir, "compiler-runtime").apply { mkdirs() }
        val existingJar = File(existingRuntimeDir, "old-runtime.jar").apply { writeText("old-content") }
        val existingSha = CompilerRuntimeProviderImpl.sha256Hex(existingJar)
        File(existingRuntimeDir, "manifest.json").writeText(
            """
            {
              "bom": "2024.01.00",
              "kotlin": "1.9.22",
              "files": [
                { "name": "old-runtime.jar", "sha256": "$existingSha", "size": ${existingJar.length()} }
              ]
            }
            """.trimIndent()
        )

        val fakeZip = createValidRuntimeZip(
            tempFolder.newFile("bundle.zip"),
            bom = "2025.09.00",
            kotlin = "2.1.21"
        )
        val wrongSha = "0000000000000000000000000000000000000000000000000000000000000000"

        val downloader = FakeDownloader(
            zipSource = fakeZip,
            sha256Response = "$wrongSha  compiler-runtime-2025.09.00-k2.1.21.zip\n"
        )

        val provider: CompilerRuntimeProvider = CompilerRuntimeProviderImpl(
            filesDir = filesDir,
            downloader = downloader,
            expectedBomVersion = "2025.09.00",
            expectedKotlinVersion = "2.1.21",
            baseUrl = "https://github.com/scto/codeforge-tools/releases/download/compiler-runtime-2025.09.00-k2.1.21",
            zipName = "compiler-runtime-2025.09.00-k2.1.21.zip"
        )

        val result = provider.ensureInstalled()
        assertTrue("Expected failure on SHA-256 mismatch", result.isFailure)
        val message = result.exceptionOrNull()?.message.orEmpty()
        assertTrue("Error message should mention SHA-256 mismatch: $message", message.contains("SHA-256"))

        // Alte Installation muss unverändert vorhanden sein
        assertTrue(existingJar.isFile)
        assertEquals("old-content", existingJar.readText())
    }

    @Test
    fun `zip slip entry is rejected and existing installation remains intact`() = runBlocking {
        val filesDir = tempFolder.newFolder("files-zipslip")

        val existingRuntimeDir = File(filesDir, "compiler-runtime").apply { mkdirs() }
        val existingJar = File(existingRuntimeDir, "safe.jar").apply { writeText("safe-content") }
        val existingSha = CompilerRuntimeProviderImpl.sha256Hex(existingJar)
        File(existingRuntimeDir, "manifest.json").writeText(
            """
            {
              "bom": "2024.01.00",
              "kotlin": "1.9.22",
              "files": [
                { "name": "safe.jar", "sha256": "$existingSha", "size": ${existingJar.length()} }
              ]
            }
            """.trimIndent()
        )

        val evilZip = tempFolder.newFile("evil.zip")
        ZipOutputStream(evilZip.outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry("../evil.jar"))
            zos.write("pwned".toByteArray())
            zos.closeEntry()
        }
        val evilSha = CompilerRuntimeProviderImpl.sha256Hex(evilZip)

        val downloader = FakeDownloader(
            zipSource = evilZip,
            sha256Response = "$evilSha  compiler-runtime-2025.09.00-k2.1.21.zip\n"
        )

        val provider: CompilerRuntimeProvider = CompilerRuntimeProviderImpl(
            filesDir = filesDir,
            downloader = downloader,
            expectedBomVersion = "2025.09.00",
            expectedKotlinVersion = "2.1.21",
            baseUrl = "https://github.com/scto/codeforge-tools/releases/download/compiler-runtime-2025.09.00-k2.1.21",
            zipName = "compiler-runtime-2025.09.00-k2.1.21.zip"
        )

        val result = provider.ensureInstalled()
        assertTrue("Expected failure on Zip-Slip entry", result.isFailure)
        val message = result.exceptionOrNull()?.message.orEmpty()
        assertTrue("Error message should mention Zip-Slip: $message", message.contains("Zip-Slip"))
        assertFalse("Evil file must not exist outside target dir", File(filesDir, "evil.jar").exists())
        assertEquals("safe-content", existingJar.readText())
    }

    @Test
    fun `version mismatch in existing installation triggers re-download and replaces installation`() = runBlocking {
        val filesDir = tempFolder.newFolder("files-version")

        // Alte Installation mit veralteter BOM-Version
        val existingRuntimeDir = File(filesDir, "compiler-runtime").apply { mkdirs() }
        val oldJar = File(existingRuntimeDir, "old.jar").apply { writeText("old-jar-bytes") }
        val oldSha = CompilerRuntimeProviderImpl.sha256Hex(oldJar)
        File(existingRuntimeDir, "manifest.json").writeText(
            """
            {
              "bom": "2024.09.00",
              "kotlin": "2.0.20",
              "files": [
                { "name": "old.jar", "sha256": "$oldSha", "size": ${oldJar.length()} }
              ]
            }
            """.trimIndent()
        )

        val validZip = createValidRuntimeZip(
            tempFolder.newFile("new-bundle.zip"),
            bom = "2025.09.00",
            kotlin = "2.1.21"
        )
        val validZipSha = CompilerRuntimeProviderImpl.sha256Hex(validZip)

        var downloadCalls = 0
        val downloader = FakeDownloader(
            zipSource = validZip,
            sha256Response = "$validZipSha  compiler-runtime-2025.09.00-k2.1.21.zip\n",
            onDownload = { downloadCalls++ }
        )

        val provider: CompilerRuntimeProvider = CompilerRuntimeProviderImpl(
            filesDir = filesDir,
            downloader = downloader,
            expectedBomVersion = "2025.09.00",
            expectedKotlinVersion = "2.1.21",
            baseUrl = "https://github.com/scto/codeforge-tools/releases/download/compiler-runtime-2025.09.00-k2.1.21",
            zipName = "compiler-runtime-2025.09.00-k2.1.21.zip"
        )

        val progressValues = mutableListOf<Int>()
        val firstResult = provider.ensureInstalled { progressValues.add(it) }
        assertTrue("Expected successful update: ${firstResult.exceptionOrNull()}", firstResult.isSuccess)
        assertEquals(2, downloadCalls) // 1x sha256 + 1x zip
        assertTrue(progressValues.contains(100))

        val installedDir = firstResult.getOrThrow()
        assertFalse("Old JAR should be removed after atomic swap", File(installedDir, "old.jar").exists())
        assertTrue(
            "Plugin JAR should exist",
            File(installedDir, "kotlin-compose-compiler-plugin-embeddable.jar").isFile
        )

        // Zweiter Aufruf bei identischer Version darf keinen erneuten Download auslösen
        val secondResult = provider.ensureInstalled()
        assertTrue(secondResult.isSuccess)
        assertEquals("Second call should hit local valid installation without downloading", 2, downloadCalls)
    }

    @Test
    fun `resolveRuntimeJars excludes kotlin-compose-compiler-plugin-embeddable jar`() {
        val dir = tempFolder.newFolder("runtime-jars")
        File(dir, "kotlin-stdlib-2.1.21.jar").writeText("stdlib")
        File(dir, "androidx_compose_runtime-runtime-android-1.9.1.jar").writeText("runtime")
        File(dir, "kotlin-compose-compiler-plugin-embeddable.jar").writeText("plugin")
        File(dir, "manifest.json").writeText("{}")

        val userClasspath = ComposePreviewRendererImpl.resolveRuntimeJars(dir)
        val names = userClasspath.map { it.name }
        assertEquals(
            listOf(
                "androidx_compose_runtime-runtime-android-1.9.1.jar",
                "kotlin-stdlib-2.1.21.jar"
            ),
            names
        )
        assertFalse(names.contains("kotlin-compose-compiler-plugin-embeddable.jar"))
    }

    @Test
    fun `version comparator sorts versions properly`() {
        val versions = listOf("33.0.1", "34.0.0", "30.0.3", "35.0.0")
        val sorted = versions.sortedWith(ComposePreviewRendererImpl.VersionComparator)
        assertEquals(listOf("30.0.3", "33.0.1", "34.0.0", "35.0.0"), sorted)
    }

    @Test
    fun `gradle generated manifest json matches generated jars in build compiler-runtime`() {
        val buildDirProp = System.getProperty("compilerRuntimeBuildDir")
        val runtimeBuildDir = if (!buildDirProp.isNullOrBlank()) {
            File(buildDirProp)
        } else {
            File("build/compiler-runtime")
        }

        assertTrue(
            "Expected build/compiler-runtime directory to exist at ${runtimeBuildDir.absolutePath}",
            runtimeBuildDir.isDirectory
        )

        val manifestFile = File(runtimeBuildDir, "manifest.json")
        assertTrue("Expected manifest.json in ${runtimeBuildDir.absolutePath}", manifestFile.isFile)

        val manifest = CompilerRuntimeManifest.parse(manifestFile.readText(Charsets.UTF_8))
        assertEquals(BuildConfig.COMPOSE_BOM_VERSION, manifest.bom)
        assertEquals(BuildConfig.KOTLIN_VERSION, manifest.kotlin)

        val jarFilesOnDisk = runtimeBuildDir
            .listFiles { f -> f.isFile && f.extension == "jar" }
            ?.sortedBy { it.name }
            .orEmpty()

        assertEquals(
            "Number of JARs in manifest.json must match JAR files on disk",
            jarFilesOnDisk.size,
            manifest.files.size
        )

        val pluginEntry = manifest.files.find { it.name == "kotlin-compose-compiler-plugin-embeddable.jar" }
        assertNotNull("manifest.json must contain kotlin-compose-compiler-plugin-embeddable.jar", pluginEntry)

        for (entry in manifest.files) {
            val jarFile = File(runtimeBuildDir, entry.name)
            assertTrue("Missing JAR listed in manifest: ${entry.name}", jarFile.isFile)
            assertEquals("Size mismatch for ${entry.name}", entry.size, jarFile.length())
            assertEquals(
                "SHA-256 mismatch for ${entry.name}",
                entry.sha256.lowercase(),
                CompilerRuntimeProviderImpl.sha256Hex(jarFile).lowercase()
            )
        }

        // Auch das erzeugte Release-ZIP und dessen .sha256-Datei prüfen
        val zipFile = File(runtimeBuildDir, BuildConfig.COMPILER_RUNTIME_ZIP_NAME)
        val shaFile = File(runtimeBuildDir, "${BuildConfig.COMPILER_RUNTIME_ZIP_NAME}.sha256")
        assertTrue("Expected release ZIP ${zipFile.name}", zipFile.isFile)
        assertTrue("Expected .sha256 file ${shaFile.name}", shaFile.isFile)
        val recordedZipSha = shaFile.readText(Charsets.UTF_8).trim().split(Regex("\\s+")).first()
        assertEquals(
            "Release ZIP SHA-256 must match .sha256 file",
            recordedZipSha.lowercase(),
            CompilerRuntimeProviderImpl.sha256Hex(zipFile).lowercase()
        )
    }

    private fun createValidRuntimeZip(targetZip: File, bom: String, kotlin: String): File {
        val dummyStdlibBytes = "dummy-stdlib-jar-content".toByteArray(Charsets.UTF_8)
        val dummyPluginBytes = "dummy-plugin-jar-content".toByteArray(Charsets.UTF_8)
        val stdlibSha = sha256Bytes(dummyStdlibBytes)
        val pluginSha = sha256Bytes(dummyPluginBytes)

        val manifestJson = """
            {
              "bom": "$bom",
              "kotlin": "$kotlin",
              "files": [
                { "name": "kotlin-stdlib-$kotlin.jar", "sha256": "$stdlibSha", "size": ${dummyStdlibBytes.size} },
                { "name": "kotlin-compose-compiler-plugin-embeddable.jar", "sha256": "$pluginSha", "size": ${dummyPluginBytes.size} }
              ]
            }
        """.trimIndent().toByteArray(Charsets.UTF_8)

        ZipOutputStream(targetZip.outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry("kotlin-stdlib-$kotlin.jar"))
            zos.write(dummyStdlibBytes)
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("kotlin-compose-compiler-plugin-embeddable.jar"))
            zos.write(dummyPluginBytes)
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("manifest.json"))
            zos.write(manifestJson)
            zos.closeEntry()
        }
        return targetZip
    }

    private fun sha256Bytes(bytes: ByteArray): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        digest.update(bytes)
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private class FakeDownloader(private val zipSource: File, private val sha256Response: String, private val onDownload: () -> Unit = {}) :
        CompilerRuntimeDownloader {
        override suspend fun download(
            url: String,
            destination: File,
            onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit
        ): Result<File> = runCatching {
            onDownload()
            if (url.endsWith(".sha256")) {
                destination.writeText(sha256Response, Charsets.UTF_8)
            } else {
                onProgress(100L, 1000L)
                zipSource.copyTo(destination, overwrite = true)
                onProgress(1000L, 1000L)
            }
            destination
        }
    }
}
