package com.codeforge.libs.code_tools.search

import java.util.regex.Matcher
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/** Suchoptionen — gelten identisch für Einzeldatei- und Projektsuche. */
data class SearchOptions(
    val query: String,
    val caseSensitive: Boolean = false,
    val regex: Boolean = false,
    val wholeWord: Boolean = false,
)

/** Ein Treffer. [line] und [column] sind 0-basiert; [lineText] ist die komplette Trefferzeile. */
data class TextMatch(
    val start: Int,
    val end: Int,
    val line: Int,
    val column: Int,
    val lineText: String,
    val matchText: String,
)

data class ReplaceResult(val text: String, val count: Int)

class SearchTimeoutException : RuntimeException("Suche abgebrochen (Zeitlimit — evtl. zu aufwendiger Regex)")

/** Baut aus [SearchOptions] ein [Pattern]; liefert bei ungültigem Regex eine verständliche Fehlermeldung. */
fun SearchOptions.compile(): Result<Pattern> {
    if (query.isEmpty()) return Result.failure(IllegalArgumentException("Suchbegriff ist leer"))
    return try {
        val body = if (regex) query else Pattern.quote(query)
        val wrapped = if (wholeWord) "(?<![\\p{L}\\p{N}_])(?:$body)(?![\\p{L}\\p{N}_])" else body
        var flags = Pattern.MULTILINE
        if (!caseSensitive) flags = flags or Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE
        Result.success(Pattern.compile(wrapped, flags))
    } catch (e: PatternSyntaxException) {
        Result.failure(IllegalArgumentException("Ungültiger Regex: ${e.description}", e))
    }
}

/** CharSequence, die nach Ablauf eines Zeitlimits eine [SearchTimeoutException] wirft (Schutz vor Regex-Backtracking). */
private class DeadlineCharSequence(private val inner: CharSequence, private val deadlineNanos: Long) : CharSequence {
    private var counter = 0
    override val length: Int get() = inner.length
    override fun get(index: Int): Char {
        if ((++counter and 0xFFFF) == 0 && System.nanoTime() > deadlineNanos) throw SearchTimeoutException()
        return inner[index]
    }
    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence =
        DeadlineCharSequence(inner.subSequence(startIndex, endIndex), deadlineNanos)
    override fun toString(): String = inner.toString()
}

object TextSearch {

    const val DEFAULT_TIMEOUT_MS = 2_000L

    /** Alle nicht-leeren Treffer in [text]; bei mehr als [limit] Treffern wird abgeschnitten. */
    fun findAll(
        text: String,
        options: SearchOptions,
        limit: Int = Int.MAX_VALUE,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): Result<List<TextMatch>> = runCatching {
        val pattern = options.compile().getOrThrow()
        val matcher = pattern.matcher(guard(text, timeoutMs))
        val index = LineIndex(text)
        val result = ArrayList<TextMatch>()
        while (result.size < limit && matcher.find()) {
            if (matcher.end() == matcher.start()) continue
            result += index.toMatch(text, matcher.start(), matcher.end())
        }
        result
    }

    /** Ersetzt alle Treffer. Im Regex-Modus sind `$1`/`${name}` in [replacement] Gruppenreferenzen. */
    fun replaceAll(
        text: String,
        options: SearchOptions,
        replacement: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): Result<ReplaceResult> = runCatching {
        val pattern = options.compile().getOrThrow()
        val matcher = pattern.matcher(guard(text, timeoutMs))
        val out = StringBuilder()
        var count = 0
        var last = 0
        while (matcher.find()) {
            if (matcher.end() == matcher.start()) continue
            out.append(text, last, matcher.start())
            out.append(expand(matcher, options, replacement))
            last = matcher.end()
            count++
        }
        out.append(text, last, text.length)
        ReplaceResult(if (count == 0) text else out.toString(), count)
    }

    /** Ersetzt genau den Treffer, der bei [matchStart] beginnt. */
    fun replaceAt(
        text: String,
        options: SearchOptions,
        replacement: String,
        matchStart: Int,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): Result<ReplaceResult> = runCatching {
        val pattern = options.compile().getOrThrow()
        val matcher = pattern.matcher(guard(text, timeoutMs))
        matcher.region(matchStart, text.length)
        matcher.useTransparentBounds(true)
        matcher.useAnchoringBounds(false)
        if (!matcher.lookingAt() || matcher.end() == matcher.start()) return@runCatching ReplaceResult(text, 0)
        val expanded = expand(matcher, options, replacement)
        ReplaceResult(text.substring(0, matcher.start()) + expanded + text.substring(matcher.end()), 1)
    }

