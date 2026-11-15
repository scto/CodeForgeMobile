import dev.detekt.gradle.extensions.DetektExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jlleitschuh.gradle.ktlint.KtlintExtension

/**
 * Konsolenprogramm in Kotlin: Kotlin/JVM 17, `application` (`./gradlew run`), JUnit,
 * ktlint (Regeln: `.editorconfig`) und detekt (Regeln: `config/detekt/detekt.yml`).
 */
class KotlinApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.jvm")
            pluginManager.apply("application")
            pluginManager.apply("org.jlleitschuh.gradle.ktlint")
            pluginManager.apply("dev.detekt")
            val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

            extensions.configure<JavaPluginExtension> {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
            extensions.configure<KotlinJvmProjectExtension> {
                compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
            }
            extensions.configure<KtlintExtension> {
                version.set(libs.findVersion("ktlint").get().requiredVersion)
            }
            extensions.configure<DetektExtension> {
                config.setFrom(rootProject.file("config/detekt/detekt.yml"))
                buildUponDefaultConfig.set(true)
            }
            dependencies {
                add("testImplementation", libs.findLibrary("junit").get())
            }
        }
    }
}
