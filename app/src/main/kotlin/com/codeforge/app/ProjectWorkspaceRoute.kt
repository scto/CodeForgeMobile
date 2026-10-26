/**
 * Modul: :app
 * @author Thomas Schmid
 *
 * Projekt-Arbeitsbereich: Editor (+ ggf. Compose-Preview-Tab) als Hauptbildschirm, Bonsai-
 * Dateibaum (:feature:filetree) als [ModalNavigationDrawer]-Inhalt — ersetzt die vorherige
 * eigenständige Vollbild-Route für den Dateibaum. Komposition auf :app-Ebene, da
 * :feature:filetree und :feature:editor laut Dependency-Regel nicht direkt voneinander
 * abhängen dürfen (Kommunikation läuft über :core:navigation, siehe OpenFileRequestBridge).
 */
package com.codeforge.app

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import com.codeforge.core.resources.stringRes
import com.codeforge.core.ui.rememberIsExpandedWidth
import com.codeforge.feature.build.BuildBottomSheet
import com.codeforge.feature.build.BuildTab
import com.codeforge.feature.build.BuildUiEffect
import com.codeforge.feature.build.BuildUiIntent
import com.codeforge.feature.build.BuildViewModel
import com.codeforge.feature.build.rememberBuildTextActions
import com.codeforge.feature.composepreview.ComposePreviewRoute
import com.codeforge.feature.debug.DebugBottomSheet
import com.codeforge.feature.dependencyupdates.DependencyUpdatesHost
import androidx.activity.compose.BackHandler
import com.codeforge.feature.editor.EditorRoute
import com.codeforge.feature.editor.EditorViewModel
import com.codeforge.feature.j2k.J2kIntent
import com.codeforge.feature.j2k.J2kViewModel
import com.codeforge.feature.j2k.ui.J2kDialog
import com.codeforge.feature.x2c.X2cEffect
import com.codeforge.feature.x2c.X2cIntent
import com.codeforge.feature.x2c.X2cViewModel
import com.codeforge.feature.x2c.ui.X2cDialog
import com.codeforge.feature.x2c.ui.X2cProgressScreen
import com.codeforge.feature.x2c.ui.X2cResultScreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectWorkspaceRoute(
    modifier: Modifier = Modifier,
    rootPath: String,
    onNavigate: (String) -> Unit,
    onOpenGitSettings: () -> Unit
) {
    val permanentDrawer = rememberIsExpandedWidth()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    val bridgeViewModel: ComposablePreviewBridgeViewModel = hiltViewModel()
    val activeFile by bridgeViewModel.activeFile.collectAsState()

    val buildViewModel: BuildViewModel = hiltViewModel()
    val editorViewModel: EditorViewModel = hiltViewModel()
    val j2kViewModel: J2kViewModel = hiltViewModel()
    val x2cViewModel: X2cViewModel = hiltViewModel()

    val buildState by buildViewModel.uiState.collectAsState()
    val j2kState by j2kViewModel.uiState.collectAsState()
    val x2cState by x2cViewModel.uiState.collectAsState()

    // Erst nach der Indexierungs-Entscheidung (LaunchedEffect unten) für den Auto-Scan freigegeben.
    var x2cScannedForProject by remember(rootPath) { mutableStateOf(true) }
    var showBuildSheet by remember { mutableStateOf(false) }
    var showDebugSheet by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val safeNavigate: (String) -> Unit = { targetRoute ->
        if (targetRoute == Routes.WELCOME) {
            editorViewModel.requestClose { onNavigate(targetRoute) }
        } else {
            onNavigate(targetRoute)
        }
    }

    BackHandler {
        editorViewModel.requestClose {
            onNavigate(Routes.WELCOME)
        }
    }

    LaunchedEffect(rootPath) {
        buildViewModel.connectProject(rootPath)
        // Automatische Indexierung aus: weder Java→Kotlin- noch XML→Compose-Scan beim Öffnen.
        if (editorViewModel.isProjectIndexingEnabled()) {
            x2cScannedForProject = false
            j2kViewModel.handleIntent(J2kIntent.ScanProject(rootPath, manualTrigger = false))
        }
    }

    // Task 52.2.3: Sequenziertes Prompting: Nur ein Dialog zur Zeit.
    // Fragt Task 50 (Java -> Kotlin) gerade, kommt dieser Dialog erst danach.
    LaunchedEffect(rootPath, j2kState.isScanning, j2kState.showDialog) {
        if (!j2kState.isScanning && !j2kState.showDialog && !x2cScannedForProject) {
            x2cScannedForProject = true
            x2cViewModel.handleIntent(X2cIntent.ScanProject(rootPath, manualTrigger = false))
        }
    }

    LaunchedEffect(x2cViewModel) {
        x2cViewModel.effects.collect { effect ->
            when (effect) {
                is X2cEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message.resolve())
                // Das Ergebnis liegt über dem Editor; zum Anzeigen der Datei zuerst schließen (über die Symbolleiste wieder aufrufbar).
                is X2cEffect.NavigateToFile -> x2cViewModel.handleIntent(X2cIntent.DismissResult)
            }
        }
    }

    val buildTextActions = rememberBuildTextActions { message ->
        coroutineScope.launch { snackbarHostState.showSnackbar(message) }
    }

    LaunchedEffect(buildViewModel) {
        buildViewModel.effects.collect { effect ->
            when (effect) {
                is BuildUiEffect.CopyToClipboard -> buildTextActions.copy(effect.text)
                is BuildUiEffect.ShareText -> buildTextActions.share(effect.text, effect.title)
                is BuildUiEffect.SaveText -> buildTextActions.save(effect.text, effect.fileName)
                is BuildUiEffect.ShowSnackbar -> {
                    val isFailure = effect.message == Res.string(R.string.build_fehlgeschlagen)
                    val actionLabel = if (isFailure) Res.string(R.string.build_probleme_anzeigen) else null
                    val result = snackbarHostState.showSnackbar(
                        message = effect.message,
                        actionLabel = actionLabel
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        buildViewModel.handleIntent(BuildUiIntent.SelectTab(BuildTab.PROBLEMS))
                        showBuildSheet = true
                    }
                }
                is BuildUiEffect.OpenEditorAt -> {
                    showBuildSheet = false
                }
                is BuildUiEffect.LaunchIntent -> {
                    context.startActivity(effect.intent)
                }
                is BuildUiEffect.OpenApkAnalyzer -> {
                    showBuildSheet = false
                    onNavigate(Routes.apkAnalyzer(effect.apkPath))
                }
                else -> {}
            }
        }
    }

    // Prüft beim Öffnen des Projekts (Neu/Clone/Import/Wieder-Öffnen — alle führen hierher) im
    // Hintergrund auf Dependency-Updates und zeigt bei Treffern den Dismiss/Ask later/Update-Dialog.
    DependencyUpdatesHost(rootPath = rootPath)

    // Scaffold-Inhalt: TopAppBar + Editor. Das Scaffold konsumiert die Insets (Status-/Navigationsleiste),
    // damit verschachtelte Scaffolds im Editor nicht doppelt padden; imePadding hält Tastatur-Eingaben sichtbar.
    val workspaceContent: @Composable (Modifier) -> Unit = { contentModifier ->
        Scaffold(
            modifier = contentModifier,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { Text(rootPath.substringAfterLast('/').ifBlank { Res.string(R.string.common_projekt) }) },
                    navigationIcon = {
                        if (!permanentDrawer) {
                            IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                                Icon(Icons.Filled.Menu, contentDescription = stringRes(R.string.app_menue_oeffnen))
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            x2cViewModel.handleIntent(
                                X2cIntent.ScanProject(
                                    projectPath = rootPath,
                                    manualTrigger = true
                                )
                            )
                        }) {
                            Icon(
                                imageVector = Icons.Default.Transform,
                                contentDescription = stringRes(R.string.x2c_menu_convert_layouts)
                            )
                        }
                        if (x2cState.report != null && !x2cState.showResultScreen && !x2cState.isConverting) {
                            IconButton(onClick = { x2cViewModel.handleIntent(X2cIntent.ShowResult) }) {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = stringRes(R.string.x2c_result_title)
                                )
                            }
                        }
                        IconButton(onClick = { showDebugSheet = true }) {
                            Icon(
                                imageVector = Icons.Default.BugReport,
                                contentDescription = stringRes(R.string.debug_title)
                            )
                        }
                        IconButton(onClick = { showBuildSheet = true }) {
                            if (buildState.isBuilding) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Build,
                                    contentDescription = stringRes(R.string.build_bauen)
                                )
                            }
                        }
                    }
                )
            }
        ) { padding ->
            EditorWithPreviewHost(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .imePadding(),
                hasComposables = activeFile?.composableFunctionNames?.isNotEmpty() == true,
                editorContent = { EditorRoute(projectRootPath = rootPath, onNavigate = safeNavigate) },
                previewContent = { ComposePreviewRoute() }
            )
        }
    }

    if (permanentDrawer) {
        // Expanded (>= 840dp): Drawer dauerhaft neben dem Editor.
        Row(modifier = modifier.fillMaxSize()) {
            WorkspaceDrawer(
                rootPath = rootPath,
                onCloseDrawer = {},
                onNavigate = safeNavigate,
                onOpenGitSettings = onOpenGitSettings,
                onConvertLayout = { layoutPath ->
                    x2cViewModel.handleIntent(
                        X2cIntent.ScanProject(
                            projectPath = rootPath,
                            manualTrigger = true,
                            preselectedLayout = layoutPath
                        )
                    )
                },
                permanent = true
            )
            workspaceContent(
                Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Start))
            )
        }
    } else {
        ModalNavigationDrawer(
            modifier = modifier.fillMaxSize(),
            drawerState = drawerState,
            // Geschlossen öffnet nur die Menü-Taste den Dateibaum: ein Wisch im Editor (Scrollen, Text markieren)
            // zog sonst ständig den Drawer herein. Offen lässt er sich weiter per Wischen oder Tippen daneben schließen.
            gesturesEnabled = drawerState.isOpen,
            drawerContent = {
                WorkspaceDrawer(
                    rootPath = rootPath,
                    onCloseDrawer = { coroutineScope.launch { drawerState.close() } },
                    onNavigate = safeNavigate,
                    onOpenGitSettings = onOpenGitSettings,
                    onConvertLayout = { layoutPath ->
                        x2cViewModel.handleIntent(
                            X2cIntent.ScanProject(
                                projectPath = rootPath,
                                manualTrigger = true,
                                preselectedLayout = layoutPath
                            )
                        )
                    }
                )
            }
        ) {
            workspaceContent(Modifier.fillMaxSize())
        }
    }

    if (showBuildSheet) {
        BuildBottomSheet(
            onDismissRequest = { showBuildSheet = false },
            viewModel = buildViewModel
        )
    }

    if (showDebugSheet) {
        DebugBottomSheet(
            projectRoot = rootPath,
            onDismissRequest = { showDebugSheet = false },
            onNavigateToSource = { _, _ ->
                showDebugSheet = false
            }
        )
    }

    J2kDialog(
        state = j2kState,
        onIntent = j2kViewModel::handleIntent
    )

    X2cDialog(
        state = x2cState,
        onIntent = x2cViewModel::handleIntent
    )

    // Task 52.11: Fortschritt und Ergebnis als Vollbild über dem Editor. Während des Laufs gilt Zurück nicht als Abbruch.
    if (x2cState.isConverting) {
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false
            )
        ) {
            X2cProgressScreen(state = x2cState, onIntent = x2cViewModel::handleIntent)
        }
    }
    if (x2cState.showResultScreen && x2cState.report != null) {
        Dialog(
            onDismissRequest = { x2cViewModel.handleIntent(X2cIntent.DismissResult) },
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false
            )
        ) {
            X2cResultScreen(state = x2cState, onIntent = x2cViewModel::handleIntent)
        }
    }
}
