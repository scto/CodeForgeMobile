// Modul: :core:domain
package com.codeforge.core.domain.model

data class LspServerLaunch(
    val command: List<String>,
    val env: Map<String, String> = emptyMap()
)
