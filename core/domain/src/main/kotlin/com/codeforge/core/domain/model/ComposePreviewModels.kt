/**
 * Modul: :core:domain
 * @author Thomas Schmid
 */
package com.codeforge.core.domain.model

/** Hell/Dunkel-Wahl der Vorschau; AUTO übernimmt `uiMode` aus der @Preview-Annotation (sonst hell). */
enum class PreviewThemeMode { AUTO, LIGHT, DARK }

/** Ausgewertete Parameter einer `@Preview(...)`-Annotation (nur die, die der Renderer berücksichtigt). */
data class PreviewOptions(
    val showBackground: Boolean = false,
    /** ARGB, z. B. 0xFF000000; `null` = Standard je nach Hell/Dunkel. */
    val backgroundColor: Long? = null,
    val widthDp: Int? = null,
    val heightDp: Int? = null,
    /** `true` bei `uiMode = UI_MODE_NIGHT_YES`, `false` bei NIGHT_NO, `null` = nicht angegeben. */
    val darkMode: Boolean? = null
)

data class ComposableCandidate(
    val functionName: String,
    val hasPreviewAnnotation: Boolean,
    val startLine: Int,
    /** `true`, wenn die Funktion Parameter deklariert (die Vorschau kann nur parameterlose Funktionen rendern). */
    val hasParameters: Boolean = false,
    val options: PreviewOptions = PreviewOptions()
)

data class PreviewRenderResult(
    val functionName: String,
    val imageBytes: ByteArray?,
    val compiledFiles: List<String> = emptyList(),
    val isFallbackTheme: Boolean = false,
    val fallbackReason: String? = null
)
