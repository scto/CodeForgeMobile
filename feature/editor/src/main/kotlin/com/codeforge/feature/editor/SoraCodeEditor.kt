/**
 * Modul: :feature:editor
 * @author Thomas Schmid
 *
 * Vollständige sora-editor-Integration. Deckt den im Projekt geforderten Funktionsumfang ab:
 *
 *  - Incremental Syntax-Highlight          -> TextMate/TreeSitter-[Language] + EditorColorScheme
 *  - Auto-Completion (mit Code-Snippets)   -> [LspAwareLanguage] (LSP + dokumentinterne Symbole)
 *  - Code Block Indicators                 -> isBlockLineEnabled
 *  - Unlimited Undo-Stack + Fast Search    -> editors Content-Klasse (intrinsisch) + EditorSearcher
 *  - Word Wrap Display Mode                -> isWordwrap
 *  - Display Non-Printable Characters      -> nonPrintablePaintingFlags
 *  - Diagnostic Markers + Tooltip Window   -> DiagnosticsContainer (siehe LspDiagnosticsBridge)
 *  - Text Magnifier                        -> getComponent(Magnifier::class.java).isEnabled
 *  - Punctuation Pair Matching/Highlight   -> isHighlightBracketPair
 *  - Sticky Scroll                         -> getComponent(EditorStickyScroll::class.java)
 *  - Language Support (TextMate+TreeSitter)-> [EditorLanguageFactory]
 *
 * AssetLoader (TextMate-Grammars/Themes) via [TextMateAssetLoader]; FileProvider für
 * Teilen/Öffnen via [EditorFileProvider] (siehe dort, separat von der Editor-View selbst
 * genutzt, z.B. aus einer Toolbar-Aktion in [EditorScreen]).
 */
package com.codeforge.feature.editor

import android.graphics.Typeface
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.codeforge.feature.editor.lsp.LspAwareLanguage
import com.codeforge.feature.editor.lsp.LspCompletionProvider
import com.codeforge.feature.editor.lsp.LspDiagnosticsBridge
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.luminance
import com.codeforge.feature.editor.textmate.TextMateAssetLoader
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import com.codeforge.core.domain.model.Breakpoint
import com.codeforge.core.domain.model.LspDiagnostic
import com.codeforge.feature.editor.overlay.BreakpointOverlayLayer
import com.codeforge.feature.editor.overlay.ColorLiteralScanner
import com.codeforge.feature.editor.overlay.EditorOverlayLayer
import com.codeforge.feature.editor.overlay.TestTarget
import com.codeforge.feature.editor.overlay.TestTargetScanner
import com.codeforge.feature.editor.overlay.buildLineOverlays
import com.codeforge.libs.dependency_updater_api.DependencyUpdate
import com.codeforge.libs.dependency_updater_api.FileUpdateAnnotation
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.ScrollEvent
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticsContainer
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.subscribeEvent
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion
import io.github.rosemoe.sora.widget.component.Magnifier
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme

/**
 * Konfiguration, die aus [EditorConfig][com.codeforge.core.datastore.proto.EditorConfig]
 * (per [EditorViewModel]) gespeist wird — entkoppelt [SoraCodeEditor] von der Proto-Klasse,
 * damit :feature:editor nicht transitiv auf das generierte Proto-Schema angewiesen ist.
 */
data class EditorDisplaySettings(
    val tabSize: Int = 4,
    val useTreeSitter: Boolean = true,
    val textmateTheme: String = TextMateAssetLoader.DEFAULT_DARK_THEME,
    val fontSizeSp: Float = 14f,
    val fontFamilyAssetPath: String = "",
    val wordWrap: Boolean = false,
    val showNonPrintableChars: Boolean = false,
    val stickyScrollEnabled: Boolean = true,
    val magnifierEnabled: Boolean = true,
    val symbolPairAutocompleteEnabled: Boolean = true,
    val minimapEnabled: Boolean = true,
    val rainbowBracketsEnabled: Boolean = true,
    val pinchToZoomEnabled: Boolean = true,
    val desktopShortcutsEnabled: Boolean = true,
    val showLineNumbers: Boolean = true,
    val pinLineNumbers: Boolean = false,
    val cursorAnimationEnabled: Boolean = true,
    val lineSpacingPercent: Int = 100,
    val readOnlyDefault: Boolean = false,
    val txtWordWrap: Boolean = false,
    val colorPreviewsEnabled: Boolean = true,
    val disableSoftKeyboardWithHardwareKeyboard: Boolean = false,
    val keyboardSuggestions: Boolean = false
)

