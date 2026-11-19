/**
 * Plugin: Python-Tests
 */
package com.codeforge.plugin.pytest

/** Gefundener Test: [id] ist die pytest-Knoten-ID innerhalb der Datei (`test_x` oder `TestK::test_y`). */
data class FoundTest(val id: String, val line: Int)

object Pytest {
    private val FUNCTION = Regex("^(\\s*)(?:async\\s+)?def\\s+(test\\w*)\\s*\\(")
    private val CLASS = Regex("^class\\s+(Test\\w*)\\s*[(:]")
    private val RESULT = Regex("^(PASSED|FAILED|ERROR|SKIPPED|XFAIL|XPASS)\\s+(\\S+?)(?:\\s+-\\s+(.*))?$")

    fun isTestFile(name: String): Boolean = name.endsWith(".py") && (name.startsWith("test_") || name.endsWith("_test.py"))

    fun discover(text: String): List<FoundTest> {
        val out = ArrayList<FoundTest>()
        var currentClass: String? = null
        text.lines().forEachIndexed { i, line ->
            CLASS.find(line)?.let { currentClass = it.groupValues[1]; return@forEachIndexed }
            if (line.isNotBlank() && !line[0].isWhitespace() && !line.startsWith("@")) currentClass = null
            FUNCTION.find(line)?.let { m ->
                val indented = m.groupValues[1].isNotEmpty()
                val cls = currentClass
                when {
                    !indented -> out += FoundTest(m.groupValues[2], i)
                    cls != null -> out += FoundTest("$cls::${m.groupValues[2]}", i)
                }
            }
        }
        return out
    }

    /** Auswertung von `pytest -rA`: Zeilen wie `PASSED test_x.py::test_a` oder `FAILED test_x.py::K::test_b - assert …`. */
    fun results(output: List<String>): List<Triple<String, Boolean, String?>> = output.mapNotNull { line ->
        val m = RESULT.find(line.trim()) ?: return@mapNotNull null
        val id = m.groupValues[2].substringAfter(".py::", m.groupValues[2])
        val ok = m.groupValues[1] in setOf("PASSED", "XFAIL", "SKIPPED")
        Triple(id, ok, m.groupValues[3].ifEmpty { null })
    }
}
