package com.codeforge.feature.editor.state

data class EditorSession(
    val filePath: String,
    val content: String = "",
    val languageId: String? = null,
    val dirty: Boolean = false
)
