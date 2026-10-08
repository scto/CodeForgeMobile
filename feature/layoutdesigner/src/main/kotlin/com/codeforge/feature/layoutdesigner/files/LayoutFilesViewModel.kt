/**
 * Modul: :feature:layoutdesigner
 * @author Thomas Schmid
 */
package com.codeforge.feature.layoutdesigner.files

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeforge.core.navigation.FileSyncBridge
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class LayoutFilesViewModel @Inject constructor(
    private val fileSyncBridge: FileSyncBridge,
) : ViewModel() {

    private val _state = MutableStateFlow(LayoutFilesUiState())
    val state: StateFlow<LayoutFilesUiState> = _state.asStateFlow()

    private val _effects = Channel<LayoutFilesUiEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    fun onEvent(event: LayoutFilesUiEvent) {
        when (event) {
            is LayoutFilesUiEvent.Initialize -> if (_state.value.rootPath != event.rootPath) {
                _state.update { LayoutFilesUiState(rootPath = event.rootPath) }
                scan()
            }
            LayoutFilesUiEvent.Refresh -> scan()
            LayoutFilesUiEvent.ShowCreate -> _state.update {
                it.copy(showCreate = true, newName = "", createError = null, selectedResDir = it.resDirs.firstOrNull().orEmpty())
            }
            LayoutFilesUiEvent.DismissCreate -> _state.update { it.copy(showCreate = false) }
            is LayoutFilesUiEvent.NameChanged -> _state.update { it.copy(newName = event.value, createError = null) }
            is LayoutFilesUiEvent.ResDirSelected -> _state.update { it.copy(selectedResDir = event.value, createError = null) }
            LayoutFilesUiEvent.ConfirmCreate -> create()
            is LayoutFilesUiEvent.Open -> viewModelScope.launch { _effects.send(LayoutFilesUiEffect.OpenDesigner(event.path)) }
        }
    }

    private fun scan() {
        val root = _state.value.rootPath
        if (root.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(isScanning = true, error = null) }
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = File(root)
                    val res = LayoutScanner.findResDirs(dir).ifEmpty { listOf(DEFAULT_RES_DIR) }
                    LayoutScanner.findLayouts(dir) to res
                }
            }
            outcome
                .onSuccess { (files, res) ->
                    _state.update {
                        it.copy(
                            isScanning = false,
                            files = files,
                            resDirs = res,
                            selectedResDir = it.selectedResDir.takeIf { s -> s in res } ?: res.first(),
                        )
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(isScanning = false, error = Res.string(R.string.layout_scan_failed, e.message.orEmpty())) }
                }
        }
    }

    private fun create() {
        val s = _state.value
        if (!LayoutScanner.nameRegex.matches(s.newName)) {
            _state.update { it.copy(createError = Res.string(R.string.layout_name_invalid)) }
            return
        }
        viewModelScope.launch {
            val target = File(File(s.rootPath, s.selectedResDir), "layout/${s.newName}.xml")
            val result = withContext(Dispatchers.IO) {
                when {
                    target.exists() -> Result.failure(IllegalStateException(Res.string(R.string.layout_exists)))
                    else -> runCatching {
                        target.parentFile?.mkdirs()
                        target.writeText(LayoutScanner.TEMPLATE)
                    }
                }
            }
            result
                .onSuccess {
                    fileSyncBridge.notifyDirectoryChange(target.parentFile?.path.orEmpty())
                    _state.update { it.copy(showCreate = false) }
                    scan()
                    _effects.send(LayoutFilesUiEffect.OpenDesigner(target.path))
                }
                .onFailure { e ->
                    _state.update { it.copy(createError = Res.string(R.string.layout_create_failed, e.message.orEmpty())) }
                }
        }
    }

    private companion object {
        const val DEFAULT_RES_DIR = "app/src/main/res"
    }
}
