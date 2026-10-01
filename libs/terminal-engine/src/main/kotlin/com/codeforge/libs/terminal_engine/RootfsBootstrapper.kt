// Modul: :libs:terminal-engine
package com.codeforge.libs.terminal_engine

import com.codeforge.core.domain.repository.BootstrapProgress
import com.codeforge.core.domain.repository.DistroBootstrapRepository
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RootfsBootstrapper @Inject constructor(
    private val distroBootstrapRepository: DistroBootstrapRepository
) {
    fun ensureRootfsReady(distro: String = "ubuntu", onStatusUpdate: (String) -> Unit = {}) {
        if (!distroBootstrapRepository.isBootstrapped(distro)) {
            runBlocking {
                distroBootstrapRepository.bootstrap(distro).collect { progress ->
                    when (progress) {
                        is BootstrapProgress.Downloading -> onStatusUpdate("Downloading rootfs... ${progress.percent}%")
                        is BootstrapProgress.Extracting -> onStatusUpdate("Extracting rootfs... ${progress.percent}%")
                        BootstrapProgress.Finalizing -> onStatusUpdate("Finalizing rootfs setup...")
                        BootstrapProgress.Completed -> onStatusUpdate("Rootfs ready.")
                        is BootstrapProgress.Failed -> onStatusUpdate("Rootfs bootstrap failed: ${progress.message}")
                    }
                }
            }
        }
    }
}
