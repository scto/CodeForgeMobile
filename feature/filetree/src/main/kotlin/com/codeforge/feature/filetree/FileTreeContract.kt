// Modul: :feature:filetree
package com.codeforge.feature.filetree

import androidx.compose.runtime.Immutable
import com.codeforge.core.domain.model.FileEntry

@Immutable
data class FileTreeNodeUi(
    val entry: FileEntry,
    val depth: Int,
    val isExpanded: Boolean
)

enum class FileTreeActionType { CREATE_FILE, CREATE_DIRECTORY, RENAME }

data class FileTreeAction(val type: FileTreeActionType, val targetPath: String)

@Immutable
data class FileTreeUiState(
    val rootPath: String = "",
    val visibleNodes: List<FileTreeNodeUi> = emptyList(),
    val expandedPaths: Set<String> = emptySet(),
    val selectedPath: String? = null,
    val pendingAction: FileTreeAction? = null,
    val isLoading: Boolean = true
)

sealed interface FileTreeUiEvent {
    data class NodeClicked(val entry: FileEntry) : FileTreeUiEvent
    data class NodeLongPressed(val entry: FileEntry) : FileTreeUiEvent
    data object DismissContextMenu : FileTreeUiEvent
    data object CreateFileClicked : FileTreeUiEvent
    data object CreateDirectoryClicked : FileTreeUiEvent
    data class RenameClicked(val entry: FileEntry) : FileTreeUiEvent
    data class DeleteClicked(val entry: FileEntry) : FileTreeUiEvent
    data class ActionConfirmed(val inputName: String) : FileTreeUiEvent
    data object ActionCancelled : FileTreeUiEvent
    data object Refresh : FileTreeUiEvent
}

sealed interface FileTreeUiEffect {
    data class OpenFileInEditor(val path: String) : FileTreeUiEffect
    data class ShowSnackbar(val message: String) : FileTreeUiEffect
}
