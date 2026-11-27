/**
 * Modul: :feature:build
 * @author Thomas Schmid
 */
package com.codeforge.feature.build

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Task
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes

/** Passendes Symbol je Task anhand von Name/Gruppe. */
internal fun taskIcon(task: GradleTaskInfo): ImageVector {
    val key = "${task.name.substringAfterLast(':')} ${task.group}".lowercase()
    return when {
        "clean" in key -> Icons.Filled.CleaningServices
        "install" in key -> Icons.Filled.InstallMobile
        "bundle" in key -> Icons.Filled.Widgets
        "compile" in key -> Icons.Filled.Memory
        "generate" in key -> Icons.Filled.AutoAwesome
        "test" in key || "verification" in key || "lint" in key || "check" in key -> Icons.Filled.CheckCircle
        "assemble" in key || "build" in key -> Icons.Filled.Build
        else -> Icons.Filled.Task
    }
}

/** Filtert nach Name, Gruppe und Beschreibung (Teilstring, Groß-/Kleinschreibung egal). */
internal fun filterTasks(tasks: List<GradleTaskInfo>, query: String): List<GradleTaskInfo> {
    val q = query.trim()
    if (q.isEmpty()) return tasks
    return tasks.filter {
        it.name.contains(q, ignoreCase = true) ||
            it.group.contains(q, ignoreCase = true) ||
            it.description.contains(q, ignoreCase = true)
    }
}

/**
 * Gradle-Tasks als Blatt: Suche, Mehrfachauswahl per Häkchen (Reihenfolge = Reihenfolge des Antippens), Build-Variante
 * des Start-Knopfs und „Ausführen (n)“. Kompakt für Smartphones: eine Zeile je Task, Gruppen aufklappbar.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskRunnerSheet(
    tasks: List<GradleTaskInfo>,
    variants: List<String>,
    selectedVariant: String,
    isLoading: Boolean,
    onVariantSelected: (String) -> Unit,
    onRun: (List<String>) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    var checked by remember { mutableStateOf(emptyList<String>()) }
    val filtered = remember(query, tasks) { filterTasks(tasks, query) }
    val sections = remember(filtered) { GradleTaskListParser.grouped(filtered) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SheetHeader(
                checkedCount = checked.size,
                onClear = { checked = emptyList() },
                onRun = { onRun(checked) }
            )
            Text(
                text = stringRes(R.string.build_variante),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                variants.forEach { variant ->
                    FilterChip(
                        selected = variant == selectedVariant,
                        onClick = { onVariantSelected(variant) },
                        label = { Text(variant) }
                    )
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringRes(R.string.build_tasks_suchen)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            SelectionInfo(checked = checked, isLoading = isLoading, isEmpty = filtered.isEmpty())
            TaskSections(
                sections = sections,
                searching = query.isNotBlank(),
                checked = checked,
                onToggle = { name -> checked = if (name in checked) checked - name else checked + name }
            )
        }
    }
}

@Composable
private fun SheetHeader(checkedCount: Int, onClear: () -> Unit, onRun: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringRes(R.string.build_tasks_titel),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f)
        )
        if (checkedCount > 0) {
            TextButton(onClick = onClear) {
                Text(stringRes(R.string.build_tasks_auswahl_leeren))
            }
        }
        Button(
            onClick = onRun,
            enabled = checkedCount > 0,
            contentPadding = PaddingValues(horizontal = 14.dp)
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringRes(R.string.build_tasks_ausfuehren, checkedCount))
        }
    }
}

// Reihenfolge der Auswahl, Ladeanzeige und „keine Treffer“.
@Composable
private fun SelectionInfo(checked: List<String>, isLoading: Boolean, isEmpty: Boolean, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (checked.size > 1) {
            Text(
                text = stringRes(R.string.build_tasks_reihenfolge, checked.joinToString(" → ")),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (isLoading) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(stringRes(R.string.build_tasks_laden), style = MaterialTheme.typography.bodySmall)
            }
        }
        if (isEmpty && !isLoading) {
            Text(
                stringRes(R.string.build_tasks_keine_treffer),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
    }
}

// Abschnitte mit aufklappbaren Überschriften; „Häufig“ und Suchtreffer sind immer offen.
@Composable
private fun TaskSections(
    sections: List<Pair<String, List<GradleTaskInfo>>>,
    searching: Boolean,
    checked: List<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val otherLabel = stringRes(R.string.build_tasks_sonstige)
    val commonLabel = stringRes(R.string.build_tasks_haeufig)
    var expanded by remember { mutableStateOf(emptySet<String>()) }
    LazyColumn(modifier = modifier.heightIn(max = 420.dp)) {
        sections.forEach { (group, groupTasks) ->
            val isCommon = GradleTaskListParser.isCommonGroup(group)
            val open = isCommon || searching || sections.size == 1 || group in expanded
            item(key = "h:$group") {
                TaskGroupHeader(
                    title = if (isCommon) commonLabel else group.ifBlank { otherLabel },
                    count = groupTasks.size,
                    expanded = open,
                    onClick = { expanded = if (group in expanded) expanded - group else expanded + group }
                )
            }
            if (open) {
                items(groupTasks, key = { "$group/${it.name}" }) { task ->
                    TaskRow(
                        task = task,
                        otherLabel = otherLabel,
                        order = checked.indexOf(task.name) + 1,
                        onClick = { onToggle(task.name) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskGroupHeader(
    title: String,
    count: Int,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$title ($count)",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// [order] > 0: Task ist ausgewählt und läuft an dieser Stelle.
@Composable
private fun TaskRow(
    task: GradleTaskInfo,
    otherLabel: String,
    order: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = order > 0, onCheckedChange = { onClick() })
        Icon(
            imageVector = taskIcon(task),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = task.description.ifBlank { task.group.ifBlank { otherLabel } },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (order > 0) {
            Text(
                text = "$order",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
    }
}
