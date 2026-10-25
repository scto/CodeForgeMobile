/**
 * Modul: :app
 * @author Thomas Schmid
 */
package com.codeforge.app

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import com.codeforge.feature.build.analyzer.ApkAnalyzerRoute
import com.codeforge.feature.dependencies.DependenciesRoute
import com.codeforge.feature.git.GitCloneRoute
import com.codeforge.feature.git.GitRoute
import com.codeforge.feature.hardcodedstrings.HardcodedStringsRoute
import com.codeforge.feature.layoutdesigner.LayoutDesignerRoute
import com.codeforge.feature.onboarding.OnboardingRoute
import com.codeforge.feature.plugins.PluginsRoute
import com.codeforge.feature.projectwizard.ProjectWizardRoute
import com.codeforge.feature.sdkmanager.SdkManagerRoute
import com.codeforge.feature.settings.EditorSettingsRoute
import com.codeforge.feature.settings.GitSettingsRoute
import com.codeforge.feature.settings.SettingsHubRoute
import com.codeforge.feature.settings.TerminalSettingsRoute
import com.codeforge.feature.settings.extensions.ExtensionsRoute
import com.codeforge.feature.settings.snippets.SnippetSettingsRoute
import com.codeforge.feature.signing.SigningRoute
import com.codeforge.feature.projectsettings.ProjectSettingsScreen
import com.codeforge.feature.projectsettings.ProjectSettingsViewModel
import com.codeforge.feature.projectsettings.ProjectSettingsIntent
import com.codeforge.feature.projectsettings.ProjectSettingsEffect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
    const val SETTINGS_SNIPPETS = "settings_snippets"
    const val SETTINGS_SIGNING = "settings_signing"
    const val SETTINGS_TERMINAL = "settings_terminal"
    const val SETTINGS_SDK_MANAGER = "settings_sdk_manager"
    const val SETTINGS_PLUGINS = "settings_plugins"
    const val SETTINGS_GIT = "settings_git"
    const val EXTENSIONS = "extensions"
    const val TERMINAL = "terminal"
    const val TERMINAL_PATTERN = "terminal?initialCommand={initialCommand}"
    const val PROJECT_WORKSPACE_PATTERN = "workspace/{rootPath}"
    const val GIT_PATTERN = "git/{repoPath}"
    const val LAYOUT_DESIGNER_PATTERN = "layout_designer/{filePath}"
    const val APK_ANALYZER_PATTERN = "apk_analyzer?apkPath={apkPath}&comparePath={comparePath}"
    const val HARDCODED_STRINGS_PATTERN = "hardcoded_strings?targetPath={targetPath}&projectRoot={projectRoot}"
    const val DEPENDENCIES_PATTERN = "dependencies?rootPath={rootPath}"
    const val PROJECT_SETTINGS_PATTERN = "project_settings/{projectPath}"

    fun projectSettings(projectPath: String) = "project_settings/${Uri.encode(projectPath)}"
    fun projectWorkspace(rootPath: String) = "workspace/${Uri.encode(rootPath)}"
    fun terminalWith(command: String) = Res.string(R.string.app_terminal_initialcommand, Uri.encode(command))
    fun git(repoPath: String) = "git/${Uri.encode(repoPath)}"
    fun layoutDesigner(filePath: String) = "layout_designer/${Uri.encode(filePath)}"
    fun dependencies(rootPath: String) = "dependencies?rootPath=${Uri.encode(rootPath)}"
    fun apkAnalyzer(apkPath: String, comparePath: String? = null): String {
        val base = "apk_analyzer?apkPath=${Uri.encode(apkPath)}"
        return if (comparePath != null) "$base&comparePath=${Uri.encode(comparePath)}" else base
    }
    fun hardcodedStrings(targetPath: String, projectRoot: String = targetPath): String {
        return "hardcoded_strings?targetPath=${Uri.encode(targetPath)}&projectRoot=${Uri.encode(projectRoot)}"
    }
}

