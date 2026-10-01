// Modul: :core:domain
package com.codeforge.core.domain.repository

import kotlinx.coroutines.flow.Flow

sealed class BootstrapProgress {
    data class Downloading(val percent: Int) : BootstrapProgress()
    data class Extracting(val percent: Int) : BootstrapProgress()
    data object Finalizing : BootstrapProgress()
    data object Completed : BootstrapProgress()
    data class Failed(val message: String) : BootstrapProgress()
}

interface DistroBootstrapRepository {
    fun bootstrap(distro: String = "ubuntu"): Flow<BootstrapProgress>
    fun isBootstrapped(distro: String = "ubuntu"): Boolean
    fun rootfsPath(distro: String = "ubuntu"): String
}
