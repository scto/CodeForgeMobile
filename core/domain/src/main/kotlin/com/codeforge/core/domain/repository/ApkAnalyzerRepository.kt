// Modul: :core:domain
package com.codeforge.core.domain.repository

import com.codeforge.core.domain.model.ApkAnalysisResult
import com.codeforge.core.domain.model.ApkComparisonResult
import java.io.File

/**
 * Schnittstelle für die Analyse und den Vergleich von APK-Dateien (35.1 - 35.3).
 */
interface ApkAnalyzerRepository {

    /**
     * Führt eine vollständige Analyse der angegebenen APK-Datei durch.
     */
    suspend fun analyzeApk(apkFile: File): Result<ApkAnalysisResult>

    /**
     * Vergleicht zwei APK-Dateien (Größenunterschiede, Sektionen, geänderte/neue/entfernte Dateien).
     */
    suspend fun compareApks(baseFile: File, comparedFile: File): Result<ApkComparisonResult>
}
