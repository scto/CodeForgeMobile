// Modul: :core:designsystem
package com.codeforge.core.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.codeforge.libs.theme_kit.ColorHex
import com.codeforge.libs.theme_kit.ThemeSpec
import com.codeforge.core.domain.extension.ExtensionColorTheme
import com.codeforge.core.domain.extension.GrammarContribution
import com.codeforge.core.domain.extension.Resolved

/**
 * Baut aus einem Plugin-Farbschema ein Material-3-Schema. Gibt das Plugin `primary` an, wird daraus (mit `secondary`
 * und `tertiary`, falls vorhanden) ein vollständiges Schema abgeleitet – Container, Flächen und Ränder passen dann zur
 * Grundfarbe statt zum Material-Standard-Lila. Jede im Plugin angegebene Rolle überschreibt den abgeleiteten Wert,
 * Autoren müssen also nur die Rollen angeben, die sie ändern wollen.
 */
fun ExtensionColorTheme.toColorScheme(): ColorScheme {
    val base = derivedBase() ?: if (isDark) darkColorScheme() else lightColorScheme()
    fun c(role: String, fallback: Color): Color = colors[role]?.let { Color(it.toInt()) } ?: fallback
    return base.copy(
        primary = c(ROLE_PRIMARY, base.primary),
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
        surfaceTint = c("surfaceTint", c(ROLE_PRIMARY, base.surfaceTint)),
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

/** Grammatiken aus aktivierten Plugins (für den Editor); leer = keine. */
val LocalActiveGrammars = staticCompositionLocalOf<List<Resolved<GrammarContribution>>> { emptyList() }

/** `null`, wenn kein Plugin-Farbschema mit eigenem Editor-Theme aktiv ist. */
val LocalEditorThemeOverride = staticCompositionLocalOf<EditorThemeOverride?> { null }

/** Alle Editor-Themes installierter Plugins (für eine ausdrückliche Auswahl in den Editor-Einstellungen). */
val LocalEditorThemes = staticCompositionLocalOf<List<EditorThemeOverride>> { emptyList() }

private const val ROLE_PRIMARY = "primary"

private fun ExtensionColorTheme.derivedBase(): ColorScheme? {
    val primary = colors[ROLE_PRIMARY] ?: return null
    fun hex(v: Long?) = v?.let { ColorHex.format(it.toInt()) }
    return ThemeSpec(seed = ColorHex.format(primary.toInt()), secondary = hex(colors["secondary"]), tertiary = hex(colors["tertiary"]))
        .toColorScheme(isDark)
}
