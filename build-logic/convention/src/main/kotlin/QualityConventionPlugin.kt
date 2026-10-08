import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jlleitschuh.gradle.ktlint.KtlintExtension

/**
 * Wendet ktlint auf das Modul an (Regeln: `.editorconfig` im Projekt-Root).
 *
 * `ignoreFailures = true`: Bestandscode wurde noch nie durch ktlint formatiert; Verstöße werden
 * berichtet (`./gradlew ktlintCheck`), brechen aber `check` nicht. Nach `./gradlew ktlintFormat`
 * hier auf `false` stellen, um ktlint zum harten Gate zu machen.
 */
class QualityConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jlleitschuh.gradle.ktlint")
            extensions.configure<KtlintExtension> {
                android.set(true)
                ignoreFailures.set(true)
            }
        }
    }
}
