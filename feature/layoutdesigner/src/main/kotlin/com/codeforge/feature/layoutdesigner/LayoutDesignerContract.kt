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

/** Reiter des Designers. */
enum class DesignerTab { PREVIEW, TREE, PROPERTIES, PROBLEMS, XML }

/** Vorschau-Gerätegrößen in dp. Namen werden in der UI über Ressourcen aufgelöst. */
enum class DevicePreset(val widthDp: Int, val heightDp: Int) {
    PHONE(360, 640),
    PHONE_LARGE(411, 891),
    TABLET_7(600, 960),
    TABLET_10(800, 1280),
}

/** Hilfsüberlagerungen der Vorschau. */
enum class OverlayType { BOUNDS, MARGINS_PADDINGS, BLUEPRINT, GRID_SNAP }

/** Zustand des Layout-Designers (Dokument, Auswahl, Gerät, Zoom, Überlagerungen, XML, Probleme, Varianten). */
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
    val availableVariants: List<String> = emptyList(),
    val currentVariant: String = "default",
    val showExternalChangeDialog: Boolean = false,
    val externalFileContent: String? = null,
) {
    /** Dateiname ohne Pfad. */
    val fileName: String get() = filePath.substringAfterLast('/')

    /** Wirksame Breite der Vorschau in dp (Gerät, Ausrichtung, eigene Maße). */
    val effectiveWidthDp: Int get() {
        val baseW = customWidthDp ?: device.widthDp
        val baseH = customHeightDp ?: device.heightDp
        return if (isLandscape) maxOf(baseW, baseH) else minOf(baseW, baseH)
    }

    /** Wirksame Höhe der Vorschau in dp. */
    val effectiveHeightDp: Int get() {
        val baseW = customWidthDp ?: device.widthDp
        val baseH = customHeightDp ?: device.heightDp
        return if (isLandscape) minOf(baseW, baseH) else maxOf(baseW, baseH)
    }
}

/** Aktionen im Layout-Designer. */
sealed interface LayoutDesignerUiEvent {
    /** Datei laden. */
    data class Load(val filePath: String) : LayoutDesignerUiEvent
    /** Knoten auswählen (`null` = Auswahl aufheben). */
    data class Select(val uid: Int?) : LayoutDesignerUiEvent
    /** Knoten zur Mehrfachauswahl hinzufügen/entfernen. */
    data class SelectToggle(val uid: Int) : LayoutDesignerUiEvent
    /** Reiter gewechselt. */
    data class TabChanged(val tab: DesignerTab) : LayoutDesignerUiEvent
    /** Gerätevorgabe gewechselt. */
    data class DeviceChanged(val device: DevicePreset) : LayoutDesignerUiEvent
    /** Widget aus der Palette hinzufügen. */
    data class AddWidget(val entry: PaletteEntry) : LayoutDesignerUiEvent
    /** Widget per Ziehen ablegen. */
    data class DropWidget(val payload: DragPayload, val target: DropTarget) : LayoutDesignerUiEvent
    /** Auswahl löschen. */
    data object DeleteSelected : LayoutDesignerUiEvent
    /** Auswahl duplizieren. */
    data object DuplicateSelected : LayoutDesignerUiEvent
    /** Auswahl um [delta] Positionen verschieben. */
    data class MoveSelected(val delta: Int) : LayoutDesignerUiEvent
    /** Auswahl in den vorigen Container einrücken. */
    data object IndentSelected : LayoutDesignerUiEvent
    /** Auswahl aus dem Container herausnehmen. */
    data object OutdentSelected : LayoutDesignerUiEvent
    /** Auswahl in einen Container einpacken. */
    data class WrapIn(val containerTag: String) : LayoutDesignerUiEvent
    /** Container auflösen. */
    data object UnwrapSelected : LayoutDesignerUiEvent
    /** Widget-Typ ändern. */
    data class ChangeTag(val uid: Int, val newTag: String, val removeIncompatible: Boolean = false) : LayoutDesignerUiEvent
    /** ID umbenennen (Verweise werden angepasst). */
    data class RenameId(val uid: Int, val newId: String) : LayoutDesignerUiEvent
    /** Attribut setzen oder entfernen (`null`). */
    data class SetAttribute(val uid: Int, val name: String, val value: String?) : LayoutDesignerUiEvent
    /** XML-Text geändert. */
    data class XmlChanged(val text: String) : LayoutDesignerUiEvent
    /** XML übernehmen. */
    data object ApplyXml : LayoutDesignerUiEvent
    /** XML-Änderung verwerfen. */
    data object RevertXml : LayoutDesignerUiEvent
    /** Rückgängig. */
    data object Undo : LayoutDesignerUiEvent
    /** Wiederholen. */
    data object Redo : LayoutDesignerUiEvent
    /** Speichern. */
    data object Save : LayoutDesignerUiEvent
    /** Im Texteditor öffnen. */
    data object OpenInEditor : LayoutDesignerUiEvent
    /** Palette ein-/ausblenden. */
    data class PaletteVisible(val visible: Boolean) : LayoutDesignerUiEvent
    /** Zurück gewünscht. */
    data object BackRequested : LayoutDesignerUiEvent
    /** Verwerfen-Dialog schließen. */
    data object DismissDiscard : LayoutDesignerUiEvent
    /** Änderungen verwerfen bestätigt. */
    data object ConfirmDiscard : LayoutDesignerUiEvent
    /** XML beim Speichern formatieren. */
    data class SetReformatOnSave(val enabled: Boolean) : LayoutDesignerUiEvent

