/**
 * Modul: :feature:layoutdesigner
 * @author Thomas Schmid
 */
package com.codeforge.feature.layoutdesigner

import com.codeforge.feature.layoutdesigner.model.CustomViewInfo
import com.codeforge.feature.layoutdesigner.model.CustomViewScanner
import com.codeforge.feature.layoutdesigner.res.IncludeResolver
import com.codeforge.feature.layoutdesigner.res.IncludedLayoutResult
import com.codeforge.feature.layoutdesigner.res.ResType
import com.codeforge.feature.layoutdesigner.res.ResourceRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeforge.core.navigation.FileSyncBridge
import com.codeforge.core.navigation.OpenFileRequestBridge
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import com.codeforge.feature.layoutdesigner.files.LayoutFileStore
import com.codeforge.feature.layoutdesigner.model.ConstraintAnchor
import com.codeforge.feature.layoutdesigner.model.ConstraintTarget
import com.codeforge.feature.layoutdesigner.model.freshId
import com.codeforge.feature.layoutdesigner.model.insertNode
import com.codeforge.feature.layoutdesigner.model.DragPayload
import com.codeforge.feature.layoutdesigner.model.DropVerdict
import com.codeforge.feature.layoutdesigner.lint.LayoutIssue
import com.codeforge.feature.layoutdesigner.lint.LayoutLinter
import com.codeforge.feature.layoutdesigner.lint.QuickFix
import com.codeforge.feature.layoutdesigner.model.LayoutClipboard
import com.codeforge.feature.layoutdesigner.model.LayoutDocument
import com.codeforge.feature.layoutdesigner.model.LayoutNode
import com.codeforge.feature.layoutdesigner.model.addWidget
import com.codeforge.feature.layoutdesigner.model.addWidgetAt
import com.codeforge.feature.layoutdesigner.model.changeTag
import com.codeforge.feature.layoutdesigner.model.duplicate
import com.codeforge.feature.layoutdesigner.model.duplicateNodes
import com.codeforge.feature.layoutdesigner.model.find
import com.codeforge.feature.layoutdesigner.model.indent
import com.codeforge.feature.layoutdesigner.model.insertTarget
import com.codeforge.feature.layoutdesigner.model.moveNode
import com.codeforge.feature.layoutdesigner.model.moveWithinParent
import com.codeforge.feature.layoutdesigner.model.outdent
import com.codeforge.feature.layoutdesigner.model.parentOf
import com.codeforge.feature.layoutdesigner.model.remove
import com.codeforge.feature.layoutdesigner.model.removeNodes
import com.codeforge.feature.layoutdesigner.model.renameId
import com.codeforge.feature.layoutdesigner.model.setAttribute
import com.codeforge.feature.layoutdesigner.model.unwrap
import com.codeforge.feature.layoutdesigner.model.wrapIn
import com.codeforge.feature.layoutdesigner.xml.LayoutParseException
import com.codeforge.feature.layoutdesigner.xml.LayoutXml
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class LayoutDesignerViewModel @Inject constructor(
    private val openFileBridge: OpenFileRequestBridge,
    private val fileSyncBridge: FileSyncBridge,
    private val fileStore: LayoutFileStore,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    internal var computeDispatcher: CoroutineDispatcher = Dispatchers.Default

    internal constructor(
        openFileBridge: OpenFileRequestBridge,
        fileSyncBridge: FileSyncBridge,
        fileStore: LayoutFileStore,
        ioDispatcher: CoroutineDispatcher,
    ) : this(openFileBridge, fileSyncBridge, fileStore, SavedStateHandle()) {
        this.ioDispatcher = ioDispatcher
        this.computeDispatcher = ioDispatcher
    }

    internal constructor(
        openFileBridge: OpenFileRequestBridge,
        fileSyncBridge: FileSyncBridge,
        fileStore: LayoutFileStore,
        savedStateHandle: SavedStateHandle,
        ioDispatcher: CoroutineDispatcher,
    ) : this(openFileBridge, fileSyncBridge, fileStore, savedStateHandle) {
        this.ioDispatcher = ioDispatcher
        this.computeDispatcher = ioDispatcher
    }

    private val _state = MutableStateFlow(LayoutDesignerUiState())
    val state: StateFlow<LayoutDesignerUiState> = _state.asStateFlow()

    private val _effects = Channel<LayoutDesignerUiEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private val undoStack = ArrayDeque<LayoutDocument>()
    private val redoStack = ArrayDeque<LayoutDocument>()

    /** Normalisiertes XML des zuletzt geladenen/gespeicherten Stands. */
    private var savedXml: String = ""

    /** Aufeinanderfolgende Eingaben in dasselbe Feld teilen sich einen Undo-Schritt. */
    private var lastEditKey: String? = null

    init {
        observeExternalChanges()
        restoreFromSavedState()
    }

    private fun observeExternalChanges() {
        viewModelScope.launch {
            fileSyncBridge.externalChanges.collect { changedPaths ->
                val currentPath = _state.value.filePath
                if (currentPath.isNotEmpty() && currentPath in changedPaths) {
                    val diskContent = withContext(ioDispatcher) {
                        runCatching { fileStore.readText(currentPath) }.getOrNull()
                    }
                    if (diskContent != null && diskContent != savedXml) {
                        if (!_state.value.dirty) {
                            load(currentPath, force = true)
                        } else {
                            _state.update {
                                it.copy(
                                    showExternalChangeDialog = true,
                                    externalFileContent = diskContent,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun restoreFromSavedState() {
        val path: String? = savedStateHandle[KEY_FILE_PATH]
        val dirtyXml: String? = savedStateHandle[KEY_DIRTY_XML]
        if (!path.isNullOrEmpty() && !dirtyXml.isNullOrEmpty()) {
            val restoredDoc = runCatching { LayoutXml.parse(dirtyXml) }.getOrNull()
            if (restoredDoc != null) {
                val tabName: String? = savedStateHandle[KEY_TAB]
                val deviceName: String? = savedStateHandle[KEY_DEVICE]
                val selectedUid: Int? = savedStateHandle[KEY_SELECTED_UID]
                val tab = tabName?.let { runCatching { DesignerTab.valueOf(it) }.getOrNull() } ?: DesignerTab.PREVIEW
                val device = deviceName?.let { runCatching { DevicePreset.valueOf(it) }.getOrNull() } ?: DevicePreset.PHONE

                _state.update {
                    it.copy(
                        filePath = path,
                        isLoading = false,
                        document = restoredDoc,
                        xmlText = dirtyXml,
                        dirty = true,
                        tab = tab,
                        device = device,
                        selectedUid = selectedUid ?: restoredDoc.root.uid,
                        selectedUids = setOf(selectedUid ?: restoredDoc.root.uid),
                    )
                }
                viewModelScope.launch {
                    _effects.send(LayoutDesignerUiEffect.ShowSnackbar(Res.string(R.string.layout_restored_unsaved)))
                }
            }
        }
    }

    fun onEvent(event: LayoutDesignerUiEvent) {
        when (event) {
            is LayoutDesignerUiEvent.Load -> load(event.filePath)
            is LayoutDesignerUiEvent.Select -> _state.update {
                it.copy(
                    selectedUid = event.uid,
                    selectedUids = if (event.uid != null) setOf(event.uid) else emptySet(),
                )
            }
            is LayoutDesignerUiEvent.SelectToggle -> _state.update { s ->
                val newSelection = if (event.uid in s.selectedUids) {
                    s.selectedUids - event.uid
                } else {
                    s.selectedUids + event.uid
                }
                val newPrimary = if (event.uid in newSelection) event.uid else newSelection.lastOrNull()
                s.copy(selectedUid = newPrimary, selectedUids = newSelection)
            }
            is LayoutDesignerUiEvent.TabChanged -> _state.update { it.copy(tab = event.tab) }
            is LayoutDesignerUiEvent.DeviceChanged -> _state.update { it.copy(device = event.device) }
            is LayoutDesignerUiEvent.PaletteVisible -> _state.update { it.copy(showPalette = event.visible) }
            is LayoutDesignerUiEvent.AddWidget -> {
                val s = _state.value
                val result = s.document.addWidget(event.entry, s.selectedUid)
                commit(result.document, selectUid = result.newUid)
                _state.update { it.copy(showPalette = false) }
            }
            is LayoutDesignerUiEvent.DropWidget -> {
                val s = _state.value
                val target = event.target
                if (target.verdict is DropVerdict.Rejected) {
                    // Ignored on drop
                } else {
                    when (val p = event.payload) {
                        is DragPayload.NewWidget -> {
                            val addResult = s.document.addWidgetAt(p.entry, target.parentUid, target.index)
                            var newDoc = addResult.document
                            val newUid = addResult.newUid
                            if (newUid != null && target.suggestedAttributes.isNotEmpty()) {
                                for ((k, v) in target.suggestedAttributes) {
                                    newDoc = newDoc.setAttribute(newUid, k, v)
                                }
                            }
                            commit(newDoc, selectUid = newUid)
                        }
                        is DragPayload.ExistingNodes -> {
                            var newDoc = s.document
                            for (uid in p.uids) {
                                newDoc = newDoc.moveNode(uid, target.parentUid, target.index)
                            }
                            commit(newDoc, selectUid = p.uids.firstOrNull())
                        }
                    }
                }
            }
            LayoutDesignerUiEvent.DeleteSelected -> {
                val s = _state.value
                if (s.selectedUids.size > 1) {
                    val firstParent = s.selectedUids.firstNotNullOfOrNull { s.document.parentOf(it) }?.uid
                    commit(s.document.removeNodes(s.selectedUids), selectUid = firstParent)
                } else {
                    selected()?.let { uid ->
                        val doc = s.document
                        val parentUid = doc.parentOf(uid)?.uid
                        commit(doc.remove(uid), selectUid = parentUid)
                    }
                }
            }
            LayoutDesignerUiEvent.DuplicateSelected -> {
                val s = _state.value
                if (s.selectedUids.size > 1) {
                    val r = s.document.duplicateNodes(s.selectedUids)
                    commit(r.document, selectUid = r.newUid)
                } else {
                    selected()?.let { uid ->
                        val r = s.document.duplicate(uid)
                        commit(r.document, selectUid = r.newUid ?: uid)
                    }
                }
            }
            is LayoutDesignerUiEvent.MoveSelected -> selected()?.let { commit(_state.value.document.moveWithinParent(it, event.delta)) }
            LayoutDesignerUiEvent.IndentSelected -> selected()?.let { commit(_state.value.document.indent(it)) }
            LayoutDesignerUiEvent.OutdentSelected -> selected()?.let { commit(_state.value.document.outdent(it)) }
            is LayoutDesignerUiEvent.WrapIn -> {
                val s = _state.value
                val targets = if (s.selectedUids.isNotEmpty()) s.selectedUids else s.selectedUid?.let { setOf(it) } ?: emptySet()
                val res = s.document.wrapIn(targets, event.containerTag)
                if (res.newUid != null) {
                    commit(res.document, selectUid = res.newUid)
                }
            }
            LayoutDesignerUiEvent.UnwrapSelected -> selected()?.let { commit(_state.value.document.unwrap(it)) }
            is LayoutDesignerUiEvent.ChangeTag -> commit(_state.value.document.changeTag(event.uid, event.newTag, event.removeIncompatible))
            is LayoutDesignerUiEvent.RenameId -> commit(_state.value.document.renameId(event.uid, event.newId))
            is LayoutDesignerUiEvent.SetAttribute ->
                commit(_state.value.document.setAttribute(event.uid, event.name, event.value), editKey = "${event.uid}:${event.name}")
            is LayoutDesignerUiEvent.XmlChanged -> _state.update { it.copy(xmlDraft = event.text, xmlError = null) }
            LayoutDesignerUiEvent.ApplyXml -> applyDraft()
            LayoutDesignerUiEvent.RevertXml -> _state.update { it.copy(xmlDraft = null, xmlError = null) }
            LayoutDesignerUiEvent.Undo -> undo()
            LayoutDesignerUiEvent.Redo -> redo()
            LayoutDesignerUiEvent.Save -> viewModelScope.launch { save() }
            LayoutDesignerUiEvent.OpenInEditor -> viewModelScope.launch {
                if (save()) {
                    openFileBridge.requestOpen(_state.value.filePath)
                    _effects.send(LayoutDesignerUiEffect.NavigateBack)
                }
            }
            LayoutDesignerUiEvent.BackRequested -> {
                val s = _state.value
                if (s.dirty || s.xmlDraft != null) _state.update { it.copy(showDiscardConfirm = true) }
                else viewModelScope.launch { _effects.send(LayoutDesignerUiEffect.NavigateBack) }
            }
            LayoutDesignerUiEvent.DismissDiscard -> _state.update { it.copy(showDiscardConfirm = false) }
            LayoutDesignerUiEvent.ConfirmDiscard -> {
                _state.update { it.copy(showDiscardConfirm = false) }
                viewModelScope.launch { _effects.send(LayoutDesignerUiEffect.NavigateBack) }
            }
            is LayoutDesignerUiEvent.SetReformatOnSave -> {
                val s = _state.value
                val updatedDoc = s.document.copy(reformatOnSave = event.enabled)
                val xml = LayoutXml.write(updatedDoc, reformat = event.enabled)
                _state.update { it.copy(reformatOnSave = event.enabled, document = updatedDoc, xmlText = xml) }
            }
            is LayoutDesignerUiEvent.SetZoom -> _state.update { it.copy(zoom = event.zoom.coerceIn(0.25f, 4.0f)) }
            is LayoutDesignerUiEvent.SetPan -> _state.update { it.copy(panX = event.panX, panY = event.panY) }
            LayoutDesignerUiEvent.ResetZoomPan -> _state.update { it.copy(zoom = 1.0f, panX = 0f, panY = 0f) }
            is LayoutDesignerUiEvent.SetLandscape -> _state.update { it.copy(isLandscape = event.isLandscape) }
            is LayoutDesignerUiEvent.SetDarkTheme -> _state.update { it.copy(isDarkTheme = event.isDark) }
            is LayoutDesignerUiEvent.SetFontScale -> _state.update { it.copy(fontScale = event.scale) }
            is LayoutDesignerUiEvent.SetRtl -> _state.update { it.copy(isRtl = event.isRtl) }
            is LayoutDesignerUiEvent.SetCustomDimensions -> _state.update { it.copy(customWidthDp = event.widthDp, customHeightDp = event.heightDp) }
            is LayoutDesignerUiEvent.ToggleOverlay -> _state.update { s ->
                when (event.overlayType) {
                    OverlayType.BOUNDS -> s.copy(showBoundsOverlay = !s.showBoundsOverlay)
                    OverlayType.MARGINS_PADDINGS -> s.copy(showMarginsPaddingsOverlay = !s.showMarginsPaddingsOverlay)
                    OverlayType.BLUEPRINT -> s.copy(blueprintMode = !s.blueprintMode)
                    OverlayType.GRID_SNAP -> s.copy(snapTo8DpGrid = !s.snapTo8DpGrid)
                }
            }
            LayoutDesignerUiEvent.CopySelected -> {
                val s = _state.value
                val targets = if (s.selectedUids.isNotEmpty()) s.selectedUids else s.selectedUid?.let { setOf(it) } ?: emptySet()
                val snippet = LayoutClipboard.createSnippet(s.document, targets)
                if (snippet.isNotBlank()) {
                    _state.update { it.copy(clipboardXml = snippet) }
                    viewModelScope.launch { _effects.send(LayoutDesignerUiEffect.CopyToClipboard(snippet)) }
                }
            }
            LayoutDesignerUiEvent.CutSelected -> {
                val s = _state.value
                val targets = if (s.selectedUids.isNotEmpty()) s.selectedUids else s.selectedUid?.let { setOf(it) } ?: emptySet()
                val snippet = LayoutClipboard.createSnippet(s.document, targets)
                if (snippet.isNotBlank()) {
                    _state.update { it.copy(clipboardXml = snippet) }
                    viewModelScope.launch { _effects.send(LayoutDesignerUiEffect.CopyToClipboard(snippet)) }
                    val firstParent = targets.firstNotNullOfOrNull { s.document.parentOf(it) }?.uid
                    commit(s.document.removeNodes(targets), selectUid = firstParent)
                }
            }
            is LayoutDesignerUiEvent.PasteSnippet -> {
                val s = _state.value
                val targetContainer = s.document.insertTarget(s.selectedUid)
                val res = LayoutClipboard.pasteSnippet(s.document, event.xmlSnippet, targetContainer.uid)
                if (res.newUid != null) {
                    commit(res.document, selectUid = res.newUid)
                }
            }
            is LayoutDesignerUiEvent.ResizeSelected -> selected()?.let { uid ->
                var doc = _state.value.document
                val grid = _state.value.snapTo8DpGrid
                if (event.widthDp != null) {
                    val w = if (grid) (Math.round(event.widthDp / 8.0) * 8).toInt() else event.widthDp
                    doc = doc.setAttribute(uid, "android:layout_width", "${w}dp")
                }
                if (event.heightDp != null) {
                    val h = if (grid) (Math.round(event.heightDp / 8.0) * 8).toInt() else event.heightDp
                    doc = doc.setAttribute(uid, "android:layout_height", "${h}dp")
                }
                commit(doc, selectUid = uid)
            }
            is LayoutDesignerUiEvent.ToggleSizeMode -> selected()?.let { uid ->
                val node = _state.value.document.find(uid) ?: return@let
                val attr = if (event.isWidth) "android:layout_width" else "android:layout_height"
                val current = node.attr(attr) ?: "wrap_content"
                val parentTag = _state.value.document.parentOf(uid)?.tag
                val matchVal = if (parentTag == "ConstraintLayout" || parentTag?.endsWith(".ConstraintLayout") == true) "0dp" else "match_parent"
                val next = if (current == matchVal) "wrap_content" else matchVal
                commit(_state.value.document.setAttribute(uid, attr, next), selectUid = uid)
            }
            is LayoutDesignerUiEvent.SetConstraint -> {
                var doc = _state.value.document
                val targetStr = when (val t = event.target) {
                    ConstraintTarget.Parent -> "parent"
                    is ConstraintTarget.Sibling -> "@id/${t.id}"
                }
                val attrName = when (event.anchor) {
                    ConstraintAnchor.START -> if (event.targetAnchor == ConstraintAnchor.END) "app:layout_constraintStart_toEndOf" else "app:layout_constraintStart_toStartOf"
                    ConstraintAnchor.END -> if (event.targetAnchor == ConstraintAnchor.START) "app:layout_constraintEnd_toStartOf" else "app:layout_constraintEnd_toEndOf"
                    ConstraintAnchor.TOP -> if (event.targetAnchor == ConstraintAnchor.BOTTOM) "app:layout_constraintTop_toBottomOf" else "app:layout_constraintTop_toTopOf"
                    ConstraintAnchor.BOTTOM -> if (event.targetAnchor == ConstraintAnchor.TOP) "app:layout_constraintBottom_toTopOf" else "app:layout_constraintBottom_toBottomOf"
                    ConstraintAnchor.BASELINE -> "app:layout_constraintBaseline_toBaselineOf"
                    else -> "app:layout_constraintStart_toStartOf"
                }
                when (event.anchor) {
                    ConstraintAnchor.START -> {
                        doc = doc.setAttribute(event.uid, "app:layout_constraintStart_toStartOf", null)
                        doc = doc.setAttribute(event.uid, "app:layout_constraintStart_toEndOf", null)
                    }
                    ConstraintAnchor.END -> {
                        doc = doc.setAttribute(event.uid, "app:layout_constraintEnd_toStartOf", null)
                        doc = doc.setAttribute(event.uid, "app:layout_constraintEnd_toEndOf", null)
                    }
                    ConstraintAnchor.TOP -> {
                        doc = doc.setAttribute(event.uid, "app:layout_constraintTop_toTopOf", null)
                        doc = doc.setAttribute(event.uid, "app:layout_constraintTop_toBottomOf", null)
                    }
                    ConstraintAnchor.BOTTOM -> {
                        doc = doc.setAttribute(event.uid, "app:layout_constraintBottom_toTopOf", null)
                        doc = doc.setAttribute(event.uid, "app:layout_constraintBottom_toBottomOf", null)
                    }
                    ConstraintAnchor.BASELINE -> {
                        doc = doc.setAttribute(event.uid, "app:layout_constraintBaseline_toBaselineOf", null)
                    }
                    else -> {}
                }
                doc = doc.setAttribute(event.uid, attrName, targetStr)
                if (event.marginDp > 0) {
                    val marginAttr = when (event.anchor) {
                        ConstraintAnchor.START -> "android:layout_marginStart"
                        ConstraintAnchor.END -> "android:layout_marginEnd"
                        ConstraintAnchor.TOP -> "android:layout_marginTop"
                        ConstraintAnchor.BOTTOM -> "android:layout_marginBottom"
                        else -> null
                    }
                    if (marginAttr != null) {
                        doc = doc.setAttribute(event.uid, marginAttr, "${event.marginDp}dp")
                    }
                }
                commit(doc, selectUid = event.uid)
            }
            is LayoutDesignerUiEvent.RemoveConstraint -> {
                var doc = _state.value.document
                when (event.anchor) {
                    ConstraintAnchor.START -> {
                        doc = doc.setAttribute(event.uid, "app:layout_constraintStart_toStartOf", null)
                        doc = doc.setAttribute(event.uid, "app:layout_constraintStart_toEndOf", null)
                        doc = doc.setAttribute(event.uid, "app:layout_constraintLeft_toLeftOf", null)
                        doc = doc.setAttribute(event.uid, "app:layout_constraintLeft_toRightOf", null)
                    }
                    ConstraintAnchor.END -> {
                        doc = doc.setAttribute(event.uid, "app:layout_constraintEnd_toStartOf", null)
                        doc = doc.setAttribute(event.uid, "app:layout_constraintEnd_toEndOf", null)
                        doc = doc.setAttribute(event.uid, "app:layout_constraintRight_toLeftOf", null)
                        doc = doc.setAttribute(event.uid, "app:layout_constraintRight_toRightOf", null)
                    }
                    ConstraintAnchor.TOP -> {
                        doc = doc.setAttribute(event.uid, "app:layout_constraintTop_toTopOf", null)
                        doc = doc.setAttribute(event.uid, "app:layout_constraintTop_toBottomOf", null)
                    }
                    ConstraintAnchor.BOTTOM -> {
                        doc = doc.setAttribute(event.uid, "app:layout_constraintBottom_toTopOf", null)
                        doc = doc.setAttribute(event.uid, "app:layout_constraintBottom_toBottomOf", null)
                    }
                    ConstraintAnchor.BASELINE -> {
                        doc = doc.setAttribute(event.uid, "app:layout_constraintBaseline_toBaselineOf", null)
                    }
                    else -> {}
                }
                commit(doc, selectUid = event.uid)
            }
            is LayoutDesignerUiEvent.CenterHorizontally -> {
                var doc = _state.value.document
                doc = doc.setAttribute(event.uid, "app:layout_constraintStart_toStartOf", "parent")
                doc = doc.setAttribute(event.uid, "app:layout_constraintStart_toEndOf", null)
                doc = doc.setAttribute(event.uid, "app:layout_constraintEnd_toEndOf", "parent")
                doc = doc.setAttribute(event.uid, "app:layout_constraintEnd_toStartOf", null)
                doc = doc.setAttribute(event.uid, "android:layout_marginStart", "0dp")
                doc = doc.setAttribute(event.uid, "android:layout_marginEnd", "0dp")
                doc = doc.setAttribute(event.uid, "app:layout_constraintHorizontal_bias", "0.5")
                commit(doc, selectUid = event.uid)
            }
            is LayoutDesignerUiEvent.CenterVertically -> {
                var doc = _state.value.document
                doc = doc.setAttribute(event.uid, "app:layout_constraintTop_toTopOf", "parent")
                doc = doc.setAttribute(event.uid, "app:layout_constraintTop_toBottomOf", null)
                doc = doc.setAttribute(event.uid, "app:layout_constraintBottom_toBottomOf", "parent")
                doc = doc.setAttribute(event.uid, "app:layout_constraintBottom_toTopOf", null)
                doc = doc.setAttribute(event.uid, "android:layout_marginTop", "0dp")
                doc = doc.setAttribute(event.uid, "android:layout_marginBottom", "0dp")
                doc = doc.setAttribute(event.uid, "app:layout_constraintVertical_bias", "0.5")
                commit(doc, selectUid = event.uid)
            }
            is LayoutDesignerUiEvent.ClearConstraints -> {
                var doc = _state.value.document
                val node = doc.find(event.uid)
                if (node != null) {
                    val toRemove = node.attributes.keys.filter { it.startsWith("app:layout_constraint") || it.startsWith("layout_constraint") }
                    for (k in toRemove) {
                        doc = doc.setAttribute(event.uid, k, null)
                    }
                }
                commit(doc, selectUid = event.uid)
            }
            is LayoutDesignerUiEvent.AddGuideline -> {
                val parent = _state.value.document.find(event.parentUid)
                if (parent != null) {
                    val orient = if (event.isVertical) "vertical" else "horizontal"
                    val attrs = linkedMapOf(
                        "android:id" to _state.value.document.freshId("guideline"),
                        "android:layout_width" to "wrap_content",
                        "android:layout_height" to "wrap_content",
                        "android:orientation" to orient,
                        "app:layout_constraintGuide_percent" to "0.5",
                    )
                    val glNode = LayoutNode(
                        uid = _state.value.document.nextUid,
                        tag = "androidx.constraintlayout.widget.Guideline",
                        attributes = attrs,
                        isNew = true,
                    )
                    val newDoc = _state.value.document.insertNode(glNode, event.parentUid, parent.children.size)
                    commit(newDoc, selectUid = glNode.uid)
                }
            }
            is LayoutDesignerUiEvent.SetHorizontalBias -> {
                val doc = _state.value.document.setAttribute(event.uid, "app:layout_constraintHorizontal_bias", String.format(java.util.Locale.US, "%.2f", event.bias))
                commit(doc, selectUid = event.uid)
            }
            is LayoutDesignerUiEvent.SetVerticalBias -> {
                val doc = _state.value.document.setAttribute(event.uid, "app:layout_constraintVertical_bias", String.format(java.util.Locale.US, "%.2f", event.bias))
                commit(doc, selectUid = event.uid)
            }
            is LayoutDesignerUiEvent.SetShowToolsAttributes -> {
                _state.update { it.copy(showToolsAttributes = event.enabled) }
            }
            is LayoutDesignerUiEvent.ExtractToResource -> {
                val layoutFile = File(_state.value.filePath)
                val resDirs = ResourceRepository.findResDirsForLayout(layoutFile)
                val targetResDir = resDirs.firstOrNull()
                    ?: layoutFile.parentFile?.parentFile?.let { File(it, "res") }
                    ?: File(layoutFile.parentFile ?: File("."), "res")
                val repo = _state.value.resourceRepo ?: ResourceRepository(resDirs)
                repo.extractToResource(targetResDir, event.resType, event.name, event.value)
                _state.update { it.copy(resourceRepo = repo) }

                val refStr = when (event.resType) {
                    ResType.STRING -> "@string/${event.name}"
                    ResType.COLOR -> "@color/${event.name}"
                    ResType.DIMEN -> "@dimen/${event.name}"
                    ResType.INTEGER -> "@integer/${event.name}"
                    ResType.BOOL -> "@bool/${event.name}"
                    else -> "@${event.resType.name.lowercase()}/${event.name}"
                }
                commit(_state.value.document.setAttribute(event.nodeUid, event.attrName, refStr), selectUid = event.nodeUid)
                viewModelScope.launch(ioDispatcher) {
                    fileSyncBridge.notifyExternalChange(listOf(targetResDir.absolutePath))
                }
            }
            is LayoutDesignerUiEvent.ToggleInflateViewStub -> {
                val current = _state.value.inflatedViewStubs
                val next = if (event.uid in current) current - event.uid else current + event.uid
                _state.update { it.copy(inflatedViewStubs = next) }
            }
            is LayoutDesignerUiEvent.OpenIncludedLayout -> {
                val layoutFile = File(_state.value.filePath)
                val resDirs = ResourceRepository.findResDirsForLayout(layoutFile)
                val target = IncludeResolver(resDirs).resolve(event.layoutRef)
                if (target is IncludedLayoutResult.Success) {
                    viewModelScope.launch {
                        _effects.send(LayoutDesignerUiEffect.OpenLayoutFile(target.file.absolutePath))
                    }
                }
            }
            is LayoutDesignerUiEvent.ApplyQuickFix -> {
                val fixedDoc = LayoutLinter.applyQuickFix(_state.value.document, event.quickFix)
                commit(fixedDoc, selectUid = _state.value.selectedUid)
            }
            is LayoutDesignerUiEvent.JumpToIssue -> {
                _state.update {
                    it.copy(
                        selectedUid = event.issue.uid,
                        selectedUids = setOf(event.issue.uid),
                        tab = DesignerTab.PREVIEW
                    )
                }
            }
            is LayoutDesignerUiEvent.SwitchVariant -> {
                val layoutFile = File(_state.value.filePath)
                val resDir = layoutFile.parentFile?.parentFile
                if (resDir != null) {
                    val targetDir = if (event.variant == "default") File(resDir, "layout") else File(resDir, "layout-${event.variant}")
                    val targetFile = File(targetDir, layoutFile.name)
                    if (targetFile.exists()) {
                        load(targetFile.absolutePath)
                    }
                }
            }
            is LayoutDesignerUiEvent.CreateVariant -> {
                val qualifier = event.qualifier.trim().removePrefix("layout-").removePrefix("-")
                val layoutFile = File(_state.value.filePath)
                val resDir = layoutFile.parentFile?.parentFile
                if (resDir != null && qualifier.isNotEmpty()) {
                    val targetDir = File(resDir, "layout-$qualifier")
                    val targetFile = File(targetDir, layoutFile.name)
                    runCatching {
                        targetDir.mkdirs()
                        targetFile.writeText(LayoutXml.write(_state.value.document, reformat = _state.value.reformatOnSave))
                        viewModelScope.launch(ioDispatcher) {
                            fileSyncBridge.notifyExternalChange(listOf(targetFile.absolutePath))
                        }
                        load(targetFile.absolutePath)
                    }
                }
            }
            LayoutDesignerUiEvent.ReloadFromExternal -> {
                _state.update { it.copy(showExternalChangeDialog = false) }
                load(_state.value.filePath, force = true)
            }
            LayoutDesignerUiEvent.KeepMyVersion -> {
                _state.update { it.copy(showExternalChangeDialog = false) }
            }
            LayoutDesignerUiEvent.CompareExternalWithMine -> {
                val externalContent = _state.value.externalFileContent
                _state.update {
                    it.copy(
                        showExternalChangeDialog = false,
                        tab = DesignerTab.XML,
                        xmlDraft = externalContent
                    )
                }
            }
        }
    }

    private fun selected(): Int? = _state.value.selectedUid?.takeIf { _state.value.document.find(it) != null }

    private fun findSrcDir(file: File): File? {
        var curr: File? = file.parentFile
        while (curr != null) {
            if (curr.name == "src") return curr
            val directSrc = File(curr, "src")
            if (directSrc.exists() && directSrc.isDirectory) return directSrc
            curr = curr.parentFile
        }
        return null
    }

    private fun load(path: String, force: Boolean = false) {
        if (!force && _state.value.filePath == path && !_state.value.isLoading) return
        _state.update { LayoutDesignerUiState(filePath = path, isLoading = true) }
        viewModelScope.launch {
            fileSyncBridge.requestFlush(setOf(path))
            val outcome = runCatching { fileStore.readText(path) }.mapCatching { LayoutXml.parse(it) }
            outcome.onSuccess { doc ->
                val xml = LayoutXml.write(doc, reformat = _state.value.reformatOnSave)
                savedXml = xml
                undoStack.clear(); redoStack.clear(); lastEditKey = null

                val layoutFile = File(path)
                val (repo, includeRes, customViews, variants, currVariant) = withContext(ioDispatcher) {
                    val dirs = ResourceRepository.findResDirsForLayout(layoutFile)
                    val r = if (dirs.isNotEmpty()) ResourceRepository(dirs) else null
                    val inc = if (dirs.isNotEmpty()) IncludeResolver(dirs) else null
                    val srcDir = findSrcDir(layoutFile)
                    val cvs = srcDir?.let { CustomViewScanner().scanDirectory(it) } ?: emptyList()

                    val resDir = layoutFile.parentFile?.parentFile
                    val fileName = layoutFile.name
                    val vList = if (resDir != null && resDir.exists()) {
                        val layoutDirs = resDir.listFiles { f -> f.isDirectory && f.name.startsWith("layout") } ?: emptyArray()
                        layoutDirs.filter { File(it, fileName).exists() }
                            .map { it.name.removePrefix("layout").removePrefix("-").ifEmpty { "default" } }
                            .sorted()
                    } else listOf("default")
                    val curV = layoutFile.parentFile?.name?.removePrefix("layout")?.removePrefix("-")?.ifEmpty { "default" } ?: "default"

                    LoadedContext(r, inc, cvs, vList, curV)
                }

                val issues = LayoutLinter.lint(doc, repo)

                _state.update {
                    it.copy(
                        isLoading = false,
                        document = doc,
                        xmlText = xml,
                        selectedUid = doc.root.uid,
                        selectedUids = setOf(doc.root.uid),
                        dirty = false,
                        canUndo = false,
                        canRedo = false,
                        resourceRepo = repo,
                        includeResolver = includeRes,
                        customViews = customViews,
                        issues = issues,
                        availableVariants = variants,
                        currentVariant = currVariant,
                    )
                }
            }.onFailure { e ->
                val msg = Res.string(R.string.layout_load_failed, (e as? LayoutParseException)?.message ?: e.message.orEmpty())
                _state.update { it.copy(isLoading = false, loadError = msg) }
            }
        }
    }

    /** Übernimmt [doc] als neuen Stand und legt den alten auf den Undo-Stapel (außer bei Folge-Eingaben desselben Felds). */
    private fun commit(doc: LayoutDocument, selectUid: Int? = null, editKey: String? = null) {
        val s = _state.value
        if (doc === s.document) return
        val coalesce = editKey != null && editKey == lastEditKey
        if (!coalesce) {
            undoStack.addLast(s.document)
            if (undoStack.size > MAX_HISTORY) undoStack.removeFirst()
        }
        lastEditKey = editKey
        redoStack.clear()
        publish(doc, selectUid ?: s.selectedUid)
    }

    private fun publish(doc: LayoutDocument, selectUid: Int?) {
        val xml = LayoutXml.write(doc, reformat = _state.value.reformatOnSave)
        val resolvedUid = selectUid?.takeIf { uid -> doc.find(uid) != null } ?: doc.root.uid
        val isDirty = xml != savedXml
        _state.update {
            it.copy(
                document = doc,
                xmlText = xml,
                xmlDraft = null,
                xmlError = null,
                selectedUid = resolvedUid,
                selectedUids = setOf(resolvedUid),
                dirty = isDirty,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
            )
        }
        savedStateHandle[KEY_FILE_PATH] = _state.value.filePath
        savedStateHandle[KEY_TAB] = _state.value.tab.name
        savedStateHandle[KEY_DEVICE] = _state.value.device.name
        savedStateHandle[KEY_SELECTED_UID] = resolvedUid
        if (isDirty) {
            savedStateHandle[KEY_DIRTY_XML] = xml
        } else {
            savedStateHandle.remove<String>(KEY_DIRTY_XML)
        }
        scheduleLint(doc, _state.value.resourceRepo)
    }

    private data class LoadedContext(
        val repo: ResourceRepository?,
        val includeResolver: IncludeResolver?,
        val customViews: List<CustomViewInfo>,
        val variants: List<String>,
        val currentVariant: String
    )

    private var lintJob: Job? = null

    private fun scheduleLint(doc: LayoutDocument, repo: ResourceRepository?) {
        lintJob?.cancel()
        lintJob = viewModelScope.launch(computeDispatcher) {
            delay(300)
            val issues = LayoutLinter.lint(doc, repo)
            _state.update { it.copy(issues = issues) }
        }
    }

    private fun undo() {
        val previous = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(_state.value.document)
        lastEditKey = null
        publish(previous, _state.value.selectedUid)
    }

    private fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(_state.value.document)
        lastEditKey = null
        publish(next, _state.value.selectedUid)
    }

    /** @return true, wenn kein Entwurf offen ist oder er erfolgreich übernommen wurde. */
    private fun applyDraft(): Boolean {
        val draft = _state.value.xmlDraft ?: return true
        return try {
            val doc = LayoutXml.parse(draft)
            undoStack.addLast(_state.value.document)
            if (undoStack.size > MAX_HISTORY) undoStack.removeFirst()
            redoStack.clear()
            lastEditKey = null
            publish(doc, null)
            true
        } catch (e: LayoutParseException) {
            _state.update { it.copy(xmlError = Res.string(R.string.layout_xml_invalid, e.message.orEmpty())) }
            false
        }
    }

    private suspend fun save(): Boolean {
        if (!applyDraft()) {
            _state.update { it.copy(tab = DesignerTab.XML) }
            return false
        }
        val s = _state.value
        if (s.isSaving) return false
        _state.update { it.copy(isSaving = true) }
        val xml = LayoutXml.write(s.document, reformat = s.reformatOnSave)
        val result = runCatching {
            fileStore.writeAtomically(s.filePath, xml)
        }
        return result.fold(
            onSuccess = {
                savedXml = xml
                _state.update { it.copy(isSaving = false, dirty = false, xmlText = xml) }
                fileSyncBridge.notifyExternalChange(listOf(s.filePath))
                _effects.send(LayoutDesignerUiEffect.ShowSnackbar(Res.string(R.string.layout_saved)))
                true
            },
            onFailure = { e ->
                _state.update { it.copy(isSaving = false) }
                _effects.send(LayoutDesignerUiEffect.ShowSnackbar(Res.string(R.string.layout_save_failed, e.message.orEmpty())))
                false
            },
        )
    }

    companion object {
        const val MAX_HISTORY = 100
        const val KEY_FILE_PATH = "ld_file_path"
        const val KEY_TAB = "ld_tab"
        const val KEY_DEVICE = "ld_device"
        const val KEY_SELECTED_UID = "ld_selected_uid"
        const val KEY_DIRTY_XML = "ld_dirty_xml"
    }
}
