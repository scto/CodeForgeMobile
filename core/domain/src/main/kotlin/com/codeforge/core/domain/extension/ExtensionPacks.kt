/**
 * Modul: :core:domain
 * @author Thomas Schmid
 */
package com.codeforge.core.domain.extension

import com.codeforge.core.domain.model.InstalledPlugin

/** Bundles (`contributes.extensionPack`): ein Plugin, das weitere Plugins als Mitglieder benennt. */
object ExtensionPacks {

    /** Mitglieder von [pack], die noch nicht installiert sind (Reihenfolge wie im Manifest, ohne Duplikate/Selbstverweis). */
    fun missingMembers(pack: InstalledPlugin, installed: Collection<InstalledPlugin>): List<String> {
        val have = installed.mapTo(HashSet()) { it.id }
        return pack.packMembers.distinct().filter { it != pack.id && it !in have }
    }

    /**
     * Alle (auch indirekt) benötigten IDs eines Bundles aus einem Katalog `id -> Mitglieder`, ohne Zyklen und
     * ohne bereits installierte; Tiefe auf [maxDepth] begrenzt. Basis für die Installation aus dem Store.
     */
    fun resolveClosure(
        rootId: String,
        membersOf: (String) -> List<String>,
        installedIds: Set<String>,
        maxDepth: Int = 4
    ): List<String> {
        val order = LinkedHashSet<String>()
        val seen = HashSet<String>().apply { add(rootId) }
        fun visit(id: String, depth: Int) {
            if (depth > maxDepth) return
            for (m in membersOf(id)) {
                if (!seen.add(m)) continue
                visit(m, depth + 1)
                if (m !in installedIds) order.add(m)
            }
        }
        visit(rootId, 1)
        return order.toList()
    }
}
