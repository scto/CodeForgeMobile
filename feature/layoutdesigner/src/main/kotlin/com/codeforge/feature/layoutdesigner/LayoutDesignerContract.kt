/**
 * Modul: :feature:layoutdesigner
 * @author Thomas Schmid
 */
package com.codeforge.feature.layoutdesigner

import androidx.compose.runtime.Immutable
import com.codeforge.feature.layoutdesigner.model.LayoutDocument
import com.codeforge.feature.layoutdesigner.model.PaletteEntry

enum class DesignerTab { PREVIEW, TREE, PROPERTIES, XML }

/** Vorschau-Gerätegrößen in dp. Namen werden in der UI über Ressourcen aufgelöst. */
enum class DevicePreset(val widthDp: Int, val heightDp: Int) {
    PHONE(360, 640),
    PHONE_LARGE(411, 891),
    TABLET_7(600, 960),
    TABLET_10(800, 1280),
}

@Immutable
data class LayoutDesignerUiState(
    val filePath: String = "",
    val isLoading: Boolean = true,
    val loadError: String? = null,
    val document: LayoutDocument = LayoutDocument.newLinearLayout(),
    val selectedUid: Int? = null,
    val tab: DesignerTab = DesignerTab.PREVIEW,
    val device: DevicePreset = DevicePreset.PHONE,
    /** Aus [document] erzeugtes XML (immer aktuell). */
    val xmlText: String = "",
    /** Vom Nutzer bearbeiteter, noch nicht übernommener XML-Text; `null` = synchron. */
    val xmlDraft: String? = null,
    val xmlError: String? = null,
    val dirty: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val isSaving: Boolean = false,
    val showPalette: Boolean = false,
    val showDiscardConfirm: Boolean = false,
) {
    val fileName: String get() = filePath.substringAfterLast('/')
}

sealed interface LayoutDesignerUiEvent {
    data class Load(val filePath: String) : LayoutDesignerUiEvent
    data class Select(val uid: Int?) : LayoutDesignerUiEvent
    data class TabChanged(val tab: DesignerTab) : LayoutDesignerUiEvent
    data class DeviceChanged(val device: DevicePreset) : LayoutDesignerUiEvent
    data class AddWidget(val entry: PaletteEntry) : LayoutDesignerUiEvent
    data object DeleteSelected : LayoutDesignerUiEvent
    data object DuplicateSelected : LayoutDesignerUiEvent
    data class MoveSelected(val delta: Int) : LayoutDesignerUiEvent
    data object IndentSelected : LayoutDesignerUiEvent
    data object OutdentSelected : LayoutDesignerUiEvent
    data class SetAttribute(val uid: Int, val name: String, val value: String?) : LayoutDesignerUiEvent
    data class XmlChanged(val text: String) : LayoutDesignerUiEvent
    data object ApplyXml : LayoutDesignerUiEvent
    data object RevertXml : LayoutDesignerUiEvent
    data object Undo : LayoutDesignerUiEvent
    data object Redo : LayoutDesignerUiEvent
    data object Save : LayoutDesignerUiEvent
    data object OpenInEditor : LayoutDesignerUiEvent
    data class PaletteVisible(val visible: Boolean) : LayoutDesignerUiEvent
    data object BackRequested : LayoutDesignerUiEvent
    data object DismissDiscard : LayoutDesignerUiEvent
    data object ConfirmDiscard : LayoutDesignerUiEvent
}

sealed interface LayoutDesignerUiEffect {
    data class ShowSnackbar(val message: String) : LayoutDesignerUiEffect
    data object NavigateBack : LayoutDesignerUiEffect
}
