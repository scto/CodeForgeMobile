/**
 * Modul: :libs:terminal-engine
 * @author Thomas Schmid
 */
package com.codeforge.libs.terminal_engine

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** Das Archiv ist unbrauchbar oder unsicher (fremdes Format, Pfade außerhalb von home/ und usr/). */
class InvalidBackupException(message: String) : IOException(message)

/** Vorgang wurde abgebrochen. */
class BackupCancelledException : IOException("cancelled")

data class BackupSummary(val files: Int, val dirs: Int, val links: Int, val bytes: Long)

/**
 * Sichert und stellt das Terminal wieder her: `$HOME` (Archivpfad `home/…`) und `$PREFIX`
 * (Archivpfad `usr/…`) als `.tar.gz` (GNU-Tar-kompatibel). Symlinks und das Ausführbar-Bit bleiben
 * erhalten. Beim Wiederherstellen werden nur Pfade unter `home/` und `usr/` akzeptiert, `..` und
 * absolute Pfade werden abgelehnt, und es wird nie durch einen Symlink aus dem Zielverzeichnis hinaus
 * geschrieben.
 */
class TerminalBackup(private val home: File, private val prefix: File) {

    fun backup(out: OutputStream, isCancelled: () -> Boolean = { false }): BackupSummary {
        var files = 0; var dirs = 0; var links = 0; var bytes = 0L
        val gz = GZIPOutputStream(out.buffered(BUFFER))
        val tar = TarWriter(gz)
        tar.writeFile(MARKER, MARKER_CONTENT.toByteArray(), executable = false, mtime = System.currentTimeMillis() / 1000)
        for ((root, archiveRoot) in listOf(home to "home", prefix to "usr")) {
            if (!root.isDirectory) continue
            val rootPath = root.toPath()
            val skipTmp = File(prefix, "tmp").toPath()
            Files.walkFileTree(rootPath, object : SimpleFileVisitor<Path>() {
                private fun name(p: Path): String {
                    val rel = rootPath.relativize(p).toString().replace(File.separatorChar, '/')
                    return if (rel.isEmpty()) archiveRoot else "$archiveRoot/$rel"
                }

                override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (isCancelled()) throw BackupCancelledException()
                    if (dir == skipTmp) return FileVisitResult.SKIP_SUBTREE
                    tar.writeDirectory(name(dir), attrs.lastModifiedTime().toMillis() / 1000)
                    dirs++
                    return FileVisitResult.CONTINUE
                }

                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (isCancelled()) throw BackupCancelledException()
                    when {
                        attrs.isSymbolicLink -> {
                            tar.writeSymlink(name(file), Files.readSymbolicLink(file).toString(), attrs.lastModifiedTime().toMillis() / 1000)
                            links++
                        }
                        attrs.isRegularFile -> {
                            val f = file.toFile()
                            runCatching { f.inputStream() }.getOrNull()?.use { input ->
                                tar.writeFile(name(file), input, attrs.size(), f.canExecute(), attrs.lastModifiedTime().toMillis() / 1000)
                                files++; bytes += attrs.size()
                            }
                        }
                    }
                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult = FileVisitResult.CONTINUE
            })
        }
        tar.finish()
        gz.finish()
        gz.flush()
        return BackupSummary(files, dirs, links, bytes)
    }

    /** Prüft das Archiv vollständig, ohne etwas zu schreiben. */
    fun validate(input: InputStream): BackupSummary = process(input, extract = false) { false }

    fun restore(input: InputStream, isCancelled: () -> Boolean = { false }): BackupSummary =
        process(input, extract = true, isCancelled = isCancelled)

    private fun process(input: InputStream, extract: Boolean, isCancelled: () -> Boolean): BackupSummary {
        var files = 0; var dirs = 0; var links = 0; var bytes = 0L
        var markerSeen = false
        val homeCanon = if (extract) home.also { it.mkdirs() }.canonicalFile.toPath() else null
        val prefixCanon = if (extract) prefix.also { it.mkdirs() }.canonicalFile.toPath() else null
        val tar = TarReader(runCatching { GZIPInputStream(input.buffered(BUFFER)) }.getOrElse { throw InvalidBackupException("kein gzip") })
        while (true) {
            if (isCancelled()) throw BackupCancelledException()
            val e = tar.next() ?: break
            val name = e.name.trimEnd('/')
            if (name == MARKER) {
                markerSeen = true
                tar.skipData()
                continue
            }
            val (base, baseCanon, rel) = resolve(name, home, prefix, homeCanon, prefixCanon)
            when (e.type) {
                TarReader.DIR -> {
                    if (extract) ensureDir(File(base, rel), baseCanon!!)
                    dirs++; tar.skipData()
                }
                TarReader.FILE -> {
                    if (extract) {
                        val target = File(base, rel)
                        prepareTarget(target, baseCanon!!)
                        target.outputStream().buffered(BUFFER).use { tar.copyDataTo(it) }
                        if (e.mode and 0b001_000_000 != 0) target.setExecutable(true, false)
                        target.setLastModified(e.mtime * 1000)
                    } else tar.skipData()
                    files++; bytes += e.size
                }
                TarReader.SYMLINK -> {
                    if (e.linkName.isEmpty() || e.linkName.contains('\u0000')) throw InvalidBackupException("ungültiger Symlink: $name")
                    if (extract) {
                        val target = File(base, rel)
                        prepareTarget(target, baseCanon!!)
                        Files.createSymbolicLink(target.toPath(), java.nio.file.Paths.get(e.linkName))
                    }
                    links++; tar.skipData()
                }
                else -> tar.skipData() // Hardlinks, Geräte etc. werden ignoriert
            }
        }
        if (!markerSeen) throw InvalidBackupException("keine CodeForge-Sicherung")
        return BackupSummary(files, dirs, links, bytes)
    }

    private data class Resolved(val base: File, val baseCanon: Path?, val rel: String)

    private fun resolve(name: String, home: File, prefix: File, homeCanon: Path?, prefixCanon: Path?): Resolved {
        if (name.isEmpty() || name.startsWith("/") || name.contains('\u0000')) throw InvalidBackupException("ungültiger Pfad: $name")
        val parts = name.split('/')
        if (parts.any { it == ".." || it == "." || it.isEmpty() }) throw InvalidBackupException("ungültiger Pfad: $name")
        val rel = parts.drop(1).joinToString("/")
        return when (parts[0]) {
            "home" -> Resolved(home, homeCanon, rel)
            "usr" -> Resolved(prefix, prefixCanon, rel)
            else -> throw InvalidBackupException("Pfad außerhalb von home/ und usr/: $name")
        }
    }

    /** Stellt sicher, dass [dir] ein echtes Verzeichnis innerhalb von [baseCanon] ist (kein Symlink hinaus). */
    private fun ensureDir(dir: File, baseCanon: Path) {
        if (!dir.exists() && !Files.isSymbolicLink(dir.toPath())) dir.mkdirs()
        if (!dir.canonicalFile.toPath().startsWith(baseCanon)) throw InvalidBackupException("Pfad verlässt das Zielverzeichnis: $dir")
    }

    private fun prepareTarget(target: File, baseCanon: Path) {
        val parent = target.parentFile ?: throw InvalidBackupException("kein Elternverzeichnis")
        ensureDir(parent, baseCanon)
        if (!parent.isDirectory) throw InvalidBackupException("Elternpfad ist kein Verzeichnis: $parent")
        val p = target.toPath()
        if (Files.exists(p, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)) throw InvalidBackupException("Verzeichnis statt Datei: $target")
            Files.delete(p)
        }
    }

    companion object {
        const val MARKER = "codeforge-backup-v1"
        private const val MARKER_CONTENT = "CodeForge terminal backup v1\n"
        private const val BUFFER = 64 * 1024
    }
}

