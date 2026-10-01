package com.codeforge.feature.editor.language

import android.content.Context

class EditorAssetRegistry(
    private val context: Context
) {
    fun textMateRoot(): String = "textmate"
    fun treeSitterQueryRoot(): String = "tree-sitter-queries"
}
