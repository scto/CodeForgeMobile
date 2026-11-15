package com.codeforge.core.domain.extension

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginSettingsTest {

    private fun manifest(settings: String) = """
        {"id":"p","name":"P","version":"1","contributes":{"settings":$settings}}
    """.trimIndent()

    @Test
    fun parsesAllTypes() {
        val parsed = PluginManifestParser.parse(
            manifest(
                """
                    {"title":"T","items":[
                    {"key":"name","type":"text","title":"Name","default":"x"},
                    {"key":"on","type":"boolean"},
                    {"key":"mode","type":"choice","options":[{"value":"a","label":"A"},{"value":"b"}],"default":"b"},
                    {"key":"n","type":"number","min":1,"max":5,"default":3}]}
                """.trimIndent()
            )
        ).getOrThrow().contributes.settings!!
        assertEquals("T", parsed.title)
        assertEquals(listOf("x", "false", "b", "3"), parsed.items.map { it.default })
        assertEquals("b", parsed.items[2].options[1].label)
    }

    @Test
    fun rejectsInvalid() {
        val bad = listOf(
            """{"items":[{"key":"1x","type":"text"}]}""",
            """{"items":[{"key":"a","type":"color"}]}""",
            """{"items":[{"key":"a","type":"choice"}]}""",
            """{"items":[{"key":"a","type":"number","min":1,"max":2,"default":9}]}""",
            """{"items":[{"key":"a","type":"text"},{"key":"a","type":"text"}]}"""
        )
        bad.forEach { assertTrue(it, PluginManifestParser.parse(manifest(it)).isFailure) }
    }

    @Test
    fun normalizeAndPlaceholders() {
        val n = PluginSettingItem("n", PluginSettingType.NUMBER, "N", default = "3", min = 1, max = 5)
        assertEquals("5", n.normalize("99"))
        assertEquals("3", n.normalize("abc"))
        val b = PluginSettingItem("b", PluginSettingType.BOOLEAN, "B", default = "false")
        assertEquals("false", b.normalize("ja"))
        val values = listOf(n, b).effectiveValues(mapOf("n" to "2"))
        assertEquals(mapOf("n" to "2", "b" to "false"), values)
        assertEquals("--n=2 --x=", resolveSettingPlaceholders("--n=\${setting:n} --x=\${setting:x}", values))
    }
}
