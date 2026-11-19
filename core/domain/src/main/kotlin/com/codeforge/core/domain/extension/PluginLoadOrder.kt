/**
 * Modul: :core:domain
 * @author Thomas Schmid
 */
package com.codeforge.core.domain.extension

/** Ladereihenfolge und Abhängige von Plugins nach `dependsOn`; rein, ohne Android-Abhängigkeit. */
object PluginLoadOrder {

    /** Ergebnis von [sort]: Reihenfolge und Plugins in einem Abhängigkeitszyklus (werden nicht geladen). */
    data class Result(val order: List<String>, val cycles: Set<String>)

    /**
     * Sortiert [dependsOn] (ID → Abhängigkeiten) topologisch: Abhängigkeiten zuerst, sonst stabile Eingabereihenfolge.
     * Abhängigkeiten, die nicht in der Map stehen, werden ignoriert (das meldet [PluginCompatibility]).
     */
    fun sort(dependsOn: Map<String, List<String>>): Result {
        val state = HashMap<String, Int>() // 0 = offen, 1 = in Arbeit, 2 = fertig
        val order = ArrayList<String>()
        val cycles = LinkedHashSet<String>()

        fun visit(id: String, path: ArrayDeque<String>) {
            if (state[id] == 2) return
            if (state[id] == 1) {
                cycles += path.dropWhile { it != id }
                return
            }
            state[id] = 1
            path.addLast(id)
            dependsOn[id].orEmpty().filter { it in dependsOn }.forEach { visit(it, path) }
            path.removeLast()
            state[id] = 2
            order += id
        }
        dependsOn.keys.forEach { visit(it, ArrayDeque()) }
        // Was von einem Zyklus abhängt, kann ebenfalls nicht geladen werden.
        val unloadable = cycles + order.filter { id -> requiresAny(id, cycles, dependsOn) }
        return Result(order.filterNot { it in unloadable }, unloadable)
    }

    /** Alle Plugins, die direkt oder indirekt von [pluginId] abhängen (ohne [pluginId] selbst). */
    fun dependentsClosure(pluginId: String, dependsOn: Map<String, List<String>>): Set<String> {
        val result = LinkedHashSet<String>()
        val queue = ArrayDeque(listOf(pluginId))
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            dependsOn.filter { (id, deps) -> current in deps && id !in result && id != pluginId }.keys.forEach {
                result += it
                queue += it
            }
        }
        return result
    }

    private fun requiresAny(id: String, targets: Set<String>, dependsOn: Map<String, List<String>>): Boolean {
        val seen = HashSet<String>()
        val queue = ArrayDeque(dependsOn[id].orEmpty())
        while (queue.isNotEmpty()) {
            val dep = queue.removeFirst()
            if (dep in targets) return true
            if (seen.add(dep)) queue += dependsOn[dep].orEmpty()
        }
        return false
    }

    /** Alle (transitiven) Abhängigkeiten von [pluginId], ohne das Plugin selbst; Zyklen werden toleriert. */
    fun dependenciesClosure(pluginId: String, dependsOn: Map<String, List<String>>): Set<String> {
        val out = LinkedHashSet<String>()
        val stack = ArrayDeque(dependsOn[pluginId].orEmpty())
        while (stack.isNotEmpty()) {
            val id = stack.removeFirst()
            if (id != pluginId && out.add(id)) stack.addAll(dependsOn[id].orEmpty())
        }
        return out
    }
}
