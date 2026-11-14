// Modul: :core:common
package com.codeforge.core.common.io

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/** SHA-256-Summen und Prüfung. */
object Sha256Utils {

    /** SHA-256 (hex) von Bytes. */
    fun calculateSha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(bytes)
        return hash.joinToString("") { "%02x".format(it) }
    }

    /** SHA-256 (hex) einer Datei. */
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

    /** Prüft, ob die Datei die erwartete Summe hat. */
    fun verifySha256(file: File, expectedHash: String): Boolean {
        if (!file.exists() || !file.isFile) return false
        val actual = calculateSha256(file)
        return actual.equals(expectedHash.trim(), ignoreCase = true)
    }

    /** Prüft, ob die Bytes die erwartete Summe haben. */
    fun verifySha256(bytes: ByteArray, expectedHash: String): Boolean {
        val actual = calculateSha256(bytes)
        return actual.equals(expectedHash.trim(), ignoreCase = true)
    }
}
