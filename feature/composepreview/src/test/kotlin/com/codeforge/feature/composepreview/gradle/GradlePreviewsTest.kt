package com.codeforge.feature.composepreview.gradle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class GradlePreviewsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun project(): Pair<File, File> {
        val root = tmp.newFolder("proj")
        File(root, "settings.gradle.kts").writeText("include(\":feature:home\")")
        File(root, "build.gradle.kts").writeText("")
        val module = File(root, "feature/home").apply { mkdirs() }
        File(module, "build.gradle.kts").writeText("")
        val src = File(module, "src/main/kotlin/com/example/Home.kt").apply { parentFile.mkdirs(); writeText("") }
        return module to src
    }

    @Test
    fun locatesModuleAndBuildsCommand() {
        val (_, src) = project()
        val module = GradlePreviews.locate(src.path)!!
        assertEquals(":feature:home", module.gradlePath)
        assertEquals(
            ":feature:home:composePreviewRenderAll -PcomposePreview.tier=fast --init-script ${GradlePreviews.INIT_SCRIPT}",
            GradlePreviews.renderCommand(module)
        )
        assertEquals("composePreviewRenderAll -PcomposePreview.tier=full --init-script ${GradlePreviews.INIT_SCRIPT}",
            GradlePreviews.renderCommand(module.copy(gradlePath = ":"), full = true))
        assertNull(GradlePreviews.locate(tmp.newFolder("loose").resolve("A.kt").path))
    }

    @Test
    fun readsManifestForFile() {
        val (moduleDir, src) = project()
        val out = File(moduleDir, "build/compose-previews").apply { mkdirs() }
        File(out, "renders").mkdirs()
        File(out, "renders/HomePreview-1a2b3c4d.png").writeBytes(byteArrayOf(1))
        File(out, "previews.json").writeText(
            """
                {"schema":"compose-previews/v1","module":":feature:home","variant":"debug","previews":[
                  {"id":"com.example.HomeKt.HomePreview","functionName":"HomePreview","className":"com.example.HomeKt",
                   "sourceFile":"Home.kt","params":{"name":"Dark"},"captures":[{"renderOutput":"renders/HomePreview-1a2b3c4d.png"}]},
                  {"id":"com.example.HomeKt.Missing","functionName":"Missing","className":"com.example.HomeKt",
                   "sourceFile":"Home.kt","captures":[{"renderOutput":"renders/Missing-00000000.png"}]},
                  {"id":"com.other.OtherKt.X","functionName":"X","className":"com.other.OtherKt","sourceFile":"Home.kt"},
                  {"id":"com.example.ListKt.Y","functionName":"Y","className":"com.example.ListKt","sourceFile":"List.kt"}
                ]}
            """.trimIndent()
        )
        val module = GradlePreviews.locate(src.path)!!
        val previews = GradlePreviews.previewsFor(module, src.path, "com.example")
        assertEquals(listOf("HomePreview", "Missing"), previews.map { it.functionName })
        assertEquals("Dark", previews[0].variant)
        assertEquals(File(out, "renders/HomePreview-1a2b3c4d.png").absolutePath, previews[0].image)
        assertNull(previews[1].image)
    }

    @Test
    fun packageAndBrokenManifest() {
        assertEquals("com.example.ui", GradlePreviews.packageOf(
            """
                // x
                package com.example.ui

                import a.B
            """.trimIndent()
        ))
        assertEquals(emptyList<GradlePreviewImage>(), GradlePreviews.parse("{kaputt", File("."), "A.kt", ""))
    }
}
