// Modul: :core:domain
package com.codeforge.core.domain.model

enum class WordDiffType {
    UNCHANGED,
    ADDED,
    REMOVED
}

data class WordDiffSpan(
    val text: String,
    val type: WordDiffType
)

object WordDiff {

    private val TOKEN_REGEX = Regex("""\w+|[^\w\s]+|\s+""")
    private const val MAX_COMPARE_MATRIX = 20000

    fun tokenize(text: String): List<String> =
        TOKEN_REGEX.findAll(text).map { it.value }.toList()

    /**
     * Vergleicht zwei Texte wort- und zeichenweise.
     * @return Pair von (oursSpans mit REMOVED für gelöschte Wörter, theirsSpans mit ADDED für neue Wörter).
     */
    fun diffWords(oursText: String, theirsText: String): Pair<List<WordDiffSpan>, List<WordDiffSpan>> {
        val oursTokens = tokenize(oursText)
        val theirsTokens = tokenize(theirsText)

        if (oursTokens.size * theirsTokens.size > MAX_COMPARE_MATRIX) {
            // Bei riesigen Blöcken einfacher Fallback: Alles als geändert markieren
            return oursTokens.map { WordDiffSpan(it, WordDiffType.REMOVED) } to
                theirsTokens.map { WordDiffSpan(it, WordDiffType.ADDED) }
        }

        val lcs = computeLcs(oursTokens, theirsTokens)

        val oursSpans = mutableListOf<WordDiffSpan>()
        var lcsIdx = 0
        for (token in oursTokens) {
            if (lcsIdx < lcs.size && token == lcs[lcsIdx]) {
                oursSpans.add(WordDiffSpan(token, WordDiffType.UNCHANGED))
                lcsIdx++
            } else {
                oursSpans.add(WordDiffSpan(token, WordDiffType.REMOVED))
            }
        }

        val theirsSpans = mutableListOf<WordDiffSpan>()
        lcsIdx = 0
        for (token in theirsTokens) {
            if (lcsIdx < lcs.size && token == lcs[lcsIdx]) {
                theirsSpans.add(WordDiffSpan(token, WordDiffType.UNCHANGED))
                lcsIdx++
            } else {
                theirsSpans.add(WordDiffSpan(token, WordDiffType.ADDED))
            }
        }

        return oursSpans to theirsSpans
    }

    private fun computeLcs(a: List<String>, b: List<String>): List<String> {
        val m = a.size
        val n = b.size
        val dp = Array(m + 1) { IntArray(n + 1) }
        for (i in 0 until m) {
            for (j in 0 until n) {
                if (a[i] == b[j]) {
                    dp[i + 1][j + 1] = dp[i][j] + 1
                } else {
                    dp[i + 1][j + 1] = maxOf(dp[i + 1][j], dp[i][j + 1])
                }
            }
        }

        val lcs = mutableListOf<String>()
        var i = m
        var j = n
        while (i > 0 && j > 0) {
            if (a[i - 1] == b[j - 1]) {
                lcs.add(a[i - 1])
                i--
                j--
            } else if (dp[i - 1][j] >= dp[i][j - 1]) {
                i--
            } else {
                j--
            }
        }
        lcs.reverse()
        return lcs
    }
}
