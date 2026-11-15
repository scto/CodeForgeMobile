import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** Android-Bibliothek ohne Compose (z. B. `:core:data`, `:core:resources`). */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")
            pluginManager.apply("app.quality")
            extensions.configure<LibraryExtension> {
                compileSdk = sdk("compileSdk")
                defaultConfig { minSdk = sdk("minSdk") }
                compileOptions {
                    sourceCompatibility = JAVA_VERSION
                    targetCompatibility = JAVA_VERSION
                }
            }
            allowModulesWithoutTests()
        }
    }
}
