package com.codeforge.feature.editor.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TestModulesTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val shotTarget = TestTarget(3, "home", "com.example.HomeScreenshotTest.home", isClass = false, screenshot = true)

    private fun project(moduleBuild: String): Pair<File, File> {
        val root = tmp.newFolder("proj")
        File(root, "settings.gradle.kts").writeText("include(\":feature:home\")")
        File(root, "build.gradle.kts").writeText("plugins { }")
        val module = File(root, "feature/home").apply { mkdirs() }
        File(module, "build.gradle.kts").writeText(moduleBuild)
        val src = File(module, "src/test/kotlin/com/example/HomeScreenshotTest.kt").apply { parentFile.mkdirs(); writeText("") }
        return root to src
    }

    @Test
    fun nestedModuleRoborazzi() {
        val (root, src) = project("plugins {\n    alias(libs.plugins.roborazzi)\n}")
        val module = TestModuleResolver.resolve(root, src.path)!!
        assertEquals(":feature:home", module.gradlePath)
        assertEquals(ScreenshotTool.ROBORAZZI, module.screenshotTool)
        assertEquals(
            ":feature:home:recordRoborazziDebug --tests \"com.example.HomeScreenshotTest.home\"",
            TestCommands.command(TestGutterAction.UPDATE_REFERENCES, shotTarget, module)
        )
        assertEquals(TestGutterAction.RUN, TestCommands.actionsFor(shotTarget, module).first())
    }

    @Test
    fun composePluginRunsWholeModule() {
        val module = TestModuleResolver.describe(":app", File("."), "plugins { id(\"com.android.compose.screenshot\") }")
        assertEquals(ScreenshotTool.COMPOSE_PLUGIN, module.screenshotTool)
        assertEquals(":app:validateDebugScreenshotTest", TestCommands.command(TestGutterAction.VALIDATE_SCREENSHOTS, shotTarget, module))
        assertEquals(":app:updateDebugScreenshotTest", TestCommands.command(TestGutterAction.UPDATE_REFERENCES, shotTarget, module))
        assertTrue(TestGutterAction.RUN !in TestCommands.actionsFor(shotTarget, module))
    }

    @Test
    fun testSuiteTaskNames() {
        val build = """
            android {
                testOptions {
                    screenshotTests.create("screenshotTest") {
                        targetVariants.add("demoDebug")
                    }
                }
            }
        """.trimIndent()
        val module = TestModuleResolver.describe(":app", File("."), build)
        assertEquals(ScreenshotTool.TEST_SUITE, module.screenshotTool)
        assertEquals(":app:updateScreenshotTestDefaultDemoDebugTestSuite", TestCommands.command(TestGutterAction.UPDATE_REFERENCES, shotTarget, module))
        assertEquals(":app:testScreenshotTestDefaultDemoDebugTestSuite", TestCommands.command(TestGutterAction.VALIDATE_SCREENSHOTS, shotTarget, module))
    }

    @Test
    fun unitTestsOnlyRun() {
        val unit = shotTarget.copy(screenshot = false)
        val module = TestModuleResolver.describe(":lib", File("."), "plugins { }")
        assertEquals(listOf(TestGutterAction.RUN), TestCommands.actionsFor(unit, module))
        assertEquals(listOf(TestGutterAction.RUN), TestCommands.actionsFor(shotTarget, null))
    }

    @Test
    fun resultImagesComparisonsFirst() {
        val dir = tmp.newFolder("mod")
        val out = File(dir, "build/outputs/roborazzi").apply { mkdirs() }
        File(out, "other.png").writeBytes(byteArrayOf(1))
        File(out, "home_compare.png").writeBytes(byteArrayOf(1))
        File(out, "home.png").writeBytes(byteArrayOf(1))
        val module = TestModule(":mod", dir, ScreenshotTool.ROBORAZZI)
        val images = TestCommands.resultImages(module, shotTarget).map { it.name }
        assertEquals(listOf("home_compare.png", "home.png"), images)
    }

    @Test
    fun reportMissingIsNull() {
        val module = TestModule(":mod", tmp.newFolder("empty"), ScreenshotTool.COMPOSE_PLUGIN)
        assertNull(TestCommands.report(module))
    }

    @Test
    fun scannerMarksScreenshotTests() {
        val preview = """
            package com.example
            @PreviewTest
            @Preview(showBackground = true)
            @Composable
            fun GreetingPreview() { }
        """.trimIndent()
        val t = TestTargetScanner.scan("ExamplePreviewScreenshotTest.kt", preview).single()
        assertTrue(t.screenshot)
        assertEquals(TestIconKind.SCREENSHOT, iconKind(t))

        val robo = """
            package com.example
            class HomeShots {
                @Test
                fun home() { composeRule.onRoot().captureRoboImage() }
            }
        """.trimIndent()
        val targets = TestTargetScanner.scan("HomeShots.kt", robo)
        assertTrue(targets.all { it.screenshot })
        assertEquals(TestIconKind.RUN, iconKind(targets.first().copy(screenshot = false)))
        assertEquals(TestIconKind.FAILED, iconKind(targets.first().copy(screenshot = false, passed = false)))
    }
}
