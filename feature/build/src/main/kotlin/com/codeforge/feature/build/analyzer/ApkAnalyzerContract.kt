// Modul: :feature:build
package com.codeforge.feature.build.analyzer

import androidx.compose.runtime.Immutable
import com.codeforge.core.domain.model.ApkAnalysisResult
import com.codeforge.core.domain.model.ApkComparisonResult
import com.codeforge.core.domain.model.ApkEntryDiff
import com.codeforge.core.domain.model.ApkEntryDiffType
import com.codeforge.core.domain.model.ApkEntryInfo
import com.codeforge.core.domain.model.ApkPermission
import com.codeforge.core.domain.model.ApkSection

enum class ApkAnalyzerTab {
    OVERVIEW,
    FILES,
    PERMISSIONS,
    COMPONENTS,
    SIGNATURE,
    COMPARE
}

enum class FileSortOrder {
    SIZE_COMPRESSED_DESC,
    SIZE_UNCOMPRESSED_DESC,
    NAME_ASC,
    CATEGORY
}

enum class FileCategoryFilter {
    ALL,
    TOP_10,
    DEX,
    RES,
    LIB,
    ASSETS,
    META_INF,
    OTHER
}

enum class PermissionFilter {
    ALL,
    DANGEROUS_ONLY
}

enum class DiffFilter {
    ALL,
    ADDED,
    REMOVED,
    CHANGED,
    UNCHANGED
}

@Immutable
data class ApkAnalyzerUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val baseApkPath: String? = null,
    val comparedApkPath: String? = null,
    val analysisResult: ApkAnalysisResult? = null,
    val comparisonResult: ApkComparisonResult? = null,
    val isComparing: Boolean = false,
    val activeTab: ApkAnalyzerTab = ApkAnalyzerTab.OVERVIEW,
    val searchQuery: String = "",
    val sortOrder: FileSortOrder = FileSortOrder.SIZE_COMPRESSED_DESC,
    val categoryFilter: FileCategoryFilter = FileCategoryFilter.ALL,
    val permissionFilter: PermissionFilter = PermissionFilter.ALL,
    val diffFilter: DiffFilter = DiffFilter.ALL,
    val expandedPaths: Set<String> = emptySet()
) {
    val filteredFiles: List<ApkEntryInfo> get() {
        val result = analysisResult ?: return emptyList()
        var list = result.entries

        // Kategorie-Filter
        list = when (categoryFilter) {
            FileCategoryFilter.ALL -> list
            FileCategoryFilter.TOP_10 -> result.topEntries
            FileCategoryFilter.DEX -> list.filter { it.section == ApkSection.DEX }
            FileCategoryFilter.RES -> list.filter { it.section == ApkSection.RES }
            FileCategoryFilter.LIB -> list.filter { it.section == ApkSection.LIB }
            FileCategoryFilter.ASSETS -> list.filter { it.section == ApkSection.ASSETS }
            FileCategoryFilter.META_INF -> list.filter { it.section == ApkSection.META_INF }
            FileCategoryFilter.OTHER -> list.filter { it.section == ApkSection.OTHER || it.section == ApkSection.MANIFEST || it.section == ApkSection.ARSC }
        }

        // Suchfilter
        if (searchQuery.isNotBlank()) {
            list = list.filter { it.path.contains(searchQuery, ignoreCase = true) }
        }

        // Sortierung
        return when (sortOrder) {
            FileSortOrder.SIZE_COMPRESSED_DESC -> list.sortedByDescending { it.compressedSize }
            FileSortOrder.SIZE_UNCOMPRESSED_DESC -> list.sortedByDescending { it.uncompressedSize }
            FileSortOrder.NAME_ASC -> list.sortedBy { it.path.lowercase() }
            FileSortOrder.CATEGORY -> list.sortedWith(compareBy({ it.section.name }, { -it.compressedSize }))
        }
    }

    val filteredPermissions: List<ApkPermission> get() {
        val perms = analysisResult?.metadata?.permissions ?: return emptyList()
        return when (permissionFilter) {
            PermissionFilter.ALL -> perms
            PermissionFilter.DANGEROUS_ONLY -> perms.filter { it.isDangerous }
        }
    }

    val filteredDiffs: List<ApkEntryDiff> get() {
        val diffs = comparisonResult?.entryDiffs ?: return emptyList()
        var list = diffs

        if (searchQuery.isNotBlank()) {
            list = list.filter { it.path.contains(searchQuery, ignoreCase = true) }
        }

        return when (diffFilter) {
            DiffFilter.ALL -> list
            DiffFilter.ADDED -> list.filter { it.diffType == ApkEntryDiffType.ADDED }
            DiffFilter.REMOVED -> list.filter { it.diffType == ApkEntryDiffType.REMOVED }
            DiffFilter.CHANGED -> list.filter { it.diffType == ApkEntryDiffType.INCREASED || it.diffType == ApkEntryDiffType.DECREASED }
            DiffFilter.UNCHANGED -> list.filter { it.diffType == ApkEntryDiffType.UNCHANGED }
        }
    }
}

sealed interface ApkAnalyzerUiEvent {
    data class LoadApk(val path: String) : ApkAnalyzerUiEvent
    data class SelectTab(val tab: ApkAnalyzerTab) : ApkAnalyzerUiEvent
    data class UpdateSearchQuery(val query: String) : ApkAnalyzerUiEvent
    data class SetSortOrder(val order: FileSortOrder) : ApkAnalyzerUiEvent
    data class SetCategoryFilter(val filter: FileCategoryFilter) : ApkAnalyzerUiEvent
    data class SetPermissionFilter(val filter: PermissionFilter) : ApkAnalyzerUiEvent
    data class SetDiffFilter(val filter: DiffFilter) : ApkAnalyzerUiEvent
    data class TogglePathExpanded(val path: String) : ApkAnalyzerUiEvent
    data class CompareWithApk(val comparePath: String) : ApkAnalyzerUiEvent
    data object ClearComparison : ApkAnalyzerUiEvent
    data object ShareMarkdownReport : ApkAnalyzerUiEvent
}

sealed interface ApkAnalyzerUiEffect {
    data class ShareReport(val markdownText: String, val title: String) : ApkAnalyzerUiEffect
    data class ShowSnackbar(val message: String) : ApkAnalyzerUiEffect
}
