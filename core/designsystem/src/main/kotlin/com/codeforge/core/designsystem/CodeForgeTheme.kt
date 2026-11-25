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
import com.codeforge.core.domain.extension.GrammarContribution
import com.codeforge.core.domain.extension.Resolved

/** Ob dynamische Farben (Material You) verfügbar sind (Android 12 oder neuer). */
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

/**
 * Findet das in [ThemeConfig.getColorSchemeId] gewählte Plugin-Farbschema (Schlüssel `ext:<plugin>/<theme>`).
 *
 * Bringt dasselbe Plugin eine Variante in der anderen Helligkeit mit, folgt die Auswahl dem Modus Hell/Dunkel/System:
 * „Monokai“ ist dann bei hellem System hell und bei dunklem dunkel. Ohne passende Variante gilt das gewählte Schema.
 */
fun ThemeConfig.findExtensionTheme(available: List<ExtensionColorTheme>, systemDark: Boolean): ExtensionColorTheme? {
    if (useDynamicColor && supportsDynamicColor()) return null
    if (!ExtensionColorTheme.isExtensionKey(colorSchemeId)) return null
    val chosen = available.firstOrNull { it.key == colorSchemeId } ?: return null
    return pairedVariant(chosen, available, resolveIsDark(systemDark))
}

/** Variante von [chosen] in der gewünschten Helligkeit aus demselben Plugin, sonst [chosen]. */
fun pairedVariant(chosen: ExtensionColorTheme, available: List<ExtensionColorTheme>, wantDark: Boolean): ExtensionColorTheme {
    if (chosen.isDark == wantDark) return chosen
    val siblings = available.filter { it.pluginId == chosen.pluginId && it.isDark == wantDark }
    // Bei mehreren Familien in einem Plugin die mit dem ähnlichsten Namen (gemeinsamer Anfang) nehmen.
    return siblings.maxByOrNull { it.themeId.commonPrefixWith(chosen.themeId).length } ?: chosen
}

private val VARIANT_SUFFIX = Regex("""\s*[(\[]?\b(dark|light|dunkel|hell)\b[)\]]?\s*$""", RegexOption.IGNORE_CASE)

/** Basisname einer Theme-Familie ohne Hell/Dunkel-Zusatz („Monokai Dark“ → „Monokai“). */
fun ExtensionColorTheme.familyLabel(): String = label.replace(VARIANT_SUFFIX, "").ifBlank { label }

/**
 * App-Theme: wählt Farbschema (Plugin, dynamisch oder CodeForge-Standard), Typografie, Editor-Theme und Icon-Theme.
 *
 * @param themeState Theme-Einstellungen.
 * @param extensionTheme Aktives Plugin-Farbschema.
 * @param extensionThemes Alle Plugin-Farbschemata (deren Editor-Themes sind im Editor wählbar).
 * @param iconTheme Aktives Plugin-Icon-Theme.
 * @param grammars Aktive Grammatiken.
 * @param content Inhalt.
 */
@Composable
fun CodeForgeTheme(
    themeState: ThemeConfig,
    extensionTheme: ExtensionColorTheme? = null,
    extensionThemes: List<ExtensionColorTheme> = emptyList(),
    iconTheme: ExtensionIconTheme? = null,
    grammars: List<Resolved<GrammarContribution>> = emptyList(),
    content: @Composable () -> Unit
) {
    val isDark = themeState.resolveIsDark(isSystemInDarkTheme(), extensionTheme)

    val colorScheme = when {
        extensionTheme != null -> remember(extensionTheme) { extensionTheme.toColorScheme() }
        themeState.useDynamicColor && supportsDynamicColor() ->
            if (isDark) dynamicDarkColorScheme(LocalContext.current)
            else dynamicLightColorScheme(LocalContext.current)
        else -> codeForgeColorScheme(isDark)
    }

    val editorOverride = remember(extensionTheme) {
        extensionTheme?.textMateThemePath?.let { EditorThemeOverride(extensionTheme.key, it, extensionTheme.isDark) }
    }
    val editorThemes = remember(extensionThemes) {
        extensionThemes.mapNotNull { t -> t.textMateThemePath?.let { EditorThemeOverride(t.key, it, t.isDark) } }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = CodeForgeTypography
    ) {
        CompositionLocalProvider(
            LocalEditorThemeOverride provides editorOverride,
            LocalEditorThemes provides editorThemes,
            LocalIconTheme provides iconTheme,
            LocalActiveGrammars provides grammars,
            content = content
        )
    }
}
