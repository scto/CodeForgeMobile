/**
 * Modul: :feature:composepreview
 * @author Thomas Schmid
 */
package com.codeforge.feature.composepreview

import androidx.compose.runtime.Immutable
import com.codeforge.core.domain.model.ComposableCandidate
import com.codeforge.core.domain.model.PreviewThemeMode

/** Zustand des Renderns. */
sealed interface PreviewRenderState {
    /** Untätig. */
    data object Idle : PreviewRenderState
    /** Laufzeit wird vorbereitet (Fortschritt in Prozent). */
    data class PreparingRuntime(val progress: Int) : PreviewRenderState
    /** Wird gerendert. */
    data object Rendering : PreviewRenderState
    /** Fertig gerendert. */
    data class Rendered(
        val functionName: String,
        val imageBytes: ByteArray?,
        val compiledFiles: List<String> = emptyList(),
        val isFallbackTheme: Boolean = false,
        val fallbackReason: String? = null,
        val engine: com.codeforge.core.domain.model.PreviewEngine = com.codeforge.core.domain.model.PreviewEngine.COMPILED,
        val durationMs: Long = 0,
        val engineNote: String? = null,
        val usesBuildOutputs: Boolean = false
    ) : PreviewRenderState
    /** Fehlgeschlagen. */
    data class Failed(val message: String) : PreviewRenderState
}

/** Wie die Vorschau entsteht. */
enum class PreviewMode {
    /** Schnell auf dem Gerät (kotlinc bzw. Interpreter, Ressourcen als Stubs). */
    LIVE,

    /** Genau über Gradle (compose-ai-tools-Plugin, Robolectric, echte Abhängigkeiten und Ressourcen). */
    GRADLE
}

/** Zustand der genauen Vorschau über Gradle. */
@Immutable
data class GradlePreviewState(
    /** Gradle-Pfad des Moduls (z. B. `:app`); `null` außerhalb eines Gradle-Projekts. */
    val module: String? = null,
    val previews: List<com.codeforge.feature.composepreview.gradle.GradlePreviewImage> = emptyList(),
    /** Es gibt schon ein `previews.json` (mindestens ein Lauf). */
    val hasManifest: Boolean = false,
    /** Zeitpunkt des letzten Manifests (ms). */
    val updatedAt: Long = 0L,
    /** Ein Lauf wurde angestoßen, das Ergebnis steht noch aus. */
    val waiting: Boolean = false
)

/** Zustand der Compose-Vorschau. */
@Immutable
data class ComposePreviewUiState(
    val filePath: String = "",
    val availableComposables: List<ComposableCandidate> = emptyList(),
    val selectedFunctionName: String? = null,
    val renderState: PreviewRenderState = PreviewRenderState.Idle,
    val themeMode: PreviewThemeMode = PreviewThemeMode.AUTO,
    /** Vergrößerung der Vorschau samt Texten (1.0 = 100 %). */
    val zoom: Float = PreviewZoom.DEFAULT,
    /** Ein neues Bild wird erzeugt; das bisherige bleibt so lange sichtbar. */
    val isRefreshing: Boolean = false,
    val mode: PreviewMode = PreviewMode.LIVE,
    val gradle: GradlePreviewState = GradlePreviewState()
)

/** Zoomstufen der Vorschau. */
object PreviewZoom {
    const val DEFAULT = 1f
    /** Stufen in Prozent: fein um 100 %, gröber darüber. */
    val STEPS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f, 4f)

    fun zoomIn(current: Float): Float = STEPS.firstOrNull { it > current + 0.001f } ?: STEPS.last()
    fun zoomOut(current: Float): Float = STEPS.lastOrNull { it < current - 0.001f } ?: STEPS.first()
}

/** Aktionen der Compose-Vorschau. */
sealed interface ComposePreviewUiEvent {
    /** Preview-Funktion gewählt. */
    data class FunctionSelected(val functionName: String) : ComposePreviewUiEvent
    /** Neu rendern. */
    data object Rerender : ComposePreviewUiEvent
    /** Hell/Dunkel gewechselt. */
    data class ThemeModeChanged(val mode: PreviewThemeMode) : ComposePreviewUiEvent
    /** Vergrößern. */
    data object ZoomIn : ComposePreviewUiEvent
    /** Verkleinern. */
    data object ZoomOut : ComposePreviewUiEvent
    /** Auf 100 % zurück. */
    data object ZoomReset : ComposePreviewUiEvent
    /** Live- oder Gradle-Vorschau gewählt. */
    data class ModeChanged(val mode: PreviewMode) : ComposePreviewUiEvent
    /** Über Gradle rendern ([full] auch teure Aufnahmen wie GIFs). */
    data class GradleRender(val full: Boolean) : ComposePreviewUiEvent
    /** Gerendertes Bild im Bildbetrachter öffnen. */
    data class OpenImage(val path: String) : ComposePreviewUiEvent
}
