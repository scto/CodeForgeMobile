/**
 * Modul: :feature:modulemaker
 * @author Thomas Schmid
 */
package com.codeforge.feature.modulemaker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeforge.core.navigation.FileSyncBridge
import com.codeforge.core.navigation.OpenFileRequestBridge
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import com.codeforge.libs.code_tools.module.GradleModulePath
import com.codeforge.libs.code_tools.module.ModuleMaker
import com.codeforge.libs.code_tools.module.ModuleRequest
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
class ModuleMakerViewModel @Inject constructor(
    private val fileSyncBridge: FileSyncBridge,
    private val openFileBridge: OpenFileRequestBridge,
) : ViewModel() {

    private val _state = MutableStateFlow(ModuleMakerUiState())
    val state: StateFlow<ModuleMakerUiState> = _state.asStateFlow()

    private val _effects = Channel<ModuleMakerUiEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    fun onEvent(event: ModuleMakerUiEvent) {
        when (event) {
            is ModuleMakerUiEvent.Initialize -> initialize(event.rootPath)
            is ModuleMakerUiEvent.InputChanged -> { _state.update { it.copy(input = event.value, result = null, error = null) }; recompute() }
            is ModuleMakerUiEvent.TypeChanged -> _state.update { it.copy(type = event.type) }
            is ModuleMakerUiEvent.BasePackageChanged -> { _state.update { it.copy(basePackageOverride = event.value) }; recompute() }
            ModuleMakerUiEvent.Create -> create()
            ModuleMakerUiEvent.DismissResult -> _state.update { it.copy(result = null, error = null) }
            is ModuleMakerUiEvent.OpenFile -> viewModelScope.launch {
                openFileBridge.requestOpen(event.path)
                _effects.send(ModuleMakerUiEffect.FileOpened)
            }
        }
    }

    private fun initialize(rootPath: String) {
        if (_state.value.rootPath == rootPath) return
        _state.update { ModuleMakerUiState(rootPath = rootPath) }
        viewModelScope.launch {
            val probe = withContext(Dispatchers.IO) { runCatching { ModuleMaker.probe(File(rootPath)) }.getOrNull() }
            _state.update {
                it.copy(
                    hasSettingsFile = probe?.settingsFile != null,
                    detectedKotlinDsl = probe?.takeIf { p -> p.settingsFile != null }?.useKotlinDsl,
                    detectedBasePackage = probe?.basePackage,
                )
            }
            recompute()
        }
    }

    /** Eingabe validieren und Vorschau berechnen (rein, billig). */
    private fun recompute() {
        _state.update { s ->
            if (s.input.isBlank()) return@update s.copy(preview = null, inputError = null)
            GradleModulePath.parse(s.input).fold(
                onSuccess = { path ->
                    val base = s.basePackageOverride.ifBlank { s.detectedBasePackage ?: "com.example" }
                    val exists = File(s.rootPath, path.relativeDir).let { d -> d.exists() && !d.list().isNullOrEmpty() }
                    s.copy(
                        preview = ModulePreview(
                            gradlePath = path.path,
                            relativeDir = path.relativeDir,
                            packageName = ModuleMaker.packageFor(base, path),
                            className = ModuleMaker.className(path),
                            buildFileName = if (s.detectedKotlinDsl == false) "build.gradle" else "build.gradle.kts",
                        ),
                        inputError = if (exists) Res.string(R.string.modulemaker_ordner_existiert_bereits, path.relativeDir) else null,
                    )
                },
                onFailure = { s.copy(preview = null, inputError = it.message) },
            )
        }
    }

    private fun create() {
        val s = _state.value
        if (!s.canCreate) return
        _state.update { it.copy(isCreating = true, error = null, result = null) }
        viewModelScope.launch {
            // Offene Editor-Puffer von settings.gradle / build.gradle sichern, bevor wir sie ändern.
            val gradleFiles = listOf("settings.gradle", "settings.gradle.kts", "build.gradle", "build.gradle.kts")
                .map { File(s.rootPath, it).path }.toSet()
            fileSyncBridge.requestFlush(gradleFiles)
            val outcome = withContext(Dispatchers.IO) {
                ModuleMaker.create(
                    File(s.rootPath),
                    ModuleRequest(s.input, s.type, s.basePackageOverride.ifBlank { null }),
                )
            }
            outcome
                .onSuccess { r ->
                    val changed = buildList {
                        if (r.settingsChanged) add(r.settingsFile)
                        if (r.rootBuildChanged) {
                            listOf("build.gradle.kts", "build.gradle").map { File(s.rootPath, it) }.firstOrNull { it.isFile }?.let { add(it.path) }
                        }
                    }
                    fileSyncBridge.notifyExternalChange(changed)
                    _state.update { it.copy(isCreating = false, result = r, input = "", preview = null) }
                    _effects.send(ModuleMakerUiEffect.ShowSnackbar(Res.string(R.string.modulemaker_modul_angelegt, r.gradlePath)))
                }
                .onFailure { e -> _state.update { it.copy(isCreating = false, error = e.message ?: Res.string(R.string.modulemaker_anlegen_fehlgeschlagen)) } }
        }
    }
}
