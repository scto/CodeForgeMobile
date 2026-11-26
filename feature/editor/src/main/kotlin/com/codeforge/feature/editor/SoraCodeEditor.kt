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

import com.codeforge.core.domain.extension.grammarForPath
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
import com.codeforge.feature.editor.completion.ImeAwareAutoCompletion
import com.codeforge.feature.editor.lsp.LspAwareLanguage
import com.codeforge.feature.editor.lsp.LspCompletionProvider
import com.codeforge.feature.editor.lsp.LspDiagnosticsBridge
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.luminance
import com.codeforge.feature.editor.textmate.TextMateAssetLoader
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
    /** Ligaturen der Schrift (z. B. `->`, `!=` in JetBrains Mono) zeichnen. */
    val fontLigatures: Boolean = true,
    /** Soft-Tab: Tab-Taste fügt Leerzeichen in Tab-Breite statt `\t` ein. */
    val softTabs: Boolean = true,
    /** Leere Zeile mit einem Backspace komplett löschen (inkl. Einrückung). */
    val deleteEmptyLineFast: Boolean = true,
    /** Backspace löscht Leerzeichen in Tab-Breite auf einmal. */
    val backspaceDeletesTab: Boolean = true,
    /** Wortgrenzen für Doppeltipp/langes Drücken per ICU (`android.icu.text.BreakIterator`) statt Zeichenklassen. */
    val icuWordSelection: Boolean = true,
    val symbolPairAutocompleteEnabled: Boolean = true,
    /** Code-Faltung (Faltmarken in der Randspalte). */
    val codeFoldingEnabled: Boolean = true,
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
    val keyboardSuggestions: Boolean = false,
    /** Aktuelle Zeile hinterlegen. */
    val highlightCurrentLine: Boolean = true,
    /** Einrückungslinien (Block-Linien) und Hervorhebung des aktuellen Blocks. */
    val indentGuides: Boolean = true,
    val cursorBlink: Boolean = true,
    /** 0 = Bewegen, 1 = Überblenden, 2 = Skalieren. */
    val cursorAnimationStyle: Int = 0,
    val scrollbars: Boolean = true,
    val stickyScrollMaxLines: Int = DEFAULT_STICKY_LINES,
    /** Tipp auf die Zeilennummer markiert die ganze Zeile. */
    val lineNumberSelectsLine: Boolean = false,
    /** 0 = Wellenlinie, 1 = Linie, 2 = Doppellinie, 3 = keine. */
    val diagnosticStyle: Int = 0,
    /** Typ- und Parameterhinweise des Sprachservers im Text. */
    val inlayHints: Boolean = true,
    /** Maus/Touchpad-Modus: 0 = automatisch, 1 = immer, 2 = nie. */
    val mouseMode: Int = 0,
    /** Auswahlgriffe: 0 = Tropfen, 1 = seitlicher Tropfen. */
    val selectionHandleStyle: Int = 0,
    /** Zeilennummer neben der Bildlaufleiste beim schnellen Scrollen. */
    val fastScrollLineTip: Boolean = true,
    /** Faltmarken als Linien statt Pfeilen. */
    val foldMarksAsLines: Boolean = false,
    /** Zeichenabstand in Hundertstel em (0 = normal). */
    val letterSpacingPercent: Int = 0,
    /** Senkrechtes Lineal nach dieser Spalte (0 = aus). */
    val rulerColumn: Int = 0,
    /** Eingefügten Text automatisch formatieren. */
    val formatOnPaste: Boolean = false
) {
    companion object {
        const val DEFAULT_STICKY_LINES = 3
    }
}

