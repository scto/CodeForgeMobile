/**
 * Modul: :feature:layoutdesigner
 * @author Thomas Schmid
 */
package com.codeforge.feature.layoutdesigner

import androidx.compose.runtime.Immutable
import com.codeforge.feature.layoutdesigner.dnd.DropTarget
import com.codeforge.feature.layoutdesigner.model.DragPayload
import com.codeforge.feature.layoutdesigner.model.LayoutDocument
import com.codeforge.feature.layoutdesigner.model.PaletteEntry

enum class DesignerTab { PREVIEW, TREE, PROPERTIES, PROBLEMS, XML }

/** Vorschau-Gerätegrößen in dp. Namen werden in der UI über Ressourcen aufgelöst. */
enum class DevicePreset(val widthDp: Int, val heightDp: Int) {
    PHONE(360, 640),
    PHONE_LARGE(411, 891),
    TABLET_7(600, 960),
    TABLET_10(800, 1280),
}

enum class OverlayType { BOUNDS, MARGINS_PADDINGS, BLUEPRINT, GRID_SNAP }

@Immutable
data class LayoutDesignerUiState(
    val filePath: String = "",
    val isLoading: Boolean = true,
    val loadError: String? = null,
    val document: LayoutDocument = LayoutDocument.newLinearLayout(),
    val selectedUid: Int? = null,
    val selectedUids: Set<Int> = emptySet(),
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
    val reformatOnSave: Boolean = false,

    // 41.3 Leinwand, Konfiguration und Werkzeuge
    val zoom: Float = 1.0f,
    val panX: Float = 0f,
    val panY: Float = 0f,
    val isLandscape: Boolean = false,
    val isDarkTheme: Boolean = false,
    val fontScale: Float = 1.0f,
    val isRtl: Boolean = false,
    val customWidthDp: Int? = null,
    val customHeightDp: Int? = null,
    val showBoundsOverlay: Boolean = false,
    val showMarginsPaddingsOverlay: Boolean = true,
    val blueprintMode: Boolean = false,
    val snapTo8DpGrid: Boolean = true,
    val clipboardXml: String? = null,
    val showToolsAttributes: Boolean = true,
    val resourceRepo: com.codeforge.feature.layoutdesigner.res.ResourceRepository? = null,
    val includeResolver: com.codeforge.feature.layoutdesigner.res.IncludeResolver? = null,
    val customViews: List<com.codeforge.feature.layoutdesigner.model.CustomViewInfo> = emptyList(),
    val inflatedViewStubs: Set<Int> = emptySet(),
    val issues: List<com.codeforge.feature.layoutdesigner.lint.LayoutIssue> = emptyList(),
) {
    val fileName: String get() = filePath.substringAfterLast('/')

    val effectiveWidthDp: Int get() {
        val baseW = customWidthDp ?: device.widthDp
        val baseH = customHeightDp ?: device.heightDp
        return if (isLandscape) maxOf(baseW, baseH) else minOf(baseW, baseH)
    }

    val effectiveHeightDp: Int get() {
        val baseW = customWidthDp ?: device.widthDp
        val baseH = customHeightDp ?: device.heightDp
        return if (isLandscape) minOf(baseW, baseH) else maxOf(baseW, baseH)
    }
}

sealed interface LayoutDesignerUiEvent {
    data class Load(val filePath: String) : LayoutDesignerUiEvent
    data class Select(val uid: Int?) : LayoutDesignerUiEvent
    data class SelectToggle(val uid: Int) : LayoutDesignerUiEvent
    data class TabChanged(val tab: DesignerTab) : LayoutDesignerUiEvent
    data class DeviceChanged(val device: DevicePreset) : LayoutDesignerUiEvent
    data class AddWidget(val entry: PaletteEntry) : LayoutDesignerUiEvent
    data class DropWidget(val payload: DragPayload, val target: DropTarget) : LayoutDesignerUiEvent
    data object DeleteSelected : LayoutDesignerUiEvent
    data object DuplicateSelected : LayoutDesignerUiEvent
    data class MoveSelected(val delta: Int) : LayoutDesignerUiEvent
    data object IndentSelected : LayoutDesignerUiEvent
    data object OutdentSelected : LayoutDesignerUiEvent
    data class WrapIn(val containerTag: String) : LayoutDesignerUiEvent
    data object UnwrapSelected : LayoutDesignerUiEvent
    data class ChangeTag(val uid: Int, val newTag: String, val removeIncompatible: Boolean = false) : LayoutDesignerUiEvent
    data class RenameId(val uid: Int, val newId: String) : LayoutDesignerUiEvent
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
    data class SetReformatOnSave(val enabled: Boolean) : LayoutDesignerUiEvent

