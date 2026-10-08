// Modul: :core:domain
package com.codeforge.core.domain.model

data class RecentProject(
    val id: String,
    val name: String,
    val path: String,
    val lastOpenedEpochMillis: Long,
    val moduleCount: Int = 1
)
