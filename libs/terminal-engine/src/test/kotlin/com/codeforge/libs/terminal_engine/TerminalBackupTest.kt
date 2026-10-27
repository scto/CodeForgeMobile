package com.codeforge.libs.terminal_engine

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.GZIPOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TerminalBackupTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun dirs(name: String): Pair<File, File> =
        File(tmp.root, "$name/home").apply { mkdirs() } to File(tmp.root, "$name/usr").apply { mkdirs() }

    private fun backupBytes(home: File, prefix: File): ByteArray {
        val out = ByteArrayOutputStream()
        TerminalBackup(home, prefix).backup(out)
        return out.toByteArray()
    }

    @Test
    fun roundtripKeepsFilesSymlinksAndExecBit() {
        val (home, prefix) = dirs("a")
        File(home, ".bashrc").writeText("export A=1\n")
        File(prefix, "bin").mkdirs()
        File(prefix, "bin/tool").apply { writeText("#!/bin/sh\n"); setExecutable(true) }
        Files.createSymbolicLink(File(prefix, "bin/alias").toPath(), java.nio.file.Paths.get("tool"))
        val longName = "d".repeat(60) + "/" + "e".repeat(70) + "/file.txt" // > 100 Zeichen
        File(prefix, longName).apply { parentFile.mkdirs(); writeText("long") }
        File(prefix, "tmp").mkdirs(); File(prefix, "tmp/skip.me").writeText("x")

        val data = backupBytes(home, prefix)

        val (home2, prefix2) = dirs("b")
        val summary = TerminalBackup(home2, prefix2).restore(ByteArrayInputStream(data))
        assertEquals("export A=1\n", File(home2, ".bashrc").readText())
        assertTrue(File(prefix2, "bin/tool").canExecute())
        assertTrue(Files.isSymbolicLink(File(prefix2, "bin/alias").toPath()))
        assertEquals("tool", Files.readSymbolicLink(File(prefix2, "bin/alias").toPath()).toString())
        assertEquals("long", File(prefix2, longName).readText())
        assertFalse(File(prefix2, "tmp/skip.me").exists())
        assertTrue(summary.files >= 3 && summary.links == 1)
    }

    @Test
    fun validateWritesNothing() {
        val (home, prefix) = dirs("c")
        File(home, "x").writeText("1")
        val data = backupBytes(home, prefix)
        val (home2, prefix2) = dirs("d")
        TerminalBackup(home2, prefix2).validate(ByteArrayInputStream(data))
        assertFalse(File(home2, "x").exists())
    }

    private fun rawTarGz(vararg entries: Triple<String, Char, String>): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { gz ->
            val w = TarWriter(gz)
            w.writeFile(TerminalBackup.MARKER, "m".toByteArray(), false, 0)
            for ((name, type, arg) in entries) when (type) {
                '2' -> w.writeSymlink(name, arg, 0)
                else -> w.writeFile(name, arg.toByteArray(), false, 0)
            }
            w.finish()
        }
        return out.toByteArray()
    }

    private fun assertRejected(data: ByteArray) {
        val (home, prefix) = dirs("r${System.nanoTime()}")
        try {
            TerminalBackup(home, prefix).validate(ByteArrayInputStream(data))
            fail("erwartet: InvalidBackupException")
        } catch (_: InvalidBackupException) {
        }
    }

    @Test
    fun rejectsPathTraversalAbsoluteAndForeignRoots() {
        assertRejected(rawTarGz(Triple("home/../evil", '0', "x")))
        assertRejected(rawTarGz(Triple("/etc/passwd", '0', "x")))
        assertRejected(rawTarGz(Triple("sdcard/x", '0', "x")))
    }

    @Test
    fun rejectsArchiveWithoutMarkerAndNonTar() {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { gz -> val w = TarWriter(gz); w.writeFile("home/a", "x".toByteArray(), false, 0); w.finish() }
        assertRejected(out.toByteArray())
        assertRejected("kein archiv".toByteArray())
    }

    @Test
    fun neverWritesThroughSymlinkOutOfTarget() {
        val outside = tmp.newFolder("outside")
        val (home, prefix) = dirs("s")
        // Eintrag 1: Symlink usr/link -> <outside>, Eintrag 2: Datei usr/link/pwned
        val data = rawTarGz(
            Triple("usr/link", '2', outside.absolutePath),
            Triple("usr/link/pwned", '0', "x")
        )
        try {
            TerminalBackup(home, prefix).restore(ByteArrayInputStream(data))
            fail("erwartet: InvalidBackupException")
        } catch (_: InvalidBackupException) {
        }
        assertFalse(File(outside, "pwned").exists())
    }
}
