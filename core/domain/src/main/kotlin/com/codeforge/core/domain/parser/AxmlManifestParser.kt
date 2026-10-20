// Modul: :core:domain
package com.codeforge.core.domain.parser

import com.codeforge.core.domain.analyzer.AndroidDangerousPermissions
import com.codeforge.core.domain.model.ApkComponentCounts
import com.codeforge.core.domain.model.ApkDetailedMetadata
import com.codeforge.core.domain.model.ApkPermission
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Reiner Kotlin-Parser für kompilierte Android-Binär-XML-Dateien (AXML / AndroidManifest.xml).
 *
 * Ermöglicht das Auslesen von Paketname, SDK-Stufen, Berechtigungen und Komponenten
 * völlig ohne `aapt` und ohne Android-SDK-Abhängigkeiten (z. B. auf JVM-Ebene).
 */
object AxmlManifestParser {

    private const val RES_XML_TYPE = 0x00080003
    private const val RES_STRING_POOL_TYPE = 0x00010001
    private const val RES_XML_START_ELEMENT_TYPE = 0x00100102
    private const val UTF8_FLAG = 0x00000100

    fun parse(bytes: ByteArray): ApkDetailedMetadata? {
        return try {
            parseStream(ByteArrayInputStream(bytes))
        } catch (_: Exception) {
            null
        }
    }

