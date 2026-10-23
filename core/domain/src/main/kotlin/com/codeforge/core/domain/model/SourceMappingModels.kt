/**
 * Modul: :core:domain
 * @author Thomas Schmid
 */
package com.codeforge.core.domain.model

/**
 * Repräsentiert ein erkanntes Klassen- oder Typsymbol innerhalb einer Quelltextdatei.
 */
data class ClassSymbolInfo(
    val simpleName: String,
    val fullyQualifiedName: String,
    val startLine: Int,
    val endLine: Int,
    val isFacade: Boolean = false,
    val isInner: Boolean = false
)

/**
 * Ergebnis der syntaktischen Analyse einer Quelltextdatei zur Zuordnung von Zeilennummern
 * zu Laufzeit-Klassennamen.
 */
data class SourceFileAnalysis(
    val filePath: String,
    val packageName: String,
    val jvmName: String?,
    val facadeClassName: String,
    val classes: List<ClassSymbolInfo>,
    val executableLines: Set<Int>
)
