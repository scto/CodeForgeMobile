package com.codeforge.core.designsystem

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.codeforge.core.datastore.proto.ThemeConfig
import com.codeforge.core.datastore.proto.ThemeMode

@Composable
fun CodeForgeTheme(
    themeState: ThemeConfig? = null,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    val isDark = when (themeState?.mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        else -> darkTheme
    }

    val useDynamic = themeState?.useDynamicColor == true && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val useAmoled = themeState?.useAmoled == true && isDark

    val colorScheme = when {
        useDynamic -> {
            val dynamicScheme = if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            if (useAmoled) {
                dynamicScheme.copy(
                    background = Color.Black,
                    surface = Color.Black,
                    surfaceVariant = Color(0xFF121212)
                )
            } else {
                dynamicScheme
            }
        }
        isDark -> {
            if (useAmoled) {
                darkColorScheme(
                    primary = Color(0xFF6366F1),
                    onPrimary = Color.White,
                    secondary = Color(0xFF06B6D4),
                    onSecondary = Color.Black,
                    tertiary = Color(0xFF10B981),
                    onTertiary = Color.Black,
                    background = Color.Black,
                    onBackground = Color(0xFFF1F5F9),
                    surface = Color.Black,
                    onSurface = Color(0xFFF1F5F9),
                    surfaceVariant = Color(0xFF121212),
                    onSurfaceVariant = Color(0xFF94A3B8),
                    outline = Color(0xFF262626),
                    outlineVariant = Color(0xFF171717)
                )
            } else {
                darkColorScheme(
                    primary = Color(0xFF6366F1),
                    onPrimary = Color.White,
                    secondary = Color(0xFF06B6D4),
                    onSecondary = Color.Black,
                    tertiary = Color(0xFF10B981),
                    onTertiary = Color.Black,
                    background = Color(0xFF111318),
                    onBackground = Color(0xFFE2E8F0),
                    surface = Color(0xFF1E2330),
                    onSurface = Color(0xFFF1F5F9),
                    surfaceVariant = Color(0xFF282F42),
                    onSurfaceVariant = Color(0xFF94A3B8),
                    outline = Color(0xFF334155),
                    outlineVariant = Color(0xFF1E293B)
                )
            }
        }
        else -> {
            lightColorScheme(
                primary = Color(0xFF4F46E5),
                onPrimary = Color.White,
                secondary = Color(0xFF0891B2),
                onSecondary = Color.White,
                tertiary = Color(0xFF059669),
                onTertiary = Color.White,
                background = Color(0xFFF8FAFC),
                onBackground = Color(0xFF0F172A),
                surface = Color(0xFFFFFFFF),
                onSurface = Color(0xFF1E293B),
                surfaceVariant = Color(0xFFF1F5F9),
                onSurfaceVariant = Color(0xFF64748B),
                outline = Color(0xFFCBD5E1),
                outlineVariant = Color(0xFFE2E8F0)
            )
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
            content = content
        )
    }
}
