// Modul: :feature:editor
package com.codeforge.feature.editor.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.codeforge.core.domain.model.LspDocumentSymbol
import com.codeforge.core.domain.model.LspSymbolKind
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentOutlineSheet(
    modifier: Modifier = Modifier,
    symbols: List<LspDocumentSymbol>,
    filter: String,
    isLoading: Boolean,
    sheetState: SheetState = rememberModalBottomSheetState(),
    onFilterChanged: (String) -> Unit,
    onSelectSymbol: (LspDocumentSymbol) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = stringRes(R.string.lsp_outline_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            OutlinedTextField(
                value = filter,
                onValueChange = onFilterChanged,
                placeholder = { Text(stringRes(R.string.lsp_outline_search)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )

            if (isLoading) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                }
            } else if (symbols.isEmpty()) {
                Text(
                    text = stringRes(R.string.lsp_outline_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    val flatList = flattenSymbols(symbols, 0)
                    items(flatList) { (symbol, depth) ->
                        SymbolItemRow(
                            symbol = symbol,
                            depth = depth,
                            onClick = { onSelectSymbol(symbol) }
                        )
                    }
                }
            }
        }
    }
}

private fun flattenSymbols(symbols: List<LspDocumentSymbol>, depth: Int): List<Pair<LspDocumentSymbol, Int>> {
    val result = mutableListOf<Pair<LspDocumentSymbol, Int>>()
    for (sym in symbols) {
        result.add(sym to depth)
        if (sym.children.isNotEmpty()) {
            result.addAll(flattenSymbols(sym.children, depth + 1))
        }
    }
    return result
}

@Composable
private fun SymbolItemRow(
    symbol: LspDocumentSymbol,
    depth: Int,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(
                start = (depth * 16 + 4).dp,
                end = 4.dp,
                top = 8.dp,
                bottom = 8.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = symbolKindIcon(symbol.kind),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = symbol.name,
                style = MaterialTheme.typography.bodyMedium
            )
            symbol.detail?.let { detail ->
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            text = "L${symbol.selectionRange.start.line + 1}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun symbolKindIcon(kind: LspSymbolKind): ImageVector = when (kind) {
    LspSymbolKind.CLASS, LspSymbolKind.STRUCT, LspSymbolKind.OBJECT -> Icons.Default.Category
    LspSymbolKind.INTERFACE -> Icons.Default.Category
    LspSymbolKind.FUNCTION, LspSymbolKind.METHOD, LspSymbolKind.CONSTRUCTOR -> Icons.Default.Code
    LspSymbolKind.VARIABLE, LspSymbolKind.FIELD, LspSymbolKind.PROPERTY, LspSymbolKind.CONSTANT -> Icons.Default.Tune
    LspSymbolKind.PACKAGE, LspSymbolKind.MODULE, LspSymbolKind.NAMESPACE -> Icons.Default.Folder
    LspSymbolKind.ENUM, LspSymbolKind.ENUM_MEMBER -> Icons.AutoMirrored.Filled.FormatListBulleted
    else -> Icons.Default.Tag
}
