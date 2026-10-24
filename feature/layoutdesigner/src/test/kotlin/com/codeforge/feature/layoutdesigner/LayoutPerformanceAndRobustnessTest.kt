/**
 * Modul: :feature:layoutdesigner
 * @author Thomas Schmid
 *
 * Tests für Leistung (500 Knoten Benchmark) und Robustheit (unbekannte Tags,
 * Entities, Unicode, sehr lange Attribute, CRLF-Zeilenenden).
 */
package com.codeforge.feature.layoutdesigner

import com.codeforge.feature.layoutdesigner.dnd.DesignerOffset
import com.codeforge.feature.layoutdesigner.dnd.DesignerRect
import com.codeforge.feature.layoutdesigner.dnd.DropResolver
import com.codeforge.feature.layoutdesigner.lint.LayoutLinter
import com.codeforge.feature.layoutdesigner.model.DragPayload
import com.codeforge.feature.layoutdesigner.model.LayoutDocument
import com.codeforge.feature.layoutdesigner.model.LayoutNode
import com.codeforge.feature.layoutdesigner.model.WidgetCatalog
import com.codeforge.feature.layoutdesigner.xml.LayoutParseException
import com.codeforge.feature.layoutdesigner.xml.LayoutXml
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureTimeMillis

class LayoutPerformanceAndRobustnessTest {

    @Test
    fun benchmark_500Nodes_parseWriteLintDropResolver() {
        // 1. 500-Knoten-Dokument synthetisch erzeugen:
        // Wurzel (1) + 50 Container (50) + je 9 Kinder (450) = 501 Knoten
        var uidCounter = 0
        val containers = ArrayList<LayoutNode>()
        for (i in 0 until 50) {
            val children = ArrayList<LayoutNode>()
            for (j in 0 until 9) {
                val leafTag = when (j % 4) {
                    0 -> "TextView"
                    1 -> "Button"
                    2 -> "ImageView"
                    else -> "CheckBox"
                }
                children.add(
                    LayoutNode(
                        uid = ++uidCounter,
                        tag = leafTag,
                        attributes = linkedMapOf(
                            "android:id" to "@+id/node_${i}_$j",
                            "android:layout_width" to "wrap_content",
                            "android:layout_height" to "wrap_content",
                            "android:text" to "Item $i-$j",
                            "android:layout_margin" to "4dp",
                        ),
                    ),
                )
            }
            containers.add(
                LayoutNode(
                    uid = ++uidCounter,
                    tag = "LinearLayout",
                    attributes = linkedMapOf(
                        "android:id" to "@+id/container_$i",
                        "android:layout_width" to "match_parent",
                        "android:layout_height" to "wrap_content",
                        "android:orientation" to "horizontal",
                    ),
                    children = children,
                ),
            )
        }

        val rootNode = LayoutNode(
            uid = 0,
            tag = "LinearLayout",
            attributes = linkedMapOf(
                "android:id" to "@+id/root",
                "android:layout_width" to "match_parent",
                "android:layout_height" to "match_parent",
                "android:orientation" to "vertical",
            ),
            children = containers,
        )

        val doc = LayoutDocument(
            root = rootNode,
            nextUid = uidCounter + 1,
            rootNamespaces = linkedMapOf("android" to "http://schemas.android.com/apk/res/android"),
        )

        val totalNodes = 1 + doc.root.children.size + doc.root.children.sumOf { it.children.size }
        assertEquals(501, totalNodes)

        // Warmup
        val warmupXml = LayoutXml.write(doc)
        LayoutXml.parse(warmupXml)
        LayoutLinter.lint(doc)

        // Benchmark Write
        val writeTimeMs = measureTimeMillis {
            val xml = LayoutXml.write(doc)
            assertTrue(xml.isNotEmpty())
        }

        // Benchmark Parse
        val xmlString = LayoutXml.write(doc)
        var parsedDoc: LayoutDocument? = null
        val parseTimeMs = measureTimeMillis {
            parsedDoc = LayoutXml.parse(xmlString)
        }
        assertNotNull(parsedDoc)
        assertEquals(501, 1 + parsedDoc!!.root.children.size + parsedDoc!!.root.children.sumOf { it.children.size })

        // Benchmark Lint
        var issueCount = 0
        val lintTimeMs = measureTimeMillis {
            val issues = LayoutLinter.lint(parsedDoc!!)
            issueCount = issues.size
        }

        // Benchmark DropResolver
        val viewBounds = mutableMapOf<Int, DesignerRect>()
        var yOffset = 0f
        viewBounds[0] = DesignerRect(0f, 0f, 1080f, 1920f)
        for (c in doc.root.children) {
            viewBounds[c.uid] = DesignerRect(0f, yOffset, 1080f, yOffset + 50f)
            var xOffset = 0f
            for (ch in c.children) {
                viewBounds[ch.uid] = DesignerRect(xOffset, yOffset, xOffset + 100f, yOffset + 50f)
                xOffset += 100f
            }
            yOffset += 50f
        }

        val textViewEntry = WidgetCatalog.palette.first { it.tag == "TextView" }
        val payload = DragPayload.NewWidget(textViewEntry)
        val dropPos = DesignerOffset(500f, 500f)

        val dropTimeMs = measureTimeMillis {
            for (r in 0 until 10) {
                val target = DropResolver.resolveDrop(
                    document = doc,
                    boundsMap = viewBounds,
                    pointer = dropPos,
                    payload = payload,
                )
                assertNotNull(target)
            }
        } / 10

        println("=== 500 NODES BENCHMARK RESULTS ===")
        println("Write: $writeTimeMs ms")
        println("Parse: $parseTimeMs ms")
        println("Lint: $lintTimeMs ms (Issues: $issueCount)")
        println("DropResolver (avg of 10): $dropTimeMs ms")
        println("===================================")

        // Verifiziere angemessene Leistung auf Entwicklungs-Umgebung (jeweils deutlich unter 500ms)
        assertTrue("Write time ($writeTimeMs ms) should be reasonable", writeTimeMs < 500)
        assertTrue("Parse time ($parseTimeMs ms) should be reasonable", parseTimeMs < 500)
        assertTrue("Lint time ($lintTimeMs ms) should be reasonable", lintTimeMs < 500)
        assertTrue("DropResolver time ($dropTimeMs ms) should be reasonable", dropTimeMs < 100)
    }