/** Compose-Hülle um den Sora-Editor (Syntax, LSP, Overlays). */
@Composable
fun SoraCodeEditor(
    modifier: Modifier = Modifier,
    path: String,
    content: String,
    language: EditorLanguageType,
    settings: EditorDisplaySettings,
    diagnostics: List<LspDiagnostic> = emptyList(),
    inlayHints: List<com.codeforge.core.domain.model.LspInlayHint> = emptyList(),
    completionProvider: LspCompletionProvider? = null,
    updateAnnotations: List<FileUpdateAnnotation> = emptyList(),
    breakpointsEnabled: Boolean = false,
    breakpoints: List<Breakpoint> = emptyList(),
    gitChanges: List<com.codeforge.core.domain.model.GitLineChange> = emptyList(),
    conflicts: List<com.codeforge.feature.editor.overlay.ConflictBlock> = emptyList(),
    pausedLine: Int? = null,
    onToggleBreakpoint: (Int) -> Unit = {},
    onEditBreakpoint: (Int) -> Unit = {},
    onUpdateChipClick: (DependencyUpdate) -> Unit = {},
    onTestTargetClick: (TestTarget) -> Unit = {},
    pluginTests: List<TestTarget> = emptyList(),
    textActions: List<EditorTextAction> = emptyList(),
    onTextAction: (String) -> Unit = {},
    onContentChanged: (String) -> Unit,
    onControllerReady: (EditorController) -> Unit = {}
) {
    val context = LocalContext.current
    // "auto"/leer folgt dem effektiven App-Theme (Hell/Dunkel/System aus den Einstellungen).
    val isDarkUi = MaterialTheme.colorScheme.background.luminance() < 0.5f
    // Ein aktives Plugin-Farbschema kann ein eigenes Editor-Syntaxtheme mitbringen (gilt bei "auto").
    val themeOverride = com.codeforge.core.designsystem.LocalEditorThemeOverride.current
    val overrideId = remember(themeOverride, context) {
        themeOverride?.takeIf {
            TextMateAssetLoader.ensureInitialized(context)
            TextMateAssetLoader.registerExternalTheme(it.id, it.filePath, it.isDark)
        }?.id
    }
    // Grammatik aus einem Plugin für Dateien ohne eingebaute Grammatik (Zuordnung über Dateiendung).
    val pluginGrammars = com.codeforge.core.designsystem.LocalActiveGrammars.current
    val extensionScope = remember(path, language, pluginGrammars) {
        if (language != EditorLanguageType.PLAIN || pluginGrammars.isEmpty()) {
            null
        } else {
            val hit = com.codeforge.core.domain.extension.ActiveContributions(grammars = pluginGrammars)
                .grammarForPath(path)
            hit?.takeIf {
                TextMateAssetLoader.ensureInitialized(context)
                TextMateAssetLoader.registerExternalGrammar(
                    it.item.scopeName,
                    java.io.File(it.pluginDir, it.item.path).path
                )
            }?.item?.scopeName
        }
    }
    // Ausdrücklich gewähltes Editor-Theme eines Plugins (`ext:<plugin>/<theme>`); fehlt das Plugin, gilt "auto".
    val pluginEditorThemes = com.codeforge.core.designsystem.LocalEditorThemes.current
    val storedTheme = remember(settings.textmateTheme, pluginEditorThemes, context) {
        selectPluginEditorTheme(settings.textmateTheme, pluginEditorThemes) { theme ->
            TextMateAssetLoader.ensureInitialized(context)
            TextMateAssetLoader.registerExternalTheme(theme.id, theme.filePath, theme.isDark)
        }
    }
    val themedSettings = remember(settings, storedTheme, isDarkUi, overrideId) {
        settings.copy(
            textmateTheme = resolveEditorTheme(storedTheme, isDarkUi, overrideId),
            wordWrap = settings.wordWrap || (settings.txtWordWrap && path.endsWith(".txt", ignoreCase = true))
        )
    }
    val controller = remember(path) { EditorController() }
    val currentTextActions by androidx.compose.runtime.rememberUpdatedState(textActions)
    val currentOnTextAction by androidx.compose.runtime.rememberUpdatedState(onTextAction)
    val diagnosticsContainer = remember(path) { DiagnosticsContainer() }
    // Zuletzt übernommene Diagnosen (Identität), damit nicht bei jeder Neuzusammensetzung neu gezeichnet wird.
    val appliedDiagnostics = remember(path) { java.util.concurrent.atomic.AtomicReference<List<LspDiagnostic>?>(null) }

    // Overlay (Farbkästchen + Update-Chips + Test-Starter): Editor-Referenz und „Tick“ für Scroll/Text-Änderungen
    var overlayEditor by remember { mutableStateOf<CodeEditor?>(null) }
    var overlayTick by remember { mutableIntStateOf(0) }
    val colorMatches = remember(path, content, settings.colorPreviewsEnabled) {
        if (settings.colorPreviewsEnabled) ColorLiteralScanner.scanForPath(path, content) else emptyList()
    }
    val scannedTests = remember(path, content) { TestTargetScanner.scan(path, content) }
    val testTargets = remember(scannedTests, pluginTests) { scannedTests + pluginTests }
    val currentOnTestTargetClick by androidx.compose.runtime.rememberUpdatedState(onTestTargetClick)
    // Test-Symbole in der Randspalte (sora-Symbolspalte), Tipp öffnet die Test-Aktionen.
    val gutterIcons = remember(path) {
        com.codeforge.feature.editor.overlay.TestGutterIcons(context) { currentOnTestTargetClick(it) }
    }
    // Farbmarken zeichnet sora selbst (ColorInlayHint), Tests stehen in der Randspalte; das Overlay trägt Update-Chips.
    val lineOverlays = remember(updateAnnotations) {
        buildLineOverlays(emptyList(), updateAnnotations.map { it.line to it.update })
    }
    val inlaySource = remember(path) { SoraInlayHintSource() }
    val currentTabSize by androidx.compose.runtime.rememberUpdatedState(settings.tabSize)

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
                colorScheme = EditorColorScheme().also(com.codeforge.feature.editor.treesitter.TreeSitterColors::apply)
                setText(content)

                setEditorLanguage(
                    EditorLanguageFactory.create(
                        context = ctx,
                        path = path,
                        languageType = language,
                        useTreeSitter = settings.useTreeSitter,
                        completionProvider = completionProvider,
                        extensionScope = extensionScope
                    )
                )

                // --- Code Block Indicators & Display Settings ---
                isBlockLineEnabled = true
                applyDisplaySettings(this, themedSettings)

                // --- Auto-Completion-Popup aktiv halten (Snippets kommen aus LspAwareLanguage) ---
                replaceComponent(EditorAutoCompletion::class.java, ImeAwareAutoCompletion(this))
                // System-Lupe statt der PixelCopy-Lupe von sora (die in Compose unsichtbar blieb).
                replaceComponent(Magnifier::class.java, SystemMagnifier(this))
                // --- Kontextmenü (Long-Click/Auswahl) mit zusätzlichen Editor-Aktionen ---
                replaceComponent(
                    io.github.rosemoe.sora.widget.component.EditorTextActionWindow::class.java,
                    CfTextActionWindow(this, { currentTextActions }, { id -> currentOnTextAction(id) }),
                )
                getComponent(EditorAutoCompletion::class.java).isEnabled = true

                // --- Diagnose-UI & Tooltips ---
                runCatching {
                    val tooltipWindow = getComponent(io.github.rosemoe.sora.widget.component.EditorDiagnosticTooltipWindow::class.java)
                    tooltipWindow.isEnabled = true
                }

                // sora-editor 0.24: Diagnosen kommen über Provider; unser Container wird bei jeder Aktualisierung übernommen.
                registerDiagnosticProvider { merged -> merged.addDiagnostics(diagnosticsContainer.regions) }

                // --- Code-Faltung: zusätzliche Bereiche je Dateityp (Code-Blöcke der Sprache kommen vom Fork selbst) ---
                com.codeforge.feature.editor.folding.CodeFolding.providersFor(path).forEach(::registerFoldingProvider)

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

                inlaySource.attach(this)
                gutterIcons.attach(this)
                setFormatterProvider(PasteReindentFormatter.provider { currentTabSize })
                subscribeEvent<io.github.rosemoe.sora.event.CreateContextMenuEvent> { event, _ ->
                    addIdeContextMenu(event.menu, currentTextActions, currentOnTextAction)
                }
                controller.attach(this)
                onControllerReady(controller)
            }
        },
        update = { editor ->
            if (editor.text.toString() != content) {
                editor.setText(content)
            }
            applyDisplaySettings(editor, themedSettings)
            if (appliedDiagnostics.get() !== diagnostics) {
                LspDiagnosticsBridge.apply(diagnosticsContainer, editor.text, diagnostics)
                editor.invalidateDiagnostics()
                appliedDiagnostics.set(diagnostics)
            }
            inlaySource.update(editor, if (settings.inlayHints) inlayHints else emptyList(), colorMatches)
            gutterIcons.update(editor, testTargets)
        }
    )

    EditorOverlayLayer(
        modifier = Modifier.fillMaxSize(),
        editor = overlayEditor,
        tick = overlayTick,
        overlays = lineOverlays,
        onUpdateChipClick = onUpdateChipClick
    )

    com.codeforge.feature.editor.overlay.ConflictOverlayLayer(
        modifier = Modifier.fillMaxSize(),
        editor = overlayEditor,
        tick = overlayTick,
        conflicts = conflicts
    )

    com.codeforge.feature.editor.overlay.GitGutterOverlayLayer(
        modifier = Modifier.fillMaxSize(),
        editor = overlayEditor,
        tick = overlayTick,
        changes = gitChanges
    )

    if (breakpointsEnabled) {
        BreakpointOverlayLayer(
            modifier = Modifier.fillMaxSize(),
            editor = overlayEditor,
            tick = overlayTick,
            breakpoints = breakpoints,
            pausedLine = pausedLine
        )
    }

    com.codeforge.feature.editor.overlay.GutterTapLayer(
        editor = overlayEditor,
        tick = overlayTick,
        breakpointsEnabled = breakpointsEnabled,
        onToggleBreakpoint = onToggleBreakpoint,
        onEditBreakpoint = onEditBreakpoint,
        onSideIconTap = { line ->
            testTargets.firstOrNull { it.line == line }?.let { currentOnTestTargetClick(it); true } ?: false
        }
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
    applyTabPolicy(editor.editorLanguage, hardTabs = !settings.softTabs, tabSize = settings.tabSize.coerceAtLeast(1))
    editor.isLigatureEnabled = settings.fontLigatures
    editor.props.deleteEmptyLineFast = settings.deleteEmptyLineFast
    // -1 = so viele Leerzeichen wie die Tab-Breite (sora-editor), 1 = ein Zeichen
    editor.props.deleteMultiSpaces = if (settings.backspaceDeletesTab) -1 else 1
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

    // Lupe: vergrößert den Text an der Cursorposition, solange eine Auswahl bzw. ein Griff gezogen wird.
    runCatching {
        editor.getComponent(Magnifier::class.java).isEnabled = settings.magnifierEnabled
    }

    // Wortauswahl per ICU: erkennt Wörter in allen Schriften (CJK, Thai …), Bindestriche, Zahlen und Emojis korrekt.
    editor.props.useICULibToSelectWords = settings.icuWordSelection

    // Symbolpaare automatisch schließen
    editor.props.symbolPairAutoCompletion = settings.symbolPairAutocompleteEnabled
    editor.isCodeFoldingEnabled = settings.codeFoldingEnabled
    // Größere Faltmarken (Standard 0.77/0.25 der Zeilenhöhe) – auf dem Handy besser zu sehen und zu treffen.
    editor.props.foldIconSizeFactor = FOLD_ICON_SIZE_FACTOR
    editor.props.foldIndicatorPadding = FOLD_INDICATOR_PADDING

    // Klammerpaare: sora hebt das zusammengehörige Paar am Cursor hervor (Hintergrund, Unterstreichung, fett).
    // Regenbogenklammern kennt sora nicht – die Einstellung schaltet die Paar-Hervorhebung.
    editor.isHighlightBracketPair = settings.rainbowBracketsEnabled
    editor.props.highlightMatchingDelimiters = settings.rainbowBracketsEnabled
    editor.props.boldMatchingDelimiters = true

    applySoraDisplay(editor, settings)

    // Pinch-to-zoom (sora: setScalable + Grenzen der Textgröße)
    val density = editor.context.resources.displayMetrics.scaledDensity
    editor.isScalable = settings.pinchToZoomEnabled
    if (settings.pinchToZoomEnabled) editor.setScaleTextSizes(MIN_ZOOM_SP * density, MAX_ZOOM_SP * density)

    // Sticky Scroll und Minimap sind in sora 0.24 Eigenschaften (props), keine Komponenten.
    editor.props.stickyScroll = settings.stickyScrollEnabled
    editor.props.stickyScrollMaxLines = settings.stickyScrollMaxLines.coerceIn(1, MAX_STICKY_LINES)
    editor.props.showMinimap = settings.minimapEnabled

    // TextMate Theme: Registry setzen UND als ColorScheme auf den Editor anwenden
    // (ohne das bleibt der Editor beim hellen Standard-EditorColorScheme).
    applyTextMateTheme(editor, settings.textmateTheme)
}

