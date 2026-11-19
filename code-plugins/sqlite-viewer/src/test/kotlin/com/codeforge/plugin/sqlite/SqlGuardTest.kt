package com.codeforge.plugin.sqlite

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SqlGuardTest {

    @Test
    fun `nur lesende Einzelanweisungen`() {
        assertNull(SqlGuard.violation("SELECT * FROM t;"))
        assertNull(SqlGuard.violation("-- Kommentar\n  pragma table_info(t)"))
        assertNull(SqlGuard.violation("WITH x AS (SELECT 1) SELECT * FROM x"))
        assertNotNull(SqlGuard.violation("DELETE FROM t"))
        assertNotNull(SqlGuard.violation("SELECT 1; DROP TABLE t"))
        assertNotNull(SqlGuard.violation("/* x */ update t set a=1"))
    }

    @Test
    fun `Limit wird ergänzt`() {
        assertEquals("SELECT * FROM t LIMIT 5", SqlGuard.withLimit("SELECT * FROM t;", 5))
        assertEquals("SELECT * FROM t limit 2", SqlGuard.withLimit("SELECT * FROM t limit 2", 5))
        assertEquals("PRAGMA x", SqlGuard.withLimit("PRAGMA x", 5))
    }

    @Test
    fun `Datenbanken werden an der Kennung erkannt`() {
        val root = Files.createTempDirectory("db").toFile()
        File(root, "app/data.db").apply { parentFile.mkdirs(); writeBytes("SQLite format 3\u0000".toByteArray() + ByteArray(100)) }
        File(root, "fake.db").writeText("kein sqlite")
        File(root, "build/x.db").apply { parentFile.mkdirs(); writeBytes("SQLite format 3\u0000".toByteArray() + ByteArray(10)) }
        assertEquals(listOf("data.db"), DatabaseFinder.find(root).map { it.name })
    }
}
