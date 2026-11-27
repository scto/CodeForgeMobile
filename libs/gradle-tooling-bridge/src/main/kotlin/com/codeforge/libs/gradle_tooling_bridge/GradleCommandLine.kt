// Modul: :libs:gradle-tooling-bridge
package com.codeforge.libs.gradle_tooling_bridge

/**
 * Zerlegt einen Gradle-Aufruf wie eine Shell in Argumente: Leerraum trennt, `"…"` und `'…'` gruppieren und werden
 * entfernt, `\` maskiert das nächste Zeichen außerhalb einfacher Anführungszeichen. Ohne das kam z. B.
 * `--tests "com.example.FooTest"` mit Anführungszeichen bei Gradle an, und der Filter griff nie.
 */
object GradleCommandLine {

    /** Argumente von [command]; leere Anführungszeichen ergeben ein leeres Argument. */
    fun split(command: String): List<String> {
        val state = State()
        var i = 0
        while (i < command.length) {
            i = state.consume(command, i)
        }
        state.flush()
        return state.args
    }

    private class State {
        val args = mutableListOf<String>()
        private val current = StringBuilder()
        private var inArg = false
        private var quote: Char? = null

        // Verarbeitet das Zeichen an [i] und liefert die nächste Position.
        fun consume(s: String, i: Int): Int {
            val c = s[i]
            val q = quote
            return when {
                q != null -> {
                    quoted(s, i, q)
                }
                c == '\\' && i + 1 < s.length -> {
                    escape(s, i)
                }
                c == '"' || c == '\'' -> {
                    quote = c
                    inArg = true
                    i + 1
                }
                c.isWhitespace() -> {
                    flush()
                    i + 1
                }
                else -> {
                    current.append(c)
                    inArg = true
                    i + 1
                }
            }
        }

        // Innerhalb von Anführungszeichen: nur das schließende Zeichen und (bei "…") \ sind besonders.
        private fun quoted(s: String, i: Int, q: Char): Int {
            val c = s[i]
            return when {
                c == q -> {
                    quote = null
                    i + 1
                }
                c == '\\' && q == '"' && i + 1 < s.length -> {
                    escape(s, i)
                }
                else -> {
                    current.append(c)
                    i + 1
                }
            }
        }

        private fun escape(s: String, i: Int): Int {
            current.append(s[i + 1])
            inArg = true
            return i + 2
        }

        fun flush() {
            if (inArg) args += current.toString()
            current.clear()
            inArg = false
        }
    }
}
