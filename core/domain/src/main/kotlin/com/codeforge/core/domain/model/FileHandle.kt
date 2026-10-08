// Modul: :core:domain
package com.codeforge.core.domain.model

data class FileHandle(
    val path: String,
    val content: String
)

data class FileEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long = 0L
)
