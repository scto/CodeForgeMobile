// Modul: :core:navigation
package com.codeforge.core.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class ActiveComposableFile(
    val path: String,
    val content: String,
    val composableFunctionNames: List<String> = emptyList()
)

@Singleton
class ActiveComposablePreviewBridge @Inject constructor() {
    private val _activeFile = MutableStateFlow<ActiveComposableFile?>(null)
    val activeFile: StateFlow<ActiveComposableFile?> = _activeFile.asStateFlow()

    fun publish(file: ActiveComposableFile?) {
        _activeFile.value = file
    }
}
