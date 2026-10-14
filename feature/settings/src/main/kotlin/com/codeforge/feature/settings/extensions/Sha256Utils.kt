// Modul: :feature:settings
package com.codeforge.feature.settings.extensions

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

object Sha256Utils {

    fun calculateSha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(bytes)
        return hash.joinToString("") { "%02x".format(it) }
    }

    fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun verifySha256(file: File, expectedHash: String): Boolean {
        if (!file.exists() || !file.isFile) return false
        val actual = calculateSha256(file)
        return actual.equals(expectedHash.trim(), ignoreCase = true)
    }

    fun verifySha256(bytes: ByteArray, expectedHash: String): Boolean {
        val actual = calculateSha256(bytes)
        return actual.equals(expectedHash.trim(), ignoreCase = true)
    }
}
