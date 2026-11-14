// Modul: :app
package com.codeforge.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codeforge.core.domain.runtime.IdeProgress
import com.codeforge.core.domain.runtime.IdeProgressKind
import com.codeforge.core.domain.runtime.IdeProgressRegistry
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes

/**
 * Schmale Leiste über allen Bildschirmen: zeigt laufende Indexierung (Datei, Prozent) und Gradle-Läufe (Task).
 * Bewusst ohne Klickfläche und ohne `Surface`, damit Berührungen an die Leisten darunter durchgehen.
 */
@Composable
fun IdeProgressFlashbar(modifier: Modifier = Modifier) {
    val items by IdeProgressRegistry.items.collectAsStateWithLifecycle()
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
                .semantics { liveRegion = LiveRegionMode.Polite }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Höchstens zwei Vorgänge zeigen, damit die Leiste schmal bleibt.
            items.takeLast(2).forEach { ProgressLine(it) }
        }
    }
}

@Composable
private fun ProgressLine(item: IdeProgress) {
    val onColor = MaterialTheme.colorScheme.inverseOnSurface
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                when (item.kind) {
                    IdeProgressKind.INDEXING -> Icons.Filled.Search
                    IdeProgressKind.GRADLE -> Icons.Filled.Build
                    IdeProgressKind.INSTALL -> Icons.Filled.Download
                },
                contentDescription = null, tint = onColor, modifier = Modifier.size(16.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringRes(
                        when (item.kind) {
                            IdeProgressKind.INDEXING -> R.string.flashbar_indexing
                            IdeProgressKind.GRADLE -> R.string.flashbar_gradle
                            IdeProgressKind.INSTALL -> R.string.flashbar_install
                        },
                        item.title
                    ),
                    style = MaterialTheme.typography.labelLarge, color = onColor, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (item.detail.isNotBlank()) {
                    Text(
                        item.detail,
                        style = MaterialTheme.typography.labelSmall, color = onColor.copy(alpha = 0.8f),
                        maxLines = 1, overflow = TextOverflow.StartEllipsis,
                    )
                }
            }
            item.percent?.let { Text("$it %", style = MaterialTheme.typography.labelMedium, color = onColor) }
        }
        val percent = item.percent
        if (percent != null) {
            LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}
