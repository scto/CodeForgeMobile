package com.codeforge.feature.assetstudio.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes
import com.codeforge.feature.assetstudio.StudioAction

/** Drawer-Panel „Asset Studio“: Studio starten und vier Schnellaktionen. */
@Composable
fun AssetStudioPanel(onAction: (StudioAction) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringRes(R.string.assetstudio_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column {
                    Text(stringRes(R.string.assetstudio_title), style = MaterialTheme.typography.headlineSmall)
                    Text(stringRes(R.string.assetstudio_subtitle), style = MaterialTheme.typography.titleMedium)
                }
                Button(onClick = { onAction(StudioAction.STUDIO) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Brush, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(stringRes(R.string.assetstudio_launch))
                }
            }
        }
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringRes(R.string.assetstudio_quick_actions), style = MaterialTheme.typography.titleLarge)
                QuickAction(R.string.assetstudio_create_drawable) { onAction(StudioAction.DRAWABLE) }
                QuickAction(R.string.assetstudio_create_icon) { onAction(StudioAction.ICON) }
                QuickAction(R.string.assetstudio_import_image) { onAction(StudioAction.IMPORT) }
                QuickAction(R.string.assetstudio_m3_icons) { onAction(StudioAction.M3) }
            }
        }
    }
}

@Composable
private fun QuickAction(label: Int, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(stringRes(label)) }
}
