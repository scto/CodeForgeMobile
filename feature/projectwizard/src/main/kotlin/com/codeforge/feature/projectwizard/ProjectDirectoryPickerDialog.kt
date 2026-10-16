// Modul: :feature:projectwizard
package com.codeforge.feature.projectwizard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codeforge.core.resources.R as CoreR
import java.io.File

/**
 * Verzeichnis-Auswahldialog für den Projekt-Wizard.
 *
 * Begrenzt die Auswahl auf Dateisystem-Pfade, die für die Termux-/App-Umgebung
 * (`codeforge-env`, POSIX-Dateisystem, `filesDir`/`projects`) zugänglich und ausführbar sind.
 */
@Composable
fun ProjectDirectoryPickerDialog(
    initialDir: String,
    onDismiss: () -> Unit,
    onSelectDir: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var currentDir by remember(initialDir) {
        val f = File(initialDir.trim())
        val resolved = if (f.exists() && f.isDirectory) f else f.parentFile?.takeIf { it.exists() && it.isDirectory } ?: File("/")
        mutableStateOf(resolved)
    }

    var showNewFolderDialog by remember { mutableStateOf(false) }

    val subDirs = remember(currentDir) {
        runCatching {
            currentDir.listFiles()
                ?.filter { it.isDirectory && !it.name.startsWith(".") && it.canRead() }
                ?.sortedBy { it.name.lowercase() }
        }.getOrNull().orEmpty()
    }

    val canGoUp = currentDir.parentFile != null && currentDir.parentFile?.canRead() == true

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier.fillMaxWidth(),
        title = {
            Text(
                text = stringResource(CoreR.string.pw_select_directory_title),
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Aktueller Pfad
                Text(
                    text = currentDir.absolutePath,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                // Aktionsleiste: Übergeordneter Ordner & Neuer Ordner
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = { currentDir.parentFile?.let { currentDir = it } },
                        enabled = canGoUp,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(CoreR.string.pw_folder_up),
                        )
                    }

                    OutlinedButton(onClick = { showNewFolderDialog = true }) {
                        Text(stringResource(CoreR.string.pw_new_folder))
                    }
                }

                HorizontalDivider(color = DividerDefaults.color)

                // Verzeichnisliste
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp),
                ) {
                    if (subDirs.isEmpty()) {
                        item {
                            Text(
                                text = "-",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    } else {
                        items(subDirs, key = { it.absolutePath }) { dir ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { currentDir = dir }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = dir.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSelectDir(currentDir.absolutePath) }) {
                Text(stringResource(CoreR.string.pw_select_this_directory))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(CoreR.string.pw_back))
            }
        },
    )

    if (showNewFolderDialog) {
        var newFolderName by remember { mutableStateOf("") }
        var folderError by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = { Text(stringResource(CoreR.string.pw_new_folder_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newFolderName,
                        onValueChange = {
                            newFolderName = it
                            folderError = false
                        },
                        label = { Text(stringResource(CoreR.string.pw_new_folder_hint)) },
                        isError = folderError,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = newFolderName.trim()
                        if (name.isNotEmpty() && !name.contains("/") && !name.contains("..")) {
                            val newDir = File(currentDir, name)
                            if (newDir.mkdirs() || newDir.isDirectory) {
                                currentDir = newDir
                                showNewFolderDialog = false
                            } else {
                                folderError = true
                            }
                        } else {
                            folderError = true
                        }
                    },
                ) {
                    Text(stringResource(CoreR.string.pw_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolderDialog = false }) {
                    Text(stringResource(CoreR.string.pw_back))
                }
            },
        )
    }
}
