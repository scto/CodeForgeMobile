/**
 * Modul: :feature:composepreview
 * @author Thomas Schmid
 */
package com.codeforge.feature.composepreview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

@Suppress("unused")
private fun privatePreviewSample(): String = "privat"

@Suppress("unused")
fun publicPreviewSample(): String = "öffentlich"

class PreviewMethodLookupTest {

    private val host: Class<*> = Class.forName("com.codeforge.feature.composepreview.PreviewMethodLookupTestKt")

    @Test
    fun `finds private top-level preview functions and makes them callable`() {
        val method = PreviewMethodLookup.find(host, "privatePreviewSample")
        assertNotNull(method)
        assertEquals("privat", method!!.invoke(null))
    }

    @Test
    fun `finds public functions and returns null for unknown names`() {
        assertEquals("öffentlich", PreviewMethodLookup.find(host, "publicPreviewSample")!!.invoke(null))
        assertNull(PreviewMethodLookup.find(host, "doesNotExist"))
    }
}
