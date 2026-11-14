import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jlleitschuh.gradle.ktlint.KtlintExtension

/**
 * Wendet ktlint (Regeln: `.editorconfig` im Projekt-Root) und detekt ([DetektConventionPlugin]) auf das Modul an.
 *
 * `ignoreFailures = true`: Bestandscode wurde noch nie durch ktlint formatiert; Verstöße werden
 * berichtet (`./gradlew ktlintCheck`), brechen aber `check` nicht. Nach `./gradlew ktlintFormat`
 * hier auf `false` stellen, um ktlint zum harten Gate zu machen.
 */
class QualityConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jlleitschuh.gradle.ktlint")
            pluginManager.apply("codeforge.detekt")
            extensions.configure<KtlintExtension> {
                android.set(true)
                ignoreFailures.set(true)
            }
        }
    }
}
