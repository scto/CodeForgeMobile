package com.codeforge.plugin.pytest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PytestTest {
    @Test
    fun `Tests in Funktionen und Klassen`() {
        val src = """
            import pytest

            def test_add():
                assert 1 + 1 == 2

            def helper():
                pass

            class TestMath:
                def test_mul(self):
                    assert 2 * 2 == 4

                def setup_method(self):
                    pass

            @pytest.mark.asyncio
            async def test_async():
                pass
        """.trimIndent()
        assertEquals(listOf("test_add", "TestMath::test_mul", "test_async"), Pytest.discover(src).map { it.id })
        assertEquals(2, Pytest.discover(src)[0].line)
    }

    @Test
    fun `Dateinamen und Ergebnisse`() {
        assertTrue(Pytest.isTestFile("test_app.py"))
        assertTrue(Pytest.isTestFile("app_test.py"))
        assertFalse(Pytest.isTestFile("app.py"))
        val r = Pytest.results(listOf("PASSED test_a.py::test_add", "FAILED test_a.py::TestMath::test_mul - assert 5 == 4", "noise"))
        assertEquals(listOf(Triple("test_add", true, null), Triple("TestMath::test_mul", false, "assert 5 == 4")), r)
    }
}
