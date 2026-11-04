package com.codeforge.feature.j2k

import androidx.compose.runtime.Immutable
import com.codeforge.core.domain.model.J2kFileResult
import com.codeforge.core.domain.model.J2kFileStatus
import com.codeforge.core.domain.model.J2kProgress
import com.codeforge.core.domain.model.J2kReport
import com.codeforge.core.domain.model.J2kScanResult

/** Filter der Ergebnisliste. */
enum class J2kFilter {
    ALL,
    CONVERTED,
    WITH_NOTES,
    FAILED
}

/** Zustand der Java-zu-Kotlin-Umwandlung. */
@Immutable
data class J2kUiState(
    val isScanning: Boolean = false,
    val scanResult: J2kScanResult? = null,
    val showDialog: Boolean = false,
    val convertGradle: Boolean = false,
    val useVersionCatalog: Boolean = false,
    val runBuildCheck: Boolean = true,
    val createGitBranch: Boolean = false,
    val includeGitInBackup: Boolean = true,
    val isConverting: Boolean = false,
    val progress: J2kProgress? = null,
    val report: J2kReport? = null,
    val showResultScreen: Boolean = false,
    val selectedFilter: J2kFilter = J2kFilter.ALL,
    val errorMessage: String? = null
) {
    /** Ergebnisse nach Filter. */
    val filteredResults: List<J2kFileResult>
        get() {
            val list = report?.fileResults ?: emptyList()
            return when (selectedFilter) {
                J2kFilter.ALL -> list
                J2kFilter.CONVERTED -> list.filter { it.status == J2kFileStatus.CONVERTED }
                J2kFilter.WITH_NOTES -> list.filter { it.status == J2kFileStatus.CONVERTED_WITH_NOTES }
                J2kFilter.FAILED -> list.filter { it.status == J2kFileStatus.FAILED }
            }
        }
}

/** Aktionen der Umwandlung. */
sealed interface J2kIntent {
    /** Projekt nach Java-Dateien durchsuchen. */
    data class ScanProject(val projectPath: String, val manualTrigger: Boolean = false) : J2kIntent
    /** Gradle-Dateien umwandeln. */
    data class SetConvertGradle(val enabled: Boolean) : J2kIntent
    /** Versionskatalog verwenden. */
    data class SetUseVersionCatalog(val enabled: Boolean) : J2kIntent
    /** Build danach prüfen. */
    data class SetRunBuildCheck(val enabled: Boolean) : J2kIntent
    /** Git-Zweig anlegen. */
    data class SetCreateGitBranch(val enabled: Boolean) : J2kIntent
    /** Git in die Sicherung aufnehmen. */
    data class SetIncludeGitInBackup(val enabled: Boolean) : J2kIntent
    /** Umwandlung starten. */
    object StartMigration : J2kIntent
    /** Umwandlung abbrechen. */
    object CancelMigration : J2kIntent
    /** Dialog schließen (optional „nicht mehr fragen“). */
    data class DismissDialog(val dontAskAgain: Boolean) : J2kIntent
    /** Sicherung wiederherstellen. */
    object RestoreBackup : J2kIntent
    /** Filter setzen. */
    data class SetFilter(val filter: J2kFilter) : J2kIntent
    /** Datei öffnen. */
    data class OpenFile(val filePath: String) : J2kIntent
    /** Ergebnis schließen. */
    object DismissResult : J2kIntent
}

/** Einmalige Effekte. */
sealed interface J2kEffect {
    /** Meldung anzeigen. */
    data class ShowMessage(val message: String) : J2kEffect
    /** Zur Datei navigieren. */
    data class NavigateToFile(val filePath: String) : J2kEffect
}
