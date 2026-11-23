/**
 * Modul: :feature:editor
 *
 * Abfrage-Prädikate, die sora-editor nicht mitbringt, die aber in den `highlights.scm` (nvim-treesitter-Stil)
 * vorkommen: `#eq?`/`#not-eq?`, `#any-of?`/`#not-any-of?`, `#lua-match?`/`#not-lua-match?`. Ohne sie würden die
 * betroffenen Muster ungeprüft gelten (z. B. jeder Bezeichner als eingebaute Funktion gefärbt).
 */
package com.codeforge.feature.editor.treesitter

import com.itsaky.androidide.treesitter.TSQuery
import com.itsaky.androidide.treesitter.TSQueryMatch
import com.itsaky.androidide.treesitter.TSQueryPredicateStep
import io.github.rosemoe.sora.editor.ts.predicate.PredicateResult
import io.github.rosemoe.sora.editor.ts.predicate.TsClientPredicateStep
import io.github.rosemoe.sora.editor.ts.predicate.TsPredicate
import io.github.rosemoe.sora.editor.ts.predicate.TsSyntheticCaptureContainer
import io.github.rosemoe.sora.editor.ts.predicate.builtin.getCaptureContent
import java.util.concurrent.ConcurrentHashMap

/** Reine Auswertung der Prädikate (ohne native Tree-sitter-Typen, testbar). */
internal object PredicateLogic {
    private val regexCache = ConcurrentHashMap<String, Regex>()

    /**
     * Wertet ein Prädikat aus. [contents] sind die Texte der ersten Capture, [args] die weiteren Argumente (Strings
     * oder Inhalte einer zweiten Capture). `null` = Prädikat unbekannt.
     */
    fun evaluate(name: String, contents: List<String>, args: List<String>): Boolean? {
        val negated = name.startsWith("not-")
        val result: Boolean? = when (name.removePrefix("not-")) {
            "eq?" -> contents.all { c -> args.firstOrNull() == c }
            "any-of?" -> contents.all { it in args }
            "lua-match?" -> args.firstOrNull()
                ?.let { pattern -> regexCache.getOrPut(pattern) { Regex(luaPatternToRegex(pattern)) } }
                ?.let { regex -> contents.all { regex.containsMatchIn(it) } }
            else -> null
        }
        return result?.let { it != negated }
    }

    /**
     * Übersetzt ein Lua-Muster in einen regulären Ausdruck: Zeichenklassen `%a %d %l %s %u %w %x %p %c`, Escapes
     * `%.`, Anker `^`/`$`, Mengen `[...]` und den Lazy-Quantor `-`.
     */
    fun luaPatternToRegex(pattern: String): String {
        val out = StringBuilder()
        var i = 0
        var inSet = false
        while (i < pattern.length) {
            val c = pattern[i]
            when {
                c == '%' && i + 1 < pattern.length -> {
                    out.append(luaClass(pattern[i + 1], inSet))
                    i++
                }
                c == '[' && !inSet -> {
                    inSet = true
                    out.append('[')
                }
                c == ']' && inSet -> {
                    inSet = false
                    out.append(']')
                }
                c == '-' && !inSet -> {
                    out.append("*?")
                }
                inSet -> {
                    out.append(if (c == '\\') "\\\\" else c)
                }
                c in "^$*+?()." -> {
                    out.append(c)
                }
                else -> {
                    out.append(Regex.escape(c.toString()).removePrefix("\\Q").removeSuffix("\\E"))
                }
            }
            i++
        }
        return out.toString()
    }

    private fun luaClass(c: Char, inSet: Boolean): String {
        val cls = when (c) {
            'a' -> "A-Za-z"
            'd' -> "0-9"
            'l' -> "a-z"
            'u' -> "A-Z"
            'w' -> "A-Za-z0-9"
            'x' -> "A-Fa-f0-9"
            's' -> "\\s"
            'p' -> "\\p{Punct}"
            'c' -> "\\p{Cntrl}"
            else -> null
        }
        return when {
            cls == null -> "\\" + c
            inSet || cls.startsWith("\\") -> cls
            else -> "[$cls]"
        }
    }
}

/** Prädikat für sora-editor, das die Auswertung an [PredicateLogic] übergibt. */
internal object ExtraPredicate : TsPredicate {
    private const val MIN_STEPS = 3
    private val NAMES = setOf("eq?", "not-eq?", "any-of?", "not-any-of?", "lua-match?", "not-lua-match?")

    override fun doPredicate(
        tsQuery: TSQuery,
        text: CharSequence,
        match: TSQueryMatch,
        predicateSteps: List<TsClientPredicateStep>,
        syntheticCaptures: TsSyntheticCaptureContainer,
    ): PredicateResult {
        val name = predicateSteps.firstOrNull()?.content?.takeIf { it in NAMES && predicateSteps.size >= MIN_STEPS }
        val first = predicateSteps.getOrNull(1)?.takeIf { it.predicateType == TSQueryPredicateStep.Type.Capture }
        if (name == null || first == null) return PredicateResult.UNHANDLED
        val contents = getCaptureContent(tsQuery, match, first.content, text)
        val args = predicateSteps.drop(2).flatMap { step ->
            when (step.predicateType) {
                TSQueryPredicateStep.Type.Capture -> getCaptureContent(tsQuery, match, step.content, text)
                TSQueryPredicateStep.Type.String -> listOf(step.content)
                else -> emptyList()
            }
        }
        return when (PredicateLogic.evaluate(name, contents, args)) {
            true -> PredicateResult.ACCEPT
            false -> PredicateResult.REJECT
            null -> PredicateResult.UNHANDLED
        }
    }
}