@Composable
fun SoraCodeEditor(
    modifier: Modifier = Modifier,
    path: String,
    content: String,
    language: EditorLanguageType,
    settings: EditorDisplaySettings,
    diagnostics: List<LspDiagnostic> = emptyList(),
    completionProvider: LspCompletionProvider? = null,
    updateAnnotations: List<FileUpdateAnnotation> = emptyList(),
    breakpoints: List<Breakpoint> = emptyList(),
    pausedLine: Int? = null,
    onToggleBreakpoint: (Int) -> Unit = {},
    onEditBreakpoint: (Int) -> Unit = {},
    onUpdateChipClick: (DependencyUpdate) -> Unit = {},
    onTestTargetClick: (TestTarget) -> Unit = {},
    onContentChanged: (String) -> Unit,
    onControllerReady: (EditorController) -> Unit = {}
) {
    val context = LocalContext.current
    // "auto"/leer folgt dem effektiven App-Theme (Hell/Dunkel/System aus den Einstellungen).
    val isDarkUi = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val themedSettings = remember(settings, isDarkUi) {
        settings.copy(
            textmateTheme = resolveEditorTheme(settings.textmateTheme, isDarkUi),
            wordWrap = settings.wordWrap || (settings.txtWordWrap && path.endsWith(".txt", ignoreCase = true))
        )
    }
    val controller = remember(path) { EditorController() }
    val diagnosticsContainer = remember(path) { DiagnosticsContainer() }

    // Overlay (Farbkästchen + Update-Chips + Test-Starter): Editor-Referenz und „Tick“ für Scroll/Text-Änderungen
    var overlayEditor by remember { mutableStateOf<CodeEditor?>(null) }
    var overlayTick by remember { mutableIntStateOf(0) }
    val colorMatches = remember(path, content, settings.colorPreviewsEnabled) {
        if (settings.colorPreviewsEnabled) ColorLiteralScanner.scanForPath(path, content) else emptyList()
    }
    val testTargets = remember(path, content) { TestTargetScanner.scan(path, content) }
    val lineOverlays = remember(colorMatches, updateAnnotations, testTargets) {
        buildLineOverlays(colorMatches, updateAnnotations.map { it.line to it.update }, testTargets)
    }

    DisposableEffect(Unit) {
        TextMateAssetLoader.ensureInitialized(context)
        onDispose { }
    }

    androidx.compose.runtime.LaunchedEffect(pausedLine) {
        if (pausedLine != null && pausedLine > 0) {
            controller.jumpToLine(pausedLine - 1)
        }
    }

    Box(modifier = modifier) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            // Muss VOR TextMateLanguage.create laufen: die DisposableEffect oben läuft erst nach
            // dem Erzeugen der View -> Grammatiken waren noch nicht registriert (kein Highlighting).
            TextMateAssetLoader.ensureInitialized(ctx)
            CodeEditor(ctx).apply {
                colorScheme = EditorColorScheme()
                setText(content)

                setEditorLanguage(
                    EditorLanguageFactory.create(
                        context = ctx,
                        path = path,
                        languageType = language,
                        useTreeSitter = settings.useTreeSitter,
                        completionProvider = completionProvider
                    )
                )

                // --- Code Block Indicators & Display Settings ---
                isBlockLineEnabled = true
                applyDisplaySettings(this, themedSettings)

                // --- Auto-Completion-Popup aktiv halten (Snippets kommen aus LspAwareLanguage) ---
                getComponent(EditorAutoCompletion::class.java).isEnabled = true

                // --- Diagnose-UI & Tooltips ---
                runCatching {
                    val tooltipWindow = getComponent(io.github.rosemoe.sora.widget.component.EditorDiagnosticTooltipWindow::class.java)
                    tooltipWindow.isEnabled = true
                }

                this.diagnostics = diagnosticsContainer

                subscribeEvent<ContentChangeEvent> { event, _ ->
                    onContentChanged(event.editor.text.toString())
                    controller.refreshUndoRedo()
                    overlayTick++
                }
                subscribeEvent<ScrollEvent> { _, _ -> overlayTick++ }

                // --- Snippet-Variablen-Resolver ($TM_FILENAME, $PACKAGE, $CLASSNAME, $CURRENT_YEAR, $CLIPBOARD) ---
                runCatching {
                    snippetController.fileVariableResolver = object : io.github.rosemoe.sora.widget.snippet.variable.FileBasedSnippetVariableResolver() {
                        override fun getResolvableNames(): Array<String> = arrayOf(
                            "TM_FILENAME", "TM_FILENAME_BASE", "TM_DIRECTORY", "TM_FILEPATH", "RELATIVE_PATH",
                            "PACKAGE", "CLASSNAME", "CURRENT_YEAR", "CLIPBOARD"
                        )

                        override fun resolve(name: String): String {
                            val file = java.io.File(path)
                            return when (name) {
                                "TM_FILENAME" -> file.name
                                "TM_FILENAME_BASE" -> file.name.substringBeforeLast('.')
                                "TM_DIRECTORY" -> file.parent.orEmpty()
                                "TM_FILEPATH" -> path
                                "RELATIVE_PATH" -> path
                                "CLASSNAME" -> com.codeforge.core.domain.model.SnippetVariableResolver.deriveClassName(path)
                                "PACKAGE" -> com.codeforge.core.domain.model.SnippetVariableResolver.derivePackageName(path)
                                "CURRENT_YEAR" -> java.time.Year.now().value.toString()
                                "CLIPBOARD" -> {
                                    val clip = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                    clip?.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                                }
                                else -> ""
                            }
                        }
                    }

                    snippetController.workspaceVariableResolver = object : io.github.rosemoe.sora.widget.snippet.variable.WorkspaceBasedSnippetVariableResolver() {
                        override fun resolve(name: String): String {
                            val root = java.io.File(path).parent.orEmpty()
                            return when (name) {
                                "WORKSPACE_NAME" -> java.io.File(root).name
                                "WORKSPACE_FOLDER" -> root
                                else -> ""
                            }
                        }
                    }
                }

                // --- Snippet Tabstopp-Navigation (Tab/Shift+Tab, Esc beendet) ---
                subscribeEvent<io.github.rosemoe.sora.event.EditorKeyEvent> { event, _ ->
                    if (snippetController.isInSnippet()) {
                        if (event.keyCode == android.view.KeyEvent.KEYCODE_TAB && event.eventType == io.github.rosemoe.sora.event.EditorKeyEvent.Type.DOWN) {
                            if (event.isShiftPressed) {
                                snippetController.shiftToPreviousTabStop()
                            } else {
                                snippetController.shiftToNextTabStop()
                            }
                            event.intercept()
                            event.result(true)
                        } else if (event.keyCode == android.view.KeyEvent.KEYCODE_ESCAPE && event.eventType == io.github.rosemoe.sora.event.EditorKeyEvent.Type.DOWN) {
                            snippetController.stopSnippet()
                            event.intercept()
                            event.result(true)
                        }
                    }
                }

                overlayEditor = this

                controller.attach(this)
                onControllerReady(controller)
            }
        },
        update = { editor ->
            if (editor.text.toString() != content) {
                editor.setText(content)
            }
            applyDisplaySettings(editor, themedSettings)
            LspDiagnosticsBridge.apply(diagnosticsContainer, editor.text, diagnostics)
        }
    )

    EditorOverlayLayer(
        modifier = Modifier.fillMaxSize(),
        editor = overlayEditor,
        tick = overlayTick,
        overlays = lineOverlays,
        onUpdateChipClick = onUpdateChipClick,
        onTestTargetClick = onTestTargetClick
    )

    BreakpointOverlayLayer(
        modifier = Modifier.fillMaxSize(),
        editor = overlayEditor,
        tick = overlayTick,
        breakpoints = breakpoints,
        pausedLine = pausedLine,
        onToggleBreakpoint = onToggleBreakpoint,
        onEditBreakpoint = onEditBreakpoint
    )
    }

    DisposableEffect(Unit) {
        onDispose { controller.detach() }
    }
}

