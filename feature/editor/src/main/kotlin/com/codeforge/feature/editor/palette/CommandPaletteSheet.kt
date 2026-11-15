// Modul: :feature:editor
package com.codeforge.feature.editor.palette

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes

/**
 * Befehlspalette: Suchfeld + Liste aller in der aktuellen Lage verfügbaren Befehle (eingebaut, Sprachserver, Plugins).
 * Ohne Suchtext gruppiert nach Kategorie; Enter führt den ersten Treffer aus.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommandPaletteSheet(
    state: CommandPaletteState,
    onQueryChange: (String) -> Unit,
    onSelect: (PaletteItem) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focusRequester = remember { FocusRequester() }
    val items = remember(state) { state.visibleItems }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text(
                text = stringRes(R.string.palette_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                placeholder = { Text(stringRes(R.string.palette_search_hint)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { items.firstOrNull()?.let(onSelect) }),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
            )
            if (items.isEmpty()) {
                Text(
                    text = stringRes(R.string.palette_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else {
                PaletteList(items = items, grouped = state.query.isBlank(), onSelect = onSelect)
            }
        }
    }
}

@Composable
private fun PaletteList(items: List<PaletteItem>, grouped: Boolean, onSelect: (PaletteItem) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp).padding(top = 8.dp, bottom = 16.dp)) {
        items.forEachIndexed { index, item ->
            if (grouped && (index == 0 || items[index - 1].category != item.category)) {
                item(key = "header-${item.category}") {
                    Text(
                        text = stringRes(item.category.titleRes()),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                    )
                }
            }
            item(key = item.action.toString()) { PaletteRow(item = item, onClick = { onSelect(item) }) }
        }
    }
}

@Composable
private fun PaletteRow(item: PaletteItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = item.category.icon(),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = item.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            item.detail?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        item.shortcut?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

private fun PaletteCategory.icon(): ImageVector = when (this) {
    PaletteCategory.REFACTOR -> Icons.Default.Edit
    PaletteCategory.NAVIGATION -> Icons.AutoMirrored.Filled.OpenInNew
    PaletteCategory.CODE -> Icons.Default.Code
    PaletteCategory.EDITOR -> Icons.Default.Tune
    PaletteCategory.SERVER -> Icons.Default.Dns
    PaletteCategory.PLUGIN -> Icons.Default.Extension
}