    // 41.3 Neue Ereignisse
    data class SetZoom(val zoom: Float) : LayoutDesignerUiEvent
    data class SetPan(val panX: Float, val panY: Float) : LayoutDesignerUiEvent
    data object ResetZoomPan : LayoutDesignerUiEvent
    data class SetLandscape(val isLandscape: Boolean) : LayoutDesignerUiEvent
    data class SetDarkTheme(val isDark: Boolean) : LayoutDesignerUiEvent
    data class SetFontScale(val scale: Float) : LayoutDesignerUiEvent
    data class SetRtl(val isRtl: Boolean) : LayoutDesignerUiEvent
    data class SetCustomDimensions(val widthDp: Int?, val heightDp: Int?) : LayoutDesignerUiEvent
    data class ToggleOverlay(val overlayType: OverlayType) : LayoutDesignerUiEvent
    data object CopySelected : LayoutDesignerUiEvent
    data object CutSelected : LayoutDesignerUiEvent
    data class PasteSnippet(val xmlSnippet: String) : LayoutDesignerUiEvent
    data class ResizeSelected(val widthDp: Int?, val heightDp: Int?) : LayoutDesignerUiEvent
    data class ToggleSizeMode(val isWidth: Boolean) : LayoutDesignerUiEvent

    // 41.4 ConstraintLayout & RelativeLayout
    data class SetConstraint(
        val uid: Int,
        val anchor: com.codeforge.feature.layoutdesigner.model.ConstraintAnchor,
        val target: com.codeforge.feature.layoutdesigner.model.ConstraintTarget,
        val targetAnchor: com.codeforge.feature.layoutdesigner.model.ConstraintAnchor,
        val marginDp: Int = 0,
    ) : LayoutDesignerUiEvent
    data class RemoveConstraint(val uid: Int, val anchor: com.codeforge.feature.layoutdesigner.model.ConstraintAnchor) : LayoutDesignerUiEvent
    data class CenterHorizontally(val uid: Int) : LayoutDesignerUiEvent
    data class CenterVertically(val uid: Int) : LayoutDesignerUiEvent
    data class ClearConstraints(val uid: Int) : LayoutDesignerUiEvent
    data class AddGuideline(val parentUid: Int, val isVertical: Boolean) : LayoutDesignerUiEvent
    data class SetHorizontalBias(val uid: Int, val bias: Float) : LayoutDesignerUiEvent
    data class SetVerticalBias(val uid: Int, val bias: Float) : LayoutDesignerUiEvent

    // 41.5 Ressourcen auflösen & auslagern
    data class ExtractToResource(
        val nodeUid: Int,
        val attrName: String,
        val resType: com.codeforge.feature.layoutdesigner.res.ResType,
        val name: String,
        val value: String,
    ) : LayoutDesignerUiEvent
    data class SetShowToolsAttributes(val enabled: Boolean) : LayoutDesignerUiEvent

    // 41.6 include, merge, ViewStub & eigene Views
    data class ToggleInflateViewStub(val uid: Int) : LayoutDesignerUiEvent
    data class OpenIncludedLayout(val layoutRef: String) : LayoutDesignerUiEvent

    // 41.8 Lint & Probleme
    data class ApplyQuickFix(val quickFix: com.codeforge.feature.layoutdesigner.lint.QuickFix) : LayoutDesignerUiEvent
    data class JumpToIssue(val issue: com.codeforge.feature.layoutdesigner.lint.LayoutIssue) : LayoutDesignerUiEvent
}

sealed interface LayoutDesignerUiEffect {
    data class ShowSnackbar(val message: String) : LayoutDesignerUiEffect
    data object NavigateBack : LayoutDesignerUiEffect
    data class CopyToClipboard(val text: String) : LayoutDesignerUiEffect
    data class OpenLayoutFile(val path: String) : LayoutDesignerUiEffect
}
