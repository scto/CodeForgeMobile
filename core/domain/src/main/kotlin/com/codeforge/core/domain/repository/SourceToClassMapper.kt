/**
 * Modul: :core:domain
 * @author Thomas Schmid
 */
package com.codeforge.core.domain.repository

import com.codeforge.core.domain.model.Breakpoint
import com.codeforge.core.domain.model.SourceFileAnalysis
import java.io.File

/**
 * Verantwortlich für die Zuordnung zwischen Quelltextzeilen (Dateipfad + Zeilennummer)
 * und JVM-/ART-Laufzeitklassen sowie zur Verifikation von Haltepunkten.
 */
interface SourceToClassMapper {

    /**
     * Analysiert den Inhalt einer Quelltextdatei (Kotlin oder Java) syntaktisch.
     */
    fun analyzeSource(filePath: String, content: String): SourceFileAnalysis

    /**
     * Ermittelt den vollqualifizierten Klassennamen (z. B. "com.example.MainActivity" oder
     * "com.example.UtilsKt"), dem eine gegebene Quelltextzeile zur Laufzeit angehört.
     * Gibt `null` zurück, wenn die Zeile keine ausführbare Anweisung darstellt (z. B. Leerzeile,
     * Kommentar, Import) oder nicht zuordenbar ist.
     */
    fun resolveClassName(filePath: String, content: String, line: Int): String?

    /**
     * Prüft anhand der Quelltextanalyse, ob an der angegebenen Zeilennummer ein Breakpoint
     * verifiziert werden kann (Zeile enthält ausführbaren Code).
     */
    fun isLineVerifiable(analysis: SourceFileAnalysis, line: Int): Boolean

    /**
     * Aktualisiert den `verified`-Status einer Liste von Haltepunkten für die angegebene Datei.
     * Haltepunkte auf nicht ausführbaren oder nicht zuordenbaren Zeilen werden als `verified = false` markiert.
     */
    fun verifyBreakpoints(filePath: String, content: String, breakpoints: List<Breakpoint>): List<Breakpoint>

    /**
     * Ermittelt anhand eines vollqualifizierten Klassennamens aus der Laufzeit
     * (z. B. aus einem Call-Stack-Frame) die zugehörige Quelltextdatei aus einer Liste
     * indizierter Projektdateien.
     */
    fun findSourceFileForClass(className: String, sourceFiles: List<File>): File?
}
