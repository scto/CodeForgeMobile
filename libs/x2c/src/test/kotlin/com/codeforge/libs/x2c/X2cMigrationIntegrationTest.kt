package com.codeforge.libs.x2c

import com.codeforge.core.domain.model.X2cFileStatus
import com.codeforge.core.domain.model.X2cOptions
import com.codeforge.core.domain.model.X2cProgress
import com.codeforge.core.domain.repository.BuildEnableResult
import com.codeforge.core.domain.repository.ComposeBuildEnabler
import com.codeforge.libs.x2c.apply.ProcessGitBranchCommitter
import com.codeforge.libs.x2c.build.ComposeBuildEnablerImpl
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Task 52.9: ganzer Ablauf auf einem echten (temporären) Projekt, ohne Gradle. */
class X2cMigrationIntegrationTest {

    @get:Rule val tmp = TemporaryFolder()
    private lateinit var project: File
    private var n = 0

    private fun write(rel: String, text: String) = File(project, rel).apply { parentFile.mkdirs(); writeText(text) }
    private fun read(rel: String) = File(project, rel).readText()
    private fun snapshot(): Map<String, String> = project.walkTopDown()
        .onEnter { it.name != "Backup" && it.name != ".git" }
        .filter { it.isFile }.associate { it.relativeTo(project).path to it.readText() }

