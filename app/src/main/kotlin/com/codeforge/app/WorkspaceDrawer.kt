/**
 * Modul: :app
 * @author Thomas Schmid
 *
 * Inhalt des Projekt-Drawers: oben eine horizontal scrollbare Icon-Leiste (Files, Suche, Git,
 * Module, Terminal, Einstellungen — beliebig erweiterbar über [DrawerSection]), darunter das
 * Panel des gewählten Bereichs. „Navigations“-Bereiche (Terminal, Einstellungen) öffnen eine
 * eigene Route statt eines Panels.
 */
package com.codeforge.app

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Source
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes
import com.codeforge.feature.filetree.FileTreeRoute
import com.codeforge.feature.git.GitPanel
import com.codeforge.feature.layoutdesigner.files.LayoutFilesPanel
import com.codeforge.feature.modulemaker.ModuleMakerRoute
import com.codeforge.feature.search.SearchRoute

/** Bereiche der Drawer-Leiste. [isPanel] = false → öffnet stattdessen eine Route. */
enum class DrawerSection(@StringRes val labelRes: Int, val icon: ImageVector, val isPanel: Boolean = true) {
    FILES(R.string.drawer_files, Icons.Filled.Folder),
    SEARCH(R.string.drawer_search, Icons.Filled.Search),
    GIT(R.string.common_git, Icons.Filled.Source),
    MODULES(R.string.drawer_modules, Icons.Filled.CreateNewFolder),
    LAYOUTS(R.string.drawer_layouts, Icons.Filled.GridView),
    TERMINAL(R.string.settings_hub_terminal, Icons.Filled.Terminal, isPanel = false),
    SETTINGS(R.string.settings_hub_title, Icons.Filled.Settings, isPanel = false),
}

private val MaxDrawerWidth = 420.dp
private val PermanentDrawerWidth = 360.dp

@Composable
fun WorkspaceDrawer(
    rootPath: String,
    onCloseDrawer: () -> Unit,
    onNavigate: (route: String) -> Unit,
    onOpenGitSettings: () -> Unit,
    modifier: Modifier = Modifier,
    /** true = dauerhaft sichtbar (Expanded-Breite): feste Breite, keine runden Ecken. */
    permanent: Boolean = false,
) {
    // Eigene Surface statt ModalDrawerSheet: dessen Maximalbreite (360 dp) ist für Git-Graph
    // und Trefferlisten zu schmal.
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val drawerWidth = if (permanent) PermanentDrawerWidth else minOf(screenWidth * 0.92f, MaxDrawerWidth)

    var selected by rememberSaveable { mutableStateOf(DrawerSection.FILES) }
    val snackbar = remember { SnackbarHostState() }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); message = null }
    }

    Surface(
        modifier = modifier.fillMaxHeight().width(drawerWidth),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shape = if (permanent) RectangleShape else RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
    ) {
        // Edge-to-Edge: Drawer zeichnet unter die Systemleisten, Inhalt hält Abstand (Status-/Navigationsleiste, Cutout, IME).
        Box(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Top + WindowInsetsSides.Bottom + WindowInsetsSides.Start,
                    ),
                ),
        ) {
            Column(Modifier.fillMaxSize()) {
                SectionRail(
                    selected = selected,
                    onSelect = { section ->
                        if (section.isPanel) {
                            selected = section
                        } else {
                            onCloseDrawer()
                            onNavigate(
                                when (section) {
                                    DrawerSection.TERMINAL -> Routes.TERMINAL
                                    else -> Routes.SETTINGS
                                }
                            )
                        }
                    },
                )
                HorizontalDivider()
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (selected) {
                        DrawerSection.FILES -> FileTreeRoute(rootPath = rootPath, onOpenFile = { onCloseDrawer() })
                        DrawerSection.SEARCH -> SearchRoute(
                            rootPath = rootPath,
                            onMatchOpened = onCloseDrawer,
                            onMessage = { message = it },
                        )
                        DrawerSection.GIT -> GitPanel(
                            repoPath = rootPath,
                            onFileOpened = onCloseDrawer,
                            onOpenSettings = {
                                onCloseDrawer()
                                onOpenGitSettings()
                            },
                        )
                        DrawerSection.MODULES -> ModuleMakerRoute(
                            rootPath = rootPath,
                            onFileOpened = onCloseDrawer,
                            onMessage = { message = it },
                        )
                        DrawerSection.LAYOUTS -> LayoutFilesPanel(
                            rootPath = rootPath,
                            onOpenDesigner = { path ->
                                onCloseDrawer()
                                onNavigate(Routes.layoutDesigner(path))
                            },
                        )
                        DrawerSection.TERMINAL, DrawerSection.SETTINGS -> Unit
                    }
                }
            }
            SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp))
        }
    }
}

/** Horizontal scrollbare Icon-Leiste; bei genug Platz passen alle Einträge, sonst wird gewischt. */
@Composable
private fun SectionRail(
    selected: DrawerSection,
    onSelect: (DrawerSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
    ) {
        items(DrawerSection.entries, key = { it.name }) { section ->
            val isSelected = section == selected && section.isPanel
            Column(
                modifier = Modifier
                    .width(64.dp)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent,
                        RoundedCornerShape(14.dp),
                    )
                    .clickable { onSelect(section) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    section.icon,
                    contentDescription = stringRes(section.labelRes),
                    tint = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringRes(section.labelRes),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
