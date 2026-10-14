// Modul: :core:designsystem
package com.codeforge.core.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Microsoft Fluent 2 Design Tokens für Jetpack Compose.
 *
 * Ermöglicht die Verwendung von Fluent 2 Gestaltungselementen (Farben, Radien, Elevationen)
 * parallel zum bestehenden Material 3 Expressive Designsystem, ohne externe Alpha-Bibliotheken
 * einzubinden. Referenz: FLUENT2_JETPACK_COMPOSE_GUIDE.md.
 */
object FluentTokens {

    /**
     * Fluent 2 Standard Brand-Farben (Microsoft Blue / Windows 11 Accent).
     */
    object Brand {
        val Shade30 = Color(0xFF004578)
        val Shade20 = Color(0xFF005A9E)
        val Shade10 = Color(0xFF106EBE)
        val Primary = Color(0xFF0078D4)
        val Tint10 = Color(0xFF2B88D8)
        val Tint20 = Color(0xFFC7E0F4)
        val Tint30 = Color(0xFFDEECF9)
        val Tint40 = Color(0xFFEFF6FC)
    }

    /**
     * Fluent 2 Neutrale Farben für Hell- und Dunkelmodus.
     */
    object Neutral {
        // Light Mode
        val Grey14 = Color(0xFF242424)
        val Grey26 = Color(0xFF424242)
        val Grey68 = Color(0xFFADADAD)
        val Grey92 = Color(0xFFEBEBEB)
        val Grey96 = Color(0xFFF5F5F5)
        val White = Color(0xFFFFFFFF)

        // Dark Mode
        val Grey4 = Color(0xFF0A0A0A)
        val Grey8 = Color(0xFF141414)
        val Grey12 = Color(0xFF1F1F1F)
        val Grey20 = Color(0xFF333333)
        val Grey84 = Color(0xFFD6D6D6)
    }

    /**
     * Fluent 2 Eckenradien (Corner Radii).
     */
    object CornerRadius {
        val None: Dp = 0.dp
        val Small: Dp = 2.dp
        val Medium: Dp = 4.dp
        val Large: Dp = 8.dp
        val XLarge: Dp = 12.dp
        val Circle: Dp = 9999.dp
    }

    /**
     * Fluent 2 Schatten- & Elevationsebenen.
     */
    object Elevation {
        val Level0: Dp = 0.dp
        val Level1: Dp = 2.dp
        val Level2: Dp = 4.dp
        val Level3: Dp = 8.dp
        val Level4: Dp = 16.dp
    }

    /**
     * Fluent 2 Rahmenstärken (Stroke Widths).
     */
    object Stroke {
        val None: Dp = 0.dp
        val Thin: Dp = 1.dp
        val Thick: Dp = 2.dp
        val Thicker: Dp = 3.dp
    }
}

/**
 * Fluent 2 Farbschema-Container für Compose-Hierarchien.
 */
@Immutable
data class FluentColorScheme(
    val brandPrimary: Color,
    val background: Color,
    val surface: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val stroke: Color,
    val isDark: Boolean
) {
    companion object {
        val Light = FluentColorScheme(
            brandPrimary = FluentTokens.Brand.Primary,
            background = FluentTokens.Neutral.White,
            surface = FluentTokens.Neutral.Grey96,
            textPrimary = FluentTokens.Neutral.Grey14,
            textSecondary = FluentTokens.Neutral.Grey26,
            stroke = FluentTokens.Neutral.Grey92,
            isDark = false
        )

        val Dark = FluentColorScheme(
            brandPrimary = FluentTokens.Brand.Tint10,
            background = FluentTokens.Neutral.Grey4,
            surface = FluentTokens.Neutral.Grey12,
            textPrimary = FluentTokens.Neutral.White,
            textSecondary = FluentTokens.Neutral.Grey84,
            stroke = FluentTokens.Neutral.Grey20,
            isDark = true
        )
    }
}
