// Modul: :feature:editor
package com.codeforge.feature.editor.overlay

import androidx.compose.runtime.Immutable
import java.io.File

/** Womit ein Modul Screenshot-Tests ausführt. */
enum class ScreenshotTool {
    /** Keine Screenshot-Tests eingerichtet. */
    NONE,

    /** Roborazzi (Robolectric, normale Unit-Test-Tasks; Filter mit `--tests` möglich). */
    ROBORAZZI,

    /** Eigenständiges Plugin `com.android.compose.screenshot` (von Google nicht mehr gepflegt). */
    COMPOSE_PLUGIN,

    /** AGP-Test-Suites (`testOptions { screenshotTests.create("…") }`, ab AGP 9.5). */
    TEST_SUITE
}

/** Gradle-Modul einer Datei samt Screenshot-Einrichtung. */
@Immutable
data class TestModule(
    val gradlePath: String,
    val dir: File,
    val screenshotTool: ScreenshotTool,
    /** Name der Screenshot-Suite (nur [ScreenshotTool.TEST_SUITE]). */
    val suiteName: String? = null,
    /** Ziel-Variante der Suite, z. B. `demoDebug` (nur [ScreenshotTool.TEST_SUITE]). */
    val suiteVariant: String = "debug"
)

/** Was ein Tipp auf das Test-Symbol im Rand auslösen kann. */
enum class TestGutterAction {
    /** Unit-Test (bzw. Roborazzi-Test im Prüfmodus) ausführen. */
    RUN,

    /** Screenshots mit den Referenzbildern vergleichen. */
    VALIDATE_SCREENSHOTS,

    /** Referenzbilder neu aufnehmen. */
    UPDATE_REFERENCES,

    /** Ergebnisbilder (Referenz, Tatsächlich, Unterschied) ansehen. */
    SHOW_RESULTS,

    /** HTML-Bericht öffnen. */
    OPEN_REPORT
}

/** Findet das Gradle-Modul einer Datei und dessen Screenshot-Werkzeug. */
object TestModuleResolver {

    private val BUILD_FILES = listOf("build.gradle.kts", "build.gradle")

    /** Modul der Datei [filePath] innerhalb von [projectRoot]; `null` außerhalb eines Moduls. */
    fun resolve(projectRoot: File, filePath: String): TestModule? {
        val root = projectRoot.canonicalFile
        val dirs = generateSequence(File(filePath).canonicalFile.parentFile) { it.parentFile.takeIf { _ -> it != root } }
            .takeWhile { it.path.startsWith(root.path) }
        for (dir in dirs) {
            val build = BUILD_FILES.map { File(dir, it) }.firstOrNull { it.isFile } ?: continue
            val rel = dir.relativeTo(root).invariantSeparatorsPath
            return describe(if (rel.isEmpty()) ":" else ":" + rel.replace('/', ':'), dir, build.readText())
        }
        return null
    }

