/**
 * Modul: :core:domain
 * @author Thomas Schmid
 */
package com.codeforge.core.domain.parser

import com.codeforge.core.domain.model.ApkMetadata
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApkMetadataParser @Inject constructor() {

    fun parse(
        jsonContent: String,
        metadataDirectory: File,
        supportedAbis: List<String> = emptyList()
    ): Result<ApkMetadata> = runCatching {
        val appId = extractString(jsonContent, "applicationId")
            ?: error("applicationId not found in output-metadata.json")
        val variantName = extractString(jsonContent, "variantName") ?: "unknown"

        val elements = parseElements(jsonContent)
        if (elements.isEmpty()) {
            error("No elements found in output-metadata.json")
        }

        // Finde passendes Element für die Geräte-ABIs (falls ABI-Splits vorhanden sind)
        val selectedElement = if (supportedAbis.isNotEmpty()) {
            val matching = supportedAbis.firstNotNullOfOrNull { abi ->
                elements.firstOrNull { it.abi?.equals(abi, ignoreCase = true) == true }
            }
            matching ?: elements.first()
        } else {
            elements.first()
        }

        val apkFile = File(metadataDirectory, selectedElement.outputFile)
        val isSigned = !apkFile.name.contains("-unsigned", ignoreCase = true)

        ApkMetadata(
            file = apkFile,
            applicationId = appId,
            variantName = variantName,
            versionCode = selectedElement.versionCode,
            versionName = selectedElement.versionName,
            abiFilter = selectedElement.abi,
            isSigned = isSigned
        )
    }

    private data class ElementInfo(
        val outputFile: String,
        val versionCode: Long?,
        val versionName: String?,
        val abi: String?
    )

    private fun parseElements(json: String): List<ElementInfo> {
        val elementsContent = extractArrayContent(json, "elements") ?: return emptyList()
        val objects = extractTopLevelObjects(elementsContent)

        return objects.mapNotNull { objText ->
            val outputFile = extractString(objText, "outputFile") ?: return@mapNotNull null
            val versionCode = extractLong(objText, "versionCode")
            val versionName = extractString(objText, "versionName")

            // ABI-Filter aus "filters": [{"filterType": "ABI", "value": "..."}]
            val abi = extractAbi(objText)

            ElementInfo(
                outputFile = outputFile,
                versionCode = versionCode,
                versionName = versionName,
                abi = abi
            )
        }
    }

    private fun extractArrayContent(json: String, arrayKey: String): String? {
        val keyIndex = json.indexOf("\"$arrayKey\"")
        if (keyIndex == -1) return null
        val bracketOpen = json.indexOf('[', keyIndex)
        if (bracketOpen == -1) return null

        var depth = 0
        var inString = false
        var escape = false
        for (i in bracketOpen until json.length) {
            val c = json[i]
            if (escape) {
                escape = false
                continue
            }
            if (c == '\\') {
                escape = true
                continue
            }
            if (c == '"') {
                inString = !inString
                continue
            }
            if (!inString) {
                if (c == '[') {
                    depth++
                } else if (c == ']') {
                    depth--
                    if (depth == 0) {
                        return json.substring(bracketOpen + 1, i)
                    }
                }
            }
        }
        return null
    }

    private fun extractTopLevelObjects(content: String): List<String> {
        val result = mutableListOf<String>()
        var depth = 0
        var startIndex = -1
        var inString = false
        var escape = false

        for (i in content.indices) {
            val c = content[i]
            if (escape) {
                escape = false
                continue
            }
            if (c == '\\') {
                escape = true
                continue
            }
            if (c == '"') {
                inString = !inString
                continue
            }
            if (!inString) {
                if (c == '{') {
                    if (depth == 0) {
                        startIndex = i
                    }
                    depth++
                } else if (c == '}') {
                    depth--
                    if (depth == 0 && startIndex != -1) {
                        result.add(content.substring(startIndex, i + 1))
                        startIndex = -1
                    }
                }
            }
        }
        return result
    }

    private fun extractString(json: String, key: String): String? {
        val regex = Regex(""""$key"\s*:\s*"([^"]+)"""")
        return regex.find(json)?.groupValues?.get(1)
    }

    private fun extractLong(json: String, key: String): Long? {
        val regex = Regex(""""$key"\s*:\s*([0-9]+)""")
        return regex.find(json)?.groupValues?.get(1)?.toLongOrNull()
    }

    private fun extractAbi(objText: String): String? {
        val abiRegex = Regex(""""value"\s*:\s*"([^"]+)"""")
        return abiRegex.find(objText)?.groupValues?.get(1)
    }
}
