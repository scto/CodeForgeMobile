// Modul: :core:designsystem
package com.codeforge.core.designsystem

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.codeforge.core.datastore.proto.ThemeConfig
import com.codeforge.core.datastore.proto.ThemeMode

fun supportsDynamicColor(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/** Löst den Dunkelmodus der App-Einstellung auf; [systemDark] = Systemzustand bei ThemeMode.SYSTEM. */
fun ThemeConfig.resolveIsDark(systemDark: Boolean): Boolean = when (mode) {
    ThemeMode.DARK -> true
    ThemeMode.LIGHT -> false
    else -> systemDark
}

/** Material 3 Expressive: expressive Motion-Physik + Expressive-Komponenten (z.B. LoadingIndicator). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CodeForgeTheme(
    themeState: ThemeConfig,
    content: @Composable () -> Unit
) {
    val isDark = themeState.resolveIsDark(isSystemInDarkTheme())

    val colorScheme = when {
        themeState.useDynamicColor && supportsDynamicColor() ->
            if (isDark) dynamicDarkColorScheme(LocalContext.current)
            else dynamicLightColorScheme(LocalContext.current)
        else -> resolveCustomScheme(themeState.colorSchemeId, themeState.customPalette, isDark)
    }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        typography = CodeForgeTypography,
        content = content
    )
}
