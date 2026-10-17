// Modul: :feature:settings
package com.codeforge.feature.settings.extensions

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

fun interface FileDownloader {
    suspend fun download(
        url: String,
        destination: File,
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit
    ): Result<File>
}

@Singleton
class HttpFileDownloader @Inject constructor() : FileDownloader {

    override suspend fun download(
        url: String,
        destination: File,
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
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
                        currentUrl = URI(currentUrl).resolve(location).toString()
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
}
