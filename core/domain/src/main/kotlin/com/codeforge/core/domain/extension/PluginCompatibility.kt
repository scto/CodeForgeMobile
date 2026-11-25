/**
 * Modul: :core:domain
 * @author Thomas Schmid
 */
package com.codeforge.core.domain.extension

/** Grund, warum ein Plugin nicht (vollständig) nutzbar ist. */
sealed interface PluginIssue {
    /** Das Plugin verlangt eine neuere App ([required]) als die installierte ([actual]). */
    data class HostTooOld(val required: String, val actual: String) : PluginIssue

    /** Ein Plugin, von dem dieses abhängt, fehlt. */
    data class DependencyMissing(val pluginId: String) : PluginIssue

    /** Ein Plugin, von dem dieses abhängt, ist installiert, aber deaktiviert. */
    data class DependencyDisabled(val pluginId: String) : PluginIssue

    /** Ein Plugin, von dem dieses abhängt, kann selbst nicht geladen werden (z. B. weil dessen Abhängigkeit fehlt). */
    data class DependencyUnavailable(val pluginId: String) : PluginIssue

    /** Das Plugin steckt in einem Abhängigkeitszyklus (`dependsOn` im Kreis). */
    data object DependencyCycle : PluginIssue

    /** Code-Plugin mit nicht mehr unterstützter API-Version ([apiVersion], siehe [PluginApiVersion.MIN_CODE_API]). */
    data class UnsupportedApi(val apiVersion: Int) : PluginIssue
}

/** Prüft `minHostVersion` und `dependsOn` eines Plugins; rein, ohne Android-Abhängigkeit. */
object PluginCompatibility {

    /** Vergleicht Versionsstrings numerisch je Segment (`3.0.5` < `3.1`); Suffixe wie `-beta` werden ignoriert. */
    fun compareVersions(a: String, b: String): Int {
        fun parts(v: String) = v.substringBefore('-').substringBefore('+').split('.')
            .map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        val pa = parts(a)
        val pb = parts(b)
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val c = pa.getOrElse(i) { 0 }.compareTo(pb.getOrElse(i) { 0 })
            if (c != 0) return c
        }
        return 0
    }

    /**
     * Liefert alle Probleme eines Plugins.
     *
     * @param minHostVersion verlangte Mindestversion der App (leer = keine).
     * @param dependsOn IDs der benötigten Plugins.
     * @param hostVersion installierte App-Version.
     * @param installed ID → aktiviert? aller installierten Plugins.
     */
    fun check(
        minHostVersion: String,
        dependsOn: List<String>,
        hostVersion: String,
        installed: Map<String, Boolean>,
    ): List<PluginIssue> = buildList {
        if (minHostVersion.isNotBlank() && compareVersions(hostVersion, minHostVersion) < 0) {
            add(PluginIssue.HostTooOld(minHostVersion, hostVersion))
        }
        dependsOn.forEach { dep ->
            when (installed[dep]) {
                null -> add(PluginIssue.DependencyMissing(dep))
                false -> add(PluginIssue.DependencyDisabled(dep))
                true -> Unit
            }
        }
    }

    /**
     * Probleme aller Plugins inklusive Folgeproblemen: Ein Plugin ist nur nutzbar, wenn es selbst und jede seiner
     * (transitiven) Abhängigkeiten nutzbar ist; Zyklen werden als [PluginIssue.DependencyCycle] gemeldet.
     *
     * @param plugins ID → (aktiviert, minHostVersion, dependsOn).
     * @param hostVersion installierte App-Version.
     */
    fun checkAll(plugins: Map<String, PluginNode>, hostVersion: String): Map<String, List<PluginIssue>> {
        val enabled = plugins.mapValues { it.value.enabled }
        val direct = plugins.mapValues { (_, n) -> check(n.minHostVersion, n.dependsOn, hostVersion, enabled) }
        val sorted = PluginLoadOrder.sort(plugins.mapValues { it.value.dependsOn })
        val result = LinkedHashMap<String, List<PluginIssue>>()
        sorted.cycles.forEach { result[it] = direct[it].orEmpty() + PluginIssue.DependencyCycle }
        for (id in sorted.order) {
            val unavailable = plugins.getValue(id).dependsOn.filter { dep ->
                enabled[dep] == true && result[dep].orEmpty().isNotEmpty()
            }
            result[id] = direct[id].orEmpty() + unavailable.map { PluginIssue.DependencyUnavailable(it) }
        }
        return result
    }
}

/** Angaben eines Plugins für [PluginCompatibility.checkAll]. */
data class PluginNode(val enabled: Boolean, val minHostVersion: String, val dependsOn: List<String>)
