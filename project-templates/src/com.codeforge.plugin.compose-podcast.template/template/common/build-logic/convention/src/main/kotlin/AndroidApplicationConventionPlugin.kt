import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.HostTestBuilder
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/** App-Modul: Android-Anwendung mit Compose, Hilt und Qualitätsprüfungen. */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
            pluginManager.apply("app.android.hilt")
            pluginManager.apply("app.quality")

            extensions.configure<ApplicationExtension> {
                compileSdk = sdk("compileSdk")
                defaultConfig {
                    minSdk = sdk("minSdk")
                    targetSdk = sdk("targetSdk")
                }
                compileOptions {
                    sourceCompatibility = JAVA_VERSION
                    targetCompatibility = JAVA_VERSION
                }
                buildFeatures { compose = true }
                testOptions { unitTests.isIncludeAndroidResources = true }
            }
            extensions.configure<ApplicationAndroidComponentsExtension> {
                beforeVariants { builder -> builder.hostTests[HostTestBuilder.UNIT_TEST_TYPE]?.enable = true }
            }
            allowModulesWithoutTests()

            dependencies {
                add("implementation", library("androidx-core-ktx"))
                add("implementation", library("androidx-activity-compose"))
                add("implementation", library("androidx-compose-ui"))
                add("implementation", library("androidx-compose-material3"))
                add("implementation", library("androidx-compose-material-icons-core"))
                add("implementation", library("androidx-compose-ui-tooling-preview"))
                add("debugImplementation", library("androidx-compose-ui-tooling"))
                add("implementation", library("androidx-lifecycle-runtime-compose"))
                add("testImplementation", library("junit"))
            }
        }
    }
}
