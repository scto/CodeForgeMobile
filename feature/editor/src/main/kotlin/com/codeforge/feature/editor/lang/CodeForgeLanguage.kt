package com.codeforge.feature.editor.lang

import android.os.Bundle
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.lang.completion.CompletionHelper
import io.github.rosemoe.sora.lang.completion.CompletionPublisher
import io.github.rosemoe.sora.lang.completion.IdentifierAutoComplete
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.ContentReference
import io.github.rosemoe.sora.util.MyCharacter

/**
 * Universal Language Wrapper for CodeForge Mobile that provides identifier and keyword
 * autocompletion on top of any underlying syntax highlighting language (TextMate, Monarch, TreeSitter).
 */
class CodeForgeLanguage(
    private val delegate: Language,
    keywords: Array<String> = DEFAULT_KEYWORDS
) : Language by delegate {

    private val identifierAutoComplete = IdentifierAutoComplete(keywords)

    override fun requireAutoComplete(
        content: ContentReference,
        position: CharPosition,
        publisher: CompletionPublisher,
        extraArguments: Bundle
    ) {
        val prefix = CompletionHelper.computePrefix(content, position) { MyCharacter.isJavaIdentifierPart(it) }
        if (prefix.isNotEmpty()) {
            identifierAutoComplete.requireAutoComplete(content, position, prefix, publisher, null)
        }
        runCatching {
            delegate.requireAutoComplete(content, position, publisher, extraArguments)
        }
    }

    companion object {
        val KOTLIN_KEYWORDS = arrayOf(
            "Composable", "Preview", "OptIn", "Single", "Inject", "HiltViewModel",
            "Modifier", "Column", "Row", "Box", "Text", "Button", "Icon", "IconButton",
            "Spacer", "LazyColumn", "LazyRow", "Card", "Scaffold", "TopAppBar", "Surface",
            "MaterialTheme", "Color", "fun", "val", "var", "class", "interface", "object",
            "enum", "sealed", "data", "package", "import", "return", "if", "else", "when",
            "for", "while", "do", "try", "catch", "finally", "throw", "private", "protected",
            "public", "internal", "override", "open", "abstract", "by", "companion", "init",
            "constructor", "this", "super", "true", "false", "null", "remember",
            "rememberCoroutineScope", "rememberState", "mutableStateOf", "collectAsState",
            "LaunchedEffect", "DisposableEffect", "SideEffect", "StateFlow", "SharedFlow",
            "Flow", "launch", "async", "await", "delay"
        )

        val DEFAULT_KEYWORDS = arrayOf(
            "class", "function", "fun", "var", "val", "let", "const", "import", "export",
            "return", "if", "else", "for", "while", "do", "switch", "case", "break",
            "continue", "try", "catch", "finally", "throw", "public", "private", "protected",
            "interface", "enum", "extends", "implements", "package", "this", "super",
            "new", "null", "true", "false", "void", "int", "float", "double", "boolean",
            "string", "def", "self", "None", "True", "False", "from", "async", "await"
        )
    }
}
