/**
 * Plugin: Gradle-Aufgaben
 *
 * Nutzt die API-4-Dienste `project` (Module, Build-Ergebnisse) und `build` (Gradle-Aufgaben ausführen).
 */
package com.codeforge.plugin.gradle

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.codeforge.libs.plugin_api.CodeForgePlugin
import com.codeforge.libs.plugin_api.PluginContext
import com.codeforge.libs.plugin_api.PluginModule
import com.codeforge.libs.plugin_ui_api.PanelHost
import com.codeforge.libs.plugin_ui_api.PluginPanel
import com.codeforge.libs.plugin_ui_api.PluginUiExtensionPoints
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GradleTasksPlugin : CodeForgePlugin {
    override fun onLoad(context: PluginContext) {
        context.register(PluginUiExtensionPoints.PANEL, GradlePanel(context))
    }

    override fun onUnload() = Unit
}

private class GradlePanel(private val context: PluginContext) : PluginPanel {
    override val id = "gradle"
    override val title = "Gradle-Aufgaben"

    @Composable
    override fun Content(host: PanelHost, modifier: Modifier) {
        val scope = rememberCoroutineScope()
        var modules by remember { mutableStateOf<List<Pair<PluginModule, Boolean>>>(emptyList()) }
        var selected by remember { mutableStateOf<PluginModule?>(null) }
        var buildState by remember { mutableStateOf("") }
        var custom by remember { mutableStateOf("") }
        var running by remember { mutableStateOf(false) }
        val log = remember { mutableStateListOf<String>() }
        val listState = rememberLazyListState()

        LaunchedEffect(host.projectRoot) {
            withContext(Dispatchers.IO) {
                val project = context.hostServices.project
                modules = project.modules().map { m ->
                    val script = listOf("build.gradle.kts", "build.gradle").map { File(m.directory, it) }.firstOrNull { it.isFile }
                    m to GradleTasks.isAndroid(script?.readText().orEmpty())
                }
                buildState = project.buildOutputs()?.let { "Letzter Build aktuell (${it.variant}, ${it.classDirectories.size} Klassenordner)" }
                    ?: "Kein aktueller Gradle-Build"
            }
            if (selected == null) selected = modules.firstOrNull { it.second }?.first ?: modules.firstOrNull()?.first
        }
        LaunchedEffect(log.size) { if (log.isNotEmpty()) listState.scrollToItem(log.lastIndex) }

        fun run(tasks: List<String>) {
            if (tasks.isEmpty() || running) return
            running = true
            log.clear()
            log += "> ${tasks.joinToString(" ")}"
            scope.launch {
                val result = context.hostServices.build.runTasks(tasks) { line -> scope.launch { log += line } }
                log += if (result.success) "✔ Erfolgreich" else "✘ ${result.message ?: "Fehlgeschlagen"}"
                running = false
            }
        }

        Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(buildState, style = MaterialTheme.typography.labelSmall)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                modules.forEach { (m, _) -> FilterChip(selected = m == selected, onClick = { selected = m }, label = { Text(m.gradlePath) }) }
            }
            selected?.let { m ->
                val android = modules.firstOrNull { it.first == m }?.second == true
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GradleTasks.forModule(m.gradlePath, android).forEach { t ->
                        AssistChip(onClick = { run(listOf(t)) }, enabled = !running, label = { Text(t.substringAfterLast(':')) })
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(custom, { custom = it }, singleLine = true, label = { Text("Aufgaben, z. B. :app:bundleRelease") }, modifier = Modifier.weight(1f))
                Button(onClick = { run(GradleTasks.parse(custom)) }, enabled = !running && custom.isNotBlank(), modifier = Modifier.padding(top = 8.dp)) { Text("Start") }
            }
            if (running) OutlinedButton(onClick = { scope.launch { context.hostServices.build.cancel() } }) { Text("Abbrechen") }
            if (log.isNotEmpty()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)).padding(8.dp)
                ) {
                    items(log) { Text(it, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)) }
                }
            }
        }
    }
}
