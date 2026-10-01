package com.codeforge.feature.filetree

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.ui.graphics.vector.ImageVector

fun iconForExtension(extension: String): ImageVector = when (extension.lowercase()) {
    "kt", "kts", "java", "cpp", "c", "h", "js", "ts" -> Icons.Filled.Code
    "xml", "json", "yml", "yaml", "toml", "properties", "pro" -> Icons.Filled.Settings
    "png", "jpg", "jpeg", "webp", "gif", "svg" -> Icons.Filled.Image
    "apk" -> Icons.Filled.Android
    "txt", "log", "md" -> Icons.Filled.Description
    "sh", "bash", "zsh", "bat", "gradlew" -> Icons.Filled.Terminal
    else -> Icons.Filled.InsertDriveFile
}

fun iconForComposable(): ImageVector = Icons.Filled.AutoAwesome

fun iconForFolder(name: String, isOpen: Boolean): ImageVector = when (name) {
    ".git", ".github" -> Icons.Filled.FolderSpecial
    else -> if (isOpen) Icons.Filled.FolderOpen else Icons.Filled.Folder
}
