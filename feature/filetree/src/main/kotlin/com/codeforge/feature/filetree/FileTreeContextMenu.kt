package com.codeforge.feature.filetree

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.launch
import okio.Path

data class ContextMenuTarget(val path: Path, val name: String, val isDirectory: Boolean)

@Composable
fun FileTreeContextMenu(
    target: ContextMenuTarget?,
    clipboard: ClipboardEntry?,
    onDismiss: () -> Unit,
    onNewFile: (ContextMenuTarget) -> Unit,
    onNewDirectory: (ContextMenuTarget) -> Unit,
    onCut: (ContextMenuTarget) -> Unit,
    onCopy: (ContextMenuTarget) -> Unit,
    onPaste: (ContextMenuTarget) -> Unit,
    onRename: (ContextMenuTarget) -> Unit,
    onDelete: (ContextMenuTarget) -> Unit,
    onCopyPath: (ContextMenuTarget) -> Unit,
    onProperties: (ContextMenuTarget) -> Unit = {},
    offset: IntOffset = IntOffset(0, 0)
) {
    if (target == null) return

    val canPaste = clipboard != null
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    val themedConfig = LocalConfiguration.current
    val themedCtx = LocalContext.current

    Popup(
        alignment = Alignment.TopStart,
        offset = offset,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        CompositionLocalProvider(
            LocalContext provides themedCtx,
            LocalConfiguration provides themedConfig
        ) {
            Column(
                modifier = Modifier
                    .width(200.dp)
                    .shadow(8.dp, RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(vertical = 4.dp)
            ) {
                ContextMenuItem(
                    icon = Icons.Filled.NoteAdd,
                    label = "Neue Datei",
                    onClick = {
                        val t = target
                        onDismiss()
                        onNewFile(t)
                    }
                )
                ContextMenuItem(
                    icon = Icons.Filled.CreateNewFolder,
                    label = "Neuer Ordner",
                    onClick = {
                        val t = target
                        onDismiss()
                        onNewDirectory(t)
                    }
                )

                HorizontalDivider(color = Color.Gray, modifier = Modifier.padding(vertical = 4.dp))

                ContextMenuItem(
                    icon = Icons.Filled.ContentCut,
                    label = "Ausschneiden",
                    onClick = {
                        val t = target
                        onDismiss()
                        onCut(t)
                    }
                )
                ContextMenuItem(
                    icon = Icons.Filled.ContentCopy,
                    label = "Kopieren",
                    onClick = {
                        val t = target
                        onDismiss()
                        onCopy(t)
                    }
                )
                ContextMenuItem(
                    icon = Icons.Filled.ContentPaste,
                    label = "Einfügen",
                    enabled = canPaste,
                    onClick = {
                        val t = target
                        onDismiss()
                        onPaste(t)
                    }
                )

                HorizontalDivider(color = Color.Gray, modifier = Modifier.padding(vertical = 4.dp))

                ContextMenuItem(
                    icon = Icons.Filled.Edit,
                    label = "Umbenennen",
                    onClick = {
                        val t = target
                        onDismiss()
                        onRename(t)
                    }
                )
                ContextMenuItem(
                    icon = Icons.Filled.Delete,
                    label = "Löschen",
                    onClick = {
                        val t = target
                        onDismiss()
                        onDelete(t)
                    }
                )

                HorizontalDivider(color = Color.Gray, modifier = Modifier.padding(vertical = 4.dp))

                ContextMenuItem(
                    icon = Icons.Filled.Link,
                    label = "Pfad kopieren",
                    onClick = {
                        val t = target
                        onDismiss()
                        onCopyPath(t)
                        scope.launch {
                            clipboardManager.setText(AnnotatedString(t.path.toString()))
                        }
                    }
                )

                ContextMenuItem(
                    icon = Icons.Filled.Info,
                    label = "Eigenschaften",
                    onClick = {
                        val t = target
                        onDismiss()
                        onProperties(t)
                    }
                )
            }
        }
    }
}

@Composable
private fun ContextMenuItem(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val textColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.outline
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = label,
            color = textColor,
            fontSize = 13.sp
        )
    }
}
