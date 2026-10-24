package com.codeforge.feature.layoutdesigner

import com.codeforge.core.navigation.FileSyncBridge
import com.codeforge.core.navigation.FlushRequest
import com.codeforge.core.navigation.JumpTarget
import com.codeforge.core.navigation.OpenFileRequestBridge
import com.codeforge.feature.layoutdesigner.dnd.DesignerRect
import com.codeforge.feature.layoutdesigner.dnd.DropIndicator
import com.codeforge.feature.layoutdesigner.dnd.DropTarget
import com.codeforge.feature.layoutdesigner.files.LayoutFileStore
import com.codeforge.feature.layoutdesigner.model.DragPayload
import com.codeforge.feature.layoutdesigner.model.DropVerdict
import com.codeforge.feature.layoutdesigner.model.PaletteEntry
import com.codeforge.feature.layoutdesigner.model.WidgetCatalog
import com.codeforge.feature.layoutdesigner.model.WidgetCategory
import com.codeforge.feature.layoutdesigner.model.find
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LayoutDesignerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var openFileBridge: FakeOpenFileRequestBridge
    private lateinit var fileSyncBridge: FakeFileSyncBridge
    private lateinit var fileStore: FakeLayoutFileStore
    private lateinit var viewModel: LayoutDesignerViewModel

    private val sampleValidXml = """
        <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
            android:id="@+id/root"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:orientation="vertical">
            <TextView
                android:id="@+id/title"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="Hello" />
        </LinearLayout>
    """.trimIndent()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        openFileBridge = FakeOpenFileRequestBridge()
        fileSyncBridge = FakeFileSyncBridge()
        fileStore = FakeLayoutFileStore()
        viewModel = LayoutDesignerViewModel(openFileBridge, fileSyncBridge, fileStore)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun load_success_populatesDocumentAndXml() = runTest {
        val path = "/path/to/layout.xml"
        fileStore.files[path] = sampleValidXml

        viewModel.onEvent(LayoutDesignerUiEvent.Load(path))
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertNull(state.loadError)
        assertEquals(path, state.filePath)
        assertEquals("LinearLayout", state.document.root.tag)
        assertEquals(1, state.document.root.children.size)
        assertEquals("TextView", state.document.root.children.first().tag)
        assertFalse(state.dirty)
        assertFalse(state.canUndo)
        assertFalse(state.canRedo)
        assertTrue(fileSyncBridge.flushedPaths.contains(setOf(path)))
    }

    @Test
    fun load_missingFile_setsLoadError() = runTest {
        val path = "/path/to/missing.xml"

        viewModel.onEvent(LayoutDesignerUiEvent.Load(path))
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertNotNull(state.loadError)
    }

    @Test
    fun load_invalidXml_setsLoadError() = runTest {
        val path = "/path/to/invalid.xml"
        fileStore.files[path] = "<LinearLayout><unclosed></LinearLayout>"

        viewModel.onEvent(LayoutDesignerUiEvent.Load(path))
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertNotNull(state.loadError)
    }

    @Test
    fun undoCoalescing_sameField_mergesIntoSingleStep() = runTest {
        val path = "/path/to/layout.xml"
        fileStore.files[path] = sampleValidXml
        viewModel.onEvent(LayoutDesignerUiEvent.Load(path))
        advanceUntilIdle()

        val targetUid = viewModel.state.value.document.root.children.first().uid

        // Multiple keystrokes into the same field: text
        viewModel.onEvent(LayoutDesignerUiEvent.SetAttribute(targetUid, "android:text", "H"))
        viewModel.onEvent(LayoutDesignerUiEvent.SetAttribute(targetUid, "android:text", "He"))
        viewModel.onEvent(LayoutDesignerUiEvent.SetAttribute(targetUid, "android:text", "Hello World"))

        assertTrue(viewModel.state.value.canUndo)
        assertTrue(viewModel.state.value.dirty)

        // Single undo should revert directly to the initial state before typing started
        viewModel.onEvent(LayoutDesignerUiEvent.Undo)
        assertEquals("Hello", viewModel.state.value.document.root.children.first().attributes["android:text"])
        assertFalse("Should be back at saved state", viewModel.state.value.canUndo)
        assertFalse("Dirty flag should return to false", viewModel.state.value.dirty)
    }

    @Test
    fun undoCoalescing_differentField_createsSeparateSteps() = runTest {
        val path = "/path/to/layout.xml"
        fileStore.files[path] = sampleValidXml
        viewModel.onEvent(LayoutDesignerUiEvent.Load(path))
        advanceUntilIdle()

        val targetUid = viewModel.state.value.document.root.children.first().uid

        viewModel.onEvent(LayoutDesignerUiEvent.SetAttribute(targetUid, "android:text", "Updated"))
        viewModel.onEvent(LayoutDesignerUiEvent.SetAttribute(targetUid, "android:textSize", "18sp"))

        // First undo reverts textSize
        viewModel.onEvent(LayoutDesignerUiEvent.Undo)
        assertEquals("Updated", viewModel.state.value.document.root.children.first().attributes["android:text"])
        assertNull(viewModel.state.value.document.root.children.first().attributes["android:textSize"])
        assertTrue(viewModel.state.value.canUndo)

        // Second undo reverts text
        viewModel.onEvent(LayoutDesignerUiEvent.Undo)
        assertEquals("Hello", viewModel.state.value.document.root.children.first().attributes["android:text"])
        assertFalse(viewModel.state.value.canUndo)
    }

    @Test
    fun undoLimit_capsAt100Steps() = runTest {
        val path = "/path/to/layout.xml"
        fileStore.files[path] = sampleValidXml
        viewModel.onEvent(LayoutDesignerUiEvent.Load(path))
        advanceUntilIdle()

        val targetUid = viewModel.state.value.document.root.children.first().uid

        // Perform 105 distinct changes
        for (i in 1..105) {
            viewModel.onEvent(LayoutDesignerUiEvent.SetAttribute(targetUid, "android:tag_$i", "v$i"))
        }

        // Count how many undos we can perform
        var undoCount = 0
        while (viewModel.state.value.canUndo) {
            viewModel.onEvent(LayoutDesignerUiEvent.Undo)
            undoCount++
        }

        assertEquals(100, undoCount)
    }

    @Test
    fun dirty_returnsToFalse_whenUndoneToSavedXml() = runTest {
        val path = "/path/to/layout.xml"
        fileStore.files[path] = sampleValidXml
        viewModel.onEvent(LayoutDesignerUiEvent.Load(path))
        advanceUntilIdle()

        val targetUid = viewModel.state.value.document.root.children.first().uid

        viewModel.onEvent(LayoutDesignerUiEvent.SetAttribute(targetUid, "android:text", "Changed"))
        assertTrue(viewModel.state.value.dirty)

        viewModel.onEvent(LayoutDesignerUiEvent.Undo)
        assertFalse("Dirty must be false after undoing to original XML", viewModel.state.value.dirty)

        viewModel.onEvent(LayoutDesignerUiEvent.Redo)
        assertTrue("Dirty must be true again after redo", viewModel.state.value.dirty)
    }

    @Test
    fun xmlDraft_apply_updatesDocumentAndEnablesUndo() = runTest {
        val path = "/path/to/layout.xml"
        fileStore.files[path] = sampleValidXml
        viewModel.onEvent(LayoutDesignerUiEvent.Load(path))
        advanceUntilIdle()

        val newDraft = """
            <FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent">
                <Button android:id="@+id/btn" android:layout_width="wrap_content" android:layout_height="wrap_content" />
            </FrameLayout>
        """.trimIndent()

        viewModel.onEvent(LayoutDesignerUiEvent.XmlChanged(newDraft))
        assertEquals(newDraft, viewModel.state.value.xmlDraft)

        viewModel.onEvent(LayoutDesignerUiEvent.ApplyXml)
        val state = viewModel.state.value
        assertNull(state.xmlDraft)
        assertNull(state.xmlError)
        assertEquals("FrameLayout", state.document.root.tag)
        assertTrue(state.dirty)
        assertTrue(state.canUndo)

        // Undo reverts the applied XML draft
        viewModel.onEvent(LayoutDesignerUiEvent.Undo)
        assertEquals("LinearLayout", viewModel.state.value.document.root.tag)
    }

    @Test
    fun xmlDraft_apply_invalidXml_setsError() = runTest {
        val path = "/path/to/layout.xml"
        fileStore.files[path] = sampleValidXml
        viewModel.onEvent(LayoutDesignerUiEvent.Load(path))
        advanceUntilIdle()

        viewModel.onEvent(LayoutDesignerUiEvent.XmlChanged("<broken-xml>"))
        viewModel.onEvent(LayoutDesignerUiEvent.ApplyXml)

        assertNotNull(viewModel.state.value.xmlError)
        // Document unchanged
        assertEquals("LinearLayout", viewModel.state.value.document.root.tag)
    }

    @Test
    fun xmlDraft_revert_discardsDraft() = runTest {
        val path = "/path/to/layout.xml"
        fileStore.files[path] = sampleValidXml
        viewModel.onEvent(LayoutDesignerUiEvent.Load(path))
        advanceUntilIdle()

        viewModel.onEvent(LayoutDesignerUiEvent.XmlChanged("<draft/>"))
        assertEquals("<draft/>", viewModel.state.value.xmlDraft)

        viewModel.onEvent(LayoutDesignerUiEvent.RevertXml)
        assertNull(viewModel.state.value.xmlDraft)
        assertNull(viewModel.state.value.xmlError)
    }

    @Test
    fun save_success_writesFileAndNotifiesBridge() = runTest {
        val path = "/path/to/layout.xml"
        fileStore.files[path] = sampleValidXml
        viewModel.onEvent(LayoutDesignerUiEvent.Load(path))
        advanceUntilIdle()

        val targetUid = viewModel.state.value.document.root.children.first().uid
        viewModel.onEvent(LayoutDesignerUiEvent.SetAttribute(targetUid, "android:text", "SavedText"))
        assertTrue(viewModel.state.value.dirty)

        var receivedEffect: LayoutDesignerUiEffect? = null
        val effectJob = launch {
            receivedEffect = viewModel.effects.first()
        }

        viewModel.onEvent(LayoutDesignerUiEvent.Save)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.dirty)
        assertFalse(viewModel.state.value.isSaving)
        assertTrue(fileStore.files[path]?.contains("SavedText") == true)
        assertTrue(fileSyncBridge.notifiedChanges.contains(listOf(path)))
        assertTrue(receivedEffect is LayoutDesignerUiEffect.ShowSnackbar)

        effectJob.cancel()
    }

    @Test
    fun save_failure_emitsErrorSnackbarAndRemainsDirty() = runTest {
        val path = "/path/to/layout.xml"
        fileStore.files[path] = sampleValidXml
        viewModel.onEvent(LayoutDesignerUiEvent.Load(path))
        advanceUntilIdle()

        fileStore.shouldFailWrite = true

        val targetUid = viewModel.state.value.document.root.children.first().uid
        viewModel.onEvent(LayoutDesignerUiEvent.SetAttribute(targetUid, "android:text", "FailedSave"))

        var receivedEffect: LayoutDesignerUiEffect? = null
        val effectJob = launch {
            receivedEffect = viewModel.effects.first()
        }

        viewModel.onEvent(LayoutDesignerUiEvent.Save)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.dirty)
        assertFalse(viewModel.state.value.isSaving)
        assertTrue(receivedEffect is LayoutDesignerUiEffect.ShowSnackbar)

        effectJob.cancel()
    }

    @Test
    fun backRequested_cleanState_navigatesBack() = runTest {
        val path = "/path/to/layout.xml"
        fileStore.files[path] = sampleValidXml
        viewModel.onEvent(LayoutDesignerUiEvent.Load(path))
        advanceUntilIdle()

        var receivedEffect: LayoutDesignerUiEffect? = null
        val effectJob = launch {
            receivedEffect = viewModel.effects.first()
        }

        viewModel.onEvent(LayoutDesignerUiEvent.BackRequested)
        advanceUntilIdle()

        assertEquals(LayoutDesignerUiEffect.NavigateBack, receivedEffect)
        assertFalse(viewModel.state.value.showDiscardConfirm)
        effectJob.cancel()
    }

    @Test
    fun backRequested_dirtyState_promptsConfirmation() = runTest {
        val path = "/path/to/layout.xml"
        fileStore.files[path] = sampleValidXml
        viewModel.onEvent(LayoutDesignerUiEvent.Load(path))
        advanceUntilIdle()

        val targetUid = viewModel.state.value.document.root.children.first().uid
        viewModel.onEvent(LayoutDesignerUiEvent.SetAttribute(targetUid, "android:text", "Mod"))

        viewModel.onEvent(LayoutDesignerUiEvent.BackRequested)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.showDiscardConfirm)

        // Dismiss
        viewModel.onEvent(LayoutDesignerUiEvent.DismissDiscard)
        assertFalse(viewModel.state.value.showDiscardConfirm)

        // Re-request and confirm
        viewModel.onEvent(LayoutDesignerUiEvent.BackRequested)
        assertTrue(viewModel.state.value.showDiscardConfirm)

        var receivedEffect: LayoutDesignerUiEffect? = null
        val effectJob = launch {
            receivedEffect = viewModel.effects.first()
        }

        viewModel.onEvent(LayoutDesignerUiEvent.ConfirmDiscard)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.showDiscardConfirm)
        assertEquals(LayoutDesignerUiEffect.NavigateBack, receivedEffect)
        effectJob.cancel()
    }

    @Test
    fun openInEditor_savesAndRequestsOpen() = runTest {
        val path = "/path/to/layout.xml"
        fileStore.files[path] = sampleValidXml
        viewModel.onEvent(LayoutDesignerUiEvent.Load(path))
        advanceUntilIdle()

        val receivedEffects = mutableListOf<LayoutDesignerUiEffect>()
        val effectJob = launch {
            viewModel.effects.collect { receivedEffects.add(it) }
        }

        viewModel.onEvent(LayoutDesignerUiEvent.OpenInEditor)
        advanceUntilIdle()

        assertEquals(path, openFileBridge.lastRequestedOpen)
        assertTrue(receivedEffects.contains(LayoutDesignerUiEffect.NavigateBack))
        effectJob.cancel()
    }

    @Test
    fun selectToggle_managesMultiSelection() = runTest {
        val path = "/path/to/layout.xml"
        fileStore.files[path] = sampleValidXml
        viewModel.onEvent(LayoutDesignerUiEvent.Load(path))
        advanceUntilIdle()

        viewModel.onEvent(LayoutDesignerUiEvent.Select(0))
        assertEquals(setOf(0), viewModel.state.value.selectedUids)

        // Toggle node 1 -> both 0 and 1 are selected
        viewModel.onEvent(LayoutDesignerUiEvent.SelectToggle(1))
        assertEquals(setOf(0, 1), viewModel.state.value.selectedUids)
        assertEquals(1, viewModel.state.value.selectedUid)

        // Toggle node 1 off -> only 0 selected
        viewModel.onEvent(LayoutDesignerUiEvent.SelectToggle(1))
        assertEquals(setOf(0), viewModel.state.value.selectedUids)
    }

    @Test
    fun wrapIn_and_unwrap_recordsUndo() = runTest {
        val path = "/path/to/layout.xml"
        fileStore.files[path] = sampleValidXml
        viewModel.onEvent(LayoutDesignerUiEvent.Load(path))
        advanceUntilIdle()

        // TextView has uid 1
        viewModel.onEvent(LayoutDesignerUiEvent.Select(1))
        viewModel.onEvent(LayoutDesignerUiEvent.WrapIn("FrameLayout"))

        val state = viewModel.state.value
        assertEquals(1, state.document.root.children.size)
        val container = state.document.root.children[0]
        assertEquals("FrameLayout", container.tag)
        assertEquals(1, container.children.size)
        assertTrue(state.canUndo)

        // Undo unwraps
        viewModel.onEvent(LayoutDesignerUiEvent.Undo)
        assertEquals("TextView", viewModel.state.value.document.root.children[0].tag)
    }

    @Test
    fun renameId_updatesStateAndRecordsUndo() = runTest {
        val path = "/path/to/layout.xml"
        fileStore.files[path] = sampleValidXml
        viewModel.onEvent(LayoutDesignerUiEvent.Load(path))
        advanceUntilIdle()

        viewModel.onEvent(LayoutDesignerUiEvent.RenameId(1, "@+id/newHeader"))
        assertEquals("@+id/newHeader", viewModel.state.value.document.find(1)!!.attr("android:id"))
        assertTrue(viewModel.state.value.canUndo)

        viewModel.onEvent(LayoutDesignerUiEvent.Undo)
        assertEquals("@+id/title", viewModel.state.value.document.find(1)!!.attr("android:id"))
    }

    @Test
    fun setReformatOnSave_togglesReformatAndRebuildsXml() = runTest {
        val path = "/path/to/layout.xml"
        fileStore.files[path] = sampleValidXml
        viewModel.onEvent(LayoutDesignerUiEvent.Load(path))
        advanceUntilIdle()

        viewModel.onEvent(LayoutDesignerUiEvent.SetReformatOnSave(true))
        assertTrue(viewModel.state.value.reformatOnSave)

        viewModel.onEvent(LayoutDesignerUiEvent.SetReformatOnSave(false))
        assertFalse(viewModel.state.value.reformatOnSave)
    }

    @Test
    fun dropWidget_insertsNewWidget_recordsSingleUndoStep() = runTest {
        val path = "/path/to/layout.xml"
        fileStore.files[path] = sampleValidXml
        viewModel.onEvent(LayoutDesignerUiEvent.Load(path))
        advanceUntilIdle()

        val button = PaletteEntry("button", "Button", WidgetCategory.BUTTON, emptyMap())
        val target = DropTarget(
            parentUid = 0,
            index = 0,
            indicator = DropIndicator.Line(0f, 0f, 100f, 0f),
            verdict = DropVerdict.Allowed,
            suggestedAttributes = mapOf("android:text" to "DroppedButton"),
        )

        viewModel.onEvent(LayoutDesignerUiEvent.DropWidget(DragPayload.NewWidget(button), target))
        val state = viewModel.state.value
        assertEquals(2, state.document.root.children.size)
        assertEquals("Button", state.document.root.children[0].tag)
        assertEquals("DroppedButton", state.document.root.children[0].attributes["android:text"])
        assertEquals(state.document.root.children[0].uid, state.selectedUid)
        assertTrue(state.canUndo)

        // Single undo reverts drop
        viewModel.onEvent(LayoutDesignerUiEvent.Undo)
        assertEquals(1, viewModel.state.value.document.root.children.size)
        assertFalse(viewModel.state.value.canUndo)
    }

    @Test
    fun dropWidget_movesExistingNode_recordsSingleUndoStep() = runTest {
        val path = "/path/to/layout.xml"
        fileStore.files[path] = """
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:orientation="vertical">
                <TextView android:id="@+id/t1" android:layout_width="wrap_content" android:layout_height="wrap_content" />
                <Button android:id="@+id/b1" android:layout_width="wrap_content" android:layout_height="wrap_content" />
            </LinearLayout>
        """.trimIndent()
        viewModel.onEvent(LayoutDesignerUiEvent.Load(path))
        advanceUntilIdle()

        // Move Button (uid 2) before TextView (uid 1) -> index 0
        val target = DropTarget(
            parentUid = 0,
            index = 0,
            indicator = DropIndicator.Line(0f, 0f, 100f, 0f),
            verdict = DropVerdict.Allowed,
        )

        viewModel.onEvent(LayoutDesignerUiEvent.DropWidget(DragPayload.ExistingNodes(setOf(2)), target))
        assertEquals("Button", viewModel.state.value.document.root.children[0].tag)
        assertEquals("TextView", viewModel.state.value.document.root.children[1].tag)
        assertTrue(viewModel.state.value.canUndo)

        viewModel.onEvent(LayoutDesignerUiEvent.Undo)
        assertEquals("TextView", viewModel.state.value.document.root.children[0].tag)
        assertEquals("Button", viewModel.state.value.document.root.children[1].tag)
    }

    // --- Fakes ---

    private class FakeLayoutFileStore : LayoutFileStore {
        val files = mutableMapOf<String, String>()
        var shouldFailWrite = false

        override suspend fun readText(path: String): String {
            return files[path] ?: throw IOException("File not found: $path")
        }

        override suspend fun writeAtomically(path: String, content: String) {
            if (shouldFailWrite) throw IOException("Disk write error")
            files[path] = content
        }
    }

    private class FakeOpenFileRequestBridge : OpenFileRequestBridge {
        private val _openRequests = MutableSharedFlow<String>(extraBufferCapacity = 4)
        override val openRequests: SharedFlow<String> = _openRequests.asSharedFlow()

        private val _jumpRequests = MutableSharedFlow<JumpTarget>(extraBufferCapacity = 4)
        override val jumpRequests: SharedFlow<JumpTarget> = _jumpRequests.asSharedFlow()

        var lastRequestedOpen: String? = null

        override suspend fun requestOpen(path: String) {
            lastRequestedOpen = path
            _openRequests.emit(path)
        }

        override suspend fun requestOpenAt(path: String, line: Int, column: Int, length: Int) {
            lastRequestedOpen = path
            _openRequests.emit(path)
            _jumpRequests.emit(JumpTarget(path, line, column, length))
        }
    }

    private class FakeFileSyncBridge : FileSyncBridge {
        private val _flushRequests = MutableSharedFlow<FlushRequest>(extraBufferCapacity = 4)
        override val flushRequests: SharedFlow<FlushRequest> = _flushRequests.asSharedFlow()

        private val _externalChanges = MutableSharedFlow<Set<String>>(extraBufferCapacity = 4)
        override val externalChanges: SharedFlow<Set<String>> = _externalChanges.asSharedFlow()

        private val _directoryChanges = MutableSharedFlow<String>(extraBufferCapacity = 4)
        override val directoryChanges: SharedFlow<String> = _directoryChanges.asSharedFlow()

        val flushedPaths = mutableListOf<Set<String>>()
        val notifiedChanges = mutableListOf<Collection<String>>()

        override suspend fun requestFlush(paths: Set<String>) {
            flushedPaths.add(paths)
        }

        override suspend fun notifyExternalChange(paths: Collection<String>) {
            notifiedChanges.add(paths)
        }

        override suspend fun requestFlushDirectory(directory: String) {}
        override suspend fun notifyDirectoryChange(directory: String) {}
    }
}
