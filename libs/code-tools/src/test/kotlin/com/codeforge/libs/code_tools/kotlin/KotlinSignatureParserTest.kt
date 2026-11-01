package com.codeforge.libs.code_tools.kotlin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KotlinSignatureParserTest {

    @Test
    fun `Funktion mit Parametern, Default und Rueckgabetyp`() {
        val s = KotlinSignatureParser.parse(
            "public inline fun <T> List<T>.joinTo(sep: CharSequence = \", \", transform: ((T) -> CharSequence)? = null, vararg x: Map<String, Int>): String"
        )!!
        assertEquals("fun", s.kind)
        assertEquals(listOf("public", "inline"), s.modifiers)
        assertEquals("joinTo", s.name)
        assertEquals("List<T>", s.receiver)
        assertEquals(3, s.params.size)
        assertEquals(KotlinParam("sep", "CharSequence", "\", \""), s.params[0])
        assertEquals("((T) -> CharSequence)?", s.params[1].type)
        assertEquals("null", s.params[1].default)
        assertEquals("vararg", s.params[2].modifiers)
        assertEquals("Map<String, Int>", s.params[2].type)
        assertEquals("String", s.returnType)
    }

    @Test
    fun `Funktion ohne Rueckgabetyp ist Unit`() {
        assertEquals("Unit", KotlinSignatureParser.parse("fun run()")!!.returnType)
    }

    @Test
    fun `val und var werden unterschieden`() {
        val v = KotlinSignatureParser.parse("val size: Int")!!
        assertEquals(false, v.isMutable)
        assertEquals("Int", v.returnType)
        val m = KotlinSignatureParser.parse("lateinit var name: String?")!!
        assertEquals(true, m.isMutable)
        assertEquals("String?", m.returnType)
        assertEquals(listOf("lateinit"), m.modifiers)
    }

    @Test
    fun `Erweiterungsproperty`() {
        val s = KotlinSignatureParser.parse("val <T> List<T>.lastIndex: Int")!!
        assertEquals("lastIndex", s.name)
        assertEquals("List<T>", s.receiver)
    }

    @Test
    fun `Package wird erkannt`() {
        assertEquals("kotlin.collections", KotlinSignatureParser.packageOf("package kotlin.collections\nfun x()"))
        assertEquals("kotlin.text", KotlinSignatureParser.packageOf("Defined in `kotlin.text`".lowercase().replace("defined", "defined")))
        assertNull(KotlinSignatureParser.packageOf("nur Text"))
    }

    @Test
    fun `kein Kotlin liefert null`() {
        assertNull(KotlinSignatureParser.parse("hello world"))
        assertTrue(KotlinSignatureParser.parse("class Foo")!!.name == "Foo")
    }
}
