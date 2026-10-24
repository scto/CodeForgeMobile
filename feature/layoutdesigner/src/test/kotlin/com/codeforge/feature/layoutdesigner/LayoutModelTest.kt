package com.codeforge.feature.layoutdesigner

import com.codeforge.feature.layoutdesigner.model.DragPayload
import com.codeforge.feature.layoutdesigner.model.DropVerdict
import com.codeforge.feature.layoutdesigner.model.LayoutDocument
import com.codeforge.feature.layoutdesigner.model.PaletteEntry
import com.codeforge.feature.layoutdesigner.model.SizeMode
import com.codeforge.feature.layoutdesigner.model.WidgetCatalog
import com.codeforge.feature.layoutdesigner.model.WidgetCategory
import com.codeforge.feature.layoutdesigner.model.addWidget
import com.codeforge.feature.layoutdesigner.model.addWidgetAt
import com.codeforge.feature.layoutdesigner.model.canDrop
import com.codeforge.feature.layoutdesigner.model.changeTag
import com.codeforge.feature.layoutdesigner.model.duplicate
import com.codeforge.feature.layoutdesigner.model.duplicateNodes
import com.codeforge.feature.layoutdesigner.model.find
import com.codeforge.feature.layoutdesigner.model.findIncompatibleChildAttributes
import com.codeforge.feature.layoutdesigner.model.flatten
import com.codeforge.feature.layoutdesigner.model.indent
import com.codeforge.feature.layoutdesigner.model.insertNode
import com.codeforge.feature.layoutdesigner.model.moveNode
import com.codeforge.feature.layoutdesigner.model.moveWithinParent
import com.codeforge.feature.layoutdesigner.model.outdent
import com.codeforge.feature.layoutdesigner.model.parseColorArgb
import com.codeforge.feature.layoutdesigner.model.parseDimension
import com.codeforge.feature.layoutdesigner.model.parseSize
import com.codeforge.feature.layoutdesigner.model.remove
import com.codeforge.feature.layoutdesigner.model.removeNodes
import com.codeforge.feature.layoutdesigner.model.removeWithReferences
import com.codeforge.feature.layoutdesigner.model.renameId
import com.codeforge.feature.layoutdesigner.model.setAttribute
import com.codeforge.feature.layoutdesigner.model.unwrap
import com.codeforge.feature.layoutdesigner.model.wrapIn
import com.codeforge.feature.layoutdesigner.xml.LayoutParseException
import com.codeforge.feature.layoutdesigner.xml.LayoutXml
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

    @Test fun byteIdenticalRoundTripWithCommentsAndCustomNamespaces() {
        val originalXml = """<?xml version="1.0" encoding="utf-8"?>
<!-- Header comment before root -->
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
  xmlns:custom="http://schemas.android.com/apk/res/custom"
  android:id="@+id/root_layout"
  android:layout_width="match_parent"
  android:layout_height="match_parent"
  android:orientation="vertical">
  <!-- Child leading comment -->
  <TextView
    android:id="@+id/custom_text"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:text="Hello World" />
  <!-- Trailing comment inside root -->
</LinearLayout>
"""
        val doc = LayoutXml.parse(originalXml)
        val written = LayoutXml.write(doc)
        assertEquals(originalXml.trim(), written.trim())
    }

    @Test fun invalidXmlThrows() {
        try { LayoutXml.parse("<LinearLayout><TextView></LinearLayout>"); fail("expected") } catch (e: LayoutParseException) { }
        try { LayoutXml.parse("<!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]><x>&e;</x>"); fail("expected") } catch (e: LayoutParseException) { }
    }

    @Test fun addWidgetGoesIntoSelectedContainerOrParent() {
        val doc = LayoutXml.parse(sample)
        val button = PaletteEntry("button", "Button", WidgetCategory.BUTTON, mapOf("android:layout_width" to "wrap_content"))
        val inFrame = doc.addWidget(button, 2)
        assertEquals(2, doc.find(2)!!.children.size + 1)
        assertEquals(2, inFrame.document.find(2)!!.children.size)
        val sibling = doc.addWidget(button, 1)
        assertEquals(3, sibling.document.root.children.size)
        assertEquals("@+id/button1", sibling.document.find(sibling.newUid!!)!!.attr("android:id"))
        val second = sibling.document.addWidget(button, null)
        assertEquals("@+id/button2", second.document.find(second.newUid!!)!!.attr("android:id"))
    }

    @Test fun addWidgetAt_insertsAtSpecificIndex() {
        val doc = LayoutXml.parse(sample)
        val button = PaletteEntry("button", "Button", WidgetCategory.BUTTON, mapOf("android:layout_width" to "wrap_content"))
        val res = doc.addWidgetAt(button, parentUid = 0, index = 0)
        assertNotNull(res.newUid)
        assertEquals(3, res.document.root.children.size)
        assertEquals(res.newUid, res.document.root.children[0].uid)
    }

    @Test fun moveNode_validWithinSameParentAndDifferentParent() {
        val doc = LayoutXml.parse(sample)
        // doc root has children [1 (TextView), 2 (FrameLayout with child 3)]
        val movedSame = doc.moveNode(uid = 1, newParentUid = 0, index = 1)
        assertEquals(2, movedSame.root.children[0].uid)
        assertEquals(1, movedSame.root.children[1].uid)

        // Move TextView (1) into FrameLayout (2)
        val movedIntoFrame = doc.moveNode(uid = 1, newParentUid = 2, index = 0)
        assertEquals(1, movedIntoFrame.root.children.size)
        assertEquals(2, movedIntoFrame.root.children[0].children.size)
        assertEquals(1, movedIntoFrame.root.children[0].children[0].uid)
    }

    @Test fun moveNode_invalidMovesAreRejected() {
        val doc = LayoutXml.parse(sample)
        // Root cannot be moved
        assertSame(doc, doc.moveNode(uid = 0, newParentUid = 2, index = 0))
        // Cannot move into self
        assertSame(doc, doc.moveNode(uid = 2, newParentUid = 2, index = 0))
        // Cannot move into descendant
        assertSame(doc, doc.moveNode(uid = 2, newParentUid = 3, index = 0))
        // Cannot move into non-container
        assertSame(doc, doc.moveNode(uid = 2, newParentUid = 1, index = 0))
    }

    @Test fun canDrop_verifiesDropRules() {
        val doc = LayoutXml.parse(sample)
        val buttonEntry = PaletteEntry("button", "Button", WidgetCategory.BUTTON, emptyMap())
        val mergeEntry = PaletteEntry("merge", "merge", WidgetCategory.LAYOUT, emptyMap())
        val tableRowEntry = PaletteEntry("TableRow", "TableRow", WidgetCategory.LAYOUT, emptyMap())

        // Non-container rejects drop
        assertTrue(doc.canDrop(DragPayload.NewWidget(buttonEntry), parentUid = 1) is DropVerdict.Rejected)

        // Valid container allows drop
        assertTrue(doc.canDrop(DragPayload.NewWidget(buttonEntry), parentUid = 0) is DropVerdict.Allowed)

        // merge outside root rejected
        assertTrue(doc.canDrop(DragPayload.NewWidget(mergeEntry), parentUid = 2) is DropVerdict.Rejected)

        // TableRow in non-TableLayout gives Warning
        assertTrue(doc.canDrop(DragPayload.NewWidget(tableRowEntry), parentUid = 0) is DropVerdict.Warning)

        // Existing node moving to self or descendant rejected
        assertTrue(doc.canDrop(DragPayload.ExistingNodes(setOf(2)), parentUid = 2) is DropVerdict.Rejected)
        assertTrue(doc.canDrop(DragPayload.ExistingNodes(setOf(2)), parentUid = 3) is DropVerdict.Rejected)
        assertTrue(doc.canDrop(DragPayload.ExistingNodes(setOf(0)), parentUid = 2) is DropVerdict.Rejected)
    }

    @Test fun wrapIn_and_unwrap() {
        val doc = LayoutXml.parse(sample)
        // Root has TextView (1) and FrameLayout (2)
        val wrapped = doc.wrapIn(setOf(1, 2), "LinearLayout")
        assertNotNull(wrapped.newUid)
        assertEquals(1, wrapped.document.root.children.size)
        val container = wrapped.document.root.children[0]
        assertEquals("LinearLayout", container.tag)
        assertEquals(2, container.children.size)
        assertEquals(1, container.children[0].uid)
        assertEquals(2, container.children[1].uid)

        // Unwrap container restores original children
        val unwrapped = wrapped.document.unwrap(container.uid)
        assertEquals(2, unwrapped.root.children.size)
        assertEquals(1, unwrapped.root.children[0].uid)
        assertEquals(2, unwrapped.root.children[1].uid)

        // Root cannot be unwrapped
        assertSame(doc, doc.unwrap(0))
    }

    @Test fun changeTag_withAttributeCleanup() {
        val doc = LayoutXml.parse(sample)
        // Add layout_weight to TextView (1)
        val withWeight = doc.setAttribute(1, "android:layout_weight", "1")
        val incompatible = withWeight.findIncompatibleChildAttributes(0, "FrameLayout")
        assertEquals(1, incompatible.size)
        assertEquals("android:layout_weight", incompatible[0].attributeName)

        val converted = withWeight.changeTag(0, "FrameLayout", removeIncompatibleAttrs = true)
        assertEquals("FrameLayout", converted.root.tag)
        assertNull(converted.find(1)!!.attr("android:layout_weight"))
    }

    @Test fun renameId_updatesAllReferencesAcrossDocument() {
        val xmlWithRefs = """<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent">
    <TextView
        android:id="@+id/oldTitle"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content" />
    <Button
        android:id="@+id/actionBtn"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:labelFor="@id/oldTitle"
        android:nextFocusDown="@id/oldTitle"
        app:layout_constraintTop_toBottomOf="@id/oldTitle" />
    <androidx.constraintlayout.widget.Barrier
        android:id="@+id/barrier"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        app:constraint_referenced_ids="oldTitle,actionBtn" />
</androidx.constraintlayout.widget.ConstraintLayout>"""
        val doc = LayoutXml.parse(xmlWithRefs)
        val renamed = doc.renameId(1, "@+id/newTitle")

        assertEquals("@+id/newTitle", renamed.find(1)!!.attr("android:id"))
        val button = renamed.find(2)!!
        assertEquals("@id/newTitle", button.attr("android:labelFor"))
        assertEquals("@id/newTitle", button.attr("android:nextFocusDown"))
        assertEquals("@id/newTitle", button.attr("app:layout_constraintTop_toBottomOf"))

        val barrier = renamed.find(3)!!
        assertEquals("newTitle,actionBtn", barrier.attr("app:constraint_referenced_ids"))
    }

    @Test fun removeWithReferences_detectsAndCleansReferences() {
        val xmlWithRefs = """<?xml version="1.0" encoding="utf-8"?>
<RelativeLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent">
    <TextView
        android:id="@+id/header"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content" />
    <Button
        android:id="@+id/btn"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_below="@id/header" />
</RelativeLayout>"""
        val doc = LayoutXml.parse(xmlWithRefs)
        val checkOnly = doc.removeWithReferences(1, clearReferences = false)
        assertEquals(1, checkOnly.danglingReferences.size)
        assertEquals("header", checkOnly.danglingReferences[0].referencedId)
        // With clearReferences = true
        val cleaned = doc.removeWithReferences(1, clearReferences = true)
        assertEquals(1, cleaned.document.root.children.size)
        assertNull(cleaned.document.find(2)!!.attr("android:layout_below"))
    }

    @Test fun multiSelection_removeAndDuplicate() {
        val doc = LayoutXml.parse(sample)
        // Remove both children 1 and 2
        val removed = doc.removeNodes(setOf(1, 2))
        assertTrue(removed.root.children.isEmpty())

        // Duplicate multiple nodes
        val duplicated = doc.duplicateNodes(setOf(1, 2))
        assertNotNull(duplicated.newUid)
        assertEquals(4, duplicated.document.root.children.size)
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
        assertSame(doc, doc.indent(1))
        assertSame(doc, doc.indent(2))
        val out = doc.outdent(3)
        assertEquals(listOf(1, 2, 3), out.root.children.map { it.uid })
        assertTrue(out.find(2)!!.children.isEmpty())
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
