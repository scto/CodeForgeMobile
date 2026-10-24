/**
 * Modul: :feature:layoutdesigner
 * @author Thomas Schmid
 */
package com.codeforge.feature.layoutdesigner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeforge.core.navigation.FileSyncBridge
import com.codeforge.core.navigation.OpenFileRequestBridge
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import com.codeforge.feature.layoutdesigner.files.LayoutFileStore
import com.codeforge.feature.layoutdesigner.model.LayoutDocument
import com.codeforge.feature.layoutdesigner.model.addWidget
import com.codeforge.feature.layoutdesigner.model.changeTag
import com.codeforge.feature.layoutdesigner.model.duplicate
import com.codeforge.feature.layoutdesigner.model.duplicateNodes
import com.codeforge.feature.layoutdesigner.model.find
import com.codeforge.feature.layoutdesigner.model.indent
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
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
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
) : ViewModel() {

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
        }
    }

    private fun selected(): Int? = _state.value.selectedUid?.takeIf { _state.value.document.find(it) != null }

    private fun load(path: String) {
        if (_state.value.filePath == path && !_state.value.isLoading) return
        _state.update { LayoutDesignerUiState(filePath = path, isLoading = true) }
        viewModelScope.launch {
            fileSyncBridge.requestFlush(setOf(path))
            val outcome = runCatching { fileStore.readText(path) }.mapCatching { LayoutXml.parse(it) }
            outcome.onSuccess { doc ->
                val xml = LayoutXml.write(doc, reformat = _state.value.reformatOnSave)
                savedXml = xml
                undoStack.clear(); redoStack.clear(); lastEditKey = null
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
        _state.update {
            it.copy(
                document = doc,
                xmlText = xml,
                xmlDraft = null,
                xmlError = null,
                selectedUid = resolvedUid,
                selectedUids = setOf(resolvedUid),
                dirty = xml != savedXml,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
            )
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

    private companion object {
        const val MAX_HISTORY = 100
    }
}
