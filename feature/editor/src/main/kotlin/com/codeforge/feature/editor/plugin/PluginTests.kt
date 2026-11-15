// Modul: :feature:editor
package com.codeforge.feature.editor.plugin

import com.codeforge.feature.editor.overlay.TestTarget
import com.codeforge.libs.plugin_api.EditorContext
import com.codeforge.libs.plugin_api.PluginLanguagePoints
import com.codeforge.libs.plugin_api.PluginTestItem
import com.codeforge.libs.plugin_api.RunConsole

// Tests aus Plugin-Quellen (TestProvider): Start-Knöpfe im Editor und Ausführung über das Ausgabeblatt.

private const val DISCOVER_TIMEOUT_MS = 2_000L
private const val MAX_TESTS_PER_FILE = 500

/** Tests aller zuständigen Quellen für die Datei in [context], nach Zeile sortiert. */
suspend fun PluginEditorBridge.discoverTests(context: EditorContext): List<TestTarget> {
    val lineCount = context.text.count { it == '\n' } + 1
    return registry.contributions(PluginLanguagePoints.TEST_PROVIDER)
        .filter { c -> runCatching { c.value.appliesTo(context.filePath, context.languageId) }.getOrDefault(false) }
        .flatMap { c ->
            val ref = "${c.pluginId}/${c.value.id}"
            guarded(c.pluginId, "discoverTests", DISCOVER_TIMEOUT_MS) { c.value.discover(context) }
                .getOrDefault(emptyList())
                .filter { it.line in 0 until lineCount && it.id.isNotBlank() }
                .distinctBy { it.id }
                .map { TestTarget(line = it.line, label = it.name.ifBlank { it.id }, filterTarget = it.id, isClass = false, pluginRef = ref) }
        }
        .sortedBy { it.line }
        .take(MAX_TESTS_PER_FILE)
}

/**
 * Führt [targets] (alle von derselben Quelle [ref]) aus [filePath] aus; leere Liste = alle Tests der Datei.
 * Liefert den Exit-Code oder `null`, wenn es die Quelle nicht mehr gibt.
 */
suspend fun PluginEditorBridge.runTests(ref: String, filePath: String, targets: List<TestTarget>, console: RunConsole): Int? {
    val providers = registry.contributions(PluginLanguagePoints.TEST_PROVIDER)
    val provider = providers.firstOrNull { "${it.pluginId}/${it.value.id}" == ref } ?: return null
    val items = targets.map { PluginTestItem(it.filterTarget, it.label, it.line) }
    return exitCodeOf(provider.pluginId, "runTests(${provider.value.id})", console) { provider.value.run(filePath, items, console) }
}
