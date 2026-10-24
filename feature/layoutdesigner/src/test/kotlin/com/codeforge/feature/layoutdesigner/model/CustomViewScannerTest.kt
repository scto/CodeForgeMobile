package com.codeforge.feature.layoutdesigner.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CustomViewScannerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun scanDirectory_detectsKotlinAndJavaViews() {
        val srcDir = tempFolder.newFolder("src", "main", "kotlin", "com", "example")

        val ktFile = File(srcDir, "CustomBadge.kt")
        ktFile.writeText(
            """
            package com.example

            import android.view.View
            import android.content.Context

            class CustomBadge(context: Context) : View(context)
            class CustomCard(context: Context) : FrameLayout(context)
            class NonViewHelper
            """.trimIndent()
        )

        val javaDir = tempFolder.newFolder("src", "main", "java", "com", "example", "java")
        val javaFile = File(javaDir, "JavaCustomButton.java")
        javaFile.writeText(
            """
            package com.example.java;

            import android.widget.Button;

            public class JavaCustomButton extends Button {
            }
            """.trimIndent()
        )

        val scanner = CustomViewScanner()
        val views = scanner.scanDirectory(tempFolder.root)

        assertEquals(3, views.size)

        val badge = views.find { it.simpleName == "CustomBadge" }!!
        assertEquals("com.example.CustomBadge", badge.qualifiedName)
        assertFalse(badge.isViewGroup)

        val card = views.find { it.simpleName == "CustomCard" }!!
        assertEquals("com.example.CustomCard", card.qualifiedName)
        assertTrue(card.isViewGroup)

        val javaBtn = views.find { it.simpleName == "JavaCustomButton" }!!
        assertEquals("com.example.java.JavaCustomButton", javaBtn.qualifiedName)
        assertFalse(javaBtn.isViewGroup)
    }
}
