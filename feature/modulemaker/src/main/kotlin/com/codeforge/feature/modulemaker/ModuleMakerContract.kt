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

/** Zustand des Modul-Assistenten. */
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
    /** Ob das Modul erstellt werden kann. */
    val canCreate: Boolean get() = preview != null && inputError == null && hasSettingsFile && !isCreating
}

/** Aktionen des Modul-Assistenten. */
sealed interface ModuleMakerUiEvent {
    /** Wurzelordner setzen. */
    data class Initialize(val rootPath: String) : ModuleMakerUiEvent
    /** Eingabe geändert. */
    data class InputChanged(val value: String) : ModuleMakerUiEvent
    /** Modultyp geändert. */
    data class TypeChanged(val type: ModuleType) : ModuleMakerUiEvent
    /** Basis-Paket geändert. */
    data class BasePackageChanged(val value: String) : ModuleMakerUiEvent
    /** Modul erstellen. */
    data object Create : ModuleMakerUiEvent
    /** Ergebnis schließen. */
    data object DismissResult : ModuleMakerUiEvent
    /** Datei öffnen. */
    data class OpenFile(val path: String) : ModuleMakerUiEvent
}

/** Einmalige Effekte. */
sealed interface ModuleMakerUiEffect {
    /** Meldung anzeigen. */
    data class ShowSnackbar(val message: String) : ModuleMakerUiEffect
    /** Datei wurde im Editor geöffnet. */
    data object FileOpened : ModuleMakerUiEffect
}
