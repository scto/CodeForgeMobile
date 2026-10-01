package com.codeforge.feature.editor.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.codeforge.core.datastore.proto.EditorConfig
import com.codeforge.feature.editor.EditorSymbolBar
import com.codeforge.feature.editor.SoraCodeEditor
import com.codeforge.feature.editor.SoraLanguageProvider
import io.github.rosemoe.sora.widget.CodeEditor

@Composable
fun SoraEditorHost(
    filePath: String?,
    initialText: String,
    onContentChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
    editorConfig: EditorConfig = EditorConfig.getDefaultInstance()
) {
    val context = LocalContext.current
    val languageProvider = remember(context) { SoraLanguageProvider(context) }
    var codeEditorInstance by remember { mutableStateOf<CodeEditor?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        SoraCodeEditor(
            content = initialText,
            filePath = filePath.orEmpty(),
            languageProvider = languageProvider,
            editorConfig = editorConfig,
            onContentChanged = onContentChanged,
            onEditorCreated = { editor -> codeEditorInstance = editor },
            modifier = Modifier.weight(1f)
        )
        if (editorConfig.symbolBarVisible) {
            EditorSymbolBar(
                editor = codeEditorInstance
            )
        }
    }
}
