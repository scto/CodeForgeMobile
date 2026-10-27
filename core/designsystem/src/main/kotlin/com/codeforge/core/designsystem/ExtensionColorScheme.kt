// Modul: :core:designsystem
package com.codeforge.core.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.codeforge.core.domain.extension.ExtensionColorTheme

/**
 * Baut aus einem Plugin-Farbschema ein Material-3-Schema: Start ist das Standardschema der passenden
 * Helligkeit, jede im Plugin angegebene Rolle überschreibt den Wert. Fehlen Container-/On-Farben,
 * bleibt dafür der Standardwert — Plugin-Autoren müssen also nur die Rollen angeben, die sie ändern wollen.
 */
fun ExtensionColorTheme.toColorScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    fun c(role: String, fallback: Color): Color = colors[role]?.let { Color(it.toInt()) } ?: fallback
    return base.copy(
        primary = c("primary", base.primary),
        onPrimary = c("onPrimary", base.onPrimary),
        primaryContainer = c("primaryContainer", base.primaryContainer),
        onPrimaryContainer = c("onPrimaryContainer", base.onPrimaryContainer),
        secondary = c("secondary", base.secondary),
        onSecondary = c("onSecondary", base.onSecondary),
        secondaryContainer = c("secondaryContainer", base.secondaryContainer),
        onSecondaryContainer = c("onSecondaryContainer", base.onSecondaryContainer),
        tertiary = c("tertiary", base.tertiary),
        onTertiary = c("onTertiary", base.onTertiary),
        tertiaryContainer = c("tertiaryContainer", base.tertiaryContainer),
        onTertiaryContainer = c("onTertiaryContainer", base.onTertiaryContainer),
        error = c("error", base.error),
        onError = c("onError", base.onError),
        errorContainer = c("errorContainer", base.errorContainer),
        onErrorContainer = c("onErrorContainer", base.onErrorContainer),
        background = c("background", base.background),
        onBackground = c("onBackground", base.onBackground),
        surface = c("surface", base.surface),
        onSurface = c("onSurface", base.onSurface),
        surfaceVariant = c("surfaceVariant", base.surfaceVariant),
        onSurfaceVariant = c("onSurfaceVariant", base.onSurfaceVariant),
        outline = c("outline", base.outline),
        outlineVariant = c("outlineVariant", base.outlineVariant),
        inverseSurface = c("inverseSurface", base.inverseSurface),
        inverseOnSurface = c("inverseOnSurface", base.inverseOnSurface),
        inversePrimary = c("inversePrimary", base.inversePrimary),
        surfaceTint = c("surfaceTint", c("primary", base.surfaceTint)),
        scrim = c("scrim", base.scrim),
        surfaceBright = c("surfaceBright", base.surfaceBright),
        surfaceDim = c("surfaceDim", base.surfaceDim),
        surfaceContainerLowest = c("surfaceContainerLowest", base.surfaceContainerLowest),
        surfaceContainerLow = c("surfaceContainerLow", base.surfaceContainerLow),
        surfaceContainer = c("surfaceContainer", base.surfaceContainer),
        surfaceContainerHigh = c("surfaceContainerHigh", base.surfaceContainerHigh),
        surfaceContainerHighest = c("surfaceContainerHighest", base.surfaceContainerHighest)
    )
}

/** Vom aktiven Plugin-Farbschema vorgegebenes Editor-Syntaxtheme (TextMate-JSON-Datei). */
data class EditorThemeOverride(
    val id: String,
    val filePath: String,
    val isDark: Boolean
)

/** `null`, wenn kein Plugin-Farbschema mit eigenem Editor-Theme aktiv ist. */
val LocalEditorThemeOverride = staticCompositionLocalOf<EditorThemeOverride?> { null }
