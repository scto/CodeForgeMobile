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
}
