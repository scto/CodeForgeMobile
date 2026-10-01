/**
 * Modul: :feature:editor
 * SoraLanguageProvider for CodeForge Mobile
 * Dynamically loads textmate/languages.json, textmate/keywords.json, themes, Monarch, and TreeSitter.
 */
package com.codeforge.feature.editor

import android.content.Context
import android.content.res.Configuration

import com.codeforge.core.common.logging.AppLogger
import com.codeforge.feature.editor.lang.JavaLanguageSpec
import com.codeforge.feature.editor.lang.TsLanguageJava

import io.github.dingyi222666.monarch.languages.JavaLanguage as MonarchJavaLang
import io.github.dingyi222666.monarch.languages.KotlinLanguage as MonarchKotlinLang
import io.github.dingyi222666.monarch.languages.PythonLanguage as MonarchPythonLang
import io.github.dingyi222666.monarch.languages.TypescriptLanguage as MonarchTsLang
import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.langs.java.JavaLanguage as BuiltinJavaLanguage
import io.github.rosemoe.sora.langs.monarch.MonarchColorScheme
import io.github.rosemoe.sora.langs.monarch.MonarchLanguage
import io.github.rosemoe.sora.langs.monarch.registry.MonarchGrammarRegistry
import io.github.rosemoe.sora.langs.monarch.registry.dsl.monarchLanguages
import io.github.rosemoe.sora.langs.monarch.registry.model.ThemeSource
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import com.codeforge.feature.editor.theme.DraculaTheme
import com.codeforge.feature.editor.theme.MonokaiTheme
import com.codeforge.feature.editor.theme.NordDarkTheme
import com.codeforge.feature.editor.theme.MaterialPalenightTheme
import com.codeforge.feature.editor.theme.TokyoNightTheme
import com.codeforge.feature.editor.theme.GitHubLightTheme
import com.codeforge.feature.editor.theme.SolarizedLightTheme
import com.codeforge.feature.editor.theme.OneLightTheme
import com.codeforge.feature.editor.theme.RosePineDawnTheme
import com.codeforge.feature.editor.theme.MaterialLightTheme
import com.codeforge.feature.editor.theme.CodeForge2DarkTheme
import com.codeforge.feature.editor.theme.CodeForge2LightTheme

