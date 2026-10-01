package com.codeforge.app.workspace

import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.codeforge.feature.editor.ui.SoraEditorHost
import com.codeforge.feature.filetree.ui.drawer.WorkspaceFileTreeDrawer
import kotlinx.coroutines.launch

@Composable
fun WorkspaceScreen(
    uiState: WorkspaceUiState,
    onFileSelected: (String) -> Unit,
    onContentChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = false,
        drawerContent = {
            WorkspaceFileTreeDrawer(
                workspaceRoot = uiState.workspaceRoot,
                onFileSelected = { path ->
                    onFileSelected(path)
                    scope.launch { drawerState.close() }
                },
                fileTreeConfig = uiState.fileTreeConfig,
                onCloseDrawer = {
                    scope.launch { drawerState.close() }
                },
            )
        },
        modifier = modifier.statusBarsPadding(),
    ) {
        SoraEditorHost(
            filePath = uiState.currentFilePath,
            initialText = uiState.currentContent,
            onContentChanged = onContentChanged,
            editorConfig = uiState.editorConfig,
        )
    }
}
