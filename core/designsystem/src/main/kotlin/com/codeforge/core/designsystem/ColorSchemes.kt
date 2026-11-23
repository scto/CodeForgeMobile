// Modul: :core:designsystem
package com.codeforge.core.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.codeforge.libs.theme_kit.ColorRole
import com.codeforge.libs.theme_kit.ColorRoles
import com.codeforge.libs.theme_kit.ThemeEngine
import com.codeforge.libs.theme_kit.ThemeSpec

/** Material-3-Schema aus allen 36 Farbrollen. */
fun ColorRoles.toColorScheme(): ColorScheme {
    fun c(role: ColorRole) = Color(get(role))
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = c(ColorRole.PRIMARY),
        onPrimary = c(ColorRole.ON_PRIMARY),
        primaryContainer = c(ColorRole.PRIMARY_CONTAINER),
        onPrimaryContainer = c(ColorRole.ON_PRIMARY_CONTAINER),
        inversePrimary = c(ColorRole.INVERSE_PRIMARY),
        secondary = c(ColorRole.SECONDARY),
        onSecondary = c(ColorRole.ON_SECONDARY),
        secondaryContainer = c(ColorRole.SECONDARY_CONTAINER),
        onSecondaryContainer = c(ColorRole.ON_SECONDARY_CONTAINER),
        tertiary = c(ColorRole.TERTIARY),
        onTertiary = c(ColorRole.ON_TERTIARY),
        tertiaryContainer = c(ColorRole.TERTIARY_CONTAINER),
        onTertiaryContainer = c(ColorRole.ON_TERTIARY_CONTAINER),
        background = c(ColorRole.BACKGROUND),
        onBackground = c(ColorRole.ON_BACKGROUND),
        surface = c(ColorRole.SURFACE),
        onSurface = c(ColorRole.ON_SURFACE),
        surfaceVariant = c(ColorRole.SURFACE_VARIANT),
        onSurfaceVariant = c(ColorRole.ON_SURFACE_VARIANT),
        surfaceTint = c(ColorRole.SURFACE_TINT),
        inverseSurface = c(ColorRole.INVERSE_SURFACE),
        inverseOnSurface = c(ColorRole.INVERSE_ON_SURFACE),
        error = c(ColorRole.ERROR),
        onError = c(ColorRole.ON_ERROR),
        errorContainer = c(ColorRole.ERROR_CONTAINER),
        onErrorContainer = c(ColorRole.ON_ERROR_CONTAINER),
        outline = c(ColorRole.OUTLINE),
        outlineVariant = c(ColorRole.OUTLINE_VARIANT),
        scrim = c(ColorRole.SCRIM),
        surfaceBright = c(ColorRole.SURFACE_BRIGHT),
        surfaceContainer = c(ColorRole.SURFACE_CONTAINER),
        surfaceContainerHigh = c(ColorRole.SURFACE_CONTAINER_HIGH),
        surfaceContainerHighest = c(ColorRole.SURFACE_CONTAINER_HIGHEST),
        surfaceContainerLow = c(ColorRole.SURFACE_CONTAINER_LOW),
        surfaceContainerLowest = c(ColorRole.SURFACE_CONTAINER_LOWEST),
        surfaceDim = c(ColorRole.SURFACE_DIM),
    )
}

/** Material-3-Schema einer [ThemeSpec]. */
fun ThemeSpec.toColorScheme(isDark: Boolean): ColorScheme = ThemeEngine.roles(this, isDark).toColorScheme()

private val codeForgeLight: ColorScheme by lazy { ThemeSpec.CODEFORGE.toColorScheme(isDark = false) }
private val codeForgeDark: ColorScheme by lazy { ThemeSpec.CODEFORGE.toColorScheme(isDark = true) }

/** Die Farbgebung von CodeForgeMobile: Indigo aus dem App-Icon, Cyan als Akzent (Tertiärfarbe). */
fun codeForgeColorScheme(isDark: Boolean): ColorScheme = if (isDark) codeForgeDark else codeForgeLight