@Composable
fun CodeForgeNavHost(
    startOnboarding: Boolean,
    useCompactTerminal: Boolean = false
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val startDestination = if (startOnboarding) Routes.ONBOARDING else Routes.WELCOME

    fun launchTermuxActivity(initialCommand: String? = null) {
        val intent = Intent(context, TermuxActivity::class.java).apply {
            if (!initialCommand.isNullOrBlank()) {
                putExtra(TermuxActivity.EXTRA_INITIAL_COMMAND, initialCommand)
            }
        }
        context.startActivity(intent)
    }

    fun navigateBackOrStart() {
        if (!navController.popBackStack()) {
            navController.navigate(startDestination) {
                popUpTo(navController.graph.id) { inclusive = true }
            }
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.ONBOARDING) {
            OnboardingRoute(
                onFinished = { setupCommand ->
                    // Termux startet und führt das Setup-Skript sichtbar aus; danach zurück zur Startseite.
                    navController.navigate(Routes.WELCOME) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                    if (useCompactTerminal) {
                        navController.navigate(Routes.terminalWith(setupCommand))
                    } else {
                        launchTermuxActivity(setupCommand)
                    }
                }
            )
        }
        composable(Routes.WELCOME) {
            WelcomeRoute(
                onNavigateToProjectWizard = { navController.navigate(Routes.PROJECT_WIZARD) },
                onNavigateToImportPicker = { navController.navigate(Routes.IMPORT_PROJECT) },
                onNavigateToCloneDialog = { navController.navigate(Routes.CLONE_PROJECT) },
                onNavigateToTerminal = {
                    if (useCompactTerminal) {
                        navController.navigate(Routes.TERMINAL)
                    } else {
                        launchTermuxActivity()
                    }
                },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenProject = { projectPath -> navController.navigate(Routes.projectWorkspace(projectPath)) },
                onOpenProjectSettings = { projectPath -> navController.navigate(Routes.projectSettings(projectPath)) }
            )
        }
        composable(Routes.PROJECT_WIZARD) {
            ProjectWizardRoute(
                onNavigateToEditor = { projectPath ->
                    navController.navigate(Routes.projectWorkspace(projectPath)) {
                        popUpTo(Routes.PROJECT_WIZARD) { inclusive = true }
                    }
                },
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
                onBack = { navigateBackOrStart() },
                onNavigateToTheme = { navController.navigate(Routes.SETTINGS_THEME) },
                onNavigateToEditor = { navController.navigate(Routes.SETTINGS_EDITOR) },
                onNavigateToTerminal = { navController.navigate(Routes.SETTINGS_TERMINAL) },
                onNavigateToSdkManager = { navController.navigate(Routes.SETTINGS_SDK_MANAGER) },
                onNavigateToPlugins = { navController.navigate(Routes.SETTINGS_PLUGINS) },
                onNavigateToGit = { navController.navigate(Routes.SETTINGS_GIT) },
                onNavigateToExtensions = { navController.navigate(Routes.EXTENSIONS) },
                onNavigateToSigning = { navController.navigate(Routes.SETTINGS_SIGNING) }
            )
        }
        composable(Routes.SETTINGS_THEME) {
            ThemeBuilderRoute(
                onBack = { navigateBackOrStart() }
            )
        }
        composable(Routes.SETTINGS_EDITOR) {
            EditorSettingsRoute(
                onBack = { navigateBackOrStart() },
                onNavigateToSnippets = { navController.navigate(Routes.SETTINGS_SNIPPETS) }
            )
        }
        composable(Routes.SETTINGS_SNIPPETS) {
            SnippetSettingsRoute(
                onBack = { navigateBackOrStart() }
            )
        }
        composable(Routes.SETTINGS_SIGNING) {
            SigningRoute(
                onNavigateBack = { navigateBackOrStart() }
            )
        }
        composable(Routes.SETTINGS_TERMINAL) {
            TerminalSettingsRoute(
                onBack = { navigateBackOrStart() }
            )
        }
        composable(Routes.SETTINGS_SDK_MANAGER) {
            SdkManagerRoute(
                onBack = { navigateBackOrStart() }
            )
        }
        composable(Routes.SETTINGS_GIT) {
            GitSettingsRoute(
                onBack = { navigateBackOrStart() }
            )
        }
        composable(Routes.SETTINGS_PLUGINS) {
            PluginsRoute(
                onBack = { navigateBackOrStart() }
            )
        }
        composable(Routes.EXTENSIONS) {
            ExtensionsRoute(
                onBack = { navigateBackOrStart() }
            )
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
        ) { backStackEntry ->
            val initialCommand = backStackEntry.arguments?.getString("initialCommand")
            if (useCompactTerminal) {
                TerminalRoute(
                    onOpenFullTermuxUi = { launchTermuxActivity(initialCommand) }
                )
            } else {
                LaunchedEffect(initialCommand) {
                    launchTermuxActivity(initialCommand)
                    navController.popBackStack()
                }
            }
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
        composable(
            route = Routes.APK_ANALYZER_PATTERN,
            arguments = listOf(
                navArgument("apkPath") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("comparePath") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val apkPath = backStackEntry.arguments?.getString("apkPath")
            val comparePath = backStackEntry.arguments?.getString("comparePath")
            ApkAnalyzerRoute(
                initialApkPath = apkPath,
                compareApkPath = comparePath,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(
            route = Routes.HARDCODED_STRINGS_PATTERN,
            arguments = listOf(
                navArgument("targetPath") { type = NavType.StringType },
                navArgument("projectRoot") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val targetPath = backStackEntry.arguments?.getString("targetPath").orEmpty()
            val projectRoot = backStackEntry.arguments?.getString("projectRoot").orEmpty()
            HardcodedStringsRoute(
                targetPath = targetPath,
                projectRoot = projectRoot,
                onNavigateBack = { navController.popBackStack() },
                onOpenEditorAt = { _, _, _ ->
                    navController.popBackStack()
                }
            )
        }
        composable(
            route = Routes.DEPENDENCIES_PATTERN,
            arguments = listOf(
                navArgument("rootPath") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val rootPath = backStackEntry.arguments?.getString("rootPath").orEmpty()
            DependenciesRoute(
                projectRoot = rootPath,
                onNavigateBack = { navController.popBackStack() },
                onShowInCatalog = {
                    navController.popBackStack()
                }
            )
        }
        composable(
            route = Routes.PROJECT_SETTINGS_PATTERN,
            arguments = listOf(
                navArgument("projectPath") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val projectPath = backStackEntry.arguments?.getString("projectPath").orEmpty()
            val vm: ProjectSettingsViewModel = hiltViewModel()
            LaunchedEffect(projectPath) {
                vm.handleIntent(ProjectSettingsIntent.Load(projectPath))
            }
            val uiState by vm.uiState.collectAsState()
            LaunchedEffect(vm) {
                vm.effects.collect { effect ->
                    when (effect) {
                        is ProjectSettingsEffect.NavigateToSigning -> {
                            navController.navigate(Routes.SETTINGS_SIGNING)
                        }
                        is ProjectSettingsEffect.ShowMessage -> {}
                    }
                }
            }
            ProjectSettingsScreen(
                state = uiState,
                onIntent = vm::handleIntent,
                onNavigateBack = { navigateBackOrStart() }
            )
        }
    }
}