    @Test
    fun robustness_unknownTagsAndNestedCustomTags_preserved() {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <com.example.custom.MyLayout
                xmlns:android="http://schemas.android.com/apk/res/android"
                xmlns:app="http://schemas.android.com/apk/res-auto"
                android:layout_width="match_parent"
                android:layout_height="match_parent">
                <unknown-widget
                    android:id="@+id/unknown1"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content" />
                <com.example.custom.InnerComponent
                    android:id="@+id/inner"
                    class="com.example.custom.View${'$'}InnerComponent"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    app:customAttribute="specialValue" />
            </com.example.custom.MyLayout>
        """.trimIndent()

        val doc = LayoutXml.parse(xml)
        assertEquals("com.example.custom.MyLayout", doc.root.tag)
        assertEquals(2, doc.root.children.size)
        assertEquals("unknown-widget", doc.root.children[0].tag)
        assertEquals("com.example.custom.InnerComponent", doc.root.children[1].tag)
        assertEquals("com.example.custom.View\$InnerComponent", doc.root.children[1].attr("class"))
        assertEquals("specialValue", doc.root.children[1].attr("app:customAttribute"))

        val written = LayoutXml.write(doc, reformat = false)
        assertTrue(written.contains("<com.example.custom.MyLayout"))
        assertTrue(written.contains("<unknown-widget"))
        assertTrue(written.contains("<com.example.custom.InnerComponent"))
        assertTrue(written.contains("class=\"com.example.custom.View\$InnerComponent\""))
        assertTrue(written.contains("app:customAttribute=\"specialValue\""))
    }

    @Test
    fun robustness_xmlEntities_handledCorrectly() {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <LinearLayout
                xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:orientation="vertical">
                <TextView
                    android:id="@+id/text1"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="Tom &amp; Jerry &lt;3 &quot;Cartoon&quot;" />
            </LinearLayout>
        """.trimIndent()

        val doc = LayoutXml.parse(xml)
        val textView = doc.root.children[0]
        assertEquals("Tom & Jerry <3 \"Cartoon\"", textView.attr("android:text"))

        val written = LayoutXml.write(doc, reformat = false)
        assertTrue(written.contains("android:text=\"Tom &amp; Jerry &lt;3 &quot;Cartoon&quot;\""))
    }

    @Test
    fun robustness_unicodeCharacters_preserved() {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <LinearLayout
                xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent">
                <TextView
                    android:id="@+id/unicode"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="Unicode: 🚀 🔥 📱 | 中文布局 | Grüße &amp; Überprüfung | مرحبا" />
            </LinearLayout>
        """.trimIndent()

        val doc = LayoutXml.parse(xml)
        val text = doc.root.children[0].attr("android:text")
        assertTrue(text!!.contains("🚀 🔥 📱"))
        assertTrue(text.contains("中文布局"))
        assertTrue(text.contains("Grüße & Überprüfung"))
        assertTrue(text.contains("مرحبا"))

        val written = LayoutXml.write(doc, reformat = false)
        assertTrue(written.contains("🚀 🔥 📱"))
        assertTrue(written.contains("中文布局"))
        assertTrue(written.contains("Grüße &amp; Überprüfung"))
        assertTrue(written.contains("مرحبا"))
    }

    @Test
    fun robustness_veryLongAttributeValues_roundtrip() {
        val longText = "A".repeat(50000)
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <LinearLayout
                xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent">
                <TextView
                    android:id="@+id/long_text"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="$longText" />
            </LinearLayout>
        """.trimIndent()

        val doc = LayoutXml.parse(xml)
        assertEquals(50000, doc.root.children[0].attr("android:text")?.length)

        val written = LayoutXml.write(doc, reformat = false)
        assertTrue(written.contains(longText))
    }

    @Test
    fun robustness_crlfLineEndings_preserved() {
        val crlfXml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\r\n" +
            "<LinearLayout\r\n" +
            "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\r\n" +
            "    android:layout_width=\"match_parent\"\r\n" +
            "    android:layout_height=\"match_parent\">\r\n" +
            "    <TextView\r\n" +
            "        android:layout_width=\"wrap_content\"\r\n" +
            "        android:layout_height=\"wrap_content\"\r\n" +
            "        android:text=\"CRLF Test\" />\r\n" +
            "</LinearLayout>\r\n"

        val doc = LayoutXml.parse(crlfXml)
        assertEquals("\r\n", doc.lineEnding)

        val written = LayoutXml.write(doc, reformat = false)
        assertTrue("Output should contain CRLF", written.contains("\r\n"))
        // Jede Zeile sollte mit \r\n enden
        val nonCrlf = written.replace("\r\n", "")
        assertFalse("Output should not have naked LF", nonCrlf.contains("\n"))
    }

    @Test(expected = LayoutParseException::class)
    fun robustness_malformedXml_throwsLayoutParseException() {
        val malformed = "<LinearLayout><unclosed></LinearLayout>"
        LayoutXml.parse(malformed)
    }
}
