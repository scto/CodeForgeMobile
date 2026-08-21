// Modul: :core:domain
package com.codeforge.core.domain.repository

import kotlinx.coroutines.flow.Flow

sealed interface BootstrapProgress {
    data class Downloading(val percent: Int) : BootstrapProgress
    data class Extracting(val percent: Int) : BootstrapProgress
    data object Finalizing : BootstrapProgress
    data object Completed : BootstrapProgress
    data class Failed(val message: String) : BootstrapProgress
}

/**
 * Bootstrapped die PRoot-Rootfs der gewählten Distro (Abschnitt 6). Implementiert in
 * :libs:terminal-engine, konsumiert von :feature:onboarding (SetupScreen) und
 * :feature:settings (Distro-Wechsel).
 */
interface DistroBootstrapRepository {
    fun bootstrap(distro: String): Flow<BootstrapProgress>

    /** Lokaler Pfad der (bereits oder noch zu bootstrappenden) Rootfs für [distro]. */
    fun rootfsPath(distro: String): String
}