/** Minimaler GNU-Tar-Schreiber (Header 512 Byte, lange Namen über `././@LongLink`). */
internal class TarWriter(private val out: OutputStream) {
    fun writeDirectory(name: String, mtime: Long) = writeEntry(name.trimEnd('/') + "/", '5', "", 0, 0b111_101_101, mtime)
    fun writeSymlink(name: String, target: String, mtime: Long) = writeEntry(name, '2', target, 0, 0b111_111_111, mtime)

    fun writeFile(name: String, content: ByteArray, executable: Boolean, mtime: Long) {
        writeEntry(name, '0', "", content.size.toLong(), mode(executable), mtime)
        out.write(content); pad(content.size.toLong())
    }

    fun writeFile(name: String, input: InputStream, size: Long, executable: Boolean, mtime: Long) {
        // Größe steht im Header; wächst die Datei während der Sicherung, wird abgeschnitten, schrumpft sie, wird aufgefüllt.
        writeEntry(name, '0', "", size, mode(executable), mtime)
        val buf = ByteArray(32 * 1024)
        var left = size
        while (left > 0) {
            val n = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
            if (n < 0) break
            out.write(buf, 0, n); left -= n
        }
        while (left > 0) { val n = minOf(buf.size.toLong(), left).toInt(); out.write(ByteArray(n)); left -= n }
        pad(size)
    }

    fun finish() = out.write(ByteArray(1024))

    private fun mode(executable: Boolean) = if (executable) 0b111_101_101 else 0b110_100_100

    private fun pad(size: Long) {
        val rest = (size % 512).toInt()
        if (rest != 0) out.write(ByteArray(512 - rest))
    }

    private fun writeEntry(name: String, type: Char, link: String, size: Long, mode: Int, mtime: Long) {
        val nameBytes = name.toByteArray(Charsets.UTF_8)
        val linkBytes = link.toByteArray(Charsets.UTF_8)
        if (nameBytes.size > 99) writeLong('L', nameBytes)
        if (linkBytes.size > 99) writeLong('K', linkBytes)
        out.write(header(nameBytes.copyOf(minOf(nameBytes.size, 99)), type, linkBytes.copyOf(minOf(linkBytes.size, 99)), size, mode, mtime))
    }

    private fun writeLong(type: Char, data: ByteArray) {
        val payload = data + 0
        out.write(header("././@LongLink".toByteArray(), type, ByteArray(0), payload.size.toLong(), 0, 0))
        out.write(payload); pad(payload.size.toLong())
    }

