package com.codeforge.libs.x2c.apply

import com.codeforge.core.domain.model.X2cPlan
import com.codeforge.core.domain.model.XmlRemovalSelection
import com.codeforge.core.domain.repository.LayoutUsageAnalyzer
import com.codeforge.libs.x2c.analysis.LayoutUsageAnalyzerImpl

/**
 * Task 52.9: Welche XML-Layouts sind nach der Umstellung **nirgends mehr** verwendet?
 *
 * Prüft den Stand auf der Festplatte erneut (nach dem Anwenden), also ohne die schon umgestellten Verwender.
 * Verwendungen aus Layouts, die ebenfalls entfernt werden (z. B. `<include>`), zählen nicht (Fixpunkt). RemoteViews,
 * Binding, `R.layout`, `include`, Manifest/Navigation und `tools:layout` gelten als Verwendung.
 */
class XmlRemovalSelector(private val analyzer: LayoutUsageAnalyzer = LayoutUsageAnalyzerImpl()) {

    suspend fun select(projectPath: String, plan: X2cPlan, convertedLayouts: Set<String>): XmlRemovalSelection {
        val pathsByName = plan.layouts.groupBy { it.layoutName }.mapValues { (_, l) -> l.map { it.relativePath } }
        val candidates = convertedLayouts.filter { it in pathsByName }.toMutableSet()
        val usages = analyzer.findAllUsages(projectPath, candidates.associateWith { emptySet<String>() })
        val kept = LinkedHashMap<String, String>()
        var changed = true
        while (changed) {
            changed = false
            for (name in candidates.toList()) {
                val own = pathsByName.getValue(name).toSet()
                val blocking = usages[name].orEmpty().firstOrNull { u ->
                    u.filePath !in own && !isRemovedLayoutFile(u.filePath, candidates, pathsByName)
                }
                if (blocking != null) {
                    candidates -= name
                    kept[name] = "${blocking.filePath}:${blocking.lineNumber} (${blocking.usageType})"
                    changed = true
                }
            }
        }
        for (name in convertedLayouts - candidates - kept.keys) kept.putIfAbsent(name, "unbekanntes Layout")
        val paths = candidates.sorted().flatMap { pathsByName.getValue(it) }.distinct().sorted()
        return XmlRemovalSelection(paths, kept)
    }

    private fun isRemovedLayoutFile(path: String, candidates: Set<String>, pathsByName: Map<String, List<String>>) =
        candidates.any { path in pathsByName.getValue(it) }
}