private val appliedThemes = java.util.WeakHashMap<CodeEditor, String>()

private const val FOLD_ICON_SIZE_FACTOR = 0.95f
private const val FOLD_INDICATOR_PADDING = 0.35f

private fun applyTextMateTheme(editor: CodeEditor, themeName: String) {
    if (themeName.isBlank()) return
    // Die ThemeRegistry ist global: ein anderer Editor (z. B. die Vorschau) kann das Theme umgestellt haben.
    val registryTheme = runCatching { ThemeRegistry.getInstance().currentThemeModel?.name }.getOrNull()
    if (appliedThemes[editor] == themeName && registryTheme == themeName) return
    TextMateAssetLoader.ensureInitialized(editor.context)
    TextMateAssetLoader.setActiveTheme(themeName)
    runCatching {
        editor.colorScheme = SafeTextMateColorScheme.create().also(::emphasizeBracketPair)
        appliedThemes[editor] = themeName
        // Spans mit Farb-IDs des neuen Themes neu aufbauen.
        editor.rerunAnalysis()
    }.onFailure { android.util.Log.w("SoraCodeEditor", "TextMate-ColorScheme nicht anwendbar: $themeName", it) }
}

/** Klammerpaar-Farben aus der Vordergrundfarbe des Themes ableiten, damit sie in jedem Theme klar sichtbar sind. */
private fun emphasizeBracketPair(scheme: EditorColorScheme) {
    // Tree-sitter-Farben aus demselben Theme übernehmen (feste Kennungen, siehe TreeSitterColors).
    com.codeforge.feature.editor.treesitter.TreeSitterColors.apply(scheme)
    val fg = scheme.getColor(EditorColorScheme.TEXT_NORMAL)
    val accent = fg
    scheme.setColor(EditorColorScheme.HIGHLIGHTED_DELIMITERS_FOREGROUND, accent)
    scheme.setColor(EditorColorScheme.HIGHLIGHTED_DELIMITERS_BACKGROUND, androidx.core.graphics.ColorUtils.setAlphaComponent(accent, 0x44))
    scheme.setColor(EditorColorScheme.HIGHLIGHTED_DELIMITERS_UNDERLINE, accent)
}