    private fun parseStream(inputStream: InputStream): ApkDetailedMetadata? {
        val buffer = ByteBuffer.wrap(inputStream.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        if (buffer.remaining() < 8) return null

        val magic = buffer.int
        if (magic != RES_XML_TYPE) return null
        val totalSize = buffer.int

        var stringPool: List<String> = emptyList()

        var packageName: String? = null
        var versionCode: Long? = null
        var versionName: String? = null
        var minSdk: Int? = null
        var targetSdk: Int? = null
        var isDebuggable: Boolean? = null
        var allowBackup: Boolean? = null

        val permissions = mutableListOf<ApkPermission>()
        val seenPerms = mutableSetOf<String>()

        var activitiesCount = 0
        var exportedActivitiesCount = 0
        var servicesCount = 0
        var exportedServicesCount = 0
        var receiversCount = 0
        var exportedReceiversCount = 0
        var providersCount = 0
        var exportedProvidersCount = 0

        while (buffer.hasRemaining() && buffer.position() < totalSize) {
            val chunkStart = buffer.position()
            val chunkType = buffer.short.toInt() and 0xFFFF or ((buffer.short.toInt() and 0xFFFF) shl 16)
            val headerSize = buffer.short.toInt() and 0xFFFF
            buffer.short // reserved
            val chunkSize = buffer.int

            if (chunkSize <= 0 || chunkStart + chunkSize > buffer.capacity()) break

            when (chunkType) {
                RES_STRING_POOL_TYPE -> {
                    stringPool = parseStringPool(buffer, chunkStart, chunkSize, headerSize)
                    buffer.position(chunkStart + chunkSize)
                }
                RES_XML_START_ELEMENT_TYPE -> {
                    buffer.position(chunkStart + 16) // skip line number and comment
                    val uriIdx = buffer.int
                    val nameIdx = buffer.int
                    buffer.short // attrStart
                    buffer.short // attrSize
                    val attrCount = buffer.short.toInt() and 0xFFFF
                    buffer.short // idIndex
                    buffer.short // classIndex
                    buffer.short // styleIndex

                    val tagName = stringPool.getOrNull(nameIdx) ?: ""

                    val attributes = mutableMapOf<String, String>()
                    for (i in 0 until attrCount) {
                        buffer.int // attrUriIdx
                        val attrNameIdx = buffer.int
                        val attrValIdx = buffer.int
                        val attrType = (buffer.short.toInt() and 0xFFFF) shr 8
                        buffer.get() // dataType
                        val attrData = buffer.int

                        val attrName = stringPool.getOrNull(attrNameIdx) ?: ""
                        val attrVal = when {
                            attrValIdx >= 0 && attrValIdx < stringPool.size -> stringPool[attrValIdx]
                            attrType == 0x03 -> stringPool.getOrNull(attrData) ?: attrData.toString()
                            attrType == 0x12 -> (attrData != 0).toString()
                            else -> attrData.toString()
                        }
                        attributes[attrName] = attrVal
                    }

                    when (tagName) {
                        "manifest" -> {
                            attributes["package"]?.let { packageName = it }
                            attributes["versionCode"]?.toLongOrNull()?.let { versionCode = it }
                            attributes["versionName"]?.let { versionName = it }
                        }
                        "uses-sdk" -> {
                            attributes["minSdkVersion"]?.toIntOrNull()?.let { minSdk = it }
                            attributes["targetSdkVersion"]?.toIntOrNull()?.let { targetSdk = it }
                        }
                        "uses-permission" -> {
                            attributes["name"]?.let { permName ->
                                if (seenPerms.add(permName)) {
                                    val maxSdk = attributes["maxSdkVersion"]?.toIntOrNull()
                                    permissions.add(
                                        ApkPermission(
                                            name = permName,
                                            isDangerous = AndroidDangerousPermissions.isDangerous(permName),
                                            maxSdkVersion = maxSdk
                                        )
                                    )
                                }
                            }
                        }
                        "application" -> {
                            attributes["debuggable"]?.let { isDebuggable = it == "true" || it == "1" }
                            attributes["allowBackup"]?.let { allowBackup = it == "true" || it == "1" }
                        }
                        "activity", "activity-alias" -> {
                            activitiesCount++
                            val isExp = attributes["exported"] == "true" || attributes["exported"] == "1"
                            if (isExp) exportedActivitiesCount++
                        }
                        "service" -> {
                            servicesCount++
                            val isExp = attributes["exported"] == "true" || attributes["exported"] == "1"
                            if (isExp) exportedServicesCount++
                        }
                        "receiver" -> {
                            receiversCount++
                            val isExp = attributes["exported"] == "true" || attributes["exported"] == "1"
                            if (isExp) exportedReceiversCount++
                        }
                        "provider" -> {
                            providersCount++
                            val isExp = attributes["exported"] == "true" || attributes["exported"] == "1"
                            if (isExp) exportedProvidersCount++
                        }
                    }

                    buffer.position(chunkStart + chunkSize)
                }
                else -> {
                    buffer.position(chunkStart + chunkSize)
                }
            }
        }

        val warnings = mutableListOf<String>()
        if (isDebuggable == true) {
            warnings.add("Warnung: APK ist im Debug-Modus kompiliert (FLAG_DEBUGGABLE).")
        }
        if (allowBackup == true) {
            warnings.add("Warnung: Anwendungs-Backup ist erlaubt (android:allowBackup=\"true\").")
        }

        return ApkDetailedMetadata(
            packageName = packageName,
            versionName = versionName,
            versionCode = versionCode,
            minSdk = minSdk,
            targetSdk = targetSdk,
            compileSdk = null,
            permissions = permissions,
            components = ApkComponentCounts(
                activitiesCount = activitiesCount,
                exportedActivitiesCount = exportedActivitiesCount,
                servicesCount = servicesCount,
                exportedServicesCount = exportedServicesCount,
                receiversCount = receiversCount,
                exportedReceiversCount = exportedReceiversCount,
                providersCount = providersCount,
                exportedProvidersCount = exportedProvidersCount
            ),
            isDebuggable = isDebuggable,
            allowBackup = allowBackup,
            signatureResult = null,
            hasApksigner = false,
            warnings = warnings
        )
    }

    private fun parseStringPool(
        buffer: ByteBuffer,
        chunkStart: Int,
        chunkSize: Int,
        headerSize: Int
    ): List<String> {
        val stringCount = buffer.int
        buffer.int // styleCount
        val flags = buffer.int
        val stringsStart = buffer.int
        buffer.int // stylesStart

        val isUtf8 = (flags and UTF8_FLAG) != 0

        val stringOffsets = IntArray(stringCount)
        for (i in 0 until stringCount) {
            stringOffsets[i] = buffer.int
        }

        val strings = ArrayList<String>(stringCount)
        val stringsBase = chunkStart + stringsStart

        for (i in 0 until stringCount) {
            val offset = stringsBase + stringOffsets[i]
            if (offset >= buffer.capacity()) {
                strings.add("")
                continue
            }
            buffer.position(offset)
            val str = if (isUtf8) {
                // UTF-8 string: skip 1-2 length bytes
                val len1 = buffer.get().toInt() and 0xFF
                if (len1 and 0x80 != 0) buffer.get()
                val len2 = buffer.get().toInt() and 0xFF
                val byteLen = if (len2 and 0x80 != 0) {
                    ((len2 and 0x7F) shl 8) or (buffer.get().toInt() and 0xFF)
                } else {
                    len2
                }
                val strBytes = ByteArray(byteLen)
                buffer.get(strBytes)
                String(strBytes, Charsets.UTF_8)
            } else {
                // UTF-16 string
                val charLen = buffer.short.toInt() and 0xFFFF
                val strBytes = ByteArray(charLen * 2)
                buffer.get(strBytes)
                String(strBytes, Charsets.UTF_16LE)
            }
            strings.add(str)
        }

        return strings
    }
}
