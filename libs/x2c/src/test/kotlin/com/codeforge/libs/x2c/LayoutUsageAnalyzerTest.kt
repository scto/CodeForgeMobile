package com.codeforge.libs.x2c

import com.codeforge.core.domain.model.LayoutClassification
import com.codeforge.libs.x2c.analysis.LayoutUsageAnalyzerImpl
import com.codeforge.libs.x2c.scanner.ComposeMigrationScannerImpl
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LayoutUsageAnalyzerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var root: File
    private val analyzer = LayoutUsageAnalyzerImpl()

    @Before
    fun setUp() {
        root = tmp.newFolder("proj")
    }

    private fun write(rel: String, content: String) {
        File(root, rel).apply { parentFile.mkdirs() }.writeText(content.trimIndent())
    }

    private fun usages(layout: String, ids: Set<String> = emptySet()) = runBlocking {
        analyzer.findAllUsages(root.absolutePath, mapOf(layout to ids))[layout].orEmpty()
    }

    @Test
    fun `Kotlin-Activity mit setContentView ohne View-Zugriff ist Stufe-2-Kandidat`() {
        write("app/src/main/java/A.kt", """
            class A : AppCompatActivity() {
                override fun onCreate(b: Bundle?) { setContentView(R.layout.activity_a) }
            }
        """)
        val u = usages("activity_a", setOf("title")).single()
        assertEquals("setContentView", u.usageType)
        assertEquals(2, u.lineNumber)
        assertTrue(u.isStage2Candidate)
        assertTrue(u.referencedViewIds.isEmpty())
    }

    @Test
    fun `findViewById auf eine ID des Layouts verhindert Stufe 2 und wird gemeldet`() {
        write("app/src/main/java/A.kt", """
            class A : AppCompatActivity() {
                override fun onCreate(b: Bundle?) {
                    setContentView(R.layout.activity_a)
                    findViewById<TextView>(R.id.title).text = "x"
                }
            }
        """)
        val u = usages("activity_a", setOf("title", "other")).single()
        assertFalse(u.isStage2Candidate)
        assertEquals(listOf("title"), u.referencedViewIds)
    }

    @Test
    fun `Java-Activity wird nie als Kandidat gemeldet`() {
        write("app/src/main/java/B.java", """
            public class B extends AppCompatActivity {
                protected void onCreate(Bundle b) { setContentView(R.layout.activity_b); }
            }
        """)
        val u = usages("activity_b").single()
        assertFalse(u.isStage2Candidate)
        assertTrue(u.notes.any { it.contains("Java-Activity") })
    }

    @Test
    fun `ViewBinding wird erkannt und Zugriff ueber camelCase gezaehlt`() {
        write("app/src/main/java/C.kt", """
            import com.example.databinding.ActivityCBinding
            class C : AppCompatActivity() {
                private lateinit var binding: ActivityCBinding
                override fun onCreate(b: Bundle?) {
                    binding = ActivityCBinding.inflate(layoutInflater)
                    binding.btnSubmit.setOnClickListener { }
                }
            }
        """)
        val list = usages("activity_c", setOf("btn_submit", "title"))
        assertTrue(list.all { it.usageType == "ViewBinding" })
        assertTrue("Import-Zeile zählt nicht", list.none { it.lineNumber == 1 })
        assertEquals(listOf("btn_submit"), list.first().referencedViewIds)
    }

    @Test
    fun `RemoteViews und appwidget werden erkannt`() {
        write("app/src/main/java/W.kt", """
            fun build(ctx: Context) = RemoteViews(ctx.packageName, R.layout.widget_small)
        """)
        write("app/src/main/res/xml/provider.xml", """
            <appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
                android:initialLayout="@layout/widget_small" />
        """)
        val types = usages("widget_small").map { it.usageType }.toSet()
        assertEquals(setOf("RemoteViews", "appwidget"), types)
    }

    @Test
    fun `include und tools-layout in XML sowie Kommentare`() {
        write("app/src/main/res/layout/activity_main.xml", """
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android">
                <include
                    layout="@layout/part_header" />
            </LinearLayout>
        """)
        write("app/src/main/java/D.kt", """
            // setContentView(R.layout.part_header) steht nur im Kommentar
            class D
        """)
        val u = usages("part_header").single()
        assertEquals("include", u.usageType)
        assertEquals("app/src/main/res/layout/activity_main.xml", u.filePath)
        assertEquals(3, u.lineNumber)
    }

    @Test
    fun `Adapter-inflate bekommt Hinweis und keinen Kandidaten`() {
        write("app/src/main/java/Adapter.kt", """
            class Adapter : RecyclerView.Adapter<VH>() {
                override fun onCreateViewHolder(p: ViewGroup, t: Int) =
                    VH(LayoutInflater.from(p.context).inflate(R.layout.item_row, p, false))
            }
        """)
        val u = usages("item_row").single()
        assertEquals("inflate", u.usageType)
        assertFalse(u.isStage2Candidate)
        assertTrue(u.notes.any { it.contains("Adapter") })
    }

    @Test
    fun `Fragment mit onCreateView ohne View-Zugriff ist Kandidat`() {
        write("app/src/main/java/F.kt", """
            class F : Fragment() {
                override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View =
                    i.inflate(R.layout.fragment_f, c, false)
            }
        """)
        assertTrue(usages("fragment_f").single().isStage2Candidate)
    }

    @Test
    fun `build und Backup werden nicht durchsucht`() {
        write("app/build/gen/X.kt", "val a = R.layout.activity_a")
        write("Backup/old/Y.kt", "val a = R.layout.activity_a")
        assertTrue(usages("activity_a").isEmpty())
    }

    @Test
    fun `Namen mit gemeinsamem Praefix werden nicht vermischt`() {
        write("app/src/main/java/G.kt", "val a = R.layout.activity_main_land")
        assertTrue(usages("activity_main").isEmpty())
        assertEquals(1, usages("activity_main_land").size)
    }

    @Test
    fun `findRemainingViewReferences listet klassische View-Zugriffe`() = runBlocking {
        write("app/src/main/java/H.kt", """
            import android.view.LayoutInflater
            class H { fun f() { findViewById<View>(R.id.x) } }
        """)
        val rest = analyzer.findRemainingViewReferences(root.absolutePath)
        assertEquals(1, rest.size)
        assertTrue(rest.single().startsWith("app/src/main/java/H.kt:2:"))
    }

    @Test
    fun `Plan stuft Layout mit RemoteViews-Verwendung als nicht umwandelbar ein`() = runBlocking {
        write("app/build.gradle.kts", """plugins { id("com.android.application") }""")
        write("app/src/main/res/layout/notif_small.xml", """
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent" android:layout_height="wrap_content" />
        """)
        write("app/src/main/java/N.kt", "fun n(c: Context) = RemoteViews(c.packageName, R.layout.notif_small)")
        val plan = ComposeMigrationScannerImpl().createPlan(root.absolutePath)
        val e = plan.layouts.single()
        assertEquals(LayoutClassification.NOT_CONVERTIBLE, e.classification)
        assertTrue(e.reason.contains("RemoteViews"))
        assertEquals(1, plan.notConvertibleCount)
    }
}
