// Modul: :libs:terminal-engine
package com.codeforge.libs.terminal_engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Lädt eine Rootfs-Archivdatei via HttpURLConnection herunter und emittiert den
 * Fortschritt in Prozent (nur bei Änderung, um die UI nicht mit jedem Byte zu fluten).
 */
class RootfsDownloader {

    fun download(url: String, targetFile: File): Flow<Int> = flow {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 15_000
        }

        try {
            connection.connect()

            if (connection.responseCode !in 200..299) {
                error("Download fehlgeschlagen (HTTP ${connection.responseCode}): $url")
            }

            val totalBytes = connection.contentLengthLong
            targetFile.parentFile?.mkdirs()

            connection.inputStream.use { input ->
                targetFile.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var totalRead = 0L
                    var lastEmittedPercent = -1

                    while (true) {
                        val readCount = input.read(buffer)
                        if (readCount == -1) break
                        output.write(buffer, 0, readCount)
                        totalRead += readCount

                        if (totalBytes > 0) {
                            val percent = ((totalRead * 100) / totalBytes).toInt().coerceIn(0, 100)
                            if (percent != lastEmittedPercent) {
                                lastEmittedPercent = percent
                                emit(percent)
                            }
                        }
                    }

                    if (lastEmittedPercent != 100) emit(100)
                }
            }
        } finally {
            connection.disconnect()
        }
    }.flowOn(Dispatchers.IO)
}
