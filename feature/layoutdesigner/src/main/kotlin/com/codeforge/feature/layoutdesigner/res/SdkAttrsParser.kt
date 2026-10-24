package com.codeforge.feature.layoutdesigner.res

import com.codeforge.feature.layoutdesigner.model.AttrKind
import com.codeforge.feature.layoutdesigner.model.AttrSpec
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory

data class SdkAttrDef(
    val name: String,
    val kind: AttrKind,
    val formats: Set<String>,
    val options: List<String>,
)

class SdkAttrsParser {

    private val styleablesCache = mutableMapOf<String, List<AttrSpec>>()

    /**
     * Parst attrs.xml aus einem InputStream.
     */
    fun parse(inputStream: InputStream): Map<String, List<AttrSpec>> {
        val factory = DocumentBuilderFactory.newInstance()
        val doc = try {
            factory.newDocumentBuilder().parse(inputStream)
        } catch (_: Exception) {
            return emptyMap()
        }

        val result = mutableMapOf<String, MutableList<AttrSpec>>()
        val root = doc.documentElement ?: return emptyMap()
        val children = root.childNodes

        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType != Node.ELEMENT_NODE) continue
            val elem = node as Element

            if (elem.tagName == "declare-styleable") {
                val styleableName = elem.getAttribute("name")
                if (styleableName.isEmpty()) continue
                val list = result.getOrPut(styleableName) { mutableListOf() }

                val attrNodes = elem.getElementsByTagName("attr")
                for (j in 0 until attrNodes.length) {
                    val attrElem = attrNodes.item(j) as? Element ?: continue
                    val attrName = attrElem.getAttribute("name")
                    if (attrName.isEmpty()) continue

                    val format = attrElem.getAttribute("format")
                    val formats = format.split('|').map { it.trim() }.toSet()

                    val enumNodes = attrElem.getElementsByTagName("enum")
                    val flagNodes = attrElem.getElementsByTagName("flag")

                    val options = mutableListOf<String>()
                    for (k in 0 until enumNodes.length) {
                        (enumNodes.item(k) as? Element)?.getAttribute("name")?.let { if (it.isNotEmpty()) options.add(it) }
                    }
                    for (k in 0 until flagNodes.length) {
                        (flagNodes.item(k) as? Element)?.getAttribute("name")?.let { if (it.isNotEmpty()) options.add(it) }
                    }

                    val kind = when {
                        flagNodes.length > 0 || "flag" in formats -> AttrKind.FLAG
                        enumNodes.length > 0 || "enum" in formats -> AttrKind.ENUM
                        "boolean" in formats -> AttrKind.BOOLEAN
                        "color" in formats -> AttrKind.COLOR
                        "dimension" in formats -> AttrKind.DIMENSION
                        "integer" in formats -> AttrKind.INTEGER
                        "float" in formats -> AttrKind.FLOAT
                        "reference" in formats -> AttrKind.REFERENCE
                        else -> AttrKind.TEXT
                    }

                    val qualifiedName = if (!attrName.contains(':') && !attrName.startsWith("android:")) {
                        "android:$attrName"
                    } else attrName

                    list.add(AttrSpec(qualifiedName, kind, options))
                }
            }
        }

        styleablesCache.putAll(result)
        return result
    }

    fun getAttrsFor(styleableName: String): List<AttrSpec> = styleablesCache[styleableName] ?: emptyList()

    companion object {
        /** Sucht nach der attrs.xml im Android-SDK */
        fun findSdkAttrsXml(): File? {
            val sdkDir = System.getenv("ANDROID_HOME")?.let { File(it) }
                ?: System.getenv("ANDROID_SDK_ROOT")?.let { File(it) }
                ?: File("/data/data/com.termux/files/home/android-sdk")
                ?: File("/opt/android-sdk")

            if (!sdkDir.exists()) return null
            val platformsDir = File(sdkDir, "platforms")
            if (!platformsDir.exists() || !platformsDir.isDirectory) return null

            val highestPlatform = platformsDir.listFiles { f -> f.isDirectory && f.name.startsWith("android-") }
                ?.maxByOrNull { it.name.removePrefix("android-").toIntOrNull() ?: 0 }
                ?: return null

            val attrsFile = File(highestPlatform, "data/res/values/attrs.xml")
            return if (attrsFile.exists()) attrsFile else null
        }
    }
}