    /** Liest das Screenshot-Werkzeug aus dem Text der Build-Datei. */
    fun describe(gradlePath: String, dir: File, buildText: String): TestModule {
        val suite = Regex("""screenshotTests\s*\.\s*(?:create|register)\s*\(\s*"([^"]+)"""").find(buildText)
        if (suite != null) {
            val variant = Regex("""targetVariants\s*\.\s*add\s*\(\s*"([^"]+)"""").find(buildText)?.groupValues?.get(1)
            return TestModule(gradlePath, dir, ScreenshotTool.TEST_SUITE, suite.groupValues[1], variant ?: "debug")
        }
        val tool = when {
            "com.android.compose.screenshot" in buildText ||
                Regex("""libs\.plugins\.[\w.]*screenshot\b""").containsMatchIn(buildText) -> ScreenshotTool.COMPOSE_PLUGIN
            "roborazzi" in buildText.lowercase() -> ScreenshotTool.ROBORAZZI
            else -> ScreenshotTool.NONE
        }
        return TestModule(gradlePath, dir, tool)
    }
}

/** Geöffnetes Aktionsblatt eines Tests aus der Randspalte. */
@Immutable
data class TestActionsState(
    val target: TestTarget,
    val module: TestModule?,
    val actions: List<TestGutterAction>,
    /** Ergebnisbilder (absolute Pfade); `null` = nicht angefordert. */
    val images: List<String>? = null
)

/** Gradle-Aufrufe und Ergebnisorte je Screenshot-Werkzeug. */
object TestCommands {

    private const val MAX_IMAGES = 30

    /** Aktionen, die das Menü zu [target] in [module] anbietet. */
    fun actionsFor(target: TestTarget, module: TestModule?): List<TestGutterAction> {
        val tool = module?.screenshotTool ?: ScreenshotTool.NONE
        if (!target.screenshot || tool == ScreenshotTool.NONE) return listOf(TestGutterAction.RUN)
        return buildList {
            if (tool == ScreenshotTool.ROBORAZZI) add(TestGutterAction.RUN)
            add(TestGutterAction.VALIDATE_SCREENSHOTS)
            add(TestGutterAction.UPDATE_REFERENCES)
            add(TestGutterAction.SHOW_RESULTS)
            add(TestGutterAction.OPEN_REPORT)
        }
    }

    /**
     * Gradle-Aufruf für [action] (ohne `./gradlew`), `null` bei reinen Anzeige-Aktionen.
     * Das Screenshot-Plugin und die Test-Suites dokumentieren keinen Filter für einzelne Tests: sie laufen für das
     * ganze Modul. Roborazzi nutzt die normalen Unit-Test-Tasks und filtert mit `--tests`.
     */
    fun command(action: TestGutterAction, target: TestTarget, module: TestModule): String? {
        val p = if (module.gradlePath == ":") "" else module.gradlePath
        val tests = " --tests \"${target.filterTarget}\""
        return when (module.screenshotTool) {
            ScreenshotTool.ROBORAZZI -> when (action) {
                TestGutterAction.RUN -> "$p:testDebugUnitTest$tests"
                TestGutterAction.VALIDATE_SCREENSHOTS -> "$p:verifyRoborazziDebug$tests"
                TestGutterAction.UPDATE_REFERENCES -> "$p:recordRoborazziDebug$tests"
                else -> null
            }
            ScreenshotTool.COMPOSE_PLUGIN -> when (action) {
                TestGutterAction.VALIDATE_SCREENSHOTS -> "$p:validateDebugScreenshotTest"
                TestGutterAction.UPDATE_REFERENCES -> "$p:updateDebugScreenshotTest"
                else -> null
            }
            ScreenshotTool.TEST_SUITE -> when (action) {
                TestGutterAction.VALIDATE_SCREENSHOTS -> "$p:test${suiteTaskName(module)}"
                TestGutterAction.UPDATE_REFERENCES -> "$p:update${suiteTaskName(module)}"
                else -> null
            }
            ScreenshotTool.NONE -> null
        }
    }

    /** `{Suite}Default{Variant}TestSuite`, z. B. `ScreenshotTestDefaultDemoDebugTestSuite`. */
    fun suiteTaskName(module: TestModule): String =
        "${module.suiteName.orEmpty().cap()}Default${module.suiteVariant.cap()}TestSuite"

    /** HTML-Bericht des letzten Laufs, falls vorhanden. */
    fun report(module: TestModule): File? {
        val build = File(module.dir, "build/reports")
        val candidates = when (module.screenshotTool) {
            ScreenshotTool.ROBORAZZI -> listOf("roborazzi/index.html", "tests/testDebugUnitTest/index.html")
            ScreenshotTool.COMPOSE_PLUGIN -> listOf("screenshotTest/preview/debug/index.html", "screenshotTest/debug/index.html")
            ScreenshotTool.TEST_SUITE -> listOf("tests/test${suiteTaskName(module)}/index.html")
            ScreenshotTool.NONE -> listOf("tests/testDebugUnitTest/index.html")
        }
        return candidates.map { File(build, it) }.firstOrNull { it.isFile }
    }

    /**
     * Ergebnisbilder des letzten Laufs, neueste zuerst: Vergleichs- und Unterschiedsbilder vor den übrigen,
     * passend zu [target] (Klassen- oder Funktionsname im Dateinamen), wenn es solche gibt.
     */
    fun resultImages(module: TestModule, target: TestTarget, limit: Int = MAX_IMAGES): List<File> {
        val roots = listOf("build/outputs/roborazzi", "build/outputs/screenshotTest-results", "build/outputs/screenshotTest")
            .map { File(module.dir, it) } + referenceDirs(module)
        val all = roots.filter { it.isDirectory }
            .flatMap { dir -> dir.walkTopDown().filter { it.isFile && it.extension.equals("png", true) }.toList() }
            .distinct()
        val key = target.filterTarget.substringAfterLast('.').lowercase()
        val matching = all.filter { key.isNotEmpty() && key in it.name.lowercase() }.ifEmpty { all }
        return matching.sortedWith(
            compareByDescending<File> { isComparison(it) }.thenByDescending { it.lastModified() }
        ).take(limit)
    }

    private fun referenceDirs(module: TestModule): List<File> = when (module.screenshotTool) {
        ScreenshotTool.COMPOSE_PLUGIN -> listOf(File(module.dir, "src/screenshotTestDebug/reference"))
        ScreenshotTool.TEST_SUITE -> listOf(
            File(module.dir, "src/${module.suiteName}Default${module.suiteVariant.cap()}/reference")
        )
        else -> emptyList()
    }

    private fun isComparison(file: File): Boolean {
        val n = file.name.lowercase()
        return "compare" in n || "diff" in n || "actual" in n
    }

    private fun String.cap() = replaceFirstChar { it.uppercaseChar() }
}