/**
 * Gespeichertes Editor-Theme für die Auflösung: Plugin-Schlüssel (`ext:…`) nur, wenn das Plugin-Theme in [available]
 * liegt und sich laden lässt ([register]), sonst "auto"; alle anderen Werte unverändert.
 */
internal fun selectPluginEditorTheme(
    stored: String,
    available: List<com.codeforge.core.designsystem.EditorThemeOverride>,
    register: (com.codeforge.core.designsystem.EditorThemeOverride) -> Boolean
): String {
    if (!com.codeforge.core.domain.extension.ExtensionColorTheme.isExtensionKey(stored)) return stored
    val theme = available.firstOrNull { it.id == stored } ?: return "auto"
    return if (register(theme)) stored else "auto"
}

/** "auto"/leer -> dark_plus bzw. light_plus je nach effektivem App-Theme; sonst unverändert. */
internal fun resolveEditorTheme(stored: String, isDarkUi: Boolean, extensionThemeId: String? = null): String =
    if (stored.isBlank() || stored.equals("auto", ignoreCase = true)) {
        if (extensionThemeId != null) extensionThemeId
        else if (isDarkUi) TextMateAssetLoader.DEFAULT_DARK_THEME else TextMateAssetLoader.DEFAULT_LIGHT_THEME
    } else stored

