package com.codeforge.core.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class LspInfoFormatTest {
    @Test fun bytes() {
        assertEquals("512 B", LspInfoFormat.bytes(512, Locale.US))
        assertEquals("1.5 KB", LspInfoFormat.bytes(1536, Locale.US))
        assertEquals("245 MB", LspInfoFormat.bytes(245L * 1024 * 1024, Locale.US))
        assertEquals("1.2 GB", LspInfoFormat.bytes((1.2 * 1024 * 1024 * 1024).toLong(), Locale.US))
    }

    @Test fun duration() {
        assertEquals("0 s", LspInfoFormat.duration(-5))
        assertEquals("42 s", LspInfoFormat.duration(42_000))
        assertEquals("4 min 05 s", LspInfoFormat.duration(245_000))
        assertEquals("2 h 07 min", LspInfoFormat.duration(7_620_000))
        assertEquals("3 d 4 h", LspInfoFormat.duration((3 * 86_400 + 4 * 3_600) * 1000L))
    }
}
