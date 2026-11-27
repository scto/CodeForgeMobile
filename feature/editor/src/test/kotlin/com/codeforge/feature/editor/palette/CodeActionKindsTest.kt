package com.codeforge.feature.editor.palette

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeActionKindsTest {
    @Test
    fun refactorKindOrder() {
        assertTrue(codeActionKindRank("refactor.extract.function") < codeActionKindRank("refactor.inline"))
        assertTrue(codeActionKindRank("source.organizeImports") < codeActionKindRank("source.fixAll"))
        assertEquals(codeActionKindRank("quickfix"), codeActionKindRank(null))
    }
}
