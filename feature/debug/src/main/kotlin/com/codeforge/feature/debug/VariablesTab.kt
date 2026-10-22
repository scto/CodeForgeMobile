package com.codeforge.feature.debug

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.codeforge.core.domain.model.Variable
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes

@Composable
fun VariablesTab(
    state: DebugUiState,
    onIntent: (DebugUiIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    val topLevelVars = state.scopes.flatMap { scope ->
        state.variables[scope.variablesReference].orEmpty()
    }

    if (topLevelVars.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringRes(R.string.debug_empty_variables),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(topLevelVars, key = { it.name + it.variablesReference }) { v ->
            VariableItem(
                variable = v,
                indent = 0,
                allVariables = state.variables,
                expandedRefs = state.expandedVariables,
                onToggleExpand = { onIntent(DebugUiIntent.ToggleExpandVariable(it)) }
            )
        }
    }
}

@Composable
private fun VariableItem(
    variable: Variable,
    indent: Int,
    allVariables: Map<Long, List<Variable>>,
    expandedRefs: Set<Long>,
    onToggleExpand: (Variable) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val hasChildren = variable.variablesReference > 0
    val isExpanded = expandedRefs.contains(variable.variablesReference)
    val children = allVariables[variable.variablesReference].orEmpty()

    var showFullValue by remember { mutableStateOf(false) }
    val isLongValue = variable.value.length > 80

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = hasChildren) { onToggleExpand(variable) }
                    .padding(start = (indent * 16 + 8).dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (hasChildren) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Spacer(modifier = Modifier.size(18.dp))
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Name
                Text(
                    text = variable.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Type
                if (!variable.type.isNullOrBlank()) {
                    Text(
                        text = " : ${variable.type}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                Text(
                    text = " = ",
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Value
                val displayedValue = if (isLongValue && !showFullValue) {
                    variable.value.take(80) + "…"
                } else {
                    variable.value
                }

                Text(
                    text = displayedValue,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f, fill = false)
                )

                // Copy button
                IconButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText(variable.name, variable.value)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, context.getString(R.string.debug_value_copied), Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = stringRes(R.string.debug_copy_value),
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
            }

            // "Mehr" / "Weniger" Toggle bei langen Strings
            if (isLongValue) {
                TextButton(
                    onClick = { showFullValue = !showFullValue },
                    modifier = Modifier.padding(start = (indent * 16 + 32).dp)
                ) {
                    Text(
                        text = if (showFullValue) stringRes(R.string.debug_less) else stringRes(R.string.debug_more),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            // Rekursive Kind-Variablen
            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    children.forEach { child ->
                        VariableItem(
                            variable = child,
                            indent = indent + 1,
                            allVariables = allVariables,
                            expandedRefs = expandedRefs,
                            onToggleExpand = onToggleExpand
                        )
                    }
                }
            }
        }
    }
}
