package com.codeforge.feature.layoutdesigner

import com.codeforge.feature.layoutdesigner.model.LayoutDocument
import com.codeforge.feature.layoutdesigner.model.PaletteEntry
import com.codeforge.feature.layoutdesigner.model.SizeMode
import com.codeforge.feature.layoutdesigner.model.WidgetCatalog
import com.codeforge.feature.layoutdesigner.model.WidgetCategory
import com.codeforge.feature.layoutdesigner.model.addWidget
import com.codeforge.feature.layoutdesigner.model.duplicate
import com.codeforge.feature.layoutdesigner.model.find
import com.codeforge.feature.layoutdesigner.model.flatten
import com.codeforge.feature.layoutdesigner.model.indent
import com.codeforge.feature.layoutdesigner.model.moveWithinParent
import com.codeforge.feature.layoutdesigner.model.outdent
import com.codeforge.feature.layoutdesigner.model.parseColorArgb
import com.codeforge.feature.layoutdesigner.model.parseDimension
import com.codeforge.feature.layoutdesigner.model.parseSize
import com.codeforge.feature.layoutdesigner.model.remove
import com.codeforge.feature.layoutdesigner.model.setAttribute
import com.codeforge.feature.layoutdesigner.xml.LayoutParseException
import com.codeforge.feature.layoutdesigner.xml.LayoutXml
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class LayoutModelTest {

    private val sample = """<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical">
    <!-- Kommentar -->
    <TextView
        android:id="@+id/title"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Hallo &amp; &quot;Welt&quot;"
        app:foo="bar" />
    <FrameLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content">
        <Button android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="OK" />
    </FrameLayout>
</LinearLayout>"""

    @Test fun parseReadsTreeAndAttributes() {
        val doc = LayoutXml.parse(sample)
        assertEquals("LinearLayout", doc.root.tag)
        assertEquals(2, doc.root.children.size)
        assertEquals("Hallo & \"Welt\"", doc.root.children[0].attr("android:text"))
        assertEquals("bar", doc.root.children[0].attr("app:foo"))
        assertNull(doc.root.attr("xmlns:android"))
        assertEquals(1, doc.root.children[1].children.size)
        assertEquals(4, doc.nextUid)
    }

    @Test fun writeRoundTripIsStable() {
        val doc = LayoutXml.parse(sample)
        val out = LayoutXml.write(doc)
        assertTrue(out.contains("xmlns:app=\"http://schemas.android.com/apk/res-auto\""))
        assertTrue(out.contains("android:text=\"Hallo &amp; &quot;Welt&quot;\""))
        val again = LayoutXml.parse(out)
        assertEquals(out, LayoutXml.write(again))
        assertEquals(doc.root.children.size, again.root.children.size)
    }

    @Test fun invalidXmlThrows() {
        try { LayoutXml.parse("<LinearLayout><TextView></LinearLayout>"); fail("expected") } catch (e: LayoutParseException) { }
        try { LayoutXml.parse("<!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]><x>&e;</x>"); fail("expected") } catch (e: LayoutParseException) { }
    }

    @Test fun addWidgetGoesIntoSelectedContainerOrParent() {
        val doc = LayoutXml.parse(sample)
        val button = PaletteEntry("button", "Button", WidgetCategory.BUTTON, mapOf("android:layout_width" to "wrap_content"))
        // Auswahl = FrameLayout (uid 2) → hinein
        val inFrame = doc.addWidget(button, 2)
        assertEquals(2, doc.find(2)!!.children.size + 1)
        assertEquals(2, inFrame.document.find(2)!!.children.size)
        // Auswahl = TextView (uid 1, kein Container) → in dessen Elternteil (Root)
        val sibling = doc.addWidget(button, 1)
        assertEquals(3, sibling.document.root.children.size)
        assertEquals("@+id/button1", sibling.document.find(sibling.newUid!!)!!.attr("android:id"))
        // nächste freie Id
        val second = sibling.document.addWidget(button, null)
        assertEquals("@+id/button2", second.document.find(second.newUid!!)!!.attr("android:id"))
    }

    @Test fun removeAndRootProtection() {
        val doc = LayoutXml.parse(sample)
        assertEquals(1, doc.remove(2).root.children.size)
        assertSame(doc, doc.remove(0))
    }

    @Test fun duplicateCreatesFreshUidsAndIds() {
        val doc = LayoutXml.parse(sample)
        val r = doc.duplicate(1)
        assertEquals(3, r.document.root.children.size)
        val copy = r.document.find(r.newUid!!)!!
        assertEquals("@+id/textView1", copy.attr("android:id"))
        assertEquals("@+id/title", r.document.root.children[0].attr("android:id"))
        assertEquals(r.document.root.children[1].uid, copy.uid)
        val ids = r.document.flatten().map { it.first.uid }
        assertEquals(ids.size, ids.toSet().size)
        // verschachtelter Container: Kinder bekommen ebenfalls neue uids
        val frame = doc.duplicate(2)
        val uids = frame.document.flatten().map { it.first.uid }
        assertEquals(uids.size, uids.toSet().size)
        assertEquals(6, uids.size)
    }

    @Test fun moveIndentOutdent() {
        val doc = LayoutXml.parse(sample)
        val moved = doc.moveWithinParent(2, -1)
        assertEquals(2, moved.root.children[0].uid)
        assertSame(moved, moved.moveWithinParent(2, -1))
        // TextView (1) vor FrameLayout (2) → einrücken: TextView ist kein Container → keine Änderung
        assertSame(doc, doc.indent(1))
        // FrameLayout hat TextView als vorherigen Geschwister (kein Container) → keine Änderung
        assertSame(doc, doc.indent(2))
        // Button (3) ausrücken → landet im Root hinter dem FrameLayout
        val out = doc.outdent(3)
        assertEquals(listOf(1, 2, 3), out.root.children.map { it.uid })
        assertTrue(out.find(2)!!.children.isEmpty())
        // wieder einrücken → zurück ins FrameLayout
        val back = out.indent(3)
        assertEquals(listOf(1, 2), back.root.children.map { it.uid })
        assertEquals(1, back.find(2)!!.children.size)
    }

    @Test fun setAttributeAddsChangesRemoves() {
        val doc = LayoutXml.parse(sample)
        val a = doc.setAttribute(1, "android:textSize", "18sp")
        assertEquals("18sp", a.find(1)!!.attr("android:textSize"))
        val b = a.setAttribute(1, "android:textSize", "")
        assertNull(b.find(1)!!.attr("android:textSize"))
        assertSame(doc, doc.setAttribute(1, "android:nothing", null))
        // Reihenfolge bleibt: neues Attribut am Ende
        assertEquals("android:textSize", a.find(1)!!.attributes.keys.last())
    }

    @Test fun attrsForDependOnParent() {
        val inLinear = WidgetCatalog.attrsFor("TextView", "LinearLayout").map { it.name }
        assertTrue("android:layout_weight" in inLinear)
        assertTrue("android:text" in inLinear)
        val inFrame = WidgetCatalog.attrsFor("TextView", "FrameLayout").map { it.name }
        assertFalse("android:layout_weight" in inFrame)
        assertTrue("android:layout_gravity" in inFrame)
        assertEquals(inLinear.size, inLinear.toSet().size)
    }

    @Test fun unitsAndColors() {
        assertEquals(16f, parseDimension("16dp")!!, 0f)
        assertEquals(14.5f, parseDimension("14.5sp")!!, 0f)
        assertNull(parseDimension("@dimen/x"))
        assertEquals(0xFFFF0000L, parseColorArgb("#F00"))
        assertEquals(0x80FF0000L, parseColorArgb("#80FF0000"))
        assertEquals(0xFF112233L, parseColorArgb("#112233"))
        assertEquals(0x88FF0000L, parseColorArgb("#8F00"))
        assertNull(parseColorArgb("#12"))
        assertNull(parseColorArgb("@color/x"))
        assertEquals(SizeMode.MATCH_PARENT, parseSize("match_parent").mode)
        assertEquals(48f, parseSize("48dp").dp, 0f)
        assertEquals(SizeMode.WRAP_CONTENT, parseSize("@dimen/x").mode)
    }

    @Test fun newDocumentWritesValidXml() {
        val out = LayoutXml.write(LayoutDocument.newLinearLayout())
        assertTrue(out.startsWith("<?xml version=\"1.0\" encoding=\"utf-8\"?>"))
        assertEquals("LinearLayout", LayoutXml.parse(out).root.tag)
    }
}
