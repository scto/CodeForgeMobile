package com.codeforge.core.domain.refactoring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PackageRenamePlannerTest {

    private val root = "/p/app/src/main/kotlin"

    @Test
    fun packageOfSourceDirs() {
        assertEquals("com.example.feature", PackageRenamePlanner.packageOf("$root/com/example/feature"))
        assertEquals("com.example", PackageRenamePlanner.packageOf("/p/lib/src/test/java/com/example/"))
        assertNull(PackageRenamePlanner.packageOf(root))
        assertNull(PackageRenamePlanner.packageOf("/p/app/res/values"))
        assertNull(PackageRenamePlanner.packageOf("$root/com/my-app"))
    }

    @Test
    fun packageChangeOnRename() {
        assertEquals(
            "com.example.feature" to "com.example.home",
            PackageRenamePlanner.packageChange("$root/com/example/feature", "$root/com/example/home")
        )
        assertNull(PackageRenamePlanner.packageChange("/p/docs/a", "/p/docs/b"))
    }

    @Test
    fun rewritesPackageOnlyInsideDir() {
        val code = lines("package com.example.feature.ui", "", "import com.example.feature.data.Repo", "import com.example.featureflags.Flag")
        val inside = PackageRenamePlanner.rewrite("A.kt", code, "com.example.feature", "com.example.home", insideRenamedDir = true)!!
        assertEquals(
            lines("package com.example.home.ui", "", "import com.example.home.data.Repo", "import com.example.featureflags.Flag"),
            inside.newContent
        )
        assertEquals(2, inside.editCount)
        val outside = PackageRenamePlanner.rewrite("B.kt", code, "com.example.feature", "com.example.home", insideRenamedDir = false)!!
        assertEquals(1, outside.editCount)
        assertEquals(true, outside.newContent.startsWith("package com.example.feature.ui"))
    }

    @Test
    fun rewritesJavaStaticImportAndXml() {
        val java = lines("package x;", "import static com.a.b.Util.run;")
        assertEquals(
            lines("package x;", "import static com.c.Util.run;"),
            PackageRenamePlanner.rewrite("X.java", java, "com.a.b", "com.c", false)!!.newContent
        )
        val xml = """<activity android:name="com.a.b.MainActivity"/><com.a.b.ui.Chart android:id="@+id/c"/>"""
        assertEquals(
            """<activity android:name="com.c.MainActivity"/><com.c.ui.Chart android:id="@+id/c"/>""",
            PackageRenamePlanner.rewrite("layout.xml", xml, "com.a.b", "com.c", false)!!.newContent
        )
    }

    @Test
    fun unchangedFileGivesNull() {
        assertNull(PackageRenamePlanner.rewrite("C.kt", lines("package other"), "com.a", "com.b", false))
    }

    private fun lines(vararg l: String) = l.joinToString(separator = "\n", postfix = "\n")
}
