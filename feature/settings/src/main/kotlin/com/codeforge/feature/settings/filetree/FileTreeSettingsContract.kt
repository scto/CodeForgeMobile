package com.codeforge.feature.settings.filetree

import androidx.compose.runtime.Immutable

import com.codeforge.core.datastore.proto.FileTreeConfig
import com.codeforge.core.datastore.proto.FileTreeSortByProto
import com.codeforge.core.datastore.proto.FileTreeSortOrderProto
import com.codeforge.core.datastore.proto.FileTreeViewModeProto

@Immutable
data class FileTreeSettingsUiState(
    val fileTreeConfig: FileTreeConfig = FileTreeConfig.getDefaultInstance()
)

sealed interface FileTreeSettingsUiEvent {
    data class SortOrderChanged(val sortOrder: FileTreeSortOrderProto) : FileTreeSettingsUiEvent
    data class SortByChanged(val sortBy: FileTreeSortByProto) : FileTreeSettingsUiEvent
    data class ShowHiddenFilesToggled(val show: Boolean) : FileTreeSettingsUiEvent
    data class ShowIndentLinesToggled(val show: Boolean) : FileTreeSettingsUiEvent
    data class ShowFileDetailsToggled(val show: Boolean) : FileTreeSettingsUiEvent
    data class CompactModeToggled(val compact: Boolean) : FileTreeSettingsUiEvent
    data class FontSizeChanged(val fontSize: Int) : FileTreeSettingsUiEvent
    data class ViewModeChanged(val viewMode: FileTreeViewModeProto) : FileTreeSettingsUiEvent
}
