import com.codeforge.buildlogic.configureKotlin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeCompatibilityRule
import org.gradle.api.attributes.AttributeDisambiguationRule
import org.gradle.api.attributes.CompatibilityCheckDetails
import org.gradle.api.attributes.MultipleCandidatesDetails
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import com.android.build.api.attributes.BuildTypeAttr

class KotlinLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("org.jetbrains.kotlin.jvm")
            }

            extensions.configure<JavaPluginExtension> {
                sourceCompatibility = org.gradle.api.JavaVersion.VERSION_17
                targetCompatibility = org.gradle.api.JavaVersion.VERSION_17
            }
            configureKotlin()

            // Erlaubt reinen JVM-Modulen (z.B. :examples:*-lsp-plugin) Abhängigkeiten
            // auf Android-Bibliotheken (:core:domain, :libs:plugin-api etc.) aufzulösen.
            dependencies.attributesSchema {
                attribute(KotlinPlatformType.attribute) {
                    compatibilityRules.add(AndroidJvmCompatibilityRule::class.java)
                }
                attribute(BuildTypeAttr.ATTRIBUTE) {
                    disambiguationRules.add(BuildTypeDisambiguationRule::class.java)
                }
                attribute(Attribute.of("artifactType", String::class.java)) {
                    disambiguationRules.add(ArtifactTypeDisambiguationRule::class.java)
                }
            }
        }
    }

    class AndroidJvmCompatibilityRule : AttributeCompatibilityRule<KotlinPlatformType> {
        override fun execute(details: CompatibilityCheckDetails<KotlinPlatformType>) {
            if (details.consumerValue == KotlinPlatformType.jvm && details.producerValue == KotlinPlatformType.androidJvm) {
                details.compatible()
            }
        }
    }

    class BuildTypeDisambiguationRule : AttributeDisambiguationRule<BuildTypeAttr> {
        override fun execute(details: MultipleCandidatesDetails<BuildTypeAttr>) {
            val debug = details.candidateValues.firstOrNull { it.name == "debug" }
            if (debug != null) {
                details.closestMatch(debug)
            }
        }
    }

    class ArtifactTypeDisambiguationRule : AttributeDisambiguationRule<String> {
        override fun execute(details: MultipleCandidatesDetails<String>) {
            if (details.candidateValues.contains("jar")) {
                details.closestMatch("jar")
            }
        }
    }
}
