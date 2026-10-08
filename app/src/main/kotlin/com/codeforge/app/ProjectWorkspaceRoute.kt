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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.codeforge.core.resources.R
import com.codeforge.core.ui.rememberIsExpandedWidth
import com.codeforge.core.resources.Res
import com.codeforge.core.resources.stringRes
import com.codeforge.feature.composepreview.ComposePreviewRoute
import com.codeforge.feature.dependencyupdates.DependencyUpdatesHost
import com.codeforge.feature.editor.EditorRoute
import kotlinx.coroutines.launch

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

    // Prüft beim Öffnen des Projekts (Neu/Clone/Import/Wieder-Öffnen — alle führen hierher) im
    // Hintergrund auf Dependency-Updates und zeigt bei Treffern den Dismiss/Ask later/Update-Dialog.
    DependencyUpdatesHost(rootPath = rootPath)

    // Scaffold-Inhalt: TopAppBar + Editor. Das Scaffold konsumiert die Insets (Status-/Navigationsleiste),
    // damit verschachtelte Scaffolds im Editor nicht doppelt padden; imePadding hält Tastatur-Eingaben sichtbar.
    val workspaceContent: @Composable (Modifier) -> Unit = { contentModifier ->
        Scaffold(
            modifier = contentModifier,
            topBar = {
                TopAppBar(
                    title = { Text(rootPath.substringAfterLast('/').ifBlank { Res.string(R.string.common_projekt) }) },
                    navigationIcon = {
                        if (!permanentDrawer) {
                            IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                                Icon(Icons.Filled.Menu, contentDescription = stringRes(R.string.app_menue_oeffnen))
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
                editorContent = { EditorRoute(projectRootPath = rootPath, onNavigate = onNavigate) },
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
                onNavigate = onNavigate,
                onOpenGitSettings = onOpenGitSettings,
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
            drawerContent = {
                WorkspaceDrawer(
                    rootPath = rootPath,
                    onCloseDrawer = { coroutineScope.launch { drawerState.close() } },
                    onNavigate = onNavigate,
                    onOpenGitSettings = onOpenGitSettings
                )
            }
        ) {
            workspaceContent(Modifier.fillMaxSize())
        }
    }
}
