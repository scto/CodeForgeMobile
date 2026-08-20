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
        val primary = Color(android.graphics.Color.parseColor(customPalette.primary))
        val secondary = Color(android.graphics.Color.parseColor(customPalette.secondary.ifBlank { customPalette.primary }))
        val tertiary = Color(android.graphics.Color.parseColor(customPalette.tertiary.ifBlank { customPalette.primary }))
        return if (isDark) {
            darkColorScheme(primary = primary, secondary = secondary, tertiary = tertiary)
        } else {
            lightColorScheme(primary = primary, secondary = secondary, tertiary = tertiary)
        }
    }

    return if (isDark) darkColorScheme() else lightColorScheme()
}

val CodeForgeTypography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp
    )
)
