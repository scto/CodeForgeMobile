import dev.detekt.gradle.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Wendet detekt auf das Modul an. Regeln: `config/detekt/detekt.yml` im Projekt-Root (eine Datei für alle Module).
 *
 * - `buildUponDefaultConfig = true`: Regeln, die in der Datei fehlen, gelten mit detekt-Standard.
 * - `ignoreFailures = true`: Der Bestandscode wurde nie gegen diese (strengen) Regeln geprüft; Verstöße werden
 *   berichtet (`./gradlew detekt`), brechen aber `check` nicht. Zum harten Gate machen: zuerst eine Baseline
 *   schreiben (`./gradlew detektBaseline`), dann hier auf `false` stellen.
 * - Baseline: `config/detekt/baseline/<Modulpfad>.xml` wird genutzt, sobald die Datei existiert.
 */
class DetektConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("dev.detekt")
            extensions.configure<DetektExtension> {
                config.setFrom(rootProject.file("config/detekt/detekt.yml"))
                buildUponDefaultConfig.set(true)
                parallel.set(true)
                ignoreFailures.set(true)
                basePath.set(rootProject.layout.projectDirectory)
                baseline.set(rootProject.file("config/detekt/baseline/${path.trim(':').replace(':', '-')}.xml"))
            }
        }
    }
}
