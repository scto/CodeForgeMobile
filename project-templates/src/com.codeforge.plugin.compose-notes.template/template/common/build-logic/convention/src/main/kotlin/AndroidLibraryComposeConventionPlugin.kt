import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/** Android-Bibliothek mit Jetpack Compose (z. B. `:core:designsystem`). */
class AndroidLibraryComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("app.android.library")
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
            extensions.configure<LibraryExtension> {
                buildFeatures { compose = true }
                testOptions { unitTests.isIncludeAndroidResources = true }
            }
            enableHostTestsForAllVariants()
            dependencies {
                add("implementation", library("androidx-compose-ui"))
                add("implementation", library("androidx-compose-foundation"))
                add("implementation", library("androidx-compose-material3"))
                add("implementation", library("androidx-compose-material-icons-core"))
                add("implementation", library("androidx-compose-ui-tooling-preview"))
                add("debugImplementation", library("androidx-compose-ui-tooling"))
            }
        }
    }
}