    /** Vorschau des Ersetzungstexts für einen Treffer (für die Ergebnisliste). */
    fun previewReplacement(match: TextMatch, options: SearchOptions, replacement: String): String {
        if (!options.regex) return replacement
        val pattern = options.compile().getOrNull() ?: return replacement
        val m = pattern.matcher(match.matchText)
        return if (m.matches() || m.find()) runCatching { expand(m, options, replacement) }.getOrDefault(replacement) else replacement
    }

    private sealed interface Token {
        class Literal(val text: String) : Token
        class Group(val index: Int) : Token
        class Named(val name: String) : Token
    }

    /** Zerlegt die Ersetzung (`$1`, `${name}`, `\\x`); wirft [IllegalArgumentException] bei Syntaxfehlern. */
    private fun parseReplacement(replacement: String, groupCount: Int): List<Token> {
        val tokens = ArrayList<Token>()
        val lit = StringBuilder()
        fun flush() { if (lit.isNotEmpty()) { tokens += Token.Literal(lit.toString()); lit.setLength(0) } }
        var i = 0
        while (i < replacement.length) {
            val c = replacement[i]
            when {
                c == '\\' -> {
                    require(i + 1 < replacement.length) { "Ungültige Ersetzung: Backslash am Ende" }
                    lit.append(replacement[i + 1]); i += 2
                }
                c == '$' -> {
                    require(i + 1 < replacement.length) { "Ungültige Ersetzung: '$' am Ende" }
                    flush()
                    if (replacement[i + 1] == '{') {
                        val close = replacement.indexOf('}', i + 2)
                        require(close > 0) { "Ungültige Ersetzung: '}' fehlt" }
                        tokens += Token.Named(replacement.substring(i + 2, close))
                        i = close + 1
                    } else {
                        require(replacement[i + 1].isDigit()) { "Ungültige Ersetzung: Gruppenreferenz nach '$' erwartet" }
                        var ref = replacement[i + 1] - '0'
                        require(ref <= groupCount) { "Gruppe $ref existiert nicht (Muster hat $groupCount)" }
                        i += 2
                        while (i < replacement.length && replacement[i].isDigit()) {
                            val next = ref * 10 + (replacement[i] - '0')
                            if (next > groupCount) break
                            ref = next; i++
                        }
                        tokens += Token.Group(ref)
                    }
                }
                else -> { lit.append(c); i++ }
            }
        }
        flush()
        return tokens
    }

    /** Prüft Ersetzungssyntax gegen das Suchmuster, ohne Text zu brauchen (vor „Alle ersetzen“ im Projekt). */
    fun validateReplacement(options: SearchOptions, replacement: String): Result<Unit> = runCatching {
        val pattern = options.compile().getOrThrow()
        if (!options.regex) return@runCatching
        val tokens = parseReplacement(replacement, pattern.matcher("").groupCount())
        tokens.filterIsInstance<Token.Named>().forEach {
            require(options.query.contains("(?<${it.name}>")) { "Gruppe „${it.name}“ existiert nicht" }
        }
    }

    private fun expand(matcher: Matcher, options: SearchOptions, replacement: String): String {
        if (!options.regex) return replacement
        val out = StringBuilder()
        for (t in parseReplacement(replacement, matcher.groupCount())) {
            when (t) {
                is Token.Literal -> out.append(t.text)
                is Token.Group -> out.append(matcher.group(t.index) ?: "")
                is Token.Named -> out.append(matcher.group(t.name) ?: "")
            }
        }
        return out.toString()
    }

    private fun guard(text: String, timeoutMs: Long): CharSequence =
        if (timeoutMs <= 0) text else DeadlineCharSequence(text, System.nanoTime() + timeoutMs * 1_000_000L)
}

/** Offset → (Zeile, Spalte) per Binärsuche über Zeilenanfänge. */
internal class LineIndex(text: String) {
    private val starts: IntArray = run {
        val list = ArrayList<Int>()
        list += 0
        for (i in text.indices) if (text[i] == '\n') list += i + 1
        list.toIntArray()
    }

    fun lineOf(offset: Int): Int {
        var lo = 0
        var hi = starts.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (starts[mid] <= offset) lo = mid else hi = mid - 1
        }
        return lo
    }

    fun toMatch(text: String, start: Int, end: Int): TextMatch {
        val line = lineOf(start)
        val lineStart = starts[line]
        var lineEnd = text.indexOf('\n', lineStart).let { if (it < 0) text.length else it }
        if (lineEnd > lineStart && text[lineEnd - 1] == '\r') lineEnd--
        val lineText = text.substring(lineStart, lineEnd)
        return TextMatch(start, end, line, start - lineStart, lineText, text.substring(start, end))
    }
}