    // 41.3 Neue Ereignisse
    /** Zoom setzen. */
    data class SetZoom(val zoom: Float) : LayoutDesignerUiEvent
    /** Verschiebung setzen. */
    data class SetPan(val panX: Float, val panY: Float) : LayoutDesignerUiEvent
    /** Zoom und Verschiebung zurücksetzen. */
    data object ResetZoomPan : LayoutDesignerUiEvent
    /** Querformat. */
    data class SetLandscape(val isLandscape: Boolean) : LayoutDesignerUiEvent
    /** Dunkles Design in der Vorschau. */
    data class SetDarkTheme(val isDark: Boolean) : LayoutDesignerUiEvent
    /** Schriftskalierung. */
    data class SetFontScale(val scale: Float) : LayoutDesignerUiEvent
    /** Rechts-nach-links-Layout. */
    data class SetRtl(val isRtl: Boolean) : LayoutDesignerUiEvent
    /** Eigene Vorschaumaße. */
    data class SetCustomDimensions(val widthDp: Int?, val heightDp: Int?) : LayoutDesignerUiEvent
    /** Überlagerung ein-/ausschalten. */
    data class ToggleOverlay(val overlayType: OverlayType) : LayoutDesignerUiEvent
    /** Auswahl kopieren. */
    data object CopySelected : LayoutDesignerUiEvent
    /** Auswahl ausschneiden. */
    data object CutSelected : LayoutDesignerUiEvent
    /** XML-Ausschnitt einfügen. */
    data class PasteSnippet(val xmlSnippet: String) : LayoutDesignerUiEvent
    /** Größe der Auswahl ändern. */
    data class ResizeSelected(val widthDp: Int?, val heightDp: Int?) : LayoutDesignerUiEvent
    /** Größenmodus (Breite/Höhe) umschalten. */
    data class ToggleSizeMode(val isWidth: Boolean) : LayoutDesignerUiEvent

    // 41.4 ConstraintLayout & RelativeLayout
    /** Constraint eines Knotens setzen. */
    data class SetConstraint(
        val uid: Int,
        val anchor: com.codeforge.feature.layoutdesigner.model.ConstraintAnchor,
        val target: com.codeforge.feature.layoutdesigner.model.ConstraintTarget,
        val targetAnchor: com.codeforge.feature.layoutdesigner.model.ConstraintAnchor,
        val marginDp: Int = 0,
    ) : LayoutDesignerUiEvent
    /** Constraint entfernen. */
    data class RemoveConstraint(val uid: Int, val anchor: com.codeforge.feature.layoutdesigner.model.ConstraintAnchor) : LayoutDesignerUiEvent
    /** Horizontal zentrieren. */
    data class CenterHorizontally(val uid: Int) : LayoutDesignerUiEvent
    /** Vertikal zentrieren. */
    data class CenterVertically(val uid: Int) : LayoutDesignerUiEvent
    /** Alle Constraints entfernen. */
    data class ClearConstraints(val uid: Int) : LayoutDesignerUiEvent
    /** Hilfslinie hinzufügen. */
    data class AddGuideline(val parentUid: Int, val isVertical: Boolean) : LayoutDesignerUiEvent
    /** Horizontale Gewichtung setzen. */
    data class SetHorizontalBias(val uid: Int, val bias: Float) : LayoutDesignerUiEvent
    /** Vertikale Gewichtung setzen. */
    data class SetVerticalBias(val uid: Int, val bias: Float) : LayoutDesignerUiEvent

    // 41.5 Ressourcen auflösen & auslagern
    /** Wert in eine Ressource auslagern. */
    data class ExtractToResource(
        val nodeUid: Int,
        val attrName: String,
        val resType: com.codeforge.feature.layoutdesigner.res.ResType,
        val name: String,
        val value: String,
    ) : LayoutDesignerUiEvent
    /** `tools:`-Attribute anzeigen. */
    data class SetShowToolsAttributes(val enabled: Boolean) : LayoutDesignerUiEvent

    // 41.6 include, merge, ViewStub & eigene Views
    /** ViewStub aufblasen/zuklappen. */
    data class ToggleInflateViewStub(val uid: Int) : LayoutDesignerUiEvent
    /** Eingebundenes Layout öffnen. */
    data class OpenIncludedLayout(val layoutRef: String) : LayoutDesignerUiEvent

    // 41.8 Lint & Probleme
    /** Schnellkorrektur anwenden. */
    data class ApplyQuickFix(val quickFix: com.codeforge.feature.layoutdesigner.lint.QuickFix) : LayoutDesignerUiEvent
    /** Zum Problem springen. */
    data class JumpToIssue(val issue: com.codeforge.feature.layoutdesigner.lint.LayoutIssue) : LayoutDesignerUiEvent

    // 41.9 Editor-Integration & Varianten
    /** Layout-Variante wechseln. */
    data class SwitchVariant(val variant: String) : LayoutDesignerUiEvent
    /** Variante anlegen. */
    data class CreateVariant(val qualifier: String) : LayoutDesignerUiEvent
    /** Extern geänderte Fassung laden. */
    data object ReloadFromExternal : LayoutDesignerUiEvent
    /** Eigene Fassung behalten. */
    data object KeepMyVersion : LayoutDesignerUiEvent
    /** Externe und eigene Fassung vergleichen. */
    data object CompareExternalWithMine : LayoutDesignerUiEvent
}

/** Einmalige Effekte. */
sealed interface LayoutDesignerUiEffect {
    /** Meldung anzeigen. */
    data class ShowSnackbar(val message: String) : LayoutDesignerUiEffect
    /** Zurücknavigieren. */
    data object NavigateBack : LayoutDesignerUiEffect
    /** In die Zwischenablage kopieren. */
    data class CopyToClipboard(val text: String) : LayoutDesignerUiEffect
    /** Layout-Datei öffnen. */
    data class OpenLayoutFile(val path: String) : LayoutDesignerUiEffect
}
