/**
 * Modul: :feature:composepreview
 * @author Thomas Schmid
 */
package com.codeforge.feature.composepreview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewCodeStripperTest {

    private val viewModelFile = """
        package com.example.feature.settings

        import androidx.compose.runtime.Immutable
        import androidx.lifecycle.ViewModel
        import androidx.lifecycle.viewModelScope
        import dagger.hilt.android.lifecycle.HiltViewModel
        import javax.inject.Inject

        /** Zustand. */
        @Immutable
        data class SettingsUiState(val dark: Boolean = false)

        sealed interface SettingsUiEvent {
            data object Toggle : SettingsUiEvent
        }

        /** ViewModel. */
        @HiltViewModel
        class SettingsViewModel @Inject constructor(
            private val update: UpdateSettingsUseCase,
        ) : ViewModel() {
            fun onEvent(event: SettingsUiEvent) {
                if (event is SettingsUiEvent.Toggle) { update() }
            }
        }

        private const val STOP = 5_000L
    """.trimIndent()

    @Test
    fun `removes view model but keeps state and events`() {
        val stripped = PreviewCodeStripper.strip(viewModelFile)
        assertEquals(setOf("SettingsViewModel"), stripped.removedNames)
        assertTrue(stripped.text.contains("data class SettingsUiState"))
        assertTrue(stripped.text.contains("sealed interface SettingsUiEvent"))
        assertTrue(stripped.text.contains("private const val STOP"))
        assertFalse(stripped.text.contains("UpdateSettingsUseCase"))
        assertFalse(stripped.text.contains("HiltViewModel"))
        assertFalse(stripped.text.contains("javax.inject"))
        assertFalse(stripped.text.contains("/** ViewModel. */"))
    }

    @Test
    fun `removes class without body`() {
        val stripped = PreviewCodeStripper.removeUnavailableClasses(
            "package a\n\nclass AppViewModel : ViewModel()\n\nfun keep() = 1\n"
        )
        assertEquals(setOf("AppViewModel"), stripped.removedNames)
        assertTrue(stripped.text.contains("fun keep() = 1"))
        assertFalse(stripped.text.contains("AppViewModel"))
    }

    @Test
    fun `keeps classes with lambda parameters and ordinary supertypes`() {
        val text = """
            package a

            data class Item(val onClick: () -> Unit = {}, val name: String) : Comparable<Item> {
                override fun compareTo(other: Item) = name.compareTo(other.name)
            }

            object Theme : Base()
        """.trimIndent()
        val stripped = PreviewCodeStripper.removeUnavailableClasses(text)
        assertTrue(stripped.removedNames.isEmpty())
        assertEquals(text, stripped.text)
    }

    @Test
    fun `removes route that looks up a view model and its imports`() {
        val screen = """
            package com.example.feature.settings

            import androidx.compose.runtime.Composable
            import androidx.compose.runtime.getValue
            import androidx.hilt.navigation.compose.hiltViewModel
            import androidx.lifecycle.compose.collectAsStateWithLifecycle
            import com.example.core.designsystem.component.GradientHeader

            /** Route. */
            @Composable
            fun SettingsRoute(modifier: Modifier = Modifier, viewModel: SettingsViewModel = hiltViewModel()) {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                SettingsScreen(state, viewModel::onEvent, modifier)
            }

            /** Screen. */
            @Composable
            fun SettingsScreen(state: SettingsUiState, onEvent: (SettingsUiEvent) -> Unit, modifier: Modifier = Modifier) {
                GradientHeader(title = "x")
            }

            private fun Mode.label(): Int = when (this) {
                Mode.A -> 1
                Mode.B -> 2
            }

            @Preview
            @Composable
            private fun SettingsScreenPreview() {
                SettingsScreen(SettingsUiState(), {})
            }
        """.trimIndent()
        val stripped = PreviewCodeStripper.strip(screen, unavailable = setOf("SettingsViewModel"))
        assertEquals(setOf("SettingsRoute"), stripped.removedNames)
        assertFalse(stripped.text.contains("SettingsRoute"))
        assertFalse(stripped.text.contains("/** Route. */"))
        assertFalse(stripped.text.contains("androidx.hilt"))
        assertTrue(stripped.text.contains("import androidx.compose.runtime.getValue"))
        assertTrue(stripped.text.contains("/** Screen. */\n@Composable\nfun SettingsScreen("))
        assertTrue(stripped.text.contains("private fun Mode.label(): Int = when (this) {"))
        assertTrue(stripped.text.contains("@Preview\n@Composable\nprivate fun SettingsScreenPreview()"))
    }

    @Test
    fun `removes injection annotations from use cases`() {
        val text = "package a\n\nimport javax.inject.Inject\n\nclass Load @Inject constructor(private val repo: Repo) {\n    operator fun invoke() = repo.all()\n}\n"
        val stripped = PreviewCodeStripper.strip(text)
        assertTrue(stripped.text.contains("class Load constructor(private val repo: Repo)"))
        assertFalse(stripped.text.contains("Inject"))
    }

    @Test
    fun `keeps public companion constants of a removed view model and drops its imports`() {
        val text = """
            package a

            import androidx.lifecycle.ViewModel
            import androidx.lifecycle.viewModelScope
            import a.usecase.PlaybackUseCase

            class PlayerViewModel(private val playback: PlaybackUseCase) : ViewModel() {
                fun x() = viewModelScope

                companion object {
                    const val SKIP_BACK_SEC = 15
                }
            }

            fun skip() = PlayerViewModel.SKIP_BACK_SEC
        """.trimIndent()
        val stripped = PreviewCodeStripper.strip(text)
        assertTrue(stripped.removedNames.isEmpty())
        assertTrue(stripped.text.contains("object PlayerViewModel {\n    const val SKIP_BACK_SEC = 15\n}"))
        assertFalse(stripped.text.contains("import"))
        assertTrue(stripped.text.contains("fun skip() = PlayerViewModel.SKIP_BACK_SEC"))
    }
}
