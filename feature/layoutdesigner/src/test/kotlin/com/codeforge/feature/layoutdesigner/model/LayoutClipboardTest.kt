package com.codeforge.feature.layoutdesigner.model

import com.codeforge.feature.layoutdesigner.xml.LayoutXml
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutClipboardTest {

    @Test
    fun createSnippet_and_pasteWithCollisionHandling() {
        val xml = """<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical">
    <TextView
        android:id="@+id/myText"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="CopyMe" />
</LinearLayout>"""
        val doc = LayoutXml.parse(xml)
        val snippet = LayoutClipboard.createSnippet(doc, setOf(1))
        assertTrue(snippet.contains("CopyMe"))
        assertTrue(snippet.contains("myText"))

        // Paste snippet back into the same document (id collision on myText)
        val pasted = LayoutClipboard.pasteSnippet(doc, snippet, targetParentUid = 0)
        assertNotNull(pasted.newUid)
        val newDoc = pasted.document
        assertEquals(2, newDoc.root.children.size)

        val firstChild = newDoc.root.children[0]
        val secondChild = newDoc.root.children[1]

        assertEquals("@+id/myText", firstChild.attr("android:id"))
        assertNotEquals("@+id/myText", secondChild.attr("android:id"))
        assertTrue(secondChild.attr("android:id")!!.startsWith("@+id/textView"))
        assertEquals("CopyMe", secondChild.attr("android:text"))
    }
}