/**
 * Wendet [EditorDisplaySettings] einheitlich auf eine [CodeEditor]-Instanz an.
 * Wird sowohl bei der Ersterzeugung (factory) als auch bei Recompositions (update)
 * aufgerufen, damit Einstellungen garantiert synchron bleiben.
 */
fun applyDisplaySettings(editor: CodeEditor, settings: EditorDisplaySettings) {
    editor.isWordwrap = settings.wordWrap
    editor.isLineNumberEnabled = settings.showLineNumbers
    editor.setPinLineNumber(settings.pinLineNumbers)
    editor.isCursorAnimationEnabled = settings.cursorAnimationEnabled
    editor.lineSpacingMultiplier = settings.lineSpacingPercent.coerceIn(80, 200) / 100f
    editor.isEditable = !settings.readOnlyDefault
    editor.isDisableSoftKbdIfHardKbdAvailable = settings.disableSoftKeyboardWithHardwareKeyboard
    editor.inputType = android.text.InputType.TYPE_CLASS_TEXT or
        android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
        (if (settings.keyboardSuggestions) 0 else android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
    editor.tabWidth = settings.tabSize.coerceAtLeast(1)
    editor.setTextSize(settings.fontSizeSp.coerceIn(6f, 48f))

    editor.nonPrintablePaintingFlags = if (settings.showNonPrintableChars) {
        CodeEditor.FLAG_DRAW_WHITESPACE_LEADING or
            CodeEditor.FLAG_DRAW_WHITESPACE_INNER or
            CodeEditor.FLAG_DRAW_WHITESPACE_TRAILING or
            CodeEditor.FLAG_DRAW_LINE_SEPARATOR or
            CodeEditor.FLAG_DRAW_TAB_SAME_AS_SPACE
    } else {
        0
    }

    if (settings.fontFamilyAssetPath.isNotBlank()) {
        runCatching {
            editor.typefaceText = Typeface.createFromAsset(editor.context.assets, settings.fontFamilyAssetPath)
        }
    } else {
        editor.typefaceText = Typeface.MONOSPACE
    }

    // Lupe
    runCatching {
        editor.getComponent(Magnifier::class.java).isEnabled = settings.magnifierEnabled
    }

    // Symbolpaare automatisch schließen
    editor.props.symbolPairAutoCompletion = settings.symbolPairAutocompleteEnabled

    // Rainbow Brackets / Klammerpaar-Hervorhebung
    editor.isHighlightBracketPair = settings.rainbowBracketsEnabled
    editor.props.boldMatchingDelimiters = settings.rainbowBracketsEnabled
    runCatching {
        val bracketClass = Class.forName("io.github.rosemoe.sora.widget.component.BracketPairs")
        val component = editor.getComponent(bracketClass.asSubclass(
            io.github.rosemoe.sora.widget.component.EditorBuiltinComponent::class.java
        ))
        component.isEnabled = settings.rainbowBracketsEnabled
    }

    // Pinch-to-zoom
    val density = editor.context.resources.displayMetrics.scaledDensity
    if (settings.pinchToZoomEnabled) {
        editor.setScaleTextSizes(8f * density, 40f * density)
        runCatching {
            val method = editor.javaClass.getMethod("setScaleTextEnabled", Boolean::class.javaPrimitiveType)
            method.invoke(editor, true)
        }
    } else {
        val currentPx = settings.fontSizeSp * density
        editor.setScaleTextSizes(currentPx, currentPx)
        runCatching {
            val method = editor.javaClass.getMethod("setScaleTextEnabled", Boolean::class.javaPrimitiveType)
            method.invoke(editor, false)
        }
    }

    // Desktop shortcuts / PC Navigation
    runCatching {
        val field = editor.props.javaClass.getDeclaredField("navigationMode")
        field.isAccessible = true
        field.setInt(editor.props, if (settings.desktopShortcutsEnabled) 1 else 0)
    }

    // Sticky Scroll
    runCatching {
        val stickyScrollClass = Class.forName("io.github.rosemoe.sora.widget.component.EditorStickyScroll")
        val component = editor.getComponent(stickyScrollClass.asSubclass(
            io.github.rosemoe.sora.widget.component.EditorBuiltinComponent::class.java
        ))
        component.isEnabled = settings.stickyScrollEnabled
    }

    // Minimap
    runCatching {
        val minimapClass = Class.forName("io.github.rosemoe.sora.widget.component.EditorMinimap")
        val comp = editor.getComponent(minimapClass.asSubclass(
            io.github.rosemoe.sora.widget.component.EditorBuiltinComponent::class.java
        ))
        comp?.isEnabled = settings.minimapEnabled
    }

    // TextMate Theme: Registry setzen UND als ColorScheme auf den Editor anwenden
    // (ohne das bleibt der Editor beim hellen Standard-EditorColorScheme).
    applyTextMateTheme(editor, settings.textmateTheme)
}

private val appliedThemes = java.util.WeakHashMap<CodeEditor, String>()

private fun applyTextMateTheme(editor: CodeEditor, themeName: String) {
    if (themeName.isBlank() || appliedThemes[editor] == themeName) return
    TextMateAssetLoader.ensureInitialized(editor.context)
    TextMateAssetLoader.setActiveTheme(themeName)
    runCatching {
        editor.colorScheme = TextMateColorScheme.create(ThemeRegistry.getInstance())
        appliedThemes[editor] = themeName
    }.onFailure { android.util.Log.w("SoraCodeEditor", "TextMate-ColorScheme nicht anwendbar: $themeName", it) }
}

/** "auto"/leer -> dark_plus bzw. light_plus je nach effektivem App-Theme; sonst unverändert. */
internal fun resolveEditorTheme(stored: String, isDarkUi: Boolean): String =
    if (stored.isBlank() || stored.equals("auto", ignoreCase = true)) {
        if (isDarkUi) TextMateAssetLoader.DEFAULT_DARK_THEME else TextMateAssetLoader.DEFAULT_LIGHT_THEME
    } else stored
