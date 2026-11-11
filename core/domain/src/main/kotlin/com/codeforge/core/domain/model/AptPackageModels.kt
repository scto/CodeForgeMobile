/**
 * Modul: :core:domain
 * @author Thomas Schmid
 */
package com.codeforge.core.domain.model

/**
 * Ein Paket des CodeForge-APT-Repos (Index oder `large-packages.txt`).
 *
 * @property installedVersion installierte Version, `null` wenn nicht installiert.
 * @property availableVersion neueste Version im Repo.
 * @property sizeKib installierte Größe in KiB (0 = unbekannt).
 * @property isLarge `true` für große Pakete (> 30 MiB, Release-Asset statt Pool).
 * @property hasUpdate `true`, wenn [availableVersion] neuer ist als [installedVersion].
 * @property isProtected `true` für Kernpakete (bash, apt, dpkg …): sie lassen sich nicht entfernen.
 */
data class AptPackage(
    val name: String,
    val installedVersion: String?,
    val availableVersion: String?,
    val sizeKib: Long,
    val isLarge: Boolean,
    val hasUpdate: Boolean,
    val isProtected: Boolean,
    val description: String,
) {
    /** Ob das Paket installiert ist. */
    val isInstalled: Boolean get() = installedVersion != null
}

/** Ereignis einer Paketaktion (Installieren, Aktualisieren, Entfernen). */
sealed interface PackageEvent {
    /** Fortschritt mit Meldung. */
    data class Progress(val percent: Int, val message: String) : PackageEvent
    /** Aktion für [name] erfolgreich. */
    data class Success(val name: String) : PackageEvent
    /** Aktion fehlgeschlagen. */
    data class Error(val exception: Throwable) : PackageEvent
}
