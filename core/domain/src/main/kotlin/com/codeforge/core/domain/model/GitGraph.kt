// Modul: :core:domain
package com.codeforge.core.domain.model

/** Kante eines Graph-Zeilenausschnitts; Lanes sind Spaltenindizes. */
data class GraphEdge(val fromLane: Int, val toLane: Int, val colorLane: Int, val kind: Kind) {
    /** THROUGH: oben→unten durchlaufend; IN: von oben zum Knoten; OUT: vom Knoten nach unten. */
    enum class Kind { THROUGH, IN, OUT }
}

data class GraphRow(val commit: GitCommitInfo, val lane: Int, val edges: List<GraphEdge>, val laneCount: Int)

/**
 * Lane-Zuweisung wie bei `git log --graph`. Erwartet Commits in topologischer Reihenfolge
 * (Kinder vor Eltern, neueste zuerst).
 */
object GitGraphLayout {

    fun layout(commits: List<GitCommitInfo>): List<GraphRow> {
        val active = ArrayList<String?>()
        val rows = ArrayList<GraphRow>(commits.size)

        fun freeLane(): Int {
            val idx = active.indexOf(null)
            return if (idx >= 0) idx else { active += null; active.size - 1 }
        }

        for (commit in commits) {
            val edges = ArrayList<GraphEdge>()
            val matches = active.indices.filter { active[it] == commit.hash }
            val lane = matches.firstOrNull() ?: freeLane()
            var width = maxOf(active.size, lane + 1)

            for (i in active.indices) {
                when {
                    i in matches -> edges += GraphEdge(i, lane, i, GraphEdge.Kind.IN)
                    active[i] != null -> edges += GraphEdge(i, i, i, GraphEdge.Kind.THROUGH)
                }
            }
            matches.forEach { active[it] = null }

            commit.parents.forEachIndexed { index, parent ->
                val existing = active.indexOf(parent)
                val target = when {
                    existing >= 0 -> existing
                    index == 0 -> lane.also { active[lane] = parent }
                    else -> freeLane().also { active[it] = parent }
                }
                edges += GraphEdge(lane, target, target, GraphEdge.Kind.OUT)
                width = maxOf(width, active.size, target + 1)
            }
            while (active.isNotEmpty() && active.last() == null) active.removeAt(active.size - 1)
            rows += GraphRow(commit, lane, edges, width)
        }
        return rows
    }
}
