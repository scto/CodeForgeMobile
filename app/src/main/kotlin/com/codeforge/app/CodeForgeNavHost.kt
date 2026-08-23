/**
 * Modul: :app
 * @author Thomas Schmid
 */
package com.codeforge.app

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.codeforge.feature.composepreview.ComposePreviewRoute
import com.codeforge.feature.editor.EditorRoute
import com.codeforge.feature.filetree.FileTreeRoute
import com.codeforge.feature.git.GitCloneRoute
import com.codeforge.feature.git.GitRoute
import com.codeforge.feature.onboarding.OnboardingRoute
import com.codeforge.feature.plugins.PluginsRoute
import com.codeforge.feature.projectwizard.ProjectWizardRoute
import com.codeforge.feature.sdkmanager.SdkManagerRoute
import com.codeforge.feature.settings.EditorSettingsRoute
import com.codeforge.feature.settings.SettingsHubRoute
import com.codeforge.feature.settings.TerminalSettingsRoute
import com.codeforge.feature.terminal.TerminalRoute
import com.codeforge.feature.themebuilder.ThemeBuilderRoute
import com.codeforge.feature.welcome.WelcomeRoute

private object Routes {
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
    const val EDITOR = "editor"
    const val TERMINAL = "terminal"
    const val FILE_TREE_PATTERN = "filetree/{rootPath}"
    const val GIT_PATTERN = "git/{repoPath}"

    fun fileTree(rootPath: String) = "filetree/${Uri.encode(rootPath)}"
    fun git(repoPath: String) = "git/${Uri.encode(repoPath)}"
}

@Composable
fun CodeForgeNavHost(startOnboarding: Boolean) {
    val navController = rememberNavController()
    val startDestination = if (startOnboarding) Routes.ONBOARDING else Routes.WELCOME

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.ONBOARDING) {
            OnboardingRoute(
                onFinished = {
                    navController.navigate(Routes.WELCOME) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
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
                onOpenProject = { projectPath -> navController.navigate(Routes.fileTree(projectPath)) }
            )
        }
        composable(Routes.PROJECT_WIZARD) {
            ProjectWizardRoute(
                onNavigateToEditor = { projectPath -> navController.navigate(Routes.fileTree(projectPath)) },
                onCancel = { navController.popBackStack() }
            )
        }
        composable(Routes.IMPORT_PROJECT) {
            // :feature:filetree / SAF-Picker – Projekt importieren
        }
        composable(Routes.CLONE_PROJECT) {
            GitCloneRoute(
                onCloned = { rootPath ->
                    navController.navigate(Routes.fileTree(rootPath)) {
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
                onNavigateToPlugins = { navController.navigate(Routes.SETTINGS_PLUGINS) }
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
        composable(Routes.SETTINGS_PLUGINS) {
            PluginsRoute()
        }
        composable(Routes.TERMINAL) {
            TerminalRoute()
        }
        composable(
            route = Routes.FILE_TREE_PATTERN,
            arguments = listOf(navArgument("rootPath") { type = NavType.StringType })
        ) { backStackEntry ->
            val rootPath = backStackEntry.arguments?.getString("rootPath").orEmpty()
            FileTreeRoute(
                rootPath = rootPath,
                onOpenFile = { navController.navigate(Routes.EDITOR) },
                onOpenGit = { path -> navController.navigate(Routes.git(path)) }
            )
        }
        composable(
            route = Routes.GIT_PATTERN,
            arguments = listOf(navArgument("repoPath") { type = NavType.StringType })
        ) { backStackEntry ->
            val repoPath = backStackEntry.arguments?.getString("repoPath").orEmpty()
            GitRoute(repoPath = repoPath)
        }
        composable(Routes.EDITOR) {
            val bridgeViewModel: ComposablePreviewBridgeViewModel = hiltViewModel()
            val activeFile by bridgeViewModel.activeFile.collectAsState()

            EditorWithPreviewHost(
                hasComposables = activeFile?.composableFunctionNames?.isNotEmpty() == true,
                editorContent = { EditorRoute(onNavigate = { route -> navController.navigate(route) }) },
                previewContent = { ComposePreviewRoute() }
            )
        }
    }
}
