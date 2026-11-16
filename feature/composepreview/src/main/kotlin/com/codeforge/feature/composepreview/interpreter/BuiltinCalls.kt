/**
 * Modul: :feature:composepreview
 * @author Thomas Schmid
 */
package com.codeforge.feature.composepreview.interpreter

/** Letzte Instanz für grundlegende Aufrufe, die weder als Mitglied noch als Extension gefunden wurden. */
internal object BuiltinCalls {
    fun call(interp: Interpreter, recv: Any, name: String, args: List<EvalArg>, f: Frame): Interpreter.Result? {
        val a = args.map { it.value }
        return when {
            name == "toString" && a.isEmpty() -> Interpreter.Result(interp.stringOf(recv))
            name == "hashCode" && a.isEmpty() -> Interpreter.Result(recv.hashCode())
            name == "equals" && a.size == 1 -> Interpreter.Result(interp.valueEquals(recv, a[0]))
            name == "compareTo" && a.size == 1 && recv is Comparable<*> -> {
                @Suppress("UNCHECKED_CAST") Interpreter.Result((recv as Comparable<Any?>).compareTo(a[0]))
            }
            name.startsWith("component") && a.isEmpty() -> name.removePrefix("component").toIntOrNull()?.let { Interpreter.Result(interp.component(recv, it)) }
            recv is kotlin.Function<*> && name == "invoke" -> Interpreter.Result(interp.invokeValue(recv, args, f, -1))
            // Zahlen-Konvertierungen (Mitglieder der Kotlin-Zahltypen fehlen in Java)
            recv is Number && a.isEmpty() -> when (name) {
                "toInt" -> Interpreter.Result(recv.toInt())
                "toLong" -> Interpreter.Result(recv.toLong())
                "toFloat" -> Interpreter.Result(recv.toFloat())
                "toDouble" -> Interpreter.Result(recv.toDouble())
                "toShort" -> Interpreter.Result(recv.toShort())
                "toByte" -> Interpreter.Result(recv.toByte())
                "toChar" -> Interpreter.Result(recv.toInt().toChar())
                "unaryMinus" -> Interpreter.Result(Numbers.arithmetic("-", 0, recv))
                else -> null
            }
            recv is Number && a.size == 1 && a[0] is Number -> when (name) {
                "plus" -> Interpreter.Result(Numbers.arithmetic("+", recv, a[0] as Number))
                "minus" -> Interpreter.Result(Numbers.arithmetic("-", recv, a[0] as Number))
                "times" -> Interpreter.Result(Numbers.arithmetic("*", recv, a[0] as Number))
                "div" -> Interpreter.Result(Numbers.arithmetic("/", recv, a[0] as Number))
                "rem" -> Interpreter.Result(Numbers.arithmetic("%", recv, a[0] as Number))
                else -> null
            }
            recv is Char && a.isEmpty() && name == "toInt" -> Interpreter.Result(recv.code)
            recv is Boolean && a.size == 1 && a[0] is Boolean -> when (name) {
                "and" -> Interpreter.Result(recv && a[0] as Boolean)
                "or" -> Interpreter.Result(recv || a[0] as Boolean)
                "xor" -> Interpreter.Result(recv xor (a[0] as Boolean))
                else -> null
            }
            recv is Boolean && a.isEmpty() && name == "not" -> Interpreter.Result(!recv)
            else -> null
        }
    }
}
