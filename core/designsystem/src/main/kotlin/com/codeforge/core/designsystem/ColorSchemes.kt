// Modul: :core:designsystem
package com.codeforge.core.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.codeforge.core.datastore.proto.CustomPalette

/**
 * Registry vordefinierter Farbschemata (id -> ColorSchemeSet).
 * ThemeBuilder-Feature ergänzt zur Laufzeit weitere Custom-Paletten via customPalette.
 */
fun resolveCustomScheme(colorSchemeId: String, customPalette: CustomPalette, isDark: Boolean): ColorScheme {
    if (colorSchemeId == "custom" && customPalette.primary.isNotBlank()) {
        return buildColorScheme(
            primaryHex = customPalette.primary,
            secondaryHex = customPalette.secondary.ifBlank { customPalette.primary },
            tertiaryHex = customPalette.tertiary.ifBlank { customPalette.primary },
            isDark = isDark
        )
    }

    val preset = ThemePresets.findById(colorSchemeId)
    if (preset != null) {
        return buildColorScheme(
            primaryHex = preset.primaryHex,
            secondaryHex = preset.secondaryHex,
            tertiaryHex = preset.tertiaryHex,
            isDark = isDark
        )
    }

    return if (isDark) darkColorScheme() else lightColorScheme()
}

private fun buildColorScheme(primaryHex: String, secondaryHex: String, tertiaryHex: String, isDark: Boolean): ColorScheme {
    val primary = runCatching { Color(android.graphics.Color.parseColor(primaryHex)) }.getOrNull()
    val secondary = runCatching { Color(android.graphics.Color.parseColor(secondaryHex)) }.getOrNull()
    val tertiary = runCatching { Color(android.graphics.Color.parseColor(tertiaryHex)) }.getOrNull()

    if (primary == null) return if (isDark) darkColorScheme() else lightColorScheme()

    return if (isDark) {
        darkColorScheme(primary = primary, secondary = secondary ?: primary, tertiary = tertiary ?: primary)
    } else {
        lightColorScheme(primary = primary, secondary = secondary ?: primary, tertiary = tertiary ?: primary)
    }
}

val CodeForgeTypography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp
    )
)
