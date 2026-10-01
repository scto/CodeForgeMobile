import com.codeforge.buildlogic.DownloadUtils
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.internal.os.OperatingSystem
import java.io.File

/**
 * Gradle plugin which downloads the bootstrap packages for the terminal
 * and generates the C++ assembly file termux-bootstrap-zip.S.
 *
 * Adapted from assets/TerminalBootstrapPackagesPlugin.kt for CodeForgeMobile.
 *
 * @author Thomas Schmid
 */
class TerminalBootstrapPackagesPlugin : Plugin<Project> {

    companion object {
        /**
         * The bootstrap packages, mapped with the CPU ABI as the key and the ZIP file's sha256sum as the value.
         */
        private val BOOTSTRAP_PACKAGES = mapOf(
            "aarch64" to "68da03ed270d59cafcd37981b00583c713b42cb440adf03d1bf980f39a55181d",
            "arm" to "f3d9f2da7338bd00b02a8df192bdc22ad431a5eef413cecf4cd78d7a54ffffbf",
            "x86_64" to "6e4e50a206c3384c36f141b2496c1a7c69d30429e4e20268c51a84143530af67"
        )

        /**
         * The bootstrap packages version, basically the tag name of the GitHub release.
         */
        private const val BOOTSTRAP_PACKAGES_VERSION = "16.12.2023"

        private const val PACKAGES_DOWNLOAD_URL =
            "https://github.com/AndroidIDEOfficial/terminal-packages/releases/download/bootstrap-%1\$s/bootstrap-%2\$s.zip"
    }

    override fun apply(target: Project) {
        target.run {
            val bootstrapOut = project.layout.buildDirectory.dir("bootstrap-packages")
                .get().asFile

            val files = BOOTSTRAP_PACKAGES.map { (arch, sha256) ->
                val file = File(bootstrapOut, "bootstrap-${arch}.zip")
                file.parentFile.mkdirs()

                DownloadUtils.doDownload(
                    file = file,
                    remoteUrl = PACKAGES_DOWNLOAD_URL.format(BOOTSTRAP_PACKAGES_VERSION, arch),
                    expectedChecksum = sha256,
                    logger = logger
                )

                arch to file
            }.toMap()

            val asmFile = project.file("src/main/cpp/termux-bootstrap-zip.S")
            asmFile.parentFile.mkdirs()
            asmFile.writeText(
                """
                .global blob
                .global blob_size
                .section .rodata
            blob:
           #if defined __aarch64__
                .incbin "${escapePathOnWindows(files["aarch64"]!!.absolutePath)}"
            #elif defined __arm__
                .incbin "${escapePathOnWindows(files["arm"]!!.absolutePath)}"
            #elif defined __x86_64__
                .incbin "${escapePathOnWindows(files["x86_64"]!!.absolutePath)}"
            #else
            # error Unsupported arch
            #endif
            1:
            blob_size:
                .int 1b - blob
            """.trimIndent()
            )
        }
    }

    private fun escapePathOnWindows(path: String): String {
        return if (OperatingSystem.current().isWindows) {
            path.replace("\\", "\\\\")
        } else {
            path
        }
    }
}
