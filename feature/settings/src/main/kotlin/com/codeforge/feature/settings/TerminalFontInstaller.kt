/**
 * Modul: :feature:settings
 * @author Thomas Schmid
 */
package com.codeforge.feature.settings

import java.io.File
import java.io.InputStream

/**
 * Installiert die Terminal-Schriftart als `~/.termux/font.ttf` (wird von Termux beim Öffnen des Terminals geladen).
 * Es werden nur Dateien mit gültiger Font-Kennung (TTF/OTF/TTC) übernommen.
 */
class TerminalFontInstaller(private val target: File = defaultTarget()) {

    val isInstalled: Boolean get() = target.isFile

    /** `true`, wenn [input] eine gültige Schrift war und installiert wurde; die alte Schrift bleibt sonst unverändert. */
    fun install(input: InputStream): Boolean = runCatching {
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, "font.ttf.tmp")
        input.use { src -> tmp.outputStream().use { src.copyTo(it) } }
        if (!isFontFile(tmp)) {
            tmp.delete()
            return@runCatching false
        }
        if (target.exists()) target.delete()
        tmp.renameTo(target)
    }.getOrDefault(false)

    /** Zurück zur Systemschrift (Termux-Standard). */
    fun reset(): Boolean = !target.exists() || target.delete()

    companion object {
        fun defaultTarget(): File {
            val home = System.getenv("HOME")?.takeIf { it.isNotBlank() } ?: "/data/data/com.codeforge.app/files/home"
            return File(home, ".termux/font.ttf")
        }

        fun isFontFile(file: File): Boolean = runCatching {
            if (file.length() < 12) return@runCatching false
            val head = file.inputStream().use { s -> ByteArray(4).also { s.read(it) } }
            val tag = String(head, Charsets.ISO_8859_1)
            head.contentEquals(byteArrayOf(0, 1, 0, 0)) || tag == "OTTO" || tag == "true" || tag == "ttcf"
        }.getOrDefault(false)
    }
}
