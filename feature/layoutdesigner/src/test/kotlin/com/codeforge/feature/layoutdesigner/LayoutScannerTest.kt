package com.codeforge.feature.layoutdesigner

import com.codeforge.feature.layoutdesigner.files.LayoutScanner
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutScannerTest {

    private fun tmp(): File = java.nio.file.Files.createTempDirectory("ld").toFile()

    @Test fun findsLayoutsAndResDirsAndSkipsBuild() {
        val root = tmp()
        File(root, "app/src/main/res/layout").mkdirs()
        File(root, "app/src/main/res/layout-land").mkdirs()
        File(root, "app/src/main/res/values").mkdirs()
        File(root, "app/build/intermediates/res/layout").mkdirs()
        File(root, "lib/src/main/res/layout").mkdirs()
        File(root, "app/src/main/res/layout/activity_main.xml").writeText("<a/>")
        File(root, "app/src/main/res/layout-land/activity_main.xml").writeText("<a/>")
        File(root, "app/src/main/res/layout/readme.txt").writeText("x")
        File(root, "app/src/main/res/values/strings.xml").writeText("<resources/>")
        File(root, "app/build/intermediates/res/layout/generated.xml").writeText("<a/>")
        File(root, "lib/src/main/res/layout/item.xml").writeText("<a/>")

        val layouts = LayoutScanner.findLayouts(root).map { it.relativePath }
        assertEquals(
            listOf(
                "app/src/main/res/layout-land/activity_main.xml",
                "app/src/main/res/layout/activity_main.xml",
                "lib/src/main/res/layout/item.xml",
            ),
            layouts,
        )
        assertEquals(listOf("app/src/main/res", "lib/src/main/res"), LayoutScanner.findResDirs(root))
        root.deleteRecursively()
    }

    @Test fun nameValidation() {
        assertTrue(LayoutScanner.nameRegex.matches("activity_main"))
        assertTrue(LayoutScanner.nameRegex.matches("a1"))
        assertFalse(LayoutScanner.nameRegex.matches("Activity"))
        assertFalse(LayoutScanner.nameRegex.matches("1abc"))
        assertFalse(LayoutScanner.nameRegex.matches("my-layout"))
        assertFalse(LayoutScanner.nameRegex.matches(""))
    }

    @Test fun templateIsValidLayout() {
        val doc = com.codeforge.feature.layoutdesigner.xml.LayoutXml.parse(LayoutScanner.TEMPLATE)
        assertEquals("LinearLayout", doc.root.tag)
    }
}