    @Before fun setUp() {
        project = tmp.newFolder("p${n++}")
        write("settings.gradle.kts", "include(\":app\")")
        write("build.gradle.kts", """plugins {
    id("com.android.application") version "8.7.0" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
}
""")
        write("app/build.gradle.kts", """plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.example.demo"
    compileSdk = 34
    defaultConfig {
        minSdk = 24
        targetSdk = 34
    }
}
dependencies {
}
""")
        write("app/src/main/AndroidManifest.xml", "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"><application/></manifest>")
        write("app/src/main/res/layout/activity_main.xml", """<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent" android:layout_height="match_parent" android:orientation="vertical">
    <TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="@string/hello"/>
    <Button android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="@string/go"/>
</LinearLayout>""")
        write("app/src/main/res/layout/activity_other.xml", """<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent" android:layout_height="match_parent">
    <TextView android:id="@+id/label" android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="@string/hello"/>
</LinearLayout>""")
        write("app/src/main/res/values/colors.xml", "<resources><color name=\"brand\">#6200EE</color></resources>")
        write("app/src/main/res/values/themes.xml", """<resources><style name="Theme.Demo" parent="Theme.MaterialComponents.DayNight.NoActionBar"><item name="colorPrimary">@color/brand</item></style></resources>""")
        write("app/src/main/java/com/example/demo/MainActivity.kt", """package com.example.demo

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }
}
""")
        write("app/src/main/java/com/example/demo/OtherActivity.kt", """package com.example.demo

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class OtherActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_other)
        findViewById<TextView>(R.id.label).text = "x"
    }
}
""")
    }

    private val all = X2cOptions(wireActivities = true, generateTheme = true, removeXmlAfterSuccess = true, runBuildCheck = false, includeGitInBackup = false)

    private fun run(options: X2cOptions = all, repo: X2cRepositoryImpl = X2cRepositoryImpl()) =
        runBlocking { repo.executeMigration(project.path, options) }

    @Test fun `voller Lauf und Rueckgaengig`() {
        val before = snapshot()
        val repo = X2cRepositoryImpl()
        val r = run(repo = repo)

        assertTrue(File(project, "app/src/main/java/com/example/demo/MainScreen.kt").isFile || File(project, "app/src/main/kotlin/com/example/demo/MainScreen.kt").isFile)
        val screen = project.walkTopDown().first { it.name == "MainScreen.kt" }.readText()
        assertTrue("package com.example.demo" in screen && "fun MainScreen(" in screen && "stringResource(R.string.hello)" in screen)
        assertTrue("setContent {" in read("app/src/main/java/com/example/demo/MainActivity.kt"))
        assertTrue("MainScreen()" in read("app/src/main/java/com/example/demo/MainActivity.kt"))
        assertTrue("findViewById" in read("app/src/main/java/com/example/demo/OtherActivity.kt"))
        assertFalse(File(project, "app/src/main/res/layout/activity_main.xml").exists())
        assertTrue(File(project, "app/src/main/res/layout/activity_other.xml").exists())
        assertTrue(project.walkTopDown().any { it.name == "Theme.kt" && "DemoTheme" in it.readText() })
        assertTrue("compose" in read("app/build.gradle.kts"))

        assertEquals(1, r.removedXmlCount)
        assertEquals(1, r.stage2WiredCount)
        assertTrue(r.themeGenerated)
        assertTrue(File(r.journalPath).isFile && File(r.backupPath).isFile)
        assertEquals(2, r.fileResults.count { it.status != X2cFileStatus.FAILED && it.outputComposePath != null })
        assertTrue(r.fileResults.none { it.status == X2cFileStatus.FAILED })
        assertTrue(repo.progress.value is X2cProgress.Completed)

        assertTrue(runBlocking { repo.undoMigration(project.path, r.journalPath) })
        assertEquals(before, snapshot())
        assertFalse(project.walkTopDown().any { it.name == "MainScreen.kt" })
    }

    @Test fun `Fehler in der Build-Vorbereitung nimmt alles zurueck`() {
        val before = snapshot()
        val real = ComposeBuildEnablerImpl()
        val failing = object : ComposeBuildEnabler by real {
            override suspend fun enableComposeInProject(projectPath: String, useVersionCatalog: Boolean, wireActivities: Boolean, hasConstraintLayout: Boolean, kotlinVersionOverride: String?): Result<BuildEnableResult> =
                Result.failure(IllegalStateException("simulierter Fehler"))
        }
        val e = runCatching { run(repo = X2cRepositoryImpl(buildEnabler = failing)) }.exceptionOrNull()
        assertNotNull(e)
        assertTrue("simulierter Fehler" in e!!.message!!)
        assertEquals(before, snapshot())
        assertFalse(project.walkTopDown().any { it.name == "MainScreen.kt" })
    }

    @Test fun `zweiter Lauf aendert nichts und legt kein leeres Backup an`() {
        run(all.copy(removeXmlAfterSuccess = false))
        val zips = File(project, "Backup").listFiles { f -> f.name.endsWith(".zip") }!!.size
        val before = snapshot()
        val r2 = run(all.copy(removeXmlAfterSuccess = false))
        val after = snapshot()
        assertEquals("geändert: " + (before.keys + after.keys).filter { before[it] != after[it] }, before.keys, after.keys)
        assertEquals("geändert: " + (before.keys + after.keys).filter { before[it] != after[it] }, before, after)
        assertEquals(zips, File(project, "Backup").listFiles { f -> f.name.endsWith(".zip") }!!.size)
        assertEquals("", r2.backupPath)
        assertTrue(r2.fileResults.any { "existiert, übersprungen" in it.notes.joinToString() })
    }

    @Test fun `ueberschreiben nur mit Bestaetigung`() {
        run(all.copy(removeXmlAfterSuccess = false, wireActivities = false))
        val screen = project.walkTopDown().first { it.name == "MainScreen.kt" }
        screen.writeText("// von Hand geaendert")
        run(all.copy(removeXmlAfterSuccess = false, wireActivities = false))
        assertEquals("// von Hand geaendert", screen.readText())
        run(all.copy(removeXmlAfterSuccess = false, wireActivities = false, overwriteExisting = true))
        assertTrue("fun MainScreen(" in screen.readText())
    }

    @Test fun `Layout das noch ueber include verwendet wird bleibt`() {
        write("app/src/main/res/layout/activity_other.xml", """<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android" android:layout_width="match_parent" android:layout_height="match_parent">
            <include layout="@layout/activity_main"/></LinearLayout>""")
        val r = run(all.copy(selectedLayouts = setOf("activity_main")))
        assertTrue(File(project, "app/src/main/res/layout/activity_main.xml").exists())
        assertEquals(0, r.removedXmlCount)
        assertTrue(r.markdownSummary.contains("bleibt"))
    }

    @Test fun `Auswahl beschraenkt die Umwandlung`() {
        val r = run(all.copy(selectedLayouts = setOf("activity_other"), wireActivities = false, removeXmlAfterSuccess = false))
        assertTrue(project.walkTopDown().any { it.name == "OtherScreen.kt" })
        assertFalse(project.walkTopDown().any { it.name == "MainScreen.kt" })
        assertTrue(r.skippedCount >= 1)
    }

    @Test fun `Git-Zweig und Commit bei sauberem Projekt`() {
        fun git(vararg a: String) = ProcessBuilder(listOf("git", "-c", "user.name=t", "-c", "user.email=t@t") + a).directory(project).redirectErrorStream(true).start().let { p -> val o = p.inputStream.bufferedReader().readText(); p.waitFor(); o.trim() }
        git("init", "-q"); git("add", "-A"); git("commit", "-q", "-m", "init")
        val r = run(all.copy(createGitBranch = true), X2cRepositoryImpl(git = ProcessGitBranchCommitter()))
        assertTrue(git("rev-parse", "--abbrev-ref", "HEAD").startsWith("x2c/"))
        assertTrue("MainScreen.kt" in git("show", "--name-only", "--format=", "HEAD"))
        assertTrue(r.markdownSummary.contains("Commit"))
        assertFalse(git("show", "--name-only", "--format=", "HEAD").contains("Backup/"))
    }

    @Test fun `Git-Zweig wird bei unsauberem Projekt nicht angelegt`() {
        fun git(vararg a: String) = ProcessBuilder(listOf("git", "-c", "user.name=t", "-c", "user.email=t@t") + a).directory(project).redirectErrorStream(true).start().let { p -> val o = p.inputStream.bufferedReader().readText(); p.waitFor(); o.trim() }
        git("init", "-q"); git("add", "-A"); git("commit", "-q", "-m", "init")
        write("app/src/main/java/com/example/demo/Dirty.kt", "x")
        val r = run(all.copy(createGitBranch = true), X2cRepositoryImpl(git = ProcessGitBranchCommitter()))
        assertFalse(git("rev-parse", "--abbrev-ref", "HEAD").startsWith("x2c/"))
        assertTrue(r.markdownSummary.contains("nicht angelegt"))
    }

    @Test fun `ohne Backup wird nichts angewendet`() {
        val before = snapshot()
        assertTrue(runCatching { run(all.copy(createBackup = false)) }.isFailure)
        assertEquals(before, snapshot())
    }
}
