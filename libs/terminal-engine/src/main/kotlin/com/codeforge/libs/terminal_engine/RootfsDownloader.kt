// Modul: :libs:terminal-engine
package com.codeforge.libs.terminal_engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class RootfsDownloader {
    fun download(urlStr: String, destinationFile: File): Flow<Int> = flow {
        var url = URL(urlStr)
        var connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 15000
        connection.readTimeout = 30000
        connection.instanceFollowRedirects = true

        var status = connection.responseCode
        if (status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM || status == 307 || status == 308) {
            val newUrl = connection.getHeaderField("Location")
            connection.disconnect()
            url = URL(newUrl)
            connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            status = connection.responseCode
        }

        if (status != HttpURLConnection.HTTP_OK) {
            throw java.io.IOException("HTTP download failed with response code $status")
        }

        val totalBytes = connection.contentLengthLong
        destinationFile.parentFile?.mkdirs()

        connection.inputStream.use { input ->
            FileOutputStream(destinationFile).use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalBytesRead = 0L
                var lastProgress = -1

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead
                    if (totalBytes > 0) {
                        val progress = ((totalBytesRead * 100) / totalBytes).toInt()
                        if (progress != lastProgress) {
                            lastProgress = progress
                            emit(progress)
                        }
                    }
                }
            }
        }
        connection.disconnect()
    }.flowOn(Dispatchers.IO)
}
