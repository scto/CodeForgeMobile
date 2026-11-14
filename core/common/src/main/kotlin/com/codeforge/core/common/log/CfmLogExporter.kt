package com.codeforge.core.common.log

import java.io.BufferedReader
import java.io.File
import java.util.zip.ZipInputStream

/** Exportiert Protokolldateien (auch gezippte) als HTML oder XML. */
object CfmLogExporter {

    /** Zielformat. */
    enum class Format(val extension: String) { HTML("html"), XML("xml") }

    private val OWN_LINE = Regex("^\\d{4}-\\d\\d-\\d\\d \\S+ ([VDIWEF])/")
    private val LOGCAT_LINE = Regex("^\\d\\d-\\d\\d \\S+\\s+\\d+\\s+\\d+ ([VDIWEF]) ")

    /** Stufe einer Zeile (`V D I W E F`) oder `null` bei Fortsetzungszeilen (z. B. Stacktrace). */
    fun levelOf(line: String): Char? =
        (OWN_LINE.find(line) ?: LOGCAT_LINE.find(line))?.groupValues?.get(1)?.first()

    /**
     * Schreibt [source] im [format] nach [targetDir] (`<Name>.html` bzw. `.xml`) und liefert die Zieldatei.
     * Arbeitet zeilenweise, damit auch große Protokolle wenig Speicher brauchen.
     */
    fun export(source: File, format: Format, targetDir: File): File {
        targetDir.mkdirs()
        val base = source.name.removeSuffix(".zip").removeSuffix(".log")
        val target = File(targetDir, "$base.${format.extension}")
        open(source).use { reader ->
            target.bufferedWriter().use { out ->
                when (format) {
                    Format.HTML -> {
                        out.write(
                            "<!DOCTYPE html>\n<html><head><meta charset=\"utf-8\"><title>${escape(base)}</title><style>" +
                                "body{font-family:monospace;font-size:12px;background:#1c1b1f;color:#e6e1e5;margin:8px}" +
                                "div{white-space:pre-wrap;word-break:break-word;padding:1px 0}" +
                                ".E,.F{color:#f2b8b5}.W{color:#f5d38a}.I{color:#a8c7fa}.D,.V{color:#9e9aa5}" +
                                "</style></head><body>\n<h3>${escape(source.name)}</h3>\n"
                        )
                        var level = 'I'
                        reader.forEachLine { line ->
                            level = levelOf(line) ?: level
                            out.write("<div class=\"$level\">${escape(line)}</div>\n")
                        }
                        out.write("</body></html>\n")
                    }
                    Format.XML -> {
                        out.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<log file=\"${escape(source.name)}\">\n")
                        var level = 'I'
                        reader.forEachLine { line ->
                            level = levelOf(line) ?: level
                            out.write("  <entry level=\"$level\">${escape(line)}</entry>\n")
                        }
                        out.write("</log>\n")
                    }
                }
            }
        }
        return target
    }

    private fun open(source: File): BufferedReader =
        if (source.name.endsWith(".zip")) {
            val zip = ZipInputStream(source.inputStream().buffered())
            zip.nextEntry
            zip.bufferedReader()
        } else source.bufferedReader()

    /** Maskiert `& < > "` und entfernt Steuerzeichen, die in XML 1.0 nicht erlaubt sind. */
    internal fun escape(text: String): String = buildString(text.length + 16) {
        for (c in text) {
            when {
                c == '&' -> append("&amp;")
                c == '<' -> append("&lt;")
                c == '>' -> append("&gt;")
                c == '"' -> append("&quot;")
                c == '\t' || c >= ' ' && c != '￾' && c != '￿' -> append(c)
            }
        }
    }
}