private const val MIN_ZOOM_SP = 8f
private const val MAX_ZOOM_SP = 40f
private const val MAX_STICKY_LINES = 8
private const val CURSOR_BLINK_MS = 500
private const val DIAGNOSTIC_LINE = 1
private const val DIAGNOSTIC_DOUBLE = 2
private const val DIAGNOSTIC_NONE = 3
private const val ANIMATION_FADE = 1
private const val ANIMATION_SCALE = 2

// Weitere Darstellungsoptionen von sora-editor: Zeile, Einrückungslinien, Cursor, Bildlaufleisten, Diagnosen.
private fun applySoraDisplay(editor: CodeEditor, settings: EditorDisplaySettings) {
    editor.isHighlightCurrentLine = settings.highlightCurrentLine
    editor.isBlockLineEnabled = settings.indentGuides
    editor.isHighlightCurrentBlock = settings.indentGuides
    editor.setCursorBlinkPeriod(if (settings.cursorBlink) CURSOR_BLINK_MS else 0)
    val animatorClass = when (settings.cursorAnimationStyle) {
        ANIMATION_FADE -> io.github.rosemoe.sora.widget.style.builtin.FadeCursorAnimator::class.java
        ANIMATION_SCALE -> io.github.rosemoe.sora.widget.style.builtin.ScaleCursorAnimator::class.java
        else -> io.github.rosemoe.sora.widget.style.builtin.MoveCursorAnimator::class.java
    }
    // Direkte Konstruktoraufrufe statt Reflection: R8 macht nur per Reflection erzeugte Klassen abstrakt
    // (Absturz „Can't instantiate abstract class“ in Release-Builds).
    if (editor.cursorAnimator.javaClass != animatorClass) {
        editor.cursorAnimator = when (settings.cursorAnimationStyle) {
            ANIMATION_FADE -> io.github.rosemoe.sora.widget.style.builtin.FadeCursorAnimator(editor)
            ANIMATION_SCALE -> io.github.rosemoe.sora.widget.style.builtin.ScaleCursorAnimator(editor)
            else -> io.github.rosemoe.sora.widget.style.builtin.MoveCursorAnimator(editor)
        }
    }
    editor.isVerticalScrollBarEnabled = settings.scrollbars
    editor.isHorizontalScrollBarEnabled = settings.scrollbars
    editor.props.actionWhenLineNumberClicked = if (settings.lineNumberSelectsLine) {
        io.github.rosemoe.sora.widget.DirectAccessProps.LN_ACTION_SELECT_LINE
    } else {
        io.github.rosemoe.sora.widget.DirectAccessProps.LN_ACTION_PLACE_SELECTION_HOME
    }
    editor.setDiagnosticIndicatorStyle(
        when (settings.diagnosticStyle) {
            DIAGNOSTIC_LINE -> io.github.rosemoe.sora.widget.style.DiagnosticIndicatorStyle.LINE
            DIAGNOSTIC_DOUBLE -> io.github.rosemoe.sora.widget.style.DiagnosticIndicatorStyle.DOUBLE_LINE
            DIAGNOSTIC_NONE -> io.github.rosemoe.sora.widget.style.DiagnosticIndicatorStyle.NONE
            else -> io.github.rosemoe.sora.widget.style.DiagnosticIndicatorStyle.WAVY_LINE
        }
    )
    applySoraInput(editor, settings)
}