import org.eclipse.tm4e.core.registry.IThemeSource
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SoraLanguageProvider @Inject constructor(
    private val context: Context
) {
    private var isTextMateInitialized = false
    private var isMonarchInitialized = false

    private var scopeToKeywords: Map<String, Array<String>> = emptyMap()
    private var extensionToScopeMap: Map<String, String> = emptyMap()

    fun ensureTextMateInitialized() {
        if (isTextMateInitialized) return
        isTextMateInitialized = true
        runCatching {
            FileProviderRegistry.getInstance().addFileProvider(
                AssetsFileResolver(context.assets)
            )

            // 1. Load languages.json via GrammarRegistry
            runCatching { GrammarRegistry.getInstance().loadGrammars("textmate/languages.json") }

            // 2. Parse languages.json manually for fileExtension -> scopeName mapping
            runCatching {
                val jsonStr = context.assets.open("textmate/languages.json").reader().readText()
                val jsonObj = JSONObject(jsonStr)
                val langArray = jsonObj.optJSONArray("languages") ?: JSONArray()
                val extMap = mutableMapOf<String, String>()
                for (i in 0 until langArray.length()) {
                    val item = langArray.getJSONObject(i)
                    val scope = item.optString("scopeName")
                    val exts = item.optJSONArray("fileExtensions")
                    if (scope.isNotBlank() && exts != null) {
                        for (j in 0 until exts.length()) {
                            val ext = exts.getString(j).lowercase().removePrefix(".")
                            if (ext.isNotBlank()) {
                                extMap[ext] = scope
                            }
                        }
                    }
                }
                extensionToScopeMap = extMap
            }

            // 3. Parse keywords.json for scopeName -> keywords array mapping
            runCatching {
                val jsonStr = context.assets.open("textmate/keywords.json").reader().readText()
                val jsonObj = JSONObject(jsonStr)
                val kwMap = mutableMapOf<String, Array<String>>()
                val keys = jsonObj.keys()
                while (keys.hasNext()) {
                    val scope = keys.next()
                    val arr = jsonObj.getJSONArray(scope)
                    val list = ArrayList<String>()
                    for (i in 0 until arr.length()) {
                        list.add(arr.getString(i))
                    }
                    kwMap[scope] = list.toTypedArray()
                }
                scopeToKeywords = kwMap
            }

            // 4. Load all theme json files (codeforge.json & all textmate json themes)
            val themeRegistry = ThemeRegistry.getInstance()
            listOf(
                "codeforge", "darcula", "quietlight", "ayu_dark", "ayu_light",
                "ayu_mirage", "eclipse_dark", "eclipse_light", "onedark"
            ).forEach { name ->
                val fileName = "$name.json"
                val assetPath = "textmate/$fileName"
                runCatching {
                    context.assets.open(assetPath).use { inputStream ->
                        val themeSource = IThemeSource.fromInputStream(
                            inputStream,
                            fileName,
                            null
                        )
                        val themeModel = ThemeModel(themeSource, name).apply {
                            isDark = !name.contains("light")
                        }
                        themeRegistry.loadTheme(themeModel)
                    }
                }
            }
            runCatching { themeRegistry.setTheme("codeforge") }
            AppLogger.i("SoraLanguageProvider", "TextMate languages, keywords, and themes loaded successfully from assets.")
        }.onFailure { t ->
            AppLogger.e("SoraLanguageProvider", "Failed to initialize TextMate grammars and keywords", t)
        }
    }

    fun ensureMonarchInitialized() {
        if (isMonarchInitialized) return
        isMonarchInitialized = true
        runCatching {
            io.github.rosemoe.sora.langs.monarch.registry.FileProviderRegistry.addProvider(
                io.github.rosemoe.sora.langs.monarch.registry.provider.AssetsFileResolver(context.assets)
            )
            MonarchGrammarRegistry.INSTANCE.loadGrammars(
                monarchLanguages {
                    language("java") {
                        monarchLanguage = MonarchJavaLang
                        defaultScopeName()
                    }
                    language("kotlin") {
                        monarchLanguage = MonarchKotlinLang
                        defaultScopeName()
                    }
                    language("python") {
                        monarchLanguage = MonarchPythonLang
                        defaultScopeName()
                    }
                    language("typescript") {
                        monarchLanguage = MonarchTsLang
                        defaultScopeName()
                    }
                }
            )
            AppLogger.i("SoraLanguageProvider", "Monarch languages initialized successfully.")
        }.onFailure { t ->
            AppLogger.e("SoraLanguageProvider", "Failed to initialize Monarch grammars", t)
        }
    }

    /**
     * DYNAMISCHE SPRACHVERWENDUNG JE NACH DATEITYP
     * Hier entscheidet sich, welcher Parser (TreeSitter, Monarch oder TextMate) verwendet wird.
     */
    fun getLanguage(file: File, useTreeSitter: Boolean = false): Language {
        ensureTextMateInitialized()
        val ext = file.extension.lowercase()

        // 1. TreeSitter Mode (Aktuell für Java optimiert)
        if (useTreeSitter && ext == "java") {
            val tsJava = getTreeSitterJavaLanguage()
            if (tsJava !is EmptyLanguage) {
                return tsJava
            }
        }

        // 2. Monarch Parser (Spezifisch für Kotlin, Java, Python, TypeScript)
        // Monarch ist sehr schnell für mobile Geräte.
        val monarchScope = when (ext) {
            "kt", "kts" -> "kotlin"
            "java" -> "java"
            "py" -> "python"
            "ts", "js", "tsx", "jsx" -> "typescript"
            else -> null
        }
        
        if (monarchScope != null) {
            val monarchLang = getMonarchLanguage(monarchScope)
            if (monarchLang !is EmptyLanguage) {
                return monarchLang
            }
        }

        // 3. TEXTMATE FALLBACK (Hier greifen C, CPP, JSON, XML, etc.)
        // Zuerst wird geprüft, ob wir den Scope dynamisch aus `languages.json` extrahiert haben.
        // Wenn nicht, schauen wir in die statische `extensions` Map weiter unten.
        val scope = extensionToScopeMap[ext] ?: extensions[ext] ?: "source.$ext"

        val textMateLang = runCatching {
            TextMateLanguage.create(scope, true)
        }.getOrNull()

        if (textMateLang != null && textMateLang !is EmptyLanguage) {
            AppLogger.d("SoraLanguageProvider", "Loaded TextMate language for scope: $scope")
            return textMateLang
        }

        // 4. Letzter Fallback
        if (ext == "java") return BuiltinJavaLanguage()

        return EmptyLanguage()
    }

    fun getLanguageByScope(scopeName: String): Language {
        ensureTextMateInitialized()
        val textMateLang = runCatching {
            TextMateLanguage.create(scopeName, true)
        }.getOrNull()
        if (textMateLang != null && textMateLang !is EmptyLanguage) return textMateLang

        val monarchLang = getMonarchLanguage(scopeName)
        if (monarchLang !is EmptyLanguage) return monarchLang

        return EmptyLanguage()
    }

    fun getMonarchLanguage(scopeName: String): Language {
        ensureMonarchInitialized()
        
        // TextMate Scopes auf Monarch IDs mappen
        val monarchId = when (scopeName.lowercase()) {
            "source.kotlin", "kt", "kts" -> "kotlin"
            "source.java", "java" -> "java"
            "source.python", "py" -> "python"
            "source.typescript", "source.js", "ts", "js" -> "typescript"
            else -> scopeName
        }

        return runCatching {
            MonarchLanguage.create(monarchId, true)
        }.getOrElse { EmptyLanguage() }
    }

    fun getTreeSitterJavaLanguage(): Language {
        return runCatching {
            TsLanguageJava(
                JavaLanguageSpec(
                    highlightScmSource = context.assets.open("tree-sitter-queries/java/highlights.scm").reader().readText(),
                    codeBlocksScmSource = context.assets.open("tree-sitter-queries/java/blocks.scm").reader().readText(),
                    bracketsScmSource = context.assets.open("tree-sitter-queries/java/brackets.scm").reader().readText(),
                    localsScmSource = context.assets.open("tree-sitter-queries/java/locals.scm").reader().readText()
                )
            )
        }.getOrElse { EmptyLanguage() }
    }

    fun getBuiltinJavaLanguage(): Language = BuiltinJavaLanguage()

    fun applyTheme(editor: CodeEditor, themeName: String) {
        applySchemeByName(editor, themeName)
    }

    fun applySchemeByName(editor: CodeEditor, schemeName: String) {
        val cleanName = schemeName.trim().ifBlank { if (isNightMode()) "codeforge" else "quietlight" }
        ensureTextMateInitialized()
        when {
            cleanName.equals("Dracula", ignoreCase = true) -> editor.colorScheme = DraculaTheme()
            cleanName.equals("Monokai", ignoreCase = true) -> editor.colorScheme = MonokaiTheme()
            cleanName.equals("Nord Dark", ignoreCase = true) || cleanName.equals("NordDark", ignoreCase = true) -> editor.colorScheme = NordDarkTheme()
            cleanName.equals("Material Palenight", ignoreCase = true) || cleanName.equals("MaterialPalenight", ignoreCase = true) -> editor.colorScheme = MaterialPalenightTheme()
            cleanName.equals("Tokyo Night", ignoreCase = true) || cleanName.equals("TokyoNight", ignoreCase = true) -> editor.colorScheme = TokyoNightTheme()
            cleanName.equals("GitHub Light", ignoreCase = true) || cleanName.equals("GitHubLight", ignoreCase = true) -> editor.colorScheme = GitHubLightTheme()
            cleanName.equals("Solarized Light", ignoreCase = true) || cleanName.equals("SolarizedLight", ignoreCase = true) -> editor.colorScheme = SolarizedLightTheme()
            cleanName.equals("One Light", ignoreCase = true) || cleanName.equals("OneLight", ignoreCase = true) -> editor.colorScheme = OneLightTheme()
            cleanName.equals("Rose Pine Dawn", ignoreCase = true) || cleanName.equals("RosePineDawn", ignoreCase = true) -> editor.colorScheme = RosePineDawnTheme()
            cleanName.equals("Material Light", ignoreCase = true) || cleanName.equals("MaterialLight", ignoreCase = true) -> editor.colorScheme = MaterialLightTheme()
            cleanName.equals("CodeForge 2 Dark", ignoreCase = true) || cleanName.equals("CodeForge2Dark", ignoreCase = true) -> editor.colorScheme = CodeForge2DarkTheme()
            cleanName.equals("CodeForge 2 Light", ignoreCase = true) || cleanName.equals("CodeForge2Light", ignoreCase = true) -> editor.colorScheme = CodeForge2LightTheme()
            else -> {
                val tmKey = when (cleanName.lowercase()) {
                    "codeforge", "codeforge dark" -> "codeforge"
                    "quietlight for tm", "quietlight", "quiet_light" -> "quietlight"
                    "ayu dark for tm", "ayu-dark", "ayu_dark" -> "ayu_dark"
                    "ayu light for tm", "ayu-light", "ayu_light" -> "ayu_light"
                    "ayu mirage for tm", "ayu-mirage", "ayu_mirage" -> "ayu_mirage"
                    "eclipse dark", "eclipse_dark" -> "eclipse_dark"
                    "eclipse light", "eclipse_light" -> "eclipse_light"
                    "onedark", "one dark" -> "onedark"
                    "solarized dark for tm", "solarized_dark", "solarized-dark" -> "solarized_dark"
                    else -> cleanName
                }
                val applied = runCatching {
                    ThemeRegistry.getInstance().setTheme(tmKey)
                    editor.colorScheme = TextMateColorScheme.create(ThemeRegistry.getInstance())
                    true
                }.getOrDefault(false)

                if (!applied) {
                    runCatching {
                        ThemeRegistry.getInstance().setTheme("codeforge")
                        editor.colorScheme = TextMateColorScheme.create(ThemeRegistry.getInstance())
                    }.onFailure {
                        editor.colorScheme = DraculaTheme()
                    }
                }
            }
        }
        editor.invalidate()
    }

    fun isNightMode(): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    companion object {
        // HIER SIND C, CPP, XML, JSON etc. explizit für den TextMate-Fallback definiert!
        val extensions: Map<String, String> = mapOf(
            "kt" to "source.kotlin",
            "kts" to "source.kotlin",
            "java" to "source.java",
            "xml" to "text.xml",       // Fallback für XML
            "json" to "source.json",   // Fallback für JSON
            "py" to "source.python",
            "js" to "source.js",
            "jsx" to "source.js.jsx",
            "ts" to "source.ts",
            "tsx" to "source.tsx",
            "html" to "text.html.basic",
            "htm" to "text.html.basic",
            "c" to "source.c",         // Fallback für C
            "h" to "source.c",
            "cpp" to "source.cpp",     // Fallback für C++
            "cc" to "source.cpp",
            "cxx" to "source.cpp",
            "hpp" to "source.cpp",
            "hh" to "source.cpp",
            "sh" to "source.shell",
            "bash" to "source.shell",
            "zsh" to "source.shell",
            "md" to "text.html.markdown",
            "markdown" to "text.html.markdown",
            "yaml" to "source.yaml",
            "yml" to "source.yaml",
            "lua" to "source.lua",
            "dart" to "source.dart",
            "properties" to "source.properties",
            "aidl" to "source.aidl"
        )
    }
}