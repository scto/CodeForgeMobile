package com.codeforge.core.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class SafTreePathTest {
    private val primary = File("/storage/emulated/0")

    @Test fun `primary volume maps to external storage root`() {
        val f = SafTreePath.resolve(SafTreePath.EXTERNAL_STORAGE_AUTHORITY, "primary:Projekte/App", primary)
        assertEquals(File("/storage/emulated/0/Projekte/App"), f)
    }

    @Test fun `sd card volume maps below storage`() {
        val f = SafTreePath.resolve(SafTreePath.EXTERNAL_STORAGE_AUTHORITY, "1A2B-3C4D:Dev", primary)
        assertEquals(File("/storage/1A2B-3C4D/Dev"), f)
    }

    @Test fun `volume root without relative part`() {
        assertEquals(primary, SafTreePath.resolve(SafTreePath.EXTERNAL_STORAGE_AUTHORITY, "primary:", primary))
    }

    @Test fun `foreign provider traversal and garbage are rejected`() {
        assertNull(SafTreePath.resolve("com.google.android.apps.docs.storage", "primary:A", primary))
        assertNull(SafTreePath.resolve(SafTreePath.EXTERNAL_STORAGE_AUTHORITY, "primary:../etc", primary))
        assertNull(SafTreePath.resolve(SafTreePath.EXTERNAL_STORAGE_AUTHORITY, "nodelimiter", primary))
        assertNull(SafTreePath.resolve(SafTreePath.EXTERNAL_STORAGE_AUTHORITY, null, primary))
    }
}
