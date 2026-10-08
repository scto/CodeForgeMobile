// Modul: :core:testing
package com.codeforge.core.testing

import com.codeforge.core.resources.Res
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Löst [Res.string] in JVM-Unit-Tests ohne Android-Context auf: liest die echte
 * `core/resources/src/main/res/values/strings.xml` und ordnet die `R.string`-IDs per Reflection
 * den Namen zu. Tests prüfen so denselben Text wie die App.
 *
 * Nutzung: `init { TestRes.install() }` oder `@Before fun setUp() = TestRes.install()`.
 */
object TestRes {

    private const val STRINGS_PATH = "core/resources/src/main/res/values/strings.xml"
    private const val R_STRING_CLASS = "com.codeforge.core.resources.R\$string"

    fun install(stringsXml: File = locateStringsXml()) {
        val texts = parse(stringsXml)
        val names = idToName()
        Res.install(object : Res.Resolver {
            override fun string(id: Int, args: Array<out Any>): String {
                val text = names[id]?.let(texts::get) ?: return "@string/$id"
                return if (args.isEmpty()) text else String.format(Locale.GERMANY, text, *args)
            }

            override fun plural(id: Int, quantity: Int, args: Array<out Any>): String = string(id, args)
        })
    }

    /** Setzt den Resolver zurück (z. B. in `@After`). */
    fun uninstall() = Res.install(null)

    private fun locateStringsXml(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            File(dir, STRINGS_PATH).takeIf { it.isFile }?.let { return it }
            dir = dir.parentFile
        }
        error("strings.xml nicht gefunden (ausgehend von ${System.getProperty("user.dir")})")
    }

    private fun idToName(): Map<Int, String> = Class.forName(R_STRING_CLASS).fields
        .filter { java.lang.reflect.Modifier.isStatic(it.modifiers) && it.type == Int::class.javaPrimitiveType }
        .associate { it.getInt(null) to it.name }

    private fun parse(file: File): Map<String, String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("string")
        return (0 until nodes.length).associate { i ->
            val n = nodes.item(i)
            n.attributes.getNamedItem("name").nodeValue to unescape(n.textContent)
        }
    }

    /** Android-Escapes: `\'`, `\"`, `\n`, `\t`, `\@`, `\?`, `\\`, `\uXXXX`; umschließende Anführungszeichen entfallen. */
    internal fun unescape(raw: String): String {
        val src = if (raw.length >= 2 && raw.startsWith("\"") && raw.endsWith("\"")) raw.substring(1, raw.length - 1) else raw
        val sb = StringBuilder()
        var i = 0
        while (i < src.length) {
            val c = src[i]
            if (c == '\\' && i + 1 < src.length) {
                val next = src[i + 1]
                when (next) {
                    'n' -> sb.append('\n')
                    't' -> sb.append('\t')
                    'u' -> { sb.append(src.substring(i + 2, i + 6).toInt(16).toChar()); i += 4 }
                    else -> sb.append(next)
                }
                i += 2
            } else {
                sb.append(c); i++
            }
        }
        return sb.toString()
    }
}
