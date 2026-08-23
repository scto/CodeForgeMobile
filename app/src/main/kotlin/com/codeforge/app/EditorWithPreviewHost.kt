/**
 * Modul: :app
 * @author Thomas Schmid
 */
package com.codeforge.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * Beherbergt Editor- und Preview-Tab nebeneinander (Skill-Abschnitt 4: "zusätzliches
 * Tab namens Preview"). Komposition auf :app-Ebene, da :feature:editor und
 * :feature:composepreview laut Dependency-Regel nicht direkt voneinander abhängen dürfen.
 */
@Composable
fun EditorWithPreviewHost(
    hasComposables: Boolean,
    editorContent: @Composable () -> Unit,
    previewContent: @Composable () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(hasComposables) {
        if (!hasComposables) selectedTab = 0
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (hasComposables) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Editor") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Preview") })
            }
        }

        when {
            selectedTab == 1 && hasComposables -> previewContent()
            else -> editorContent()
        }
    }
}
