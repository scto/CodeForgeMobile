/**
 * Modul: :feature:modulemaker
 * @author Thomas Schmid
 */
package com.codeforge.feature.modulemaker

import androidx.compose.runtime.Immutable
import com.codeforge.libs.code_tools.module.ModuleResult
import com.codeforge.libs.code_tools.module.ModuleType

/** Abgeleitete Vorschau zur Eingabe (Ordner, Package, Klassenname). */
@Immutable
data class ModulePreview(
    val gradlePath: String,
    val relativeDir: String,
    val packageName: String,
    val className: String,
    val buildFileName: String,
)

@Immutable
data class ModuleMakerUiState(
    val rootPath: String = "",
    val input: String = "",
    val type: ModuleType = ModuleType.ANDROID_LIBRARY,
    val basePackageOverride: String = "",
    /** Erkannt aus dem Projekt (`null` = noch nicht geprüft / keine settings.gradle). */
    val detectedKotlinDsl: Boolean? = null,
    val detectedBasePackage: String? = null,
    val hasSettingsFile: Boolean = true,
    val preview: ModulePreview? = null,
    val inputError: String? = null,
    val isCreating: Boolean = false,
    val result: ModuleResult? = null,
    val error: String? = null,
) {
    val canCreate: Boolean get() = preview != null && inputError == null && hasSettingsFile && !isCreating
}

sealed interface ModuleMakerUiEvent {
    data class Initialize(val rootPath: String) : ModuleMakerUiEvent
    data class InputChanged(val value: String) : ModuleMakerUiEvent
    data class TypeChanged(val type: ModuleType) : ModuleMakerUiEvent
    data class BasePackageChanged(val value: String) : ModuleMakerUiEvent
    data object Create : ModuleMakerUiEvent
    data object DismissResult : ModuleMakerUiEvent
    data class OpenFile(val path: String) : ModuleMakerUiEvent
}

sealed interface ModuleMakerUiEffect {
    data class ShowSnackbar(val message: String) : ModuleMakerUiEffect
    /** Datei wurde im Editor geöffnet. */
    data object FileOpened : ModuleMakerUiEffect
}
