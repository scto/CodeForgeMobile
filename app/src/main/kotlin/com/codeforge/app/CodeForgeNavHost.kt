/**
 * Modul: :app
 * @author Thomas Schmid
 */
package com.codeforge.app

import com.codeforge.core.resources.ResGetter

import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.codeforge.app.workspace.WorkspaceRoute
import com.codeforge.feature.composepreview.ComposePreviewRoute
import com.codeforge.feature.editor.EditorRoute
import com.codeforge.feature.filetree.FileTreeRoute
import com.codeforge.feature.git.GitCloneRoute
import com.codeforge.feature.git.GitRoute
import com.codeforge.feature.onboarding.OnboardingRoute
import com.codeforge.feature.plugins.PluginsRoute
import com.codeforge.feature.projectwizard.ProjectWizardRoute
import com.codeforge.feature.sdkmanager.SdkManagerRoute
import com.codeforge.feature.settings.about.AboutSettingsRoute
import com.codeforge.feature.settings.debug.DebugSettingsRoute
import com.codeforge.feature.settings.editor.EditorSettingsRoute
import com.codeforge.feature.settings.hub.SettingsHubRoute
import com.codeforge.feature.settings.terminal.TerminalSettingsRoute
import com.codeforge.feature.terminal.TerminalScreen
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
    const val SETTINGS_FILETREE = "settings_filetree"
    const val SETTINGS_TERMINAL = "settings_terminal"

    const val SETTINGS_SDK_MANAGER = "settings_sdk_manager"
    const val SETTINGS_PLUGINS = "settings_plugins"
    const val SETTINGS_DEBUG = "settings_debug"
    const val EDITOR_PATTERN = "editor/{rootPath}/{filePath}"
    const val TERMINAL = "terminal"
    const val DEVKIT = "devkit"
    const val EXTENSIONS = "extensions"
    const val ABOUT = "about"
    const val FILE_TREE_PATTERN = "filetree/{rootPath}"
    const val GIT_PATTERN = "git/{repoPath}"
    const val WORKSPACE_PATTERN = "workspace/{rootPath}"

    fun fileTree(rootPath: String) = "filetree/${Uri.encode(rootPath)}"

    fun git(repoPath: String) = "git/${Uri.encode(repoPath)}"

    fun workspace(rootPath: String) = "workspace/${Uri.encode(rootPath)}"

    fun editor(
        rootPath: String,
        filePath: String,
    ) = "editor/${Uri.encode(rootPath)}/${Uri.encode(filePath)}"
}