    private fun header(name: ByteArray, type: Char, link: ByteArray, size: Long, mode: Int, mtime: Long): ByteArray {
        val h = ByteArray(512)
        name.copyInto(h, 0)
        octal(h, 100, 8, mode.toLong())
        octal(h, 108, 8, 0); octal(h, 116, 8, 0)
        octal(h, 124, 12, size)
        octal(h, 136, 12, mtime.coerceAtLeast(0))
        for (i in 148 until 156) h[i] = ' '.code.toByte()
        h[156] = type.code.toByte()
        link.copyInto(h, 157)
        "ustar  ".toByteArray().copyInto(h, 257) // GNU-Magic "ustar  \0"
        val sum = h.sumOf { it.toInt() and 0xff }
        val s = sum.toString(8).padStart(6, '0')
        s.toByteArray().copyInto(h, 148); h[154] = 0; h[155] = ' '.code.toByte()
        return h
    }

    private fun octal(h: ByteArray, off: Int, len: Int, v: Long) {
        val s = v.toString(8).padStart(len - 1, '0')
        if (s.length > len - 1) throw IOException("Wert zu groß für Tar-Header")
        s.toByteArray().copyInto(h, off); h[off + len - 1] = 0
    }
}

/** Minimaler GNU-Tar-Leser für die oben geschriebenen Einträge (inkl. LongLink). */
internal class TarReader(private val input: InputStream) {
    class Entry(val name: String, val type: Char, val linkName: String, val size: Long, val mode: Int, val mtime: Long)

    private var remaining = 0L
    private var padding = 0

    fun next(): Entry? {
        skipData()
        var longName: String? = null
        var longLink: String? = null
        while (true) {
            val h = readBlock() ?: return null
            if (h.all { it == 0.toByte() }) return null
            verifyChecksum(h)
            val type = (h[156].toInt() and 0xff).toChar().let { if (it == '\u0000') '0' else it }
            val size = parseOctal(h, 124, 12)
            if (type == 'L' || type == 'K') {
                val data = ByteArray(size.toInt().also { if (size > 65_536) throw InvalidBackupException("Name zu lang") })
                readFully(data); skipPadding(size)
                val s = String(data, Charsets.UTF_8).trimEnd('\u0000')
                if (type == 'L') longName = s else longLink = s
                continue
            }
            val name = longName ?: cString(h, 0, 100)
            val link = longLink ?: cString(h, 157, 100)
            remaining = size; padding = ((512 - size % 512) % 512).toInt()
            return Entry(name, type, link, size, parseOctal(h, 100, 8).toInt(), parseOctal(h, 136, 12))
        }
    }

    fun skipData() {
        while (remaining > 0) {
            val n = input.skip(remaining)
            if (n <= 0) { if (input.read() < 0) throw InvalidBackupException("Archiv abgeschnitten"); remaining-- } else remaining -= n
        }
        skipBytes(padding.toLong()); padding = 0
    }

    fun copyDataTo(out: OutputStream) {
        val buf = ByteArray(32 * 1024)
        while (remaining > 0) {
            val n = input.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
            if (n < 0) throw InvalidBackupException("Archiv abgeschnitten")
            out.write(buf, 0, n); remaining -= n
        }
        skipBytes(padding.toLong()); padding = 0
    }

    private fun skipPadding(size: Long) = skipBytes((512 - size % 512) % 512)
    private fun skipBytes(n: Long) { var l = n; while (l > 0) { if (input.read() < 0) throw InvalidBackupException("Archiv abgeschnitten"); l-- } }

    private fun readBlock(): ByteArray? {
        val b = ByteArray(512)
        var off = 0
        while (off < 512) { val n = input.read(b, off, 512 - off); if (n < 0) return if (off == 0) null else throw InvalidBackupException("Archiv abgeschnitten"); off += n }
        return b
    }

    private fun readFully(b: ByteArray) {
        var off = 0
        while (off < b.size) { val n = input.read(b, off, b.size - off); if (n < 0) throw InvalidBackupException("Archiv abgeschnitten"); off += n }
    }

    private fun verifyChecksum(h: ByteArray) {
        val stored = parseOctal(h, 148, 8)
        var sum = 0L
        for (i in h.indices) sum += if (i in 148 until 156) 32 else (h[i].toInt() and 0xff)
        if (sum != stored) throw InvalidBackupException("Prüfsumme falsch (kein Tar-Archiv)")
    }

    private fun parseOctal(h: ByteArray, off: Int, len: Int): Long {
        val s = cString(h, off, len).trim()
        return if (s.isEmpty()) 0 else s.toLongOrNull(8) ?: throw InvalidBackupException("ungültiger Header")
    }

    private fun cString(h: ByteArray, off: Int, len: Int): String {
        var end = off
        while (end < off + len && h[end] != 0.toByte()) end++
        return String(h, off, end - off, Charsets.UTF_8)
    }

    companion object {
        const val FILE = '0'
        const val SYMLINK = '2'
        const val DIR = '5'
    }
}
