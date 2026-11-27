// Modul: :app
package com.codeforge.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import com.codeforge.core.domain.extension.registry.Contribution
import com.codeforge.core.domain.extension.registry.ExtensionRegistry
import com.codeforge.libs.plugin_ui_api.PluginSidebarScope
import com.codeforge.libs.plugin_ui_api.PluginSidebarView
import com.codeforge.libs.plugin_ui_api.PluginUiPoints
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/** Seitenleisten-Ansichten der Plugins ([PluginUiPoints.SIDEBAR]). */
@HiltViewModel
class PluginSidebarViewModel @Inject constructor(registry: ExtensionRegistry) : ViewModel() {
    val views: StateFlow<List<Contribution<PluginSidebarView>>> = registry.observe(PluginUiPoints.SIDEBAR)
}

/** Schlüssel einer Plugin-Ansicht in der Seitenleiste (für die Auswahl). */
internal val Contribution<PluginSidebarView>.sidebarKey: String get() = "$pluginId/${value.id}"

/** Inhalt einer Plugin-Ansicht in der Seitenleiste. */
@Composable
internal fun PluginSidebarContent(
    contribution: Contribution<PluginSidebarView>,
    rootPath: String,
    onClose: () -> Unit,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = remember(contribution.sidebarKey, rootPath) {
        object : PluginSidebarScope {
            override val pluginId: String = contribution.pluginId
            override val projectRoot: String = rootPath
            override fun close() = onClose()
            override fun openScreen(screenId: String) {
                onClose()
                onNavigate(PluginRoutes.screen(contribution.pluginId, screenId))
            }
        }
    }
    contribution.value.Content(scope, modifier)
}

/** Symbol mit Beschriftung in der Leiste der Seitenleiste (eingebaute Bereiche und Plugins). */
@Composable
internal fun RailButton(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .width(64.dp)
            .background(if (selected) colors.secondaryContainer else Color.Transparent, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = label, tint = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant,
        )
    }
}
