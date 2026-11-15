import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * Feature-Modul: Compose, Hilt, typsichere Navigation; hängt an `:core:domain`, `:core:resources`
 * und – falls vorhanden – `:core:designsystem`.
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("app.android.library.compose")
            pluginManager.apply("app.android.hilt")
            pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")
            dependencies {
                add("implementation", project(":core:domain"))
                add("implementation", project(":core:resources"))
                if (rootProject.findProject(":core:designsystem") != null) {
                    add("implementation", project(":core:designsystem"))
                }
                add("implementation", library("androidx-lifecycle-runtime-compose"))
                add("implementation", library("androidx-lifecycle-viewmodel-compose"))
                add("implementation", library("androidx-navigation-compose"))
                add("implementation", library("hilt-navigation-compose"))
                add("implementation", library("kotlinx-serialization-json"))
                add("testImplementation", library("junit"))
                add("testImplementation", library("kotlinx-coroutines-test"))
            }
        }
    }
}
