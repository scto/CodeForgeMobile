import dev.detekt.gradle.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Wendet detekt auf das Modul an. Regeln: `config/detekt/detekt.yml` im Projekt-Root (eine Datei für alle Module).
 *
 * - `buildUponDefaultConfig = true`: Regeln, die in der Datei fehlen, gelten mit detekt-Standard.
 * - Scharf (`ignoreFailures = false`): Neue Verstöße lassen `check` bzw. `./gradlew detekt` scheitern.
 *   Bestehende stehen in der Baseline des Moduls und werden nicht gemeldet.
 * - Bestand bewusst abbauen: Verstoß beheben, dann `./gradlew :<modul>:detektBaseline` (Datei schrumpft).
 * - Baseline: `config/detekt/baseline/<Modulpfad>.xml`; Module ohne Datei haben keinen Bestand.
 */
class DetektConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("dev.detekt")
            extensions.configure<DetektExtension> {
                config.setFrom(rootProject.file("config/detekt/detekt.yml"))
                buildUponDefaultConfig.set(true)
                parallel.set(true)
                ignoreFailures.set(false)
                basePath.set(rootProject.layout.projectDirectory)
                baseline.set(rootProject.file("config/detekt/baseline/${path.trim(':').replace(':', '-')}.xml"))
            }
        }
    }
}
