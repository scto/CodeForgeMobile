package com.codeforge.libs.code_tools.imports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KotlinImportOrganizerTest {

    @Test
    fun `sortiert alphabetisch und entfernt Duplikate`() {
        val src = "package a\n\nimport b.Zeta\nimport a.Alpha\nimport b.Zeta\n\nfun f(x: Zeta, y: Alpha) {}\n"
        val out = KotlinImportOrganizer.organize(src)!!
        assertEquals("package a\n\nimport a.Alpha\nimport b.Zeta\n\nfun f(x: Zeta, y: Alpha) {}\n", out)
    }

    @Test
    fun `entfernt unbenutzte Imports, behaelt Stern- und Operator-Imports`() {
        val src = "package a\n\nimport x.Used\nimport x.Unused\nimport x.*\nimport x.getValue\n\nval v: Used = TODO()\n"
        val out = KotlinImportOrganizer.organize(src)!!
        assertEquals("package a\n\nimport x.*\nimport x.Used\nimport x.getValue\n\nval v: Used = TODO()\n", out)
    }

    @Test
    fun `Alias wird ueber Aliasnamen geprueft`() {
        val src = "import a.B as C\nimport a.D as E\n\nval x = C()\n"
        assertEquals("import a.B as C\n\nval x = C()\n", KotlinImportOrganizer.organize(src))
    }

    @Test
    fun `ohne Package-Zeile bleibt Inhalt erhalten`() {
        val src = "import b.B\nimport a.A\n\nclass K : A, B\n"
        assertEquals("import a.A\nimport b.B\n\nclass K : A, B\n", KotlinImportOrganizer.organize(src))
    }

    @Test
    fun `ohne removeUnused bleiben ungenutzte Imports`() {
        val src = "import b.B\nimport a.A\n\nclass K\n"
        assertEquals("import a.A\nimport b.B\n\nclass K\n", KotlinImportOrganizer.organize(src, removeUnused = false))
    }

    @Test
    fun `alle Imports unbenutzt leert nur den Block`() {
        val src = "package a\n\nimport x.A\n\nclass K\n"
        assertEquals("package a\n\n\nclass K\n", KotlinImportOrganizer.organize(src))
    }

    @Test
    fun `bereits sauber oder ohne Imports liefert null`() {
        assertNull(KotlinImportOrganizer.organize("package a\n\nimport a.A\n\nval x: A? = null\n"))
        assertNull(KotlinImportOrganizer.organize("package a\n\nclass K\n"))
        assertNull(KotlinImportOrganizer.organize(""))
    }

    @Test
    fun `CRLF bleibt erhalten`() {
        val src = "import b.B\r\nimport a.A\r\n\r\nval x: A = B()\r\n"
        assertEquals("import a.A\r\nimport b.B\r\n\r\nval x: A = B()\r\n", KotlinImportOrganizer.organize(src))
    }

    private fun insert(src: String, fqn: String): String? =
        KotlinImportOrganizer.insertionFor(src, fqn)?.let { (o, t) -> src.substring(0, o) + t + src.substring(o) }

    @Test
    fun `insertionFor sortiert in vorhandenen Block ein`() {
        val src = "package a\n\nimport a.A\nimport c.C\n\nclass K\n"
        assertEquals("package a\n\nimport a.A\nimport b.B\nimport c.C\n\nclass K\n", insert(src, "b.B"))
        assertEquals("package a\n\nimport a.A\nimport c.C\nimport d.D\n\nclass K\n", insert(src, "d.D"))
    }

    @Test
    fun `insertionFor ohne Block nach package oder am Dateianfang`() {
        assertEquals("package a\n\nimport b.B\n\nclass K\n", insert("package a\n\nclass K\n", "b.B"))
        assertEquals("import b.B\n\nclass K\n", insert("class K\n", "b.B"))
    }

    @Test
    fun `insertionFor liefert null wenn schon importiert`() {
        assertNull(insert("import b.B\nclass K\n", "b.B"))
        assertNull(insert("import b.*\nclass K\n", "b.B"))
        assertNull(insert("package b\nclass K\n", "b.B"))
    }
}
