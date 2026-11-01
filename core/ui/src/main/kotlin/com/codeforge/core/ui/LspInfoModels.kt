// Modul: :core:ui
package com.codeforge.core.ui

import androidx.compose.runtime.Immutable

enum class LspCheckLevel { OK, WARN, ERROR }

/** Eine Zeile der Diagnose (Tab „Zustand“). */
@Immutable
data class LspCheck(val title: String, val detail: String, val level: LspCheckLevel)

/** Allgemeine Angaben (Tab „Allgemein“); leere Felder werden nicht angezeigt. */
@Immutable
data class LspAbout(
    val description: String = "",
    val author: String = "",
    val homepageUrl: String = "",
    val repositoryUrl: String = "",
    val license: String = "",
    val source: String = "",
    val requires: String = "",
    val languages: String = "",
)

/** Alles, was der Info-Dialog über einen Language Server weiß – unabhängig von Extension oder Plugin. */
@Immutable
data class LspInfoModel(
    val title: String,
    val version: String,
    val languages: String,
    val installed: Boolean,
    val installPath: String?,
    val launchCommand: String,
    val checks: List<LspCheck>,
    val about: LspAbout,
)

/** Momentaufnahme der Laufzeitdaten; `null` für „Server läuft nicht“. */
@Immutable
data class LspRuntimeInfo(
    val running: Boolean,
    val starting: Boolean = false,
    val sessionCount: Int = 0,
    val memoryBytes: Long? = null,
    val startedAtMillis: Long? = null,
    val lastUsedMillis: Long? = null,
    val openDocuments: Int = 0,
    val processIds: List<Long> = emptyList(),
)
