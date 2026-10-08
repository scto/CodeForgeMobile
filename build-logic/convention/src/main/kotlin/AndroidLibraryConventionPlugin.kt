import com.android.build.api.dsl.LibraryExtension
import com.codeforge.buildlogic.configureKotlin
import com.codeforge.buildlogic.configureKotlinAndroid
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.android.library")
                apply("org.jetbrains.kotlin.android")
            }

            extensions.configure<LibraryExtension> {
                configureKotlinAndroid(this)
                if (file("consumer-rules.pro").exists()) {
                    defaultConfig { consumerProguardFiles("consumer-rules.pro") }
                }
                // proguard-rules.pro nur verdrahten, wenn vorhanden; minify steuert das Modul selbst.
                if (file("proguard-rules.pro").exists()) {
                    buildTypes {
                        getByName("release") {
                            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
                        }
                    }
                }
            }
            configureKotlin()
        }
    }
}
