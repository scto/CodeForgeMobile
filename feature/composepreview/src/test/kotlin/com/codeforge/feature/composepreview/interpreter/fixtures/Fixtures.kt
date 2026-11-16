/**
 * Modul: :feature:composepreview
 * @author Thomas Schmid
 */
package com.codeforge.feature.composepreview.interpreter.fixtures

/** Testklassen, die der Interpreter als „kompilierte Nachbarn“ aufruft. */
data class Person(val name: String, val age: Int = 30)

enum class Mood { HAPPY, SAD }

object Config {
    val greeting = "Hallo"
    fun shout(s: String) = s.uppercase() + "!"
}

fun Person.describe(prefix: String = "Person"): String = "$prefix $name ($age)"

val Int.doubled: Int get() = this * 2

@JvmInline
value class Meters(val value: Float) {
    operator fun plus(other: Meters) = Meters(value + other.value)
}

val Int.m: Meters get() = Meters(toFloat())

fun total(vararg xs: Int): Int = xs.sum()
