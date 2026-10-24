// Modul: :core:domain
package com.codeforge.core.domain.model

/**
 * Repräsentiert einen Knoten im aufgelösten Gradle-Abhängigkeitsbaum.
 */
data class DependencyNode(
    val coordinate: String,
    val group: String,
    val name: String,
    val requestedVersion: String?,
    val resolvedVersion: String?,
    val children: List<DependencyNode> = emptyList(),
    val isProject: Boolean = false,
    val isConstraint: Boolean = false,
    val isRepeated: Boolean = false,
    val failed: Boolean = false,
    val failureReason: String? = null,
    val updateAvailable: String? = null
) {
    val key: String get() = if (isProject) "project:$name" else "$group:$name"

    val hasConflict: Boolean
        get() = !isProject && requestedVersion != null && resolvedVersion != null && requestedVersion != resolvedVersion

    val displayLabel: String
        get() = when {
            isProject -> "project $name"
            hasConflict -> "$group:$name:$requestedVersion -> $resolvedVersion"
            resolvedVersion != null -> "$group:$name:$resolvedVersion"
            requestedVersion != null -> "$group:$name:$requestedVersion"
            else -> coordinate
        }
}

/**
 * Ein Versionskonflikt zwischen angefragter und aufgelöster Version.
 */
data class DependencyConflict(
    val coordinate: String,
    val group: String,
    val name: String,
    val requestedVersion: String,
    val resolvedVersion: String,
    val directRequester: String?,
    val pathToConflict: List<String> = emptyList()
)

/**
 * Eine Bibliothek, die in mehreren unterschiedlichen Versionen im Graphen oder
 * über Konfigurationen hinweg vorkommt.
 */
data class DependencyDuplicate(
    val coordinate: String,
    val group: String,
    val name: String,
    val versions: List<String>,
    val configurations: List<String> = emptyList()
)

/**
 * Vollständiger Abhängigkeitsgraph eines Moduls für eine gewählte Konfiguration.
 */
data class DependencyGraph(
    val moduleName: String,
    val configuration: String,
    val rootNodes: List<DependencyNode> = emptyList(),
    val conflicts: List<DependencyConflict> = emptyList(),
    val duplicates: List<DependencyDuplicate> = emptyList(),
    val totalDependencies: Int = 0,
    val maxDepth: Int = 0,
    val timestampMillis: Long = System.currentTimeMillis()
)

/**
 * Detaillierte Analyse einer spezifischen Abhängigkeit (aus `dependencyInsight`).
 */
data class DependencyInsight(
    val coordinate: String,
    val resolvedVersion: String,
    val selectionReasons: List<String> = emptyList(),
    val pathsToRoot: List<List<String>> = emptyList()
)
