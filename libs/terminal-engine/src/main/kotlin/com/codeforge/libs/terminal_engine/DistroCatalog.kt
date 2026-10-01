// Modul: :libs:terminal-engine
package com.codeforge.libs.terminal_engine

data class RootfsSource(val url: String, val archiveFormat: ArchiveFormat)

enum class ArchiveFormat { TAR_GZ, TAR_XZ }

object DistroCatalog {

    val ubuntuArm64 = RootfsSource(
        url = "https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/ubuntu-base-24.04.4-base-arm64.tar.gz",
        archiveFormat = ArchiveFormat.TAR_GZ
    )

    fun sourceFor(distroId: String): RootfsSource = ubuntuArm64
}
