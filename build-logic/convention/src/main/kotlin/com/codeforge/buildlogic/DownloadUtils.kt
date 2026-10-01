package com.codeforge.buildlogic

import org.gradle.api.logging.Logger
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

object DownloadUtils {

    fun doDownload(
        file: File,
        remoteUrl: String,
        expectedChecksum: String,
        logger: Logger
    ) {
        if (file.exists()) {
            if (calculateSha256(file).equals(expectedChecksum, ignoreCase = true)) {
                logger.info("Bootstrap file ${file.name} already exists and checksum matches.")
                return
            } else {
                logger.warn("Bootstrap file ${file.name} checksum mismatch. Re-downloading...")
                file.delete()
            }
        }

        logger.lifecycle("Downloading terminal bootstrap package: $remoteUrl")
        file.parentFile?.mkdirs()

        val connection = (URL(remoteUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 60_000
            instanceFollowRedirects = true
        }

        try {
            connection.inputStream.use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            file.delete()
            throw IllegalStateException("Failed to download $remoteUrl: ${e.message}", e)
        }

        val actualChecksum = calculateSha256(file)
        if (!actualChecksum.equals(expectedChecksum, ignoreCase = true)) {
            file.delete()
            throw IllegalStateException("Downloaded bootstrap package $remoteUrl SHA-256 mismatch! Expected: $expectedChecksum, Actual: $actualChecksum")
        }

        logger.lifecycle("Successfully downloaded ${file.name} (SHA-256 verified)")
    }

    private fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
