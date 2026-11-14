/**
 * Modul: :feature:sdkmanager
 * @author Thomas Schmid
 */
package com.codeforge.feature.sdkmanager

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.codeforge.core.domain.model.ToolItem
import com.codeforge.core.domain.model.ToolType
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes
import com.codeforge.core.ui.SettingsTopBar
import kotlinx.coroutines.flow.collectLatest

/** Route des SDK-Managers. */
@Composable
fun SdkManagerRoute(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    viewModel: SdkManagerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is SdkManagerEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    SdkManagerScreen(
        modifier = modifier,
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onEvent = viewModel::onEvent
    )
}

private data class CategoryTab(@StringRes val labelRes: Int, val toolType: ToolType, val icon: ImageVector)

private val TABS = listOf(
    CategoryTab(R.string.sdkmanager_category_build_tools, ToolType.BUILD_TOOLS, Icons.Filled.Build),
    CategoryTab(R.string.sdkmanager_category_ndk, ToolType.NDK, Icons.Filled.DataObject),
    CategoryTab(R.string.sdkmanager_category_cmake, ToolType.CMAKE, Icons.Filled.Construction),
    CategoryTab(R.string.sdkmanager_category_platforms, ToolType.PLATFORM, Icons.Filled.Layers),
    CategoryTab(R.string.sdkmanager_category_platform_tools, ToolType.PLATFORM_TOOLS, Icons.Filled.Usb),
    CategoryTab(R.string.sdkmanager_category_cmdline_tools, ToolType.CMDLINE_TOOLS, Icons.Filled.Terminal),
    CategoryTab(R.string.sdkmanager_category_jdk, ToolType.JDK, Icons.Filled.Coffee),
)

@Composable
private fun SdkManagerScreen(
    modifier: Modifier = Modifier,
    uiState: SdkManagerState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit = {},
    onEvent: (SdkManagerEvent) -> Unit
) {
    val tab = TABS.first { it.toolType == uiState.selectedCategory }
    val items = uiState.itemsOf(uiState.selectedCategory)
    val selected = uiState.selectedItem

    uiState.pendingUninstallId?.let { id ->
        AlertDialog(
            onDismissRequest = { onEvent(SdkManagerEvent.UninstallDismissed) },
            title = { Text(stringRes(R.string.sdkmanager_uninstall_title)) },
            text = { Text(stringRes(R.string.sdkmanager_uninstall_confirm, id)) },
            confirmButton = { TextButton(onClick = { onEvent(SdkManagerEvent.UninstallConfirmed) }) { Text(stringRes(R.string.sdkmanager_uninstall)) } },
            dismissButton = { TextButton(onClick = { onEvent(SdkManagerEvent.UninstallDismissed) }) { Text(stringRes(R.string.common_abbrechen)) } }
        )
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            SettingsTopBar(
                title = "",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { onEvent(SdkManagerEvent.RefreshRemoteList) }) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringRes(R.string.common_aktualisieren))
                    }
                }
            )
        },
        bottomBar = {
            if (selected != null) {
                SelectionBar(
                    title = stringRes(tab.labelRes),
                    item = selected,
                    progress = uiState.activeDownloads[selected.id],
                    onEvent = onEvent
                )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(stringRes(R.string.sdkmanager_title), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text(
                    stringRes(R.string.sdkmanager_subtitle),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(TABS, key = { it.toolType.name }) { t ->
                    FilterChip(
                        selected = t.toolType == uiState.selectedCategory,
                        onClick = { onEvent(SdkManagerEvent.CategorySelected(t.toolType)) },
                        label = { Text(stringRes(t.labelRes), maxLines = 1) },
                        leadingIcon = { Icon(t.icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        shape = CircleShape
                    )
                }
            }
            Text(
                stringRes(tab.labelRes),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 32.dp, top = 20.dp, bottom = 8.dp)
            )
            when {
                uiState.isLoading && items.isEmpty() ->
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

                items.isEmpty() ->
                    Text(
                        stringRes(R.string.sdkmanager_keine_versionen_verfuegbar),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                        VersionRow(
                            title = stringRes(tab.labelRes),
                            item = item,
                            selected = item.id == uiState.selectedId,
                            progress = uiState.activeDownloads[item.id],
                            shape = groupShape(index, items.size),
                            onClick = { onEvent(SdkManagerEvent.ItemSelected(item.id)) }
                        )
                    }
                }
            }
        }
    }
}

/** Ecken einer Gruppenzeile: außen stark, innen schwach gerundet (zusammenhängender Block). */
private fun groupShape(index: Int, count: Int): RoundedCornerShape {
    val outer = 20.dp
    val inner = 4.dp
    val top = if (index == 0) outer else inner
    val bottom = if (index == count - 1) outer else inner
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

@Composable
private fun VersionRow(
    title: String,
    item: ToolItem,
    selected: Boolean,
    progress: Int?,
    shape: RoundedCornerShape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(horizontal = 12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(stringRes(R.string.sdkmanager_version, item.version), style = MaterialTheme.typography.bodyMedium)
                }
                if (item.isInstalled) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(
                            stringRes(R.string.sdkmanager_installiert),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
            if (progress != null) {
                if (progress > 0) LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun SelectionBar(
    title: String,
    item: ToolItem,
    progress: Int?,
    onEvent: (SdkManagerEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "v${item.version}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            val busy = progress != null
            if (item.isInstalled) {
                OutlinedButton(
                    enabled = !busy,
                    onClick = { onEvent(SdkManagerEvent.UninstallRequested) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringRes(R.string.sdkmanager_uninstall), modifier = Modifier.padding(start = 6.dp))
                }
            }
            Button(enabled = !busy, onClick = { onEvent(SdkManagerEvent.InstallSelected) }) {
                Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    stringRes(if (item.isInstalled) R.string.sdkmanager_reinstall else R.string.sdkmanager_install),
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
        }
    }
}
