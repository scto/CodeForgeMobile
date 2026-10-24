package com.codeforge.feature.layoutdesigner.res

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ResourceRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun qualifierMatching_selectsBestMatchingLanguageAndNight() {
        val resDir = tempFolder.newFolder("res")
        val values = File(resDir, "values").apply { mkdirs() }
        val valuesDe = File(resDir, "values-de").apply { mkdirs() }
        val valuesNight = File(resDir, "values-night").apply { mkdirs() }

        File(values, "strings.xml").writeText(
            """<resources>
                <string name="hello">Hello</string>
                <color name="bg">#FFFFFF</color>
            </resources>"""
        )
        File(valuesDe, "strings.xml").writeText(
            """<resources>
                <string name="hello">Hallo</string>
            </resources>"""
        )
        File(valuesNight, "colors.xml").writeText(
            """<resources>
                <color name="bg">#000000</color>
            </resources>"""
        )

        val repo = ResourceRepository(listOf(resDir))

        // Standard: English, Day
        assertEquals("Hello", repo.resolveString("@string/hello", ResourceConfig(language = "en", isNight = false)))
        assertEquals(0xFFFFFFFFL, repo.resolveColor("@color/bg", ResourceConfig(isNight = false)))

        // German
        assertEquals("Hallo", repo.resolveString("@string/hello", ResourceConfig(language = "de", isNight = false)))

        // Night
        assertEquals(0xFF000000L, repo.resolveColor("@color/bg", ResourceConfig(isNight = true)))
    }

    @Test
    fun referenceChains_resolvedSuccessfully() {
        val resDir = tempFolder.newFolder("res")
        val values = File(resDir, "values").apply { mkdirs() }

        File(values, "colors.xml").writeText(
            """<resources>
                <color name="c1">#FF0000</color>
                <color name="c2">@color/c1</color>
                <color name="c3">@color/c2</color>
            </resources>"""
        )

        val repo = ResourceRepository(listOf(resDir))
        assertEquals(0xFFFF0000L, repo.resolveColor("@color/c3"))
    }

    @Test
    fun referenceChains_cycleDetectedWithoutInfiniteLoop() {
        val resDir = tempFolder.newFolder("res")
        val values = File(resDir, "values").apply { mkdirs() }

        File(values, "strings.xml").writeText(
            """<resources>
                <string name="a">@string/b</string>
                <string name="b">@string/a</string>
            </resources>"""
        )

        val repo = ResourceRepository(listOf(resDir))
        assertNull(repo.resolveString("@string/a"))
    }

    @Test
    fun styleInheritance_resolvesAttributesFromParent() {
        val resDir = tempFolder.newFolder("res")
        val values = File(resDir, "values").apply { mkdirs() }

        File(values, "styles.xml").writeText(
            """<resources>
                <style name="BaseTheme">
                    <item name="android:textColor">#111111</item>
                    <item name="android:textSize">14sp</item>
                </style>
                <style name="ChildTheme" parent="BaseTheme">
                    <item name="android:textSize">18sp</item>
                </style>
            </resources>"""
        )

        val repo = ResourceRepository(listOf(resDir))
        assertEquals("18sp", repo.resolveStyleAttr("ChildTheme", "textSize"))
        assertEquals("#111111", repo.resolveStyleAttr("ChildTheme", "textColor"))
    }

    @Test
    fun themeAttributes_resolvedFromBuiltInMaterialTable() {
        val repo = ResourceRepository(emptyList())
        assertNotNull(repo.resolveThemeColor("?attr/colorPrimary", isDark = false))
        assertNotNull(repo.resolveThemeColor("?attr/colorPrimary", isDark = true))
    }

    @Test
    fun extractToResource_writesToFileAndReloads() {
        val resDir = tempFolder.newFolder("res")
        val repo = ResourceRepository(listOf(resDir))

        val success = repo.extractToResource(resDir, ResType.STRING, "extracted_title", "Mein Titel")
        assertTrue(success)

        assertEquals("Mein Titel", repo.resolveString("@string/extracted_title"))
        val stringsXml = File(resDir, "values/strings.xml")
        assertTrue(stringsXml.exists())
        assertTrue(stringsXml.readText().contains("extracted_title"))
    }
}
