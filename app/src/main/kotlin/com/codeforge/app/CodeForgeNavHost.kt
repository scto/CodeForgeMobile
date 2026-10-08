/**
 * Modul: :app
 * @author Thomas Schmid
 */
package com.codeforge.app

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import com.codeforge.feature.git.GitCloneRoute
import com.codeforge.feature.git.GitRoute
import com.codeforge.feature.layoutdesigner.LayoutDesignerRoute
import com.codeforge.feature.onboarding.OnboardingRoute
import com.codeforge.feature.plugins.PluginsRoute
import com.codeforge.feature.projectwizard.ProjectWizardRoute
import com.codeforge.feature.sdkmanager.SdkManagerRoute
import com.codeforge.feature.settings.EditorSettingsRoute
import com.codeforge.feature.settings.GitSettingsRoute
import com.codeforge.feature.settings.SettingsHubRoute
import com.codeforge.feature.settings.TerminalSettingsRoute
import com.codeforge.feature.terminal.TerminalRoute
import com.codeforge.feature.themebuilder.ThemeBuilderRoute
import com.codeforge.feature.welcome.WelcomeRoute

internal object Routes {
    const val ONBOARDING = "onboarding"
    const val WELCOME = "welcome"
    const val PROJECT_WIZARD = "project_wizard"
    const val IMPORT_PROJECT = "import_project"
    const val CLONE_PROJECT = "clone_project"
    const val SETTINGS = "settings"
    const val SETTINGS_THEME = "settings_theme"
    const val SETTINGS_EDITOR = "settings_editor"
    const val SETTINGS_TERMINAL = "settings_terminal"
    const val SETTINGS_SDK_MANAGER = "settings_sdk_manager"
    const val SETTINGS_PLUGINS = "settings_plugins"
    const val SETTINGS_GIT = "settings_git"
    const val TERMINAL = "terminal"
    const val TERMINAL_PATTERN = "terminal?initialCommand={initialCommand}"
    const val PROJECT_WORKSPACE_PATTERN = "workspace/{rootPath}"
    const val GIT_PATTERN = "git/{repoPath}"
    const val LAYOUT_DESIGNER_PATTERN = "layout_designer/{filePath}"

    fun projectWorkspace(rootPath: String) = "workspace/${Uri.encode(rootPath)}"
    fun terminalWith(command: String) = Res.string(R.string.app_terminal_initialcommand, Uri.encode(command))
    fun git(repoPath: String) = "git/${Uri.encode(repoPath)}"
    fun layoutDesigner(filePath: String) = "layout_designer/${Uri.encode(filePath)}"
}

@Composable
fun CodeForgeNavHost(startOnboarding: Boolean) {
    val navController = rememberNavController()
    val startDestination = if (startOnboarding) Routes.ONBOARDING else Routes.WELCOME

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.ONBOARDING) {
            OnboardingRoute(
                onFinished = { setupCommand ->
                    // Termux startet und führt das Setup-Skript sichtbar aus; danach zurück zur Startseite.
                    navController.navigate(Routes.WELCOME) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                    navController.navigate(Routes.terminalWith(setupCommand))
                }
            )
        }
        composable(Routes.WELCOME) {
            WelcomeRoute(
                onNavigateToProjectWizard = { navController.navigate(Routes.PROJECT_WIZARD) },
                onNavigateToImportPicker = { navController.navigate(Routes.IMPORT_PROJECT) },
                onNavigateToCloneDialog = { navController.navigate(Routes.CLONE_PROJECT) },
                onNavigateToTerminal = { navController.navigate(Routes.TERMINAL) },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenProject = { projectPath -> navController.navigate(Routes.projectWorkspace(projectPath)) }
            )
        }
        composable(Routes.PROJECT_WIZARD) {
            ProjectWizardRoute(
                onNavigateToEditor = { projectPath -> navController.navigate(Routes.projectWorkspace(projectPath)) },
                onCancel = { navController.popBackStack() }
            )
        }
        composable(Routes.IMPORT_PROJECT) {
            ProjectImportRoute(
                onImported = { rootPath ->
                    navController.navigate(Routes.projectWorkspace(rootPath)) {
                        popUpTo(Routes.IMPORT_PROJECT) { inclusive = true }
                    }
                },
                onCancel = { navController.popBackStack() }
            )
        }
        composable(Routes.CLONE_PROJECT) {
            GitCloneRoute(
                onCloned = { rootPath ->
                    navController.navigate(Routes.projectWorkspace(rootPath)) {
                        popUpTo(Routes.CLONE_PROJECT) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsHubRoute(
                onNavigateToTheme = { navController.navigate(Routes.SETTINGS_THEME) },
                onNavigateToEditor = { navController.navigate(Routes.SETTINGS_EDITOR) },
                onNavigateToTerminal = { navController.navigate(Routes.SETTINGS_TERMINAL) },
                onNavigateToSdkManager = { navController.navigate(Routes.SETTINGS_SDK_MANAGER) },
                onNavigateToPlugins = { navController.navigate(Routes.SETTINGS_PLUGINS) },
                onNavigateToGit = { navController.navigate(Routes.SETTINGS_GIT) }
            )
        }
        composable(Routes.SETTINGS_THEME) {
            ThemeBuilderRoute()
        }
        composable(Routes.SETTINGS_EDITOR) {
            EditorSettingsRoute()
        }
        composable(Routes.SETTINGS_TERMINAL) {
            TerminalSettingsRoute()
        }
        composable(Routes.SETTINGS_SDK_MANAGER) {
            SdkManagerRoute()
        }
        composable(Routes.SETTINGS_GIT) {
            GitSettingsRoute()
        }
        composable(Routes.SETTINGS_PLUGINS) {
            PluginsRoute()
        }
        composable(
            route = Routes.TERMINAL_PATTERN,
            arguments = listOf(
                navArgument("initialCommand") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) {
            TerminalRoute()
        }
        composable(
            route = Routes.GIT_PATTERN,
            arguments = listOf(navArgument("repoPath") { type = NavType.StringType })
        ) { backStackEntry ->
            val repoPath = backStackEntry.arguments?.getString("repoPath").orEmpty()
            GitRoute(
                repoPath = repoPath,
                onBack = { navController.popBackStack() },
                onOpenSettings = { navController.navigate(Routes.SETTINGS_GIT) }
            )
        }
        composable(
            route = Routes.LAYOUT_DESIGNER_PATTERN,
            arguments = listOf(navArgument("filePath") { type = NavType.StringType })
        ) { backStackEntry ->
            val filePath = backStackEntry.arguments?.getString("filePath").orEmpty()
            LayoutDesignerRoute(
                filePath = filePath,
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = Routes.PROJECT_WORKSPACE_PATTERN,
            arguments = listOf(navArgument("rootPath") { type = NavType.StringType })
        ) { backStackEntry ->
            val rootPath = backStackEntry.arguments?.getString("rootPath").orEmpty()
            ProjectWorkspaceRoute(
                rootPath = rootPath,
                onNavigate = { route -> navController.navigate(route) },
                onOpenGitSettings = { navController.navigate(Routes.SETTINGS_GIT) }
            )
        }
    }
}
