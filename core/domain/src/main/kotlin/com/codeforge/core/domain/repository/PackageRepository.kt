/**
 * Modul: :core:domain
 * @author Thomas Schmid
 */
package com.codeforge.core.domain.repository

import com.codeforge.core.domain.model.AptPackage
import com.codeforge.core.domain.model.PackageEvent
import kotlinx.coroutines.flow.Flow

/**
 * Paketverwaltung des CodeForge-APT-Repos (Aufsatz auf das Termux-Skript `codeforge-env`):
 * Paketliste samt Installationsstatus, Installieren/Aktualisieren/Entfernen, Paketlisten neu laden.
 * Große Pakete (> 30 MiB, `large-packages.txt`) behandelt das Skript transparent.
 * Implementiert in :libs:terminal-engine, konsumiert von :feature:settings.
 */
interface PackageRepository {
    /** Alle Pakete des Repos mit lokalem Status. */
    suspend fun listPackages(): Result<List<AptPackage>>

    /** Installiert oder aktualisiert ein Paket (installiert immer die neueste Version). */
    fun install(name: String): Flow<PackageEvent>

    /** Entfernt ein Paket. */
    fun remove(name: String): Flow<PackageEvent>

    /** Lädt die Paketlisten neu (`apt update`). */
    fun refreshIndex(): Flow<PackageEvent>

    /** Aktualisiert alle installierten Pakete (`apt upgrade`). */
    fun upgradeAll(): Flow<PackageEvent>
}
