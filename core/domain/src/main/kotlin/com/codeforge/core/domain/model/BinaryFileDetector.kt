// Modul: :core:domain
package com.codeforge.core.domain.model

import java.io.File

object BinaryFileDetector {

    private val BINARY_EXTENSIONS = setOf(
        "png", "jpg", "jpeg", "gif", "webp", "ico", "bmp",
        "jar", "apk", "aar", "so", "a", "dylib", "zip", "tar", "gz", "tgz", "bz2", "7z",
        "pdf", "mp3", "mp4", "wav", "ogg", "mov", "avi", "webm",
        "class", "dex", "exe", "bin", "keystore", "jks"
    )

    fun isBinary(file: File): Boolean {
        if (!file.exists() || !file.isFile) return false
        val ext = file.extension.lowercase()
        if (ext in BINARY_EXTENSIONS) return true
        return runCatching {
            file.inputStream().use { stream ->
                val buffer = ByteArray(8192)
                val read = stream.read(buffer)
                if (read <= 0) false
                else (0 until read).any { buffer[it] == 0.toByte() }
            }
        }.getOrDefault(false)
    }

    fun isBinaryExtension(path: String): Boolean {
        val ext = path.substringAfterLast('.', "").lowercase()
        return ext in BINARY_EXTENSIONS
    }
}
