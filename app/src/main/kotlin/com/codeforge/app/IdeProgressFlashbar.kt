// Modul: :app
package com.codeforge.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codeforge.core.domain.runtime.IdeProgress
import com.codeforge.core.domain.runtime.IdeProgressKind
import com.codeforge.core.domain.runtime.IdeProgressRegistry
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes
import kotlinx.coroutines.delay

/**
 * Leiste über allen Bildschirmen für laufende Vorgänge (Indexierung, Gradle, Installationen, Plugins).
 *
 * Zugeklappt: höchstens zwei Vorgänge mit je einer Detailzeile. Ein Tipp klappt sie auf: alle Vorgänge mit
 * Laufzeit und den letzten Detailzeilen. Schließt sich der letzte Vorgang, ist sie beim nächsten Mal wieder zu.
 */
@Composable
fun IdeProgressFlashbar(modifier: Modifier = Modifier) {
    val items by IdeProgressRegistry.items.collectAsStateWithLifecycle()
    var expanded by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(items.isEmpty()) { if (items.isEmpty()) expanded = false }
    val stateText = stringRes(if (expanded) R.string.flashbar_expanded else R.string.flashbar_collapsed)

    AnimatedVisibility(
        visible = items.isNotEmpty(),
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = modifier.statusBarsPadding().padding(horizontal = 56.dp, vertical = 4.dp),
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.94f))
                .clickable(role = Role.Button) { expanded = !expanded }
                .semantics {
                    liveRegion = LiveRegionMode.Polite
                    stateDescription = stateText
                }
                .animateContentSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (expanded) {
                ExpandedContent(items)
            } else {
                CollapsedContent(items)
            }
        }
    }
}

@Composable
private fun CollapsedContent(items: List<IdeProgress>) {
    val shown = items.takeLast(MAX_COLLAPSED)
    shown.forEachIndexed { index, item ->
        ProgressLine(
            item = item,
            trailing = if (index == 0) ExpandIcon(expanded = false, hidden = items.size - shown.size) else null,
        )
    }
}

@Composable
private fun ExpandedContent(items: List<IdeProgress>) {
    // Laufzeiten jede Sekunde neu; nur solange aufgeklappt.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(TICK_MS)
            now = System.currentTimeMillis()
        }
    }
    Column(
        modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEachIndexed { index, item ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.2f))
            ProgressLine(
                item = item,
                elapsed = formatElapsed(now - item.startedAt),
                trailing = if (index == 0) ExpandIcon(expanded = true, hidden = 0) else null,
            )
            History(item)
        }
    }
}

@Composable
private fun History(item: IdeProgress) {
    // Die aktuelle Zeile steht schon oben; hier die vorherigen, neueste zuletzt.
    val lines = item.history.dropLast(1)
    if (lines.isEmpty()) return
    val onColor = MaterialTheme.colorScheme.inverseOnSurface
    Column(modifier = Modifier.padding(start = 24.dp)) {
        lines.forEach { line ->
            Text(
                line,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = onColor.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.StartEllipsis,
            )
        }
    }
}

/** Pfeil rechts in der ersten Zeile; [hidden] = zugeklappt nicht gezeigte Vorgänge. */
private data class ExpandIcon(val expanded: Boolean, val hidden: Int)

@Composable
private fun ProgressLine(item: IdeProgress, trailing: ExpandIcon?, elapsed: String? = null) {
    val onColor = MaterialTheme.colorScheme.inverseOnSurface
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(kindIcon(item.kind), contentDescription = null, tint = onColor, modifier = Modifier.size(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringRes(kindLabel(item.kind), item.title),
                    style = MaterialTheme.typography.labelLarge, color = onColor,
                    maxLines = if (elapsed != null) 2 else 1, overflow = TextOverflow.Ellipsis,
                )
                if (item.detail.isNotBlank()) {
                    Text(
                        item.detail,
                        style = MaterialTheme.typography.labelSmall, color = onColor.copy(alpha = 0.8f),
                        maxLines = if (elapsed != null) 3 else 1, overflow = TextOverflow.StartEllipsis,
                    )
                }
            }
            elapsed?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = onColor.copy(alpha = 0.8f)) }
            item.percent?.let { Text("$it %", style = MaterialTheme.typography.labelMedium, color = onColor) }
            trailing?.let { ExpandIndicator(it) }
        }
        val percent = item.percent
        if (percent != null) {
            LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ExpandIndicator(icon: ExpandIcon) {
    val onColor = MaterialTheme.colorScheme.inverseOnSurface
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon.hidden > 0) Text("+${icon.hidden}", style = MaterialTheme.typography.labelMedium, color = onColor)
        Icon(
            if (icon.expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = null, tint = onColor, modifier = Modifier.size(20.dp),
        )
    }
}

private fun kindIcon(kind: IdeProgressKind): ImageVector = when (kind) {
    IdeProgressKind.INDEXING -> Icons.Filled.Search
    IdeProgressKind.GRADLE -> Icons.Filled.Build
    IdeProgressKind.INSTALL -> Icons.Filled.Download
    IdeProgressKind.PLUGIN -> Icons.Filled.Extension
}

private fun kindLabel(kind: IdeProgressKind): Int = when (kind) {
    IdeProgressKind.INDEXING -> R.string.flashbar_indexing
    IdeProgressKind.GRADLE -> R.string.flashbar_gradle
    IdeProgressKind.INSTALL -> R.string.flashbar_install
    IdeProgressKind.PLUGIN -> R.string.flashbar_plugin
}

/** „0:42“ bzw. „1:02:05“. */
internal fun formatElapsed(ms: Long): String {
    val total = (ms / MS_PER_SECOND).coerceAtLeast(0)
    val h = total / SECONDS_PER_HOUR
    val m = total % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
    val s = total % SECONDS_PER_MINUTE
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private const val MAX_COLLAPSED = 2
private const val TICK_MS = 1_000L
private const val MS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3_600L
