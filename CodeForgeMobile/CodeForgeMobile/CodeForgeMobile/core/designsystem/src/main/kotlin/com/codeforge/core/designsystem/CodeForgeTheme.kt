// Modul: :core:designsystem
package com.codeforge.core.designsystem

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.codeforge.core.datastore.proto.ThemeConfig
import com.codeforge.core.datastore.proto.ThemeMode

fun supportsDynamicColor(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@Composable
fun CodeForgeTheme(
    themeState: ThemeConfig,
    content: @Composable () -> Unit
) {
    val isDark = when (themeState.mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        else -> isSystemInDarkTheme()
    }

    val colorScheme = when {
        themeState.useDynamicColor && supportsDynamicColor() ->
            if (isDark) dynamicDarkColorScheme(LocalContext.current)
            else dynamicLightColorScheme(LocalContext.current)
        else -> resolveCustomScheme(themeState.colorSchemeId, themeState.customPalette, isDark)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = CodeForgeTypography,
        content = content
    )
}
