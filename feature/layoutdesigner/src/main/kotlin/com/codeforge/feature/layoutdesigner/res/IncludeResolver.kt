package com.codeforge.feature.layoutdesigner.res

import com.codeforge.feature.layoutdesigner.model.LayoutDocument
import com.codeforge.feature.layoutdesigner.model.LayoutNode
import com.codeforge.feature.layoutdesigner.xml.LayoutXml
import java.io.File

sealed interface IncludedLayoutResult {
    data class Success(
        val layoutName: String,
        val file: File,
        val document: LayoutDocument,
        val mergedRoot: LayoutNode,
    ) : IncludedLayoutResult

    data class CycleDetected(val layoutName: String, val path: String) : IncludedLayoutResult
    data class DepthLimitExceeded(val layoutName: String) : IncludedLayoutResult
    data class NotFound(val layoutName: String) : IncludedLayoutResult
    data class ParseError(val layoutName: String, val message: String) : IncludedLayoutResult
}

class IncludeResolver(
    private val resDirs: List<File>,
) {
    /**
     * Löst eine Layout-Referenz (z.B. "@layout/header" oder "header") auf.
     * Prüft Rekursionstiefe (max 8) und Zyklen über [visitedPaths].
     */
    fun resolve(
        layoutRef: String,
        includeNode: LayoutNode? = null,
        visitedPaths: Set<String> = emptySet(),
        depth: Int = 0,
    ): IncludedLayoutResult {
        if (depth > MAX_DEPTH) {
            return IncludedLayoutResult.DepthLimitExceeded(layoutRef)
        }

        val layoutName = layoutRef.removePrefix("@layout/").substringBefore('.')
        if (layoutName.isBlank()) {
            return IncludedLayoutResult.NotFound(layoutRef)
        }

        val file = findLayoutFile(layoutName) ?: return IncludedLayoutResult.NotFound(layoutName)
        val canonicalPath = file.canonicalPath

        if (canonicalPath in visitedPaths) {
            return IncludedLayoutResult.CycleDetected(layoutName, canonicalPath)
        }

        val newVisited = visitedPaths + canonicalPath
        val text = try {
            file.readText()
        } catch (e: Exception) {
            return IncludedLayoutResult.ParseError(layoutName, e.message ?: "Failed to read file")
        }

        val doc = try {
            LayoutXml.parse(text)
        } catch (e: Exception) {
            return IncludedLayoutResult.ParseError(layoutName, e.message ?: "XML parse error")
        }

        val merged = mergeAttributes(doc.root, includeNode)
        return IncludedLayoutResult.Success(
            layoutName = layoutName,
            file = file,
            document = doc,
            mergedRoot = merged,
        )
    }

    private fun findLayoutFile(layoutName: String): File? {
        val targetName = "$layoutName.xml"
        for (resDir in resDirs) {
            if (!resDir.exists() || !resDir.isDirectory) continue
            val layoutDirs = resDir.listFiles { f -> f.isDirectory && f.name.startsWith("layout") } ?: continue
            for (dir in layoutDirs) {
                val candidate = File(dir, targetName)
                if (candidate.exists() && candidate.isFile) {
                    return candidate
                }
            }
        }
        return null
    }

    companion object {
        const val MAX_DEPTH = 8

        /**
         * Überschreibt Attribute des eingebundenen Wurzelknotens mit den im <include>-Tag definierten.
         * Insbesondere android:id und alle layout_*-Attribute.
         */
        fun mergeAttributes(originalRoot: LayoutNode, includeNode: LayoutNode?): LayoutNode {
            if (includeNode == null) return originalRoot

            val newAttrs = originalRoot.attributes.toMutableMap()

            // 1. android:id überschreiben, falls im <include> vorhanden
            includeNode.attr("android:id")?.let { newAttrs["android:id"] = it }

            // 2. Alle layout_*-Attribute des <include> übernehmen
            includeNode.attributes.forEach { (key, value) ->
                if (key.contains(":layout_") || key == "layout_width" || key == "layout_height") {
                    newAttrs[key] = value
                }
            }

            return originalRoot.copy(attributes = newAttrs)
        }
    }
}
