package com.codeforge.feature.settings

import androidx.datastore.core.DataStore
import com.codeforge.core.datastore.SettingsRepository
import com.codeforge.core.datastore.proto.AppSettings
import com.codeforge.core.datastore.proto.EditorConfig
import com.codeforge.core.datastore.proto.FileHeaderConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EditorSettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeDataStore: FakeDataStore
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var viewModel: EditorSettingsViewModel

    private class FakeDataStore(initialSettings: AppSettings = AppSettings.getDefaultInstance()) : DataStore<AppSettings> {
        private val _flow = MutableStateFlow(initialSettings)
        override val data: Flow<AppSettings> = _flow.asStateFlow()

        override suspend fun updateData(transform: suspend (t: AppSettings) -> AppSettings): AppSettings {
            val updated = transform(_flow.value)
            _flow.value = updated
            return updated
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeDataStore = FakeDataStore(
            AppSettings.newBuilder()
                .setEditor(
                    EditorConfig.newBuilder()
                        .setTabSize(4)
                        .setFontSize(14)
                        .setUseTreeSitter(true)
                        .setStickyScrollEnabled(true)
                        .setMagnifierEnabled(true)
                        .setSymbolPairAutocompleteEnabled(true)
                        .setMinimapEnabled(true)
                        .setRainbowBracketsEnabled(true)
                        .setPinchToZoomEnabled(true)
                        .setDesktopShortcutsEnabled(true)
                        .build()
                )
                .setFileHeader(
                    FileHeaderConfig.newBuilder()
                        .setEnabled(false)
                        .setAuthor("")
                        .build()
                )
                .build()
        )
        settingsRepository = SettingsRepository(fakeDataStore)
        viewModel = EditorSettingsViewModel(settingsRepository, themesDir = null)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state reflects datastore settings`() = runTest(testDispatcher) {
        val state = viewModel.uiState.first { !it.isLoading }
        assertEquals(4, state.tabSize)
        assertEquals(14, state.fontSize)
        assertTrue(state.useTreeSitter)
        assertTrue(state.stickyScrollEnabled)
        assertTrue(state.symbolPairAutocompleteEnabled)
        assertFalse(state.fileHeaderEnabled)
    }

    @Test
    fun `project indexing is on by default and can be toggled and reset`() = runTest(testDispatcher) {
        assertTrue(viewModel.uiState.first { !it.isLoading }.projectIndexingEnabled)

        viewModel.onEvent(EditorSettingsUiEvent.ProjectIndexingToggled)
        advanceUntilIdle()
        assertTrue(fakeDataStore.data.first().editor.projectIndexingDisabled)
        assertFalse(viewModel.uiState.first { !it.projectIndexingEnabled }.projectIndexingEnabled)

        viewModel.onEvent(EditorSettingsUiEvent.ResetSection(EditorSettingsSection.INDEXING))
        advanceUntilIdle()
        assertFalse(fakeDataStore.data.first().editor.projectIndexingDisabled)
    }

    @Test
    fun `changing options saves to repository and updates uiState`() = runTest(testDispatcher) {
        // Tab size
        viewModel.onEvent(EditorSettingsUiEvent.TabSizeChanged(8))
        advanceUntilIdle()
        assertEquals(8, fakeDataStore.data.first().editor.tabSize)

        // Font size
        viewModel.onEvent(EditorSettingsUiEvent.FontSizeChanged(18))
        advanceUntilIdle()
        assertEquals(18, fakeDataStore.data.first().editor.fontSize)

        // Font family
        viewModel.onEvent(EditorSettingsUiEvent.FontFamilyChanged("fonts/jetbrainsmono_regular.ttf"))
        advanceUntilIdle()
        assertEquals("fonts/jetbrainsmono_regular.ttf", fakeDataStore.data.first().editor.fontFamily)

        // Word wrap
        viewModel.onEvent(EditorSettingsUiEvent.WordWrapToggled)
        advanceUntilIdle()
        assertTrue(fakeDataStore.data.first().editor.wordWrap)

        // Non printable chars
        viewModel.onEvent(EditorSettingsUiEvent.NonPrintableCharsToggled)
        advanceUntilIdle()
        assertTrue(fakeDataStore.data.first().editor.showNonPrintableChars)

        // Sticky scroll
        viewModel.onEvent(EditorSettingsUiEvent.StickyScrollToggled)
        advanceUntilIdle()
        assertFalse(fakeDataStore.data.first().editor.stickyScrollEnabled)

        // Magnifier
        viewModel.onEvent(EditorSettingsUiEvent.MagnifierToggled)
        advanceUntilIdle()
        assertFalse(fakeDataStore.data.first().editor.magnifierEnabled)

        // Symbol pair autocomplete
        viewModel.onEvent(EditorSettingsUiEvent.SymbolPairAutocompleteToggled)
        advanceUntilIdle()
        assertFalse(fakeDataStore.data.first().editor.symbolPairAutocompleteEnabled)

        // Minimap
        viewModel.onEvent(EditorSettingsUiEvent.MinimapToggled)
        advanceUntilIdle()
        assertFalse(fakeDataStore.data.first().editor.minimapEnabled)

        // Rainbow brackets
        viewModel.onEvent(EditorSettingsUiEvent.RainbowBracketsToggled)
        advanceUntilIdle()
        assertFalse(fakeDataStore.data.first().editor.rainbowBracketsEnabled)

        // Pinch to zoom
        viewModel.onEvent(EditorSettingsUiEvent.PinchToZoomToggled)
        advanceUntilIdle()
        assertFalse(fakeDataStore.data.first().editor.pinchToZoomEnabled)

        // Desktop shortcuts
        viewModel.onEvent(EditorSettingsUiEvent.DesktopShortcutsToggled)
        advanceUntilIdle()
        assertFalse(fakeDataStore.data.first().editor.desktopShortcutsEnabled)

        // Tree sitter
        viewModel.onEvent(EditorSettingsUiEvent.TreeSitterToggled)
        advanceUntilIdle()
        assertFalse(fakeDataStore.data.first().editor.useTreeSitter)

        // TextMate Theme
        viewModel.onEvent(EditorSettingsUiEvent.TextmateThemeChanged("light_plus"))
        advanceUntilIdle()
        assertEquals("light_plus", fakeDataStore.data.first().editor.textmateTheme)

        // Format on save
        viewModel.onEvent(EditorSettingsUiEvent.FormatOnSaveKotlinToggled)
        viewModel.onEvent(EditorSettingsUiEvent.FormatOnSaveJavaToggled)
        viewModel.onEvent(EditorSettingsUiEvent.FormatOnSaveXmlToggled)
        viewModel.onEvent(EditorSettingsUiEvent.FormatOnSaveJsonToggled)
        advanceUntilIdle()
        assertTrue(fakeDataStore.data.first().editor.formatOnSaveKotlin)
        assertTrue(fakeDataStore.data.first().editor.formatOnSaveJava)
        assertTrue(fakeDataStore.data.first().editor.formatOnSaveXml)
        assertTrue(fakeDataStore.data.first().editor.formatOnSaveJson)

        // LSP concurrent sessions and idle timeout
        viewModel.onEvent(EditorSettingsUiEvent.LspMaxConcurrentSessionsChanged(5))
        viewModel.onEvent(EditorSettingsUiEvent.LspIdleTimeoutMinutesChanged(30))
        advanceUntilIdle()
        assertEquals(5, fakeDataStore.data.first().editor.lspMaxConcurrentSessions)
        assertEquals(30, fakeDataStore.data.first().editor.lspIdleTimeoutMinutes)

        // File header
        viewModel.onEvent(EditorSettingsUiEvent.FileHeaderEnabledToggled)
        viewModel.onEvent(EditorSettingsUiEvent.FileHeaderAuthorChanged("Thomas"))
        advanceUntilIdle()
        assertTrue(fakeDataStore.data.first().fileHeader.enabled)
        assertEquals("Thomas", fakeDataStore.data.first().fileHeader.author)
    }

    @Test
    fun `section reset restores defaults for that section`() = runTest(testDispatcher) {
        viewModel.onEvent(EditorSettingsUiEvent.TabSizeChanged(2))
        viewModel.onEvent(EditorSettingsUiEvent.SymbolPairAutocompleteToggled)
        advanceUntilIdle()
        assertEquals(2, fakeDataStore.data.first().editor.tabSize)
        assertFalse(fakeDataStore.data.first().editor.symbolPairAutocompleteEnabled)

        viewModel.onEvent(EditorSettingsUiEvent.ResetSection(EditorSettingsSection.INPUT))
        advanceUntilIdle()
        assertEquals(4, fakeDataStore.data.first().editor.tabSize)
        assertTrue(fakeDataStore.data.first().editor.symbolPairAutocompleteEnabled)
    }

    @Test
    fun `backward compatibility with default empty proto`() = runTest(testDispatcher) {
        val emptyDataStore = FakeDataStore(AppSettings.getDefaultInstance())
        val emptyRepo = SettingsRepository(emptyDataStore)
        val vm = EditorSettingsViewModel(emptyRepo, themesDir = null)

        val state = vm.uiState.first { !it.isLoading }
        assertEquals(4, state.tabSize)
        assertEquals(14, state.fontSize)
        assertEquals(3, state.lspMaxConcurrentSessions)
        assertEquals(10, state.lspIdleTimeoutMinutes)
        assertFalse(state.fileHeaderEnabled)
        assertEquals("", state.fileHeaderAuthor)
    }
}
