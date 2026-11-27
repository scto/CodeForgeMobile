// Modul: :app
package com.codeforge.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.lifecycle.viewModelScope
import com.codeforge.core.domain.extension.ContributionRepository
import com.codeforge.core.domain.extension.ide.IdeCommandInfo
import com.codeforge.core.domain.extension.ide.IdeCommandRegistry
import com.codeforge.core.domain.extension.ide.IdeNavTarget
import com.codeforge.core.domain.extension.ide.IdeNavigator
import com.codeforge.core.domain.extension.registry.Contribution
import com.codeforge.core.domain.extension.registry.Disposable
import com.codeforge.core.domain.extension.registry.ExtensionRegistry
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes
import com.codeforge.libs.plugin_ui_api.PluginScreen
import com.codeforge.libs.plugin_ui_api.PluginSettingsPage
import com.codeforge.libs.plugin_ui_api.PluginToolWindow
import com.codeforge.libs.plugin_ui_api.PluginUiPoints
import com.codeforge.libs.plugin_ui_api.PluginUiScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Eintrag „Plugin-Einstellungen“ in der Einstellungsübersicht. */
data class PluginSettingsEntry(val pluginId: String, val title: String, val summary: String?, val hasPage: Boolean)

/**
 * Host der Plugin-Oberflächen der API 5: Werkzeugfenster, Bildschirme, Einstellungsseiten. Meldet für jedes
 * Werkzeugfenster und jeden Bildschirm einen Befehl an (Befehlspalette), damit sie ohne eigenes Menü erreichbar sind.
 */
@HiltViewModel
class PluginUiHostViewModel @Inject constructor(
    private val registry: ExtensionRegistry,
    private val commands: IdeCommandRegistry,
    private val navigator: IdeNavigator,
    contributions: ContributionRepository
) : ViewModel() {

    val toolWindows: StateFlow<List<Contribution<PluginToolWindow>>> = registry.observe(PluginUiPoints.TOOL_WINDOW)
    val screens: StateFlow<List<Contribution<PluginScreen>>> = registry.observe(PluginUiPoints.SCREEN)

    /** Plugins mit eigener Einstellungsseite oder mit `contributes.settings` (diese öffnen die Plugin-Ansicht). */
    val settingsEntries: StateFlow<List<PluginSettingsEntry>> = combine(
        registry.observe(PluginUiPoints.SETTINGS_PAGE),
        contributions.contributions.map { it.settings }
    ) { pages, declared ->
        val withPage = pages.map { PluginSettingsEntry(it.pluginId, it.value.title, it.value.summary, hasPage = true) }
        val ids = withPage.mapTo(HashSet()) { it.pluginId }
        withPage + declared.filter { it.pluginId !in ids }
            .map { PluginSettingsEntry(it.pluginId, it.item.title ?: it.pluginId, null, hasPage = false) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private var commandHandles: List<Disposable> = emptyList()

    init {
        viewModelScope.launch {
            combine(toolWindows, screens) { tw, sc -> tw to sc }.collect { (tw, sc) ->
                commandHandles.forEach { it.dispose() }
                commandHandles = tw.map { c ->
                    commands.register(IdeCommandInfo("${c.pluginId}.toolWindow.${c.value.id}", c.value.title, "Werkzeugfenster", c.pluginId)) {
                        navigator.navigate(IdeNavTarget.PluginToolWindow(c.pluginId, c.value.id)); null
                    }
                } + sc.map { c ->
                    commands.register(IdeCommandInfo("${c.pluginId}.screen.${c.value.id}", c.value.title, "Plugin", c.pluginId)) {
                        navigator.navigate(IdeNavTarget.PluginScreen(c.pluginId, c.value.id)); null
                    }
                }
            }
        }
    }

    override fun onCleared() {
        commandHandles.forEach { it.dispose() }
        super.onCleared()
    }

    /** Werkzeugfenster [id] von [pluginId] oder `null`, wenn nicht (mehr) registriert. */
    fun toolWindow(pluginId: String, id: String): PluginToolWindow? =
        toolWindows.value.firstOrNull { it.pluginId == pluginId && it.value.id == id }?.value

    /** Bildschirm [id] von [pluginId] oder `null`. */
    fun screen(pluginId: String, id: String): PluginScreen? = screens.value.firstOrNull { it.pluginId == pluginId && it.value.id == id }?.value

    /** Einstellungsseite von [pluginId] oder `null`. */
    fun settingsPage(pluginId: String): PluginSettingsPage? =
        registry.contributions(PluginUiPoints.SETTINGS_PAGE).firstOrNull { it.pluginId == pluginId }?.value
}

private class Scope(override val pluginId: String, private val onClose: () -> Unit) : PluginUiScope {
    override fun close() = onClose()
}

/** Werkzeugfenster eines Plugins als Blatt von unten. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginToolWindowSheet(pluginId: String, window: PluginToolWindow, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        Text(window.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
        Box(modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp).padding(12.dp)) {
            window.Content(Scope(pluginId, onDismiss), Modifier.fillMaxWidth())
        }
    }
}

/** Vollbild eines Plugins (Bildschirm oder Einstellungsseite). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginFullScreen(
    title: String,
    pluginId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (PluginUiScope, Modifier) -> Unit
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringRes(R.string.common_zurueck)) }
                }
            )
        }
    ) { padding ->
        content(Scope(pluginId, onBack), Modifier.fillMaxSize().padding(padding))
    }
}

/** Platzhalter, wenn ein Plugin-Bildschirm nicht (mehr) registriert ist (Plugin deaktiviert). */
@Composable
fun PluginUiMissing(onBack: () -> Unit) {
    PluginFullScreen(title = stringRes(R.string.plugin_ui_missing_title), pluginId = "", onBack = onBack) { _, modifier ->
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(stringRes(R.string.plugin_ui_missing), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Routen der Plugin-Oberflächen (API 5). */
internal object PluginRoutes {
    const val SCREEN_PATTERN = "plugin_screen/{pluginId}/{screenId}"
    const val SETTINGS_PATTERN = "plugin_settings/{pluginId}"

    fun screen(pluginId: String, screenId: String) = "plugin_screen/${Uri.encode(pluginId)}/${Uri.encode(screenId)}"
    fun settings(pluginId: String) = "plugin_settings/${Uri.encode(pluginId)}"
}

/** Bildschirme und Einstellungsseiten von Plugins im NavHost. */
internal fun NavGraphBuilder.pluginUiRoutes(pluginUi: PluginUiHostViewModel, onBack: () -> Unit) {
    composable(PluginRoutes.SCREEN_PATTERN) { entry ->
        val pluginId = entry.arguments?.getString("pluginId").orEmpty()
        val screen = pluginUi.screen(pluginId, entry.arguments?.getString("screenId").orEmpty())
        if (screen == null) {
            PluginUiMissing(onBack)
        } else {
            PluginFullScreen(screen.title, pluginId, onBack) { scope, modifier -> screen.Content(scope, modifier) }
        }
    }
    composable(PluginRoutes.SETTINGS_PATTERN) { entry ->
        val pluginId = entry.arguments?.getString("pluginId").orEmpty()
        val page = pluginUi.settingsPage(pluginId)
        if (page == null) {
            PluginUiMissing(onBack)
        } else {
            PluginFullScreen(page.title, pluginId, onBack) { scope, modifier -> page.Content(scope, modifier) }
        }
    }
}
