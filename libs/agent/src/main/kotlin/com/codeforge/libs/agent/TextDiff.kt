/**
 * Modul: :libs:agent
 * @author Thomas Schmid
 *
 * Zeilenbasierter Unified-Diff für Freigabe-Vorschau und Änderungsübersicht.
 */
package com.codeforge.libs.agent

/** Erzeugt Unified-Diffs zweier Texte. */
object TextDiff {
    private const val CONTEXT = 2
    private const val LCS_LIMIT = 2_000_000L

    private enum class Op { KEEP, DEL, ADD }
    private class Line(val op: Op, val text: String)

    /**
     * Unified-Diff von [before] nach [after] (`null` = Datei existiert nicht); leer, wenn nichts abweicht.
     * Die Ausgabe wird nach [maxLines] Zeilen gekürzt.
     */
    fun unified(before: String?, after: String?, maxLines: Int = 200): String {
        if (before == after) return ""
        val a = before?.lines().orEmpty().let { if (before == "") emptyList() else it }
        val b = after?.lines().orEmpty().let { if (after == "") emptyList() else it }
        val script = script(a, b)
        val out = ArrayList<String>()
        var i = 0
        val n = script.size
        var oldNo = 1
        var newNo = 1
        // Positionen (alt/neu) je Skriptzeile vorab berechnen, damit Hunk-Köpfe stimmen.
        val oldAt = IntArray(n)
        val newAt = IntArray(n)
        for (k in 0 until n) {
            oldAt[k] = oldNo
            newAt[k] = newNo
            if (script[k].op != Op.ADD) oldNo++
            if (script[k].op != Op.DEL) newNo++
        }
        while (i < n) {
            if (script[i].op == Op.KEEP) { i++; continue }
            val start = maxOf(0, i - CONTEXT)
            var end = i
            var lastChange = i
            while (end < n && end - lastChange <= CONTEXT * 2) {
                if (script[end].op != Op.KEEP) lastChange = end
                end++
            }
            end = minOf(n, lastChange + CONTEXT + 1)
            val hunk = script.subList(start, end)
            val oldCount = hunk.count { it.op != Op.ADD }
            val newCount = hunk.count { it.op != Op.DEL }
            out += "@@ -${if (oldCount == 0) oldAt[start] - 1 else oldAt[start]},$oldCount +${if (newCount == 0) newAt[start] - 1 else newAt[start]},$newCount @@"
            hunk.forEach { out += (when (it.op) { Op.KEEP -> " "; Op.DEL -> "-"; Op.ADD -> "+" }) + it.text }
            i = end
        }
        return if (out.size > maxLines) out.take(maxLines).joinToString("\n") + "\n… (${out.size - maxLines} weitere Zeilen)" else out.joinToString("\n")
    }

    private fun script(a: List<String>, b: List<String>): List<Line> {
        var pre = 0
        while (pre < a.size && pre < b.size && a[pre] == b[pre]) pre++
        var suf = 0
        while (suf < a.size - pre && suf < b.size - pre && a[a.size - 1 - suf] == b[b.size - 1 - suf]) suf++
        val ma = a.subList(pre, a.size - suf)
        val mb = b.subList(pre, b.size - suf)
        val mid = if (ma.size.toLong() * mb.size > LCS_LIMIT) {
            ma.map { Line(Op.DEL, it) } + mb.map { Line(Op.ADD, it) }
        } else {
            lcs(ma, mb)
        }
        return a.subList(0, pre).map { Line(Op.KEEP, it) } + mid + a.subList(a.size - suf, a.size).map { Line(Op.KEEP, it) }
    }

    private fun lcs(a: List<String>, b: List<String>): List<Line> {
        val dp = Array(a.size + 1) { IntArray(b.size + 1) }
        for (i in a.indices.reversed()) for (j in b.indices.reversed()) {
            dp[i][j] = if (a[i] == b[j]) dp[i + 1][j + 1] + 1 else maxOf(dp[i + 1][j], dp[i][j + 1])
        }
        val out = ArrayList<Line>()
        var i = 0
        var j = 0
        while (i < a.size && j < b.size) {
            when {
                a[i] == b[j] -> { out += Line(Op.KEEP, a[i]); i++; j++ }
                dp[i + 1][j] >= dp[i][j + 1] -> out += Line(Op.DEL, a[i++])
                else -> out += Line(Op.ADD, b[j++])
            }
        }
        while (i < a.size) out += Line(Op.DEL, a[i++])
        while (j < b.size) out += Line(Op.ADD, b[j++])
        return out
    }
}
