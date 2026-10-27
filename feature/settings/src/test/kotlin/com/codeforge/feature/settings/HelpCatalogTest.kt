package com.codeforge.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HelpCatalogTest {
    private val sections = HelpCatalog.sections

    @Test
    fun everySectionHasEntries() {
        assertTrue(sections.size >= 9)
        sections.forEach { assertTrue(it.entries.isNotEmpty()) }
    }

    @Test
    fun descriptionsAndTitlesAreUnique() {
        val desc = sections.flatMap { it.entries }.map { it.descriptionRes }
        assertEquals(desc.size, desc.toSet().size)
        val titles = sections.map { it.titleRes }
        assertEquals(titles.size, titles.toSet().size)
    }
}
