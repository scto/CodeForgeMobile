package com.codeforge.feature.settings.extensions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LspUpdateCheckTest {
    @Test
    fun versionCompare() {
        assertTrue(VersionCompare.isNewer("1.3.12", "1.3.11"))
        assertTrue(VersionCompare.isNewer("v2.0.0", "1.9.9"))
        assertTrue(VersionCompare.isNewer("1.10", "1.9"))
        assertFalse(VersionCompare.isNewer("1.3.11", "1.3.11"))
        assertFalse(VersionCompare.isNewer("1.3", "1.3.0"))
        assertFalse(VersionCompare.isNewer("1.0.0", "1.2"))
        assertFalse(VersionCompare.isNewer("abc", "1.0"))
    }

    @Test
    fun parsers() {
        assertEquals("1.1.400", UpdateParsers.npmLatest("""{"name":"pyright","version":"1.1.400","x":1}"""))
        assertEquals("1.3.12", UpdateParsers.githubReleaseTag("""{"tag_name":"1.3.12"}"""))
        assertEquals("v1.46.0", UpdateParsers.githubTags("""[{"name":"latest"},{"name":"v1.46.0"},{"name":"v1.45.0"}]"""))
        assertNull(UpdateParsers.npmLatest("kaputt"))
    }

    @Test
    fun aptPolicy() {
        val out = "clang:\n  Installed: 18.1.3\n  Candidate: 19.1.0\n  Version table:\n"
        assertEquals("18.1.3" to "19.1.0", UpdateParsers.aptPolicy(out))
        assertEquals(null to "1.0", UpdateParsers.aptPolicy("x:\n  Installed: (none)\n  Candidate: 1.0\n"))
    }

    @Test
    fun everyCatalogPackageHasInfo() {
        LspExtensionCatalog.ALL.forEach { assertTrue(it.id, LspServerInfoCatalog.of(it.id) != null) }
    }
}
