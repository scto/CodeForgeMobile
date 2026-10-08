import com.codeforge.buildlogic.DownloadUtils
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.internal.os.OperatingSystem
import java.io.File

/**
 * Lädt die Bootstrap-Pakete des Terminals herunter und erzeugt daraus die Assembly-Datei
 * `src/main/cpp/termux-bootstrap-zip.S` (per `.incbin` ins native Modul eingebettet).
 *
 * Angepasst aus assets/TerminalBootstrapPackagesPlugin.kt für CodeForgeMobile. Gegenüber der
 * Vorlage läuft die Arbeit in einem Task (`embedTerminalBootstrap`, hängt an `preBuild`) statt
 * beim Konfigurieren – Gradle-Sync/IDE-Import brauchen so kein Netz mehr.
 *
 * Überschreibbar per `-P…`/`gradle.properties`:
 * - `codeforgeBootstrapVersion`     Release-Tag-Suffix (Standard: [BOOTSTRAP_PACKAGES_VERSION])
 * - `codeforgeBootstrapUrlTemplate` URL mit `%1$s` = Version, `%2$s` = ABI
 * - `codeforgeBootstrapSha256.<abi>` erwartete Prüfsumme je ABI (`aarch64`, `arm`, `x86_64`)
 * - `codeforgeBootstrapSkip=true`   Task überspringen (Offline-/CI-Build mit vorhandener .S-Datei)
 *
 * ACHTUNG: Die Standard-Pakete (AndroidIDE) sind für den Prefix
 * `/data/data/com.itsaky.androidide/files/usr` gebaut. Läuft die App als `com.codeforge.app`,
 * muss über `codeforgeBootstrapUrlTemplate` auf einen passend gebauten Fork gezeigt werden
 * (siehe docs/sub/TERMUX-PORTING.md).
 *
 * @author Thomas Schmid
 */
class TerminalBootstrapPackagesPlugin : Plugin<Project> {

    companion object {
        /** ABI → SHA-256 des Bootstrap-ZIPs. */
        private val BOOTSTRAP_PACKAGES = mapOf(
            "aarch64" to "68da03ed270d59cafcd37981b00583c713b42cb440adf03d1bf980f39a55181d",
            "arm" to "f3d9f2da7338bd00b02a8df192bdc22ad431a5eef413cecf4cd78d7a54ffffbf",
            "x86_64" to "6e4e50a206c3384c36f141b2496c1a7c69d30429e4e20268c51a84143530af67",
        )

        /** Tag-Suffix des GitHub-Releases. */
        private const val BOOTSTRAP_PACKAGES_VERSION = "16.12.2023"

        private const val PACKAGES_DOWNLOAD_URL =
            "https://github.com/AndroidIDEOfficial/terminal-packages/releases/download/bootstrap-%1\$s/bootstrap-%2\$s.zip"
    }

    override fun apply(target: Project) {
        with(target) {
            val version = (findProperty("codeforgeBootstrapVersion") as String?) ?: BOOTSTRAP_PACKAGES_VERSION
            val urlTemplate = (findProperty("codeforgeBootstrapUrlTemplate") as String?) ?: PACKAGES_DOWNLOAD_URL
            val skip = (findProperty("codeforgeBootstrapSkip") as String?)?.toBoolean() ?: false
            val checksums = BOOTSTRAP_PACKAGES.mapValues { (abi, default) ->
                (findProperty("codeforgeBootstrapSha256.$abi") as String?) ?: default
            }
            val bootstrapOut = layout.buildDirectory.dir("bootstrap-packages").get().asFile
            val asmFile = file("src/main/cpp/termux-bootstrap-zip.S")

            val embed = tasks.register("embedTerminalBootstrap") {
                group = "codeforge"
                description = "Lädt die Bootstrap-Pakete und bettet sie als .S-Blob ein (termux-bootstrap-zip.S)."
                outputs.file(asmFile)
                onlyIf { !skip }

                doLast {
                    val files = checksums.map { (abi, sha256) ->
                        val zip = File(bootstrapOut, "bootstrap-$abi.zip")
                        zip.parentFile.mkdirs()
                        DownloadUtils.doDownload(
                            file = zip,
                            remoteUrl = urlTemplate.format(version, abi),
                            expectedChecksum = sha256,
                            logger = logger,
                        )
                        abi to zip
                    }.toMap()

                    asmFile.parentFile.mkdirs()
                    asmFile.writeText(
                        """
                        .global blob
                        .global blob_size
                        .section .rodata
                    blob:
                    #if defined __aarch64__
                        .incbin "${escapePathOnWindows(files.getValue("aarch64").absolutePath)}"
                    #elif defined __arm__
                        .incbin "${escapePathOnWindows(files.getValue("arm").absolutePath)}"
                    #elif defined __x86_64__
                        .incbin "${escapePathOnWindows(files.getValue("x86_64").absolutePath)}"
                    #else
                    # error Unsupported arch
                    #endif
                    1:
                    blob_size:
                        .int 1b - blob
                    """.trimIndent() + "\n",
                    )
                }
            }

            tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(embed) }
        }
    }

    private fun escapePathOnWindows(path: String): String =
        if (OperatingSystem.current().isWindows) path.replace("\\", "\\\\") else path
}
