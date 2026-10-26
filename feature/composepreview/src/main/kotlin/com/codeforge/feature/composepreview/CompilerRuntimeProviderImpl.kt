/**
 * Modul: :feature:composepreview
 * @author Thomas Schmid
 */
package com.codeforge.feature.composepreview

import android.content.Context
import com.codeforge.core.domain.repository.CompilerRuntimeProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal const val COMPILER_RUNTIME_MANIFEST_NAME = "manifest.json"
internal const val COMPILER_RUNTIME_DIR_NAME = "compiler-runtime"

internal data class CompilerRuntimeFileEntry(val name: String, val sha256: String, val size: Long)

internal data class CompilerRuntimeManifest(val bom: String, val kotlin: String, val files: List<CompilerRuntimeFileEntry>) {
    companion object {
        // lazy: ein Regex-Syntaxfehler (ICU/Android) soll nicht die ganze Klasse unbenutzbar machen
        //  (ExceptionInInitializerError -> später nur noch der Klassenname als Meldung).
        private val BOM_REGEX by lazy { Regex(""""bom"\s*:\s*"([^"]+)"""") }
        private val KOTLIN_REGEX by lazy { Regex(""""kotlin"\s*:\s*"([^"]+)"""") }
        private val FILES_BLOCK_REGEX by lazy { Regex(""""files"\s*:\s*\[([\s\S]*?)\]""") }
        private val OBJECT_REGEX by lazy { Regex("""\{([^{}]*)\}""") }
        private val NAME_REGEX by lazy { Regex(""""name"\s*:\s*"([^"]+)"""") }
        private val SHA_REGEX by lazy { Regex(""""sha256"\s*:\s*"([0-9a-fA-F]{64})"""") }
        private val SIZE_REGEX by lazy { Regex(""""size"\s*:\s*(\d+)""") }

        fun parse(json: String): CompilerRuntimeManifest {
            val bom = BOM_REGEX.find(json)?.groupValues?.get(1)?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: throw IllegalArgumentException("Invalid manifest.json: missing 'bom'")
            val kotlin = KOTLIN_REGEX.find(json)?.groupValues?.get(1)?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: throw IllegalArgumentException("Invalid manifest.json: missing 'kotlin'")
            val filesContent = FILES_BLOCK_REGEX.find(json)?.groupValues?.get(1)
                ?: throw IllegalArgumentException("Invalid manifest.json: missing 'files' array")

            val entries = OBJECT_REGEX.findAll(filesContent).map { match ->
                val obj = match.groupValues[1]
                val name = NAME_REGEX.find(obj)?.groupValues?.get(1)
                    ?: throw IllegalArgumentException("Invalid manifest file entry: missing 'name'")
                requireSafeFileName(name)
                val sha = SHA_REGEX.find(obj)?.groupValues?.get(1)?.lowercase()
                    ?: throw IllegalArgumentException("Invalid manifest file entry '$name': invalid 'sha256'")
                val size = SIZE_REGEX.find(obj)?.groupValues?.get(1)?.toLongOrNull()
                    ?: throw IllegalArgumentException("Invalid manifest file entry '$name': invalid 'size'")
                if (size <= 0L) {
                    throw IllegalArgumentException("Invalid manifest file entry '$name': non-positive size $size")
                }
                CompilerRuntimeFileEntry(name = name, sha256 = sha, size = size)
            }.toList()

            if (entries.isEmpty()) {
                throw IllegalArgumentException("Invalid manifest.json: 'files' array is empty")
            }
            return CompilerRuntimeManifest(bom = bom, kotlin = kotlin, files = entries)
        }

        fun requireSafeFileName(name: String) {
            if (name.isBlank() || name.contains('/') || name.contains('\\') || name.contains("..")) {
                throw SecurityException("Zip-Slip attempt detected: unsafe file name '$name'")
            }
        }
    }
}

internal fun interface CompilerRuntimeDownloader {
    suspend fun download(url: String, destination: File, onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit): Result<File>
}

@Singleton
internal class HttpCompilerRuntimeDownloader @Inject constructor() : CompilerRuntimeDownloader {

    override suspend fun download(
        url: String,
        destination: File,
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            requireAllowedInitialUrl(url)
            destination.parentFile?.mkdirs()
            val tempFile = File(destination.parentFile, "${destination.name}.downloading")
            if (tempFile.exists()) {
                tempFile.delete()
            }

            var currentUrl = url
            var connection: HttpURLConnection? = null
            var redirects = 0
            val maxRedirects = 5

            try {
                while (true) {
                    val conn = URI(currentUrl).toURL().openConnection() as HttpURLConnection
                    conn.connectTimeout = 15_000
                    conn.readTimeout = 30_000
                    conn.instanceFollowRedirects = false
                    conn.setRequestProperty("User-Agent", "CodeForgeMobile/3.0.0")
                    conn.connect()

                    val responseCode = conn.responseCode
                    if (responseCode in listOf(
                            HttpURLConnection.HTTP_MOVED_PERM,
                            HttpURLConnection.HTTP_MOVED_TEMP,
                            HttpURLConnection.HTTP_SEE_OTHER,
                            307,
                            308
                        )
                    ) {
                        if (++redirects > maxRedirects) {
                            conn.disconnect()
                            throw IOException("Too many redirects ($redirects) for $url")
                        }
                        val location = conn.getHeaderField("Location")
                            ?: throw IOException("HTTP redirect $responseCode without Location header")
                        val resolved = URI(currentUrl).resolve(location).toString()
                        requireAllowedRedirectUrl(resolved)
                        currentUrl = resolved
                        conn.disconnect()
                        continue
                    }

                    if (responseCode !in 200..299) {
                        conn.disconnect()
                        throw IllegalStateException("HTTP error $responseCode while downloading $url")
                    }

                    connection = conn
                    break
                }

                val activeConnection = connection ?: throw IllegalStateException("Connection could not be established")
                val totalBytes = activeConnection.contentLengthLong

                activeConnection.inputStream.use { input ->
                    tempFile.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var totalRead = 0L
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalRead += bytesRead
                            onProgress(totalRead, totalBytes)
                        }
                    }
                }

                if (totalBytes > 0 && tempFile.length() != totalBytes) {
                    throw IllegalStateException("Downloaded ${tempFile.length()} bytes, expected $totalBytes")
                }

                if (destination.exists()) {
                    destination.delete()
                }
                if (!tempFile.renameTo(destination)) {
                    tempFile.copyTo(destination, overwrite = true)
                    tempFile.delete()
                }
                destination
            } catch (e: Exception) {
                if (tempFile.exists()) {
                    tempFile.delete()
                }
                throw e
            } finally {
                connection?.disconnect()
            }
        }
    }

    companion object {
        fun requireAllowedInitialUrl(url: String) {
            if (!url.startsWith("https://github.com/")) {
                throw SecurityException("Only https://github.com/ URLs are allowed for compiler runtime download: $url")
            }
        }

        fun requireAllowedRedirectUrl(url: String) {
            val allowed = url.startsWith("https://github.com/") ||
                url.startsWith("https://objects.githubusercontent.com/") ||
                url.startsWith("https://release-assets.githubusercontent.com/")
            if (!allowed) {
                throw SecurityException("Disallowed redirect URL for compiler runtime download: $url")
            }
        }
    }
}

@Singleton
class CompilerRuntimeProviderImpl internal constructor(
    private val filesDir: File,
    private val downloader: CompilerRuntimeDownloader,
    private val expectedBomVersion: String = BuildConfig.COMPOSE_BOM_VERSION,
    private val expectedKotlinVersion: String = BuildConfig.KOTLIN_VERSION,
    private val baseUrl: String = BuildConfig.COMPILER_RUNTIME_BASE_URL,
    private val zipName: String = BuildConfig.COMPILER_RUNTIME_ZIP_NAME
) : CompilerRuntimeProvider {

    @Inject
    internal constructor(
        @ApplicationContext context: Context,
        downloader: HttpCompilerRuntimeDownloader
    ) : this(
        filesDir = context.filesDir,
        downloader = downloader
    )

    private val mutex = Mutex()

    override suspend fun ensureInstalled(onProgress: (Int) -> Unit): Result<File> = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching {
                val runtimeDir = File(filesDir, COMPILER_RUNTIME_DIR_NAME)
                if (isDirectoryValid(runtimeDir)) {
                    onProgress(100)
                    return@runCatching runtimeDir
                }

                HttpCompilerRuntimeDownloader.requireAllowedInitialUrl(baseUrl)
                val normalizedBase = baseUrl.trimEnd('/')
                val zipUrl = "$normalizedBase/$zipName"
                val shaUrl = "$normalizedBase/$zipName.sha256"

                val tempDir = File(filesDir, "$COMPILER_RUNTIME_DIR_NAME.tmp-${System.nanoTime()}")
                if (tempDir.exists()) {
                    tempDir.deleteRecursively()
                }
                tempDir.mkdirs()

                try {
                    onProgress(0)

                    val shaFile = File(tempDir, "$zipName.sha256")
                    downloader.download(shaUrl, shaFile) { _, _ -> }.getOrThrow()
                    val expectedZipSha = parseSha256File(shaFile.readText(Charsets.UTF_8))

                    val zipFile = File(tempDir, zipName)
                    downloader.download(zipUrl, zipFile) { downloaded, total ->
                        val pct = if (total > 0L) {
                            (5 + (downloaded * 75 / total)).toInt().coerceIn(5, 80)
                        } else {
                            40
                        }
                        onProgress(pct)
                    }.getOrThrow()

                    onProgress(82)
                    val actualZipSha = sha256Hex(zipFile)
                    if (!actualZipSha.equals(expectedZipSha, ignoreCase = true)) {
                        throw IllegalStateException(
                            "SHA-256 mismatch for $zipName (expected $expectedZipSha, got $actualZipSha)"
                        )
                    }

                    onProgress(85)
                    val extractDir = File(tempDir, "extracted").apply { mkdirs() }
                    extractZipSafely(zipFile, extractDir)
                    zipFile.delete()

                    onProgress(92)
                    verifyDirectoryOrThrow(extractDir)

                    onProgress(98)
                    atomicReplaceDirectory(extractDir, runtimeDir)
                    onProgress(100)
                    runtimeDir
                } finally {
                    if (tempDir.exists()) {
                        tempDir.deleteRecursively()
                    }
                }
            }
        }
    }

    internal fun isDirectoryValid(dir: File): Boolean = runCatching { verifyDirectoryOrThrow(dir) }.isSuccess

    internal fun verifyDirectoryOrThrow(dir: File) {
        if (!dir.isDirectory) {
            throw IllegalStateException("Compiler runtime directory does not exist: ${dir.absolutePath}")
        }
        val manifestFile = File(dir, COMPILER_RUNTIME_MANIFEST_NAME)
        if (!manifestFile.isFile) {
            throw IllegalStateException("Compiler runtime manifest.json is missing")
        }
        val manifest = CompilerRuntimeManifest.parse(manifestFile.readText(Charsets.UTF_8))
        if (manifest.bom != expectedBomVersion || manifest.kotlin != expectedKotlinVersion) {
            throw IllegalStateException(
                "Compiler runtime version mismatch: manifest has bom=${manifest.bom}, kotlin=${manifest.kotlin}, " +
                    "expected bom=$expectedBomVersion, kotlin=$expectedKotlinVersion"
            )
        }
        for (entry in manifest.files) {
            CompilerRuntimeManifest.requireSafeFileName(entry.name)
            val file = File(dir, entry.name)
            if (!file.isFile) {
                throw IllegalStateException("Missing compiler runtime JAR: ${entry.name}")
            }
            if (file.length() != entry.size) {
                throw IllegalStateException(
                    "Size mismatch for ${entry.name}: expected ${entry.size}, got ${file.length()}"
                )
            }
            val actualSha = sha256Hex(file)
            if (!actualSha.equals(entry.sha256, ignoreCase = true)) {
                throw IllegalStateException(
                    "SHA-256 mismatch for ${entry.name}: expected ${entry.sha256}, got $actualSha"
                )
            }
        }
    }

    private fun extractZipSafely(zipFile: File, targetDir: File) {
        val canonicalTarget = targetDir.canonicalPath
        ZipFile(zipFile).use { zf ->
            val entries = zf.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val name = entry.name
                if (entry.isDirectory) {
                    throw SecurityException("Subdirectories are not allowed in compiler runtime archive: '$name'")
                }
                CompilerRuntimeManifest.requireSafeFileName(name)
                val destFile = File(targetDir, name)
                val canonicalDest = destFile.canonicalPath
                if (!canonicalDest.startsWith(canonicalTarget + File.separator)) {
                    throw SecurityException("Zip-Slip path traversal detected: '$name'")
                }
                zf.getInputStream(entry).use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }

    private fun atomicReplaceDirectory(newDir: File, targetDir: File) {
        val backupDir = File(filesDir, "$COMPILER_RUNTIME_DIR_NAME.bak-${System.nanoTime()}")
        val hadExisting = targetDir.exists()
        if (hadExisting) {
            if (!targetDir.renameTo(backupDir)) {
                targetDir.copyRecursively(backupDir, overwrite = true)
                targetDir.deleteRecursively()
            }
        }
        try {
            if (!newDir.renameTo(targetDir)) {
                newDir.copyRecursively(targetDir, overwrite = true)
                newDir.deleteRecursively()
            }
            if (backupDir.exists()) {
                backupDir.deleteRecursively()
            }
        } catch (e: Exception) {
            if (targetDir.exists()) {
                targetDir.deleteRecursively()
            }
            if (hadExisting && backupDir.exists()) {
                if (!backupDir.renameTo(targetDir)) {
                    backupDir.copyRecursively(targetDir, overwrite = true)
                    backupDir.deleteRecursively()
                }
            }
            throw e
        }
    }

    companion object {
        private val HEX_64_REGEX = Regex("^[0-9a-fA-F]{64}$")

        internal fun parseSha256File(content: String): String {
            val token = content.trim().split(Regex("\\s+")).firstOrNull().orEmpty()
            if (!HEX_64_REGEX.matches(token)) {
                throw IllegalArgumentException("Invalid SHA-256 checksum content: '$content'")
            }
            return token.lowercase()
        }

        internal fun sha256Hex(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { input ->
                val buf = ByteArray(8192)
                var n: Int
                while (input.read(buf).also { n = it } != -1) {
                    digest.update(buf, 0, n)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
