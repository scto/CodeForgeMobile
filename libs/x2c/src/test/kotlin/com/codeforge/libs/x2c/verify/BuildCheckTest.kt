package com.codeforge.libs.x2c.verify

import com.codeforge.core.domain.model.X2cBuildProblem
import com.codeforge.core.domain.model.X2cBuildStepResult
import com.codeforge.core.domain.model.X2cBuildStepStatus
import com.codeforge.core.domain.model.X2cOptions
import com.codeforge.core.domain.repository.X2cBuildChecker
import com.codeforge.libs.x2c.X2cRepositoryImpl
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class BuildCheckTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun gradlew(dir: File, body: String) = File(dir, "gradlew").writeText("#!/bin/sh\n$body\n")

    // ---------------------------------------------------------------- Parser

    @Test fun `Parser liest Kotlin Java und AAPT mit relativen Pfaden`() {
        val dir = tmp.newFolder("p")
        val f = File(dir, "app/src/main/java/A.kt").apply { parentFile.mkdirs(); writeText("x") }
        val log = """
            > Task :app:compileDebugKotlin FAILED
            e: file://${f.path}:12:5 Unresolved reference: foo
            e: ${f.path}: (20, 3): Type mismatch
            w: ${f.path}:7:1 Parameter 'x' is never used
            ${dir.path}/app/src/main/java/B.java:30: error: cannot find symbol
            ERROR: ${dir.path}/app/src/main/res/layout/x.xml:4:2: AAPT: error: attribute foo not found.
            * What went wrong:
            Execution failed for task ':app:compileDebugKotlin'.
            > Compilation error. See log for more details
        """.trimIndent()
        val p = GradleLogParser.parse(log, dir)
        val e = p.first { it.message == "Unresolved reference: foo" }
        assertEquals("app/src/main/java/A.kt", e.path); assertEquals(12, e.line); assertEquals(5, e.column); assertEquals("error", e.severity)
        assertEquals(":app:compileDebugKotlin", e.task)
        assertTrue(p.any { it.message == "Type mismatch" && it.line == 20 && it.column == 3 })
        assertTrue(p.any { it.severity == "warning" && it.line == 7 })
        assertTrue(p.any { it.path == "app/src/main/java/B.java" && it.line == 30 && "cannot find symbol" in it.message })
        assertTrue(p.any { it.path == "app/src/main/res/layout/x.xml" && it.line == 4 })
        assertTrue(p.any { it.path == null && it.message.startsWith("Execution failed") })
    }

    @Test fun `Parser ignoriert ANSI und liefert nichts bei Erfolg`() {
        assertTrue(GradleLogParser.parse("\u001B[32mBUILD SUCCESSFUL\u001B[0m in 3s", tmp.root).isEmpty())
    }

    // ---------------------------------------------------------------- Prozess

    @Test fun `ohne gradlew wird uebersprungen`() {
        val r = runBlocking { ProcessX2cBuildChecker().runTasks(tmp.newFolder("a").path, listOf("x")) }
        assertEquals(X2cBuildStepStatus.SKIPPED, r.status)
    }

    @Test fun `erfolgreicher Lauf`() {
        val d = tmp.newFolder("ok"); gradlew(d, "echo \"> Task \$3\"; echo BUILD SUCCESSFUL")
        val r = runBlocking { ProcessX2cBuildChecker().runTasks(d.path, listOf(":app:compileDebugKotlin")) }
        assertEquals(X2cBuildStepStatus.SUCCESS, r.status)
        assertTrue("BUILD SUCCESSFUL" in r.outputTail)
    }

    @Test fun `Fehlerlauf liefert Probleme mit Datei und Zeile`() {
        val d = tmp.newFolder("bad"); File(d, "A.kt").writeText("x")
        gradlew(d, "echo \"e: file://${d.path}/A.kt:3:4 Boom\"; exit 1")
        val r = runBlocking { ProcessX2cBuildChecker().runTasks(d.path, listOf("t")) }
        assertEquals(X2cBuildStepStatus.FAILED, r.status)
        assertEquals("A.kt", r.problems.single().path); assertEquals(3, r.problems.single().line)
        assertTrue("Code 1" in r.message!!)
    }

    @Test fun `Geheimnisse und Laenge der Ausgabe`() {
        val d = tmp.newFolder("sec")
        gradlew(d, "echo 'token=abc123SECRET'; echo 'ghp_ABCDEFGHIJKLMNOPQRSTUVWX'; i=0; while [ \$i -lt 500 ]; do echo zeile-\$i-xxxxxxxxxxxxxxxxxxxx; i=\$((i+1)); done")
        val r = runBlocking { ProcessX2cBuildChecker(maxTailChars = 2000).runTasks(d.path, listOf("t")) }
        assertTrue(r.outputTail.length <= 2010)
        assertTrue("zeile-499" in r.outputTail)
        val full = runBlocking { ProcessX2cBuildChecker().runTasks(d.path, listOf("t")) }.outputTail
        assertFalse("abc123SECRET" in full || "ghp_ABCDEF" in full)
        assertTrue("token=***" in full)
    }

    @Test fun `Abbruch beendet den Prozess`() {
        val d = tmp.newFolder("slow"); gradlew(d, "sleep 30")
        var n = 0
        val t0 = System.currentTimeMillis()
        val r = runBlocking { ProcessX2cBuildChecker().runTasks(d.path, listOf("t"), isCancelled = { ++n > 3 }) }
        assertEquals(X2cBuildStepStatus.FAILED, r.status); assertEquals("Abgebrochen", r.message)
        assertTrue(System.currentTimeMillis() - t0 < 10_000)
    }

    @Test fun `Scrubber`() {
        assertEquals("Authorization: ***", SecretScrubber.scrub("Authorization: Bearer abcdefghij1234"))
        assertEquals("harmlos", SecretScrubber.scrub("harmlos"))
    }

    // ---------------------------------------------------------------- Gesamtlauf mit Build-Prüfung und Bericht

    private class FakeChecker(val results: (List<String>) -> X2cBuildStepResult) : X2cBuildChecker {
        val calls = mutableListOf<List<String>>()
        override suspend fun runTasks(projectPath: String, tasks: List<String>, onOutput: (String) -> Unit, isCancelled: () -> Boolean): X2cBuildStepResult {
            calls += tasks; return results(tasks)
        }
    }

    private fun project(): File {
        val p = tmp.newFolder("proj")
        fun w(rel: String, t: String) = File(p, rel).apply { parentFile.mkdirs(); writeText(t) }
        w("settings.gradle.kts", "include(\":app\")")
        w("build.gradle.kts", "plugins {\n id(\"com.android.application\") version \"8.7.0\" apply false\n id(\"org.jetbrains.kotlin.android\") version \"2.0.21\" apply false\n}\n")
        w("app/build.gradle.kts", "plugins {\n id(\"com.android.application\")\n id(\"org.jetbrains.kotlin.android\")\n}\nandroid {\n namespace = \"com.example.demo\"\n compileSdk = 34\n defaultConfig { minSdk = 24 }\n}\ndependencies {\n}\n")
        w("app/src/main/res/layout/activity_main.xml", "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\" android:layout_width=\"match_parent\" android:layout_height=\"match_parent\"><TextView android:layout_width=\"wrap_content\" android:layout_height=\"wrap_content\" android:text=\"@string/hello\"/></LinearLayout>")
        w("app/src/main/java/com/example/demo/MainActivity.kt", "package com.example.demo\n\nimport androidx.appcompat.app.AppCompatActivity\n\nclass MainActivity : AppCompatActivity() {\n    override fun onCreate(b: android.os.Bundle?) {\n        super.onCreate(b)\n        setContentView(R.layout.activity_main)\n    }\n}\n")
        w("app/src/test/java/com/example/demo/T.kt", "class T")
        return p
    }

    private val opts = X2cOptions(wireActivities = true, generateTheme = false, removeXmlAfterSuccess = false, runBuildCheck = true, includeGitInBackup = false)

    @Test fun `Build-Pruefung Erfolg und Bericht`() {
        val p = project()
        val fake = FakeChecker { X2cBuildStepResult(it, X2cBuildStepStatus.SUCCESS, outputTail = "BUILD SUCCESSFUL token=geheim") }
        val r = runBlocking { X2cRepositoryImpl(buildChecker = fake).executeMigration(p.path, opts) }
        assertEquals(listOf(listOf(":app:compileDebugKotlin"), listOf(":app:assembleDebug"), listOf(":app:testDebugUnitTest")), fake.calls)
        assertEquals(true, r.buildVerificationSuccess)
        assertFalse("geheim" in r.buildOutput!!)
        val report = File(r.reportPath!!)
        assertTrue(report.isFile && report.parentFile.name == "Backup" && report.name.startsWith("x2c-report-"))
        val md = report.readText()
        for (h in listOf("## Zusammenfassung", "## Layouts", "## Compose im Build", "## Stufe 2", "## Code, der noch auf Views zugreift", "## Backup und Journal", "## Build-Prüfung", "Verhalten bitte prüfen")) assertTrue(h, h in md)
        assertTrue("Compose BOM:" in md && "Kotlin:" in md)
        assertEquals(md, r.markdownSummary)
    }

    @Test fun `Build-Fehler bleibt sichtbar und folgende Schritte werden uebersprungen`() {
        val p = project()
        val fake = FakeChecker { t ->
            if (t.first().endsWith("compileDebugKotlin")) X2cBuildStepResult(t, X2cBuildStepStatus.FAILED,
                listOf(X2cBuildProblem("error", "app/src/main/java/com/example/demo/MainScreen.kt", 9, 1, "Unresolved reference: Foo", t.first())), "x", "Gradle endete mit Code 1")
            else X2cBuildStepResult(t, X2cBuildStepStatus.SUCCESS)
        }
        val r = runBlocking { X2cRepositoryImpl(buildChecker = fake).executeMigration(p.path, opts) }
        assertEquals(false, r.buildVerificationSuccess)
        assertEquals(1, fake.calls.size)
        assertEquals(listOf(X2cBuildStepStatus.FAILED, X2cBuildStepStatus.SKIPPED, X2cBuildStepStatus.SKIPPED), r.buildSteps.map { it.status })
        assertTrue("`app/src/main/java/com/example/demo/MainScreen.kt:9`: Unresolved reference: Foo" in File(r.reportPath!!).readText())
        assertTrue(File(r.journalPath).isFile)
        assertTrue(r.notes.any { "Rückgängig" in it })
    }

    @Test fun `Vorher auch bauen markiert bestehende Fehler`() {
        val p = project()
        val err = X2cBuildProblem("error", "app/Alt.kt", 1, 1, "alt kaputt")
        var first = true
        val fake = FakeChecker { t ->
            if (t.first().endsWith("compileDebugKotlin")) X2cBuildStepResult(t, X2cBuildStepStatus.FAILED, listOf(err), message = "Code 1").also { first = false }
            else X2cBuildStepResult(t, X2cBuildStepStatus.SUCCESS)
        }
        val r = runBlocking { X2cRepositoryImpl(buildChecker = fake).executeMigration(p.path, opts.copy(buildBaselineFirst = true)) }
        assertEquals(2, fake.calls.size) // vorher + nachher compile
        assertTrue(r.buildSteps.first().problems.single().preExisting)
        assertTrue(r.notes.any { "schon vor der Umwandlung" in it })
        assertFalse(first)
    }

    @Test fun `ohne runBuildCheck wird nicht gebaut`() {
        val p = project()
        val fake = FakeChecker { error("darf nicht laufen") }
        val r = runBlocking { X2cRepositoryImpl(buildChecker = fake).executeMigration(p.path, opts.copy(runBuildCheck = false)) }
        assertTrue(fake.calls.isEmpty()); assertNull(r.buildVerificationSuccess)
        assertTrue("Nicht ausgeführt" in File(r.reportPath!!).readText())
    }

    @Test fun `ohne gradlew bleibt Ergebnis offen und wird gemeldet`() {
        val p = project()
        val r = runBlocking { X2cRepositoryImpl().executeMigration(p.path, opts) }
        assertNull(r.buildVerificationSuccess)
        assertTrue(r.notes.any { "gradlew" in it })
        assertNotNull(r.reportPath)
    }
}
