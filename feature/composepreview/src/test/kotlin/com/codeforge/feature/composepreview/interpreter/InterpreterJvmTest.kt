/**
 * Modul: :feature:composepreview
 * @author Thomas Schmid
 */
package com.codeforge.feature.composepreview.interpreter

import org.junit.Assert.assertEquals
import org.junit.Test

class InterpreterJvmTest {

    private fun run(source: String, fn: String = "run"): Any? {
        val file = KtParser(source.trimIndent()).parseFile()
        val loader = javaClass.classLoader!!
        val index = MapClassIndex(ClassIndexes.jvmClassPathNames().asSequence())
        val interp = Interpreter(file, KotlinReflect(loader, index), PreviewResourceTable(emptyMap(), loader))
        return interp.invokeRoot(fn, null)
    }

    @Test
    fun `evaluates kotlin against compiled classes`() {
        val result = run(
            """
            package demo

            import com.codeforge.feature.composepreview.interpreter.fixtures.*

            fun run(): String {
                val people = listOf(Person("Anna"), Person("Ben", age = 40))
                val names = people.filter { it.age > 35 }.map { p -> p.name }
                val mood = Mood.HAPPY
                val text = when (mood) {
                    Mood.HAPPY -> ":)"
                    Mood.SAD -> ":("
                }
                var sum = 0
                for (i in 1..4) sum += i
                val d = (2.m + 3.m).value
                return "${'$'}{Config.greeting} ${'$'}{names.joinToString()} ${'$'}text ${'$'}sum ${'$'}{people[0].describe()} ${'$'}{5.doubled} ${'$'}{total(1, 2, 3)} ${'$'}d ${'$'}{Config.shout("ja")}"
            }
            """
        )
        assertEquals("Hallo Ben :) 10 Person Anna (30) 10 6 5.0 JA!", result)
    }

    @Test
    fun `supports local functions defaults named args and control flow`() {
        val result = run(
            """
            fun label(n: Int, unit: String = "x", upper: Boolean = false): String {
                val s = if (n == 1) "ein ${'$'}unit" else "${'$'}n ${'$'}{unit}e"
                return if (upper) s.uppercase() else s
            }

            fun run(): String {
                fun twice(v: Int) = v * 2
                val parts = mutableListOf<String>()
                parts += label(1)
                parts += label(twice(2), upper = true, unit = "Abo")
                var i = 0
                while (i < 3) { i++ }
                val first = parts.firstOrNull { it.startsWith("4") } ?: "keins"
                return parts.joinToString(separator = " | ") + " / " + first + " / " + i + " / " + (7 / 2) + " / " + (7 / 2.0)
            }
            """
        )
        assertEquals("ein x | 4 ABOE / 4 ABOE / 3 / 3 / 3.5", result)
    }
}
