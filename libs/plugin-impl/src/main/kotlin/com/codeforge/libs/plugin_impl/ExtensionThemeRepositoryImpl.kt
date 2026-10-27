/**
 * Modul: :libs:plugin-impl
 * @author Thomas Schmid
 */
package com.codeforge.libs.plugin_impl

import com.codeforge.core.domain.extension.ActiveContributions
import com.codeforge.core.domain.extension.ColorThemeFileParser
import com.codeforge.core.domain.extension.ContributionRepository
import com.codeforge.core.domain.extension.ExtensionColorTheme
import com.codeforge.core.domain.extension.ExtensionIconTheme
import com.codeforge.core.domain.extension.ExtensionThemeRepository
import com.codeforge.core.domain.extension.IconThemeFileParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Liest die Farbschema-Dateien der aktivierten Plugins; defekte Dateien werden übersprungen, nie ein Absturz. */
internal object ExtensionThemeLoader {
    fun load(contributions: ActiveContributions): List<ExtensionColorTheme> =
        contributions.colorThemes.mapNotNull { resolved ->
            val item = resolved.item
            val pluginDir = File(resolved.pluginDir)
            val file = File(pluginDir, item.path)
            if (!file.isFile || !isInside(pluginDir, file)) return@mapNotNull null
            val parsed = ColorThemeFileParser.parse(file.readText()).getOrNull() ?: return@mapNotNull null
            val tm = parsed.textMateTheme?.let { File(pluginDir, it) }
                ?.takeIf { it.isFile && isInside(pluginDir, it) }
            ExtensionColorTheme(
                pluginId = resolved.pluginId,
                themeId = item.id,
                label = item.label,
                isDark = item.isDark,
                colors = parsed.colors,
                textMateThemePath = tm?.absolutePath
            )
        }

    /** Icon-Themes: Theme-Datei lesen, validieren und prüfen, dass alle referenzierten Bilder im Plugin liegen und existieren. */
    fun loadIcons(contributions: ActiveContributions): List<ExtensionIconTheme> =
        contributions.iconThemes.mapNotNull { resolved ->
            val item = resolved.item
            val pluginDir = File(resolved.pluginDir)
            val themeFile = File(pluginDir, item.path)
            if (!themeFile.isFile || !isInside(pluginDir, themeFile)) return@mapNotNull null
            val parsed = IconThemeFileParser.parse(themeFile.readText()).getOrNull() ?: return@mapNotNull null
            val baseDir = themeFile.parentFile ?: return@mapNotNull null
            // Fehlende Bilder machen das Theme nicht ungültig, werden aber nie gezeichnet (Rückfall auf Standardicon).
            if (parsed.allPaths().any { !isInside(pluginDir, File(baseDir, it)) }) return@mapNotNull null
            ExtensionIconTheme(resolved.pluginId, item.id, item.label, baseDir.absolutePath, parsed)
        }

    private fun isInside(dir: File, file: File): Boolean =
        file.canonicalPath.startsWith(dir.canonicalPath + File.separator)
}

@Singleton
class ExtensionThemeRepositoryImpl @Inject constructor(
    contributionRepository: ContributionRepository
) : ExtensionThemeRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val themes: StateFlow<List<ExtensionColorTheme>> = contributionRepository.contributions
        .map { ExtensionThemeLoader.load(it) }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    override val iconThemes: StateFlow<List<ExtensionIconTheme>> = contributionRepository.contributions
        .map { ExtensionThemeLoader.loadIcons(it) }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())
}
