package com.codeforge.feature.j2k

import androidx.compose.runtime.Immutable
import com.codeforge.core.domain.model.J2kFileResult
import com.codeforge.core.domain.model.J2kFileStatus
import com.codeforge.core.domain.model.J2kProgress
import com.codeforge.core.domain.model.J2kReport
import com.codeforge.core.domain.model.J2kScanResult

enum class J2kFilter {
    ALL,
    CONVERTED,
    WITH_NOTES,
    FAILED
}

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

sealed interface J2kIntent {
    data class ScanProject(val projectPath: String, val manualTrigger: Boolean = false) : J2kIntent
    data class SetConvertGradle(val enabled: Boolean) : J2kIntent
    data class SetUseVersionCatalog(val enabled: Boolean) : J2kIntent
    data class SetRunBuildCheck(val enabled: Boolean) : J2kIntent
    data class SetCreateGitBranch(val enabled: Boolean) : J2kIntent
    data class SetIncludeGitInBackup(val enabled: Boolean) : J2kIntent
    object StartMigration : J2kIntent
    object CancelMigration : J2kIntent
    data class DismissDialog(val dontAskAgain: Boolean) : J2kIntent
    object RestoreBackup : J2kIntent
    data class SetFilter(val filter: J2kFilter) : J2kIntent
    data class OpenFile(val filePath: String) : J2kIntent
    object DismissResult : J2kIntent
}

sealed interface J2kEffect {
    data class ShowMessage(val message: String) : J2kEffect
    data class NavigateToFile(val filePath: String) : J2kEffect
}
