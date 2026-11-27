// Modul: :app
package com.codeforge.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.ViewModel
import androidx.navigation.NavController
import com.codeforge.core.domain.extension.ide.IdeNavTarget
import com.codeforge.core.domain.extension.ide.IdeNavigator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject

/** Adapter auf den Navigator der Plugin-API 5, damit der NavHost Wünsche von Plugins und Befehlen ausführt. */
@HiltViewModel
class IdeNavigationViewModel @Inject constructor(navigator: IdeNavigator) : ViewModel() {
    /** Navigationswünsche (Einstellungen, Terminal, Git, Build-Panel, Plugin-Bildschirme). */
    val requests: SharedFlow<IdeNavTarget> = navigator.requests
}

/** Route der Einstellungsseite [page] (Namen wie in `IdeSettingsPages`); `null` = Übersicht. */
internal fun settingsRouteFor(page: String?): String = when (page) {
    "theme" -> Routes.SETTINGS_THEME
    "editor" -> Routes.SETTINGS_EDITOR
    "terminal" -> Routes.SETTINGS_TERMINAL
    "git" -> Routes.SETTINGS_GIT
    "lsp" -> Routes.SETTINGS_LSP
    "formatters" -> Routes.SETTINGS_FORMATTERS
    "sdk" -> Routes.SETTINGS_SDK_MANAGER
    "ai" -> Routes.SETTINGS_AI
    "store", "plugins" -> Routes.SETTINGS_STORE
    "packages" -> Routes.SETTINGS_PACKAGES
    "build" -> Routes.SETTINGS_BUILD_RUN
    "keymap" -> Routes.SETTINGS_KEYMAP
    "symbols" -> Routes.SETTINGS_SYMBOLS
    "colorScheme" -> Routes.SETTINGS_COLOR_SCHEME
    "codeStyle" -> Routes.SETTINGS_CODE_STYLE
    "composePreview" -> Routes.SETTINGS_COMPOSE_PREVIEW
    "debug" -> Routes.SETTINGS_DEBUG
    "about" -> Routes.SETTINGS_ABOUT
    else -> Routes.SETTINGS
}

/** Führt Navigationswünsche aus (Einstellungen, Terminal, Git, Plugin-Bildschirme); Build-Panel/Werkzeugfenster öffnet der Arbeitsbereich. */
@Composable
internal fun IdeNavigationEffect(
    viewModel: IdeNavigationViewModel,
    navController: NavController,
    useCompactTerminal: Boolean,
    launchTermux: (String?) -> Unit
) {
    LaunchedEffect(viewModel) {
        viewModel.requests.collect { target ->
            runCatching {
                when (target) {
                    is IdeNavTarget.Settings -> navController.navigate(settingsRouteFor(target.page))
                    is IdeNavTarget.Terminal -> openTerminal(navController, useCompactTerminal, launchTermux, target.command)
                    is IdeNavTarget.Git -> target.repoPath?.let { navController.navigate(Routes.git(it)) }
                    is IdeNavTarget.PluginScreen -> navController.navigate(PluginRoutes.screen(target.pluginId, target.screenId))
                    else -> Unit
                }
            }
        }
    }
}

private fun openTerminal(navController: NavController, compact: Boolean, launchTermux: (String?) -> Unit, command: String?) {
    when {
        !compact -> launchTermux(command)
        command.isNullOrBlank() -> navController.navigate(Routes.TERMINAL)
        else -> navController.navigate(Routes.terminalWith(command))
    }
}
