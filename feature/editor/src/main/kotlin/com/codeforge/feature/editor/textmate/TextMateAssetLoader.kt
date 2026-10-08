/**
 * Modul: :feature:editor
 * @author Thomas Schmid
 *
 * Initialisiert den TextMate-Sprachstack von sora-editor (language-textmate) aus den
 * App-Assets. sora-editor benötigt dafür einen [FileProviderRegistry]-Resolver, der
 * `assets://`-Pfade auflösen kann (Grammar-JSONs, .tmTheme/.json-Themes, language-
 * configuration.json für Bracket-Pairs/Comments).
 *
 * Asset-Layout (unter src/main/assets/textmate/):
 *   languages.json         — Registry-Manifest: scopeName -> Grammar-/Config-Pfade
 *   grammars/<name>.tmLanguage.json
 *   themes/<name>.json        — TextMate-/VS-Code-kompatible Themes (z.B. dark_plus.json)
 *
 * Die konkreten Grammar-/Theme-JSONs selbst (TextMate-Grammatiken für Kotlin/Java/XML/
 * Gradle-KTS) sind groß (mehrere tausend Zeilen Regex-Patterns) und werden NICHT inline
 * generiert, sondern sind 1:1 aus den offiziellen TextMate-/VS-Code-Extension-Repos zu
 * übernehmen (lizenzkonform, analog zur Vendoring-Strategie von com.termux.terminal.* in
 * :libs:terminal-engine). Siehe ASSETS.md für die genaue Bezugsquelle je Sprache.
 */
package com.codeforge.feature.editor.textmate

import android.content.Context
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import io.github.rosemoe.sora.textmate.core.theme.IRawTheme
import org.eclipse.tm4e.core.registry.IThemeSource
import java.util.concurrent.atomic.AtomicBoolean

object TextMateAssetLoader {

    private val initialized = AtomicBoolean(false)

    const val DEFAULT_DARK_THEME = "dark_plus"
    const val DEFAULT_LIGHT_THEME = "light_plus"

    /**
     * Einmalige, prozessweite Initialisierung. Muss vor der ersten Erzeugung einer
     * [io.github.rosemoe.sora.langs.textmate.TextMateLanguage]-Instanz aufgerufen werden
     * (idealerweise in Application.onCreate oder beim ersten Compose der [SoraCodeEditor]).
     */
    @Synchronized
    fun ensureInitialized(context: Context) {
        if (!initialized.compareAndSet(false, true)) return

        io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry.getInstance()
            .addFileProvider(AssetsFileResolver(context.applicationContext.assets))

        runCatching { registerGrammars() }
            .onFailure {
                android.util.Log.w(
                    "TextMateAssetLoader",
                    Res.string(R.string.editor_grammar_registrierung_fehlgeschlagen_a),
                    it
                )
            }

        runCatching { registerThemes() }
            .onFailure {
                android.util.Log.w("TextMateAssetLoader", "Theme-Registrierung fehlgeschlagen.", it)
            }
    }

    private fun registerGrammars() {
        GrammarRegistry.getInstance().loadGrammars("textmate/languages.json")
    }

    private fun registerThemes() {
        val themeRegistry = ThemeRegistry.getInstance()
        listOf(DEFAULT_DARK_THEME, DEFAULT_LIGHT_THEME).forEach { themeName ->
            val path = "textmate/themes/$themeName.json"
            val source = IThemeSource.fromInputStream(
                FileProviderRegistryHelper.open(path),
                path,
                null
            )
            themeRegistry.loadTheme(ThemeModel(source, themeName).apply {
                isDark = themeName == DEFAULT_DARK_THEME
            })
        }
        themeRegistry.setTheme(DEFAULT_DARK_THEME)
    }

    fun setActiveTheme(themeName: String) {
        if (themeName.isBlank()) return
        runCatching { ThemeRegistry.getInstance().setTheme(themeName) }
    }

    /** Liefert `true`, sobald [ensureInitialized] erfolgreich einmal durchlaufen ist. */
    fun isInitialized(): Boolean = initialized.get()
}

/**
 * Kleiner Hilfs-Wrapper, da [AssetsFileResolver] selbst keinen direkten Stream-Zugriff
 * über einen statischen Pfad anbietet — die Registry löst `assets://`-URIs intern über
 * [io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry] auf.
 */
private object FileProviderRegistryHelper {
    fun open(path: String) =
        io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry.getInstance()
            .tryGetInputStream(path)
            ?: error(Res.string(R.string.editor_textmate_asset_nicht_gefunden_siehe, path))
}
