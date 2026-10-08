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
import com.codeforge.feature.editor.textmate.TextMateAssetLoader
import com.codeforge.core.domain.model.LspDiagnostic
import com.codeforge.feature.editor.overlay.ColorLiteralScanner
import com.codeforge.feature.editor.overlay.EditorOverlayLayer
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
    val symbolPairAutocompleteEnabled: Boolean = true
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
    onUpdateChipClick: (DependencyUpdate) -> Unit = {},
    onContentChanged: (String) -> Unit,
    onControllerReady: (EditorController) -> Unit = {}
) {
    val context = LocalContext.current
    val controller = remember(path) { EditorController() }
    val diagnosticsContainer = remember(path) { DiagnosticsContainer() }

    // Overlay (Farbkästchen + Update-Chips): Editor-Referenz und „Tick“ für Scroll/Text-Änderungen
    var overlayEditor by remember { mutableStateOf<CodeEditor?>(null) }
    var overlayTick by remember { mutableIntStateOf(0) }
    val colorMatches = remember(path, content) { ColorLiteralScanner.scanForPath(path, content) }
    val lineOverlays = remember(colorMatches, updateAnnotations) {
        buildLineOverlays(colorMatches, updateAnnotations.map { it.line to it.update })
    }

    DisposableEffect(Unit) {
        TextMateAssetLoader.ensureInitialized(context)
        onDispose { }
    }

    Box(modifier = modifier) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
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

                // --- Code Block Indicators ---
                isBlockLineEnabled = true

                // --- Punctuation Pair Matching and Highlighting ---
                isHighlightBracketPair = true

                // --- Word Wrap Display Mode ---
                isWordwrap = settings.wordWrap

                // --- Display Non-Printable Characters (Leerzeichen/Tabs/Zeilenumbrüche) ---
                nonPrintablePaintingFlags = if (settings.showNonPrintableChars) {
                    CodeEditor.FLAG_DRAW_WHITESPACE_LEADING or
                        CodeEditor.FLAG_DRAW_WHITESPACE_INNER or
                        CodeEditor.FLAG_DRAW_WHITESPACE_TRAILING or
                        CodeEditor.FLAG_DRAW_LINE_SEPARATOR or
                        CodeEditor.FLAG_DRAW_TAB_SAME_AS_SPACE
                } else {
                    0
                }

                // --- Text Magnifier (Lupe bei Long-Press-Cursorplatzierung) ---
                getComponent(Magnifier::class.java).isEnabled = settings.magnifierEnabled

                // --- Auto-Completion-Popup aktiv halten (Snippets kommen aus LspAwareLanguage) ---
                getComponent(EditorAutoCompletion::class.java).isEnabled = true

                // --- Sticky Scroll: zeigt den umschließenden Code-Block fixiert oben an.
                // Komponente ist in sora-editor 0.23.x als EditorStickyScroll verfügbar;
                // Zugriff defensiv per reflection-freiem getComponent, da ältere Artefakte
                // diese Komponente u.U. nicht registrieren.
                runCatching {
                    val stickyScrollClass = Class.forName(
                        "io.github.rosemoe.sora.widget.component.EditorStickyScroll"
                    )
                    val component = getComponent(stickyScrollClass.asSubclass(
                        io.github.rosemoe.sora.widget.component.EditorBuiltinComponent::class.java
                    ))
                    component.isEnabled = settings.stickyScrollEnabled
                }

                // --- Tab-Größe ---
                tabWidth = settings.tabSize.coerceAtLeast(1)

                // --- Schriftgröße ---
                setTextSize(settings.fontSizeSp.coerceIn(6f, 48f))

                // --- Schriftart (optional, aus Assets geladen) ---
                if (settings.fontFamilyAssetPath.isNotBlank()) {
                    runCatching {
                        typefaceText = Typeface.createFromAsset(ctx.assets, settings.fontFamilyAssetPath)
                    }
                }

                TextMateAssetLoader.setActiveTheme(settings.textmateTheme)

                this.diagnostics = diagnosticsContainer

                subscribeEvent<ContentChangeEvent> { event, _ ->
                    onContentChanged(event.editor.text.toString())
                    overlayTick++
                }
                subscribeEvent<ScrollEvent> { _, _ -> overlayTick++ }
                overlayEditor = this

                controller.attach(this)
                onControllerReady(controller)
            }
        },
        update = { editor ->
            if (editor.text.toString() != content) {
                editor.setText(content)
            }
            editor.isWordwrap = settings.wordWrap
            editor.tabWidth = settings.tabSize.coerceAtLeast(1)
            editor.setTextSize(settings.fontSizeSp.coerceIn(6f, 48f))
            editor.getComponent(Magnifier::class.java).isEnabled = settings.magnifierEnabled
            TextMateAssetLoader.setActiveTheme(settings.textmateTheme)
            LspDiagnosticsBridge.apply(diagnosticsContainer, editor.text, diagnostics)
        }
    )
    EditorOverlayLayer(
        modifier = Modifier.fillMaxSize(),
        editor = overlayEditor,
        tick = overlayTick,
        overlays = lineOverlays,
        onUpdateChipClick = onUpdateChipClick
    )
    }

    DisposableEffect(Unit) {
        onDispose { controller.detach() }
    }
}