@Composable
fun CodeForgeNavHost(startOnboarding: Boolean) {
    val navController = rememberNavController()
    val startDestination = if (startOnboarding) Routes.ONBOARDING else Routes.WELCOME
    val onNavigateBack = {
        com.codeforge.core.common.logging.AppLogger
            .step("Navigation", "onNavigateBack requested")
        if (!navController.popBackStack()) {
            navController.navigate(Routes.WELCOME) {
                popUpTo(0) { inclusive = true }
            }
        }
        Unit
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.ONBOARDING) {
            com.codeforge.core.common.logging.AppLogger
                .step("Navigation", "Route entered: ONBOARDING")
            OnboardingRoute(
                onFinished = {
                    navController.navigate(Routes.WELCOME) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.WELCOME) {
            com.codeforge.core.common.logging.AppLogger
                .step("Navigation", "Route entered: WELCOME")
            WelcomeRoute(
                onNavigateToProjectWizard = {
                    com.codeforge.core.common.logging.AppLogger
                        .step("Navigation", "Navigating WELCOME -> PROJECT_WIZARD")
                    navController.navigate(Routes.PROJECT_WIZARD)
                },
                onNavigateToOpenProject = {
                    com.codeforge.core.common.logging.AppLogger
                        .step("Navigation", "Navigating WELCOME -> IMPORT_PROJECT")
                    navController.navigate(Routes.IMPORT_PROJECT)
                },
                onNavigateToCloneDialog = {
                    com.codeforge.core.common.logging.AppLogger
                        .step("Navigation", "Navigating WELCOME -> CLONE_PROJECT")
                    navController.navigate(Routes.CLONE_PROJECT)
                },
                onNavigateToDevKitSetup = {
                    com.codeforge.core.common.logging.AppLogger
                        .step("Navigation", "Navigating WELCOME -> DEVKIT")
                    navController.navigate(Routes.DEVKIT)
                },
                onNavigateToTerminal = {
                    com.codeforge.core.common.logging.AppLogger
                        .step("Navigation", "Navigating WELCOME -> TERMINAL")
                    navController.navigate(Routes.TERMINAL)
                },
                onNavigateToExtensions = {
                    com.codeforge.core.common.logging.AppLogger
                        .step("Navigation", "Navigating WELCOME -> EXTENSIONS")
                    navController.navigate(Routes.EXTENSIONS)
                },
                onNavigateToSettings = {
                    com.codeforge.core.common.logging.AppLogger
                        .step("Navigation", "Navigating WELCOME -> SETTINGS")
                    navController.navigate(Routes.SETTINGS)
                },
                onNavigateToAbout = {
                    com.codeforge.core.common.logging.AppLogger
                        .step("Navigation", "Navigating WELCOME -> ABOUT")
                    navController.navigate(Routes.ABOUT)
                },
                onOpenProject = { projectPath ->
                    com.codeforge.core.common.logging.AppLogger
                        .step("Navigation", "Opening project: $projectPath")
                    navController.navigate(Routes.editor(projectPath, ""))
                },
            )
        }
        composable(Routes.PROJECT_WIZARD) {
            com.codeforge.core.common.logging.AppLogger
                .step("Navigation", "Route entered: PROJECT_WIZARD")
            ProjectWizardRoute(
                onNavigateToEditor = { projectPath ->
                    com.codeforge.core.common.logging.AppLogger
                        .step("Navigation", "Navigating PROJECT_WIZARD -> EDITOR ($projectPath)")
                    navController.navigate(Routes.editor(projectPath, "")) {
                        popUpTo(Routes.PROJECT_WIZARD) { inclusive = true }
                    }
                },
                onCancel = { onNavigateBack() },
            )
        }
        composable(Routes.IMPORT_PROJECT) {
            val context = androidx.compose.ui.platform.LocalContext.current
            val launcher =
                androidx.activity.compose.rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts
                        .OpenDocumentTree(),
                ) { uri ->
                    if (uri != null) {
                        val takeFlags =
                            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        try {
                            context.contentResolver.takePersistableUriPermission(uri, takeFlags)
                            // Convert SAF URI to POSIX path for FileSystemRepository and PRoot
                            val decoded = android.net.Uri.decode(uri.toString())
                            val posixPath =
                                if (decoded.contains("primary:")) {
                                    "/storage/emulated/0/" + decoded.substringAfter("primary:")
                                } else {
                                    decoded // Fallback
                                }
                            navController.navigate(Routes.editor(posixPath, "")) {
                                popUpTo(Routes.WELCOME)
                            }
                        } catch (e: Exception) {
                            onNavigateBack()
                        }
                    } else {
                        onNavigateBack()
                    }
                }
            androidx.compose.runtime.LaunchedEffect(Unit) {
                launcher.launch(null)
            }
            androidx.compose.foundation.layout
                .Box(modifier = Modifier.fillMaxSize())
        }
        composable(Routes.CLONE_PROJECT) {
            GitCloneRoute(
                onCloned = { rootPath ->
                    navController.navigate(Routes.editor(rootPath, "")) {
                        popUpTo(Routes.CLONE_PROJECT) { inclusive = true }
                    }
                },
                onNavigateBack = onNavigateBack,
            )
        }
        composable(Routes.SETTINGS) {
            SettingsHubRoute(
                onNavigateToTheme = { navController.navigate(Routes.SETTINGS_THEME) },
                onNavigateToEditor = { navController.navigate(Routes.SETTINGS_EDITOR) },
                onNavigateToFileTree = { navController.navigate(Routes.SETTINGS_FILETREE) },
                onNavigateToTerminal = { navController.navigate(Routes.SETTINGS_TERMINAL) },
                onNavigateToSdkManager = { navController.navigate(Routes.SETTINGS_SDK_MANAGER) },
                onNavigateToPlugins = { navController.navigate(Routes.SETTINGS_PLUGINS) },
                onNavigateToExtensions = { navController.navigate(Routes.EXTENSIONS) },
                onNavigateToDebug = { navController.navigate(Routes.SETTINGS_DEBUG) },
                onNavigateToAbout = { navController.navigate(Routes.ABOUT) },
                onNavigateBack = onNavigateBack,
            )
        }
        composable(Routes.SETTINGS_THEME) {
            ThemeBuilderRoute(onNavigateBack = onNavigateBack)
        }
        composable(Routes.SETTINGS_EDITOR) {
            EditorSettingsRoute(onNavigateBack = onNavigateBack)
        }
        composable(Routes.SETTINGS_FILETREE) {
            com.codeforge.feature.settings.filetree
                .FileTreeSettingsRoute(onNavigateBack = onNavigateBack)
        }
        composable(Routes.SETTINGS_TERMINAL) {
            TerminalSettingsRoute(onNavigateBack = onNavigateBack)
        }
        composable(Routes.SETTINGS_SDK_MANAGER) {
            SdkManagerRoute(onNavigateBack = onNavigateBack)
        }
        composable(Routes.SETTINGS_DEBUG) {
            DebugSettingsRoute(viewModel = hiltViewModel(), onNavigateBack = onNavigateBack)
        }
        composable(Routes.SETTINGS_PLUGINS) {
            PluginsRoute(onNavigateBack = onNavigateBack)
        }
        composable(Routes.EXTENSIONS) {
            com.codeforge.feature.settings.extensions
                .ExtensionsRoute(onNavigateBack = onNavigateBack)
        }
        composable(Routes.TERMINAL) {
            TerminalScreen(
                onNavigateBack = onNavigateBack,
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS_TERMINAL) },
            )
        }
        composable(Routes.DEVKIT) {
            SdkManagerRoute(onNavigateBack = onNavigateBack)
        }
        composable(Routes.ABOUT) {
            AboutSettingsRoute(onNavigateBack = onNavigateBack)
        }
        composable(
            route = Routes.FILE_TREE_PATTERN,
            arguments = listOf(navArgument("rootPath") { type = NavType.StringType }),
        ) { backStackEntry ->
            val rootPath = backStackEntry.arguments?.getString("rootPath").orEmpty()
            FileTreeRoute(
                rootPath = rootPath,
                onOpenFile = { filePath -> navController.navigate(Routes.editor(rootPath, filePath)) },
                onOpenGit = { path -> navController.navigate(Routes.git(path)) },
            )
        }
        composable(
            route = Routes.GIT_PATTERN,
            arguments = listOf(navArgument("repoPath") { type = NavType.StringType }),
        ) { backStackEntry ->
            val repoPath = backStackEntry.arguments?.getString("repoPath").orEmpty()
            GitRoute(repoPath = repoPath)
        }
        composable(
            route = Routes.EDITOR_PATTERN,
            arguments =
                listOf(
                    navArgument("rootPath") { type = NavType.StringType },
                    navArgument("filePath") { type = NavType.StringType },
                ),
        ) { backStackEntry ->
            val rootPath = backStackEntry.arguments?.getString("rootPath").orEmpty()
            val filePath = backStackEntry.arguments?.getString("filePath").orEmpty()
            val bridgeViewModel: ComposablePreviewBridgeViewModel = hiltViewModel()
            val activeFile by bridgeViewModel.activeFile.collectAsState()

            // TODO Phase XY: EditorRoute so anpassen, dass sie rootPath und filePath entgegennimmt
            EditorWithPreviewHost(
                hasComposables = activeFile?.composableFunctionNames?.isNotEmpty() == true,
                editorContent = {
                    EditorRoute(
                        onNavigate = { route -> navController.navigate(route) },
                        onNavigateBack = onNavigateBack,
                    )
                },
                previewContent = { ComposePreviewRoute() },
            )
        }
        composable(
            route = Routes.WORKSPACE_PATTERN,
            arguments = listOf(navArgument("rootPath") { type = NavType.StringType }),
        ) {
            WorkspaceRoute()
        }
    }
}

@Composable
fun PlaceholderRoute(
    title: String,
    onNavigateBack: () -> Unit,
) {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
    ) {
        androidx.compose.material3.Text(
            text = "$title (Work In Progress)",
            style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
        )
        androidx.compose.foundation.layout
            .Spacer(modifier = Modifier.height(16.dp))
        androidx.compose.material3.Button(onClick = onNavigateBack) {
            androidx.compose.material3.Text(ResGetter.get(com.codeforge.core.resources.R.string.action_back))
        }
    }
}