private const val MOUSE_ALWAYS = 1
private const val MOUSE_NEVER = 2
private const val HANDLE_SIDE_DROP = 1
private const val PERCENT = 100f

// Maus, Auswahlgriffe, Bildlauf-Tipp, Faltmarken, Zeichenabstand, Lineal und Formatieren beim Einfügen.
private fun applySoraInput(editor: CodeEditor, settings: EditorDisplaySettings) {
    val props = editor.props
    props.mouseMode = when (settings.mouseMode) {
        MOUSE_ALWAYS -> io.github.rosemoe.sora.widget.DirectAccessProps.MOUSE_MODE_ALWAYS
        MOUSE_NEVER -> io.github.rosemoe.sora.widget.DirectAccessProps.MOUSE_MODE_NEVER
        else -> io.github.rosemoe.sora.widget.DirectAccessProps.MOUSE_MODE_AUTO
    }
    val handleClass = if (settings.selectionHandleStyle == HANDLE_SIDE_DROP) {
        io.github.rosemoe.sora.widget.style.builtin.HandleStyleSideDrop::class.java
    } else {
        io.github.rosemoe.sora.widget.style.builtin.HandleStyleDrop::class.java
    }
    if (editor.handleStyle.javaClass != handleClass) {
        editor.setSelectionHandleStyle(
            if (settings.selectionHandleStyle == HANDLE_SIDE_DROP) {
                io.github.rosemoe.sora.widget.style.builtin.HandleStyleSideDrop(editor.context)
            } else {
                io.github.rosemoe.sora.widget.style.builtin.HandleStyleDrop(editor.context)
            }
        )
    }
    editor.setDisplayLnPanel(settings.fastScrollLineTip)
    props.foldIndicatorStyle = if (settings.foldMarksAsLines) {
        io.github.rosemoe.sora.widget.DirectAccessProps.FOLD_STYLE_MARK
    } else {
        io.github.rosemoe.sora.widget.DirectAccessProps.FOLD_STYLE_ICON
    }
    val spacing = settings.letterSpacingPercent / PERCENT
    if (editor.textLetterSpacing != spacing) editor.textLetterSpacing = spacing
    if (props.hardwrapColumn != settings.rulerColumn) {
        props.hardwrapColumn = settings.rulerColumn
        editor.invalidate()
    }
    props.formatPastedText = settings.formatOnPaste
}

private const val CONTEXT_MENU_ORDER = 100

// Ergänzt das Kontextmenü von sora (Rechtsklick/Mausmodus) um die IDE-Aktionen der Textaktionsleiste.
private fun addIdeContextMenu(menu: android.view.Menu, actions: List<EditorTextAction>, onAction: (String) -> Unit) {
    actions.forEachIndexed { index, action ->
        menu.add(android.view.Menu.NONE, android.view.Menu.NONE, CONTEXT_MENU_ORDER + index, action.label)
            .setOnMenuItemClickListener {
                onAction(action.id)
                true
            }
    }
}
