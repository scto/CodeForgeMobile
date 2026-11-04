import com.android.build.api.dsl.ApplicationExtension
import com.codeforge.buildlogic.SigningKeys
import com.codeforge.buildlogic.loadReleaseSigning
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Release-Signatur für App-Module (`codeforge.android.signing`, nach `codeforge.android.application` anwenden).
 *
 * - Zugangsdaten aus `keystore.properties` (Projektstamm) oder `CODEFORGE_STORE_FILE`/`_STORE_PASSWORD`/
 *   `_KEY_ALIAS`/`_KEY_PASSWORD` (CI: Keystore aus dem Base64-Secret wiederhergestellt), siehe `docs/dev/Signing.md`.
 * - Legt die Signatur-Konfiguration `release` an und weist sie dem Build-Typ `release` zu.
 * - Ohne Zugangsdaten fällt `release` auf den Debug-Schlüssel zurück (installierbar, nicht veröffentlichbar) und
 *   gibt eine Warnung aus. Mit `CI=true` oder `-Pcodeforge.signing.required=true` bricht stattdessen jeder
 *   Release-Build (assemble/bundle/package…Release) ab.
 * - Ein unvollständiger Satz oder ein fehlender Keystore bricht immer ab.
 */
class AndroidSigningConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.withPlugin("com.android.application") {
                val release = loadReleaseSigning()

                extensions.configure<ApplicationExtension> {
                    if (release != null) {
                        signingConfigs.create("release") {
                            storeFile = release.storeFile
                            storePassword = release.storePassword
                            keyAlias = release.keyAlias
                            keyPassword = release.keyPassword
                        }
                    }
                    buildTypes.getByName("release") {
                        signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
                    }
                }

                if (release == null) {
                    val required = System.getenv("CI") == "true" ||
                        providers.gradleProperty(SigningKeys.PROP_REQUIRED).orNull == "true"
                    val projectPath = path
                    gradle.taskGraph.whenReady {
                        val releaseBuild = allTasks.any {
                            it.project.path == projectPath &&
                                it.name.matches(Regex("(assemble|bundle|package)(.*)Release"))
                        }
                        if (!releaseBuild) return@whenReady
                        val msg = "Kein Release-Schlüssel konfiguriert (keystore.properties oder " +
                            "CODEFORGE_STORE_*-Variablen, docs/dev/Signing.md)"
                        if (required) throw GradleException("$msg – Release-Build abgebrochen.")
                        logger.warn("WARNUNG: $msg – $projectPath wird mit dem DEBUG-Schlüssel signiert.")
                    }
                }
            }
        }
    }
}
