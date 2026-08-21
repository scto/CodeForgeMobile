// Modul: :libs:terminal-engine
package com.codeforge.libs.terminal_engine

/**
 * TODO: URLs regelmäßig gegen die jeweiligen offiziellen Mirrors prüfen/aktualisieren —
 * Minor-Versionen und Pfade ändern sich mit neuen Releases. Aktuell nur arm64 (aarch64),
 * da das der weit überwiegende Android-Gerätemarkt ist; weitere ABIs (x86_64 für
 * Emulatoren) können hier ergänzt werden.
 */
data class RootfsSource(val url: String, val archiveFormat: ArchiveFormat)

enum class ArchiveFormat { TAR_GZ, TAR_XZ }

object DistroCatalog {

    private val alpineArm64 = RootfsSource(
        url = "https://dl-cdn.alpinelinux.org/alpine/v3.20/releases/aarch64/alpine-minirootfs-3.20.3-aarch64.tar.gz",
        archiveFormat = ArchiveFormat.TAR_GZ
    )

    private val ubuntuArm64 = RootfsSource(
        url = "https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/ubuntu-base-24.04.1-base-arm64.tar.gz",
        archiveFormat = ArchiveFormat.TAR_GZ
    )

    private val debianArm64 = RootfsSource(
        url = "https://github.com/debuerreotype/docker-debian-artifacts/raw/dist-arm64v8/bookworm/rootfs.tar.xz",
        archiveFormat = ArchiveFormat.TAR_XZ
    )

    fun sourceFor(distroId: String): RootfsSource? = when (distroId) {
        "alpine" -> alpineArm64
        "ubuntu" -> ubuntuArm64
        "debian" -> debianArm64
        else -> null
    }
}
