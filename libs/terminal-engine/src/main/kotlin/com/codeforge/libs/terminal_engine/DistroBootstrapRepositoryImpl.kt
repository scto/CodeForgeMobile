// Modul: :libs:terminal-engine
package com.codeforge.libs.terminal_engine

import com.codeforge.core.domain.repository.BootstrapProgress
import com.codeforge.core.domain.repository.DistroBootstrapRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bootstrapped die PRoot-Rootfs für die gewählte Distro (Abschnitt 6 des Skills).
 * TODO: Download via bekannter Rootfs-Mirrors (z.B. Alpine Minirootfs, Ubuntu Base),
 * Entpacken via tar/proot-static, Verifikation via Checksumme.
 * Aktuell: Fortschritts-Flow als Grundgerüst, das durch echten Download/Extract-Code
 * ersetzt wird (siehe TerminalSession-Bridge, JNI/PTY analog Termux).
 */
@Singleton
class DistroBootstrapRepositoryImpl @Inject constructor() : DistroBootstrapRepository {

    override fun bootstrap(distro: String): Flow<BootstrapProgress> = flow {
        try {
            for (percent in 0..100 step 10) {
                delay(150)
                emit(BootstrapProgress.Downloading(percent))
            }
            for (percent in 0..100 step 20) {
                delay(150)
                emit(BootstrapProgress.Extracting(percent))
            }
            emit(BootstrapProgress.Finalizing)
            delay(200)
            emit(BootstrapProgress.Completed)
        } catch (t: Throwable) {
            emit(BootstrapProgress.Failed(t.message ?: "Unbekannter Fehler beim Bootstrap von $distro"))
        }
    }
}
