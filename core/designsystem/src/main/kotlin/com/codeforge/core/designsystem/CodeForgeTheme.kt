// Modul: :core:designsystem
package com.codeforge.core.designsystem

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.codeforge.core.datastore.proto.ThemeConfig
import com.codeforge.core.datastore.proto.ThemeMode
import com.codeforge.core.domain.extension.ExtensionColorTheme
import com.codeforge.core.domain.extension.ExtensionIconTheme

fun supportsDynamicColor(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/** Löst den Dunkelmodus der App-Einstellung auf; [systemDark] = Systemzustand bei ThemeMode.SYSTEM. */
fun ThemeConfig.resolveIsDark(systemDark: Boolean): Boolean = when (mode) {
    ThemeMode.DARK -> true
    ThemeMode.LIGHT -> false
    else -> systemDark
}

/** Wie [resolveIsDark], aber ein aktives Plugin-Farbschema ([extensionTheme]) bestimmt die Helligkeit selbst. */
fun ThemeConfig.resolveIsDark(systemDark: Boolean, extensionTheme: ExtensionColorTheme?): Boolean =
    extensionTheme?.isDark ?: resolveIsDark(systemDark)

/** Findet das in [ThemeConfig.getIconThemeId] gewählte Plugin-Icon-Theme. */
fun ThemeConfig.findIconTheme(available: List<ExtensionIconTheme>): ExtensionIconTheme? =
    if (ExtensionColorTheme.isExtensionKey(iconThemeId)) available.firstOrNull { it.key == iconThemeId } else null

/** Findet das in [ThemeConfig.getColorSchemeId] gewählte Plugin-Farbschema (Schlüssel `ext:<plugin>/<theme>`). */
fun ThemeConfig.findExtensionTheme(available: List<ExtensionColorTheme>): ExtensionColorTheme? =
    if (!(useDynamicColor && supportsDynamicColor()) && ExtensionColorTheme.isExtensionKey(colorSchemeId)) {
        available.firstOrNull { it.key == colorSchemeId }
    } else {
        null
    }

@Composable
fun CodeForgeTheme(
    themeState: ThemeConfig,
    extensionTheme: ExtensionColorTheme? = null,
    iconTheme: ExtensionIconTheme? = null,
    content: @Composable () -> Unit
) {
    val isDark = themeState.resolveIsDark(isSystemInDarkTheme(), extensionTheme)

    val colorScheme = when {
        extensionTheme != null -> remember(extensionTheme) { extensionTheme.toColorScheme() }
        themeState.useDynamicColor && supportsDynamicColor() ->
            if (isDark) dynamicDarkColorScheme(LocalContext.current)
            else dynamicLightColorScheme(LocalContext.current)
        else -> resolveCustomScheme(themeState.colorSchemeId, themeState.customPalette, isDark)
    }

    val editorOverride = remember(extensionTheme) {
        extensionTheme?.textMateThemePath?.let { EditorThemeOverride(extensionTheme.key, it, extensionTheme.isDark) }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = CodeForgeTypography
    ) {
        CompositionLocalProvider(
            LocalEditorThemeOverride provides editorOverride,
            LocalIconTheme provides iconTheme,
            content = content
        )
    }
}
