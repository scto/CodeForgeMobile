/**
 * Modul: :libs:dependency-updater-api
 * @author Thomas Schmid
 */
package com.codeforge.libs.dependency_updater_api

import kotlinx.coroutines.flow.Flow

interface DependencyUpdateRepository {

    fun observe(rootPath: String): Flow<ProjectUpdateState>

    /**
     * Wird bei jedem Öffnen eines Projekts (Neu/Clone/Import/Wieder-Öffnen) aufgerufen:
     * setzt „Ask later“-Markierungen zurück und startet die Prüfung im Hintergrund.
     */
    fun onProjectOpened(rootPath: String)

    /** Prüft Server-Versionen. [force] ignoriert gecachte Metadaten. */
    suspend fun check(rootPath: String, force: Boolean = false): Result<List<DependencyUpdate>>

    /** Merkt das Update dauerhaft (pro Bibliothek + Zielversion) als dismissed. */
    suspend fun dismiss(rootPath: String, update: DependencyUpdate)

    /** „Ask later“: bis zum nächsten Projektöffnen nicht mehr im Dialog anbieten. */
    fun snooze(rootPath: String, update: DependencyUpdate)

    /**
     * Schreibt die neuen Versionen in die Dateien auf der Platte (frisch neu eingelesen und
     * validiert, atomar je Datei) und prüft danach neu. Offene Editor-Puffer muss der Aufrufer
     * vorher speichern und danach neu laden ([ApplyResult.changedFiles]).
     */
    suspend fun apply(rootPath: String, updates: List<DependencyUpdate>): Result<ApplyResult>

    /**
     * Zeilen-Annotationen für eine im Editor geöffnete Datei anhand des LIVE-Textes (nicht der
     * Festplattenversion), damit Chips auch nach ungespeicherten Änderungen an der richtigen
     * Zeile stehen. Schnell und nicht blockierend.
     */
    fun annotate(rootPath: String, filePath: String, text: String): List<FileUpdateAnnotation>
}
