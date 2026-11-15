import com.android.build.api.variant.HostTestBuilder
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType

/** Version Catalog `gradle/libs.versions.toml`. */
internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun Project.library(alias: String): Provider<MinimalExternalModuleDependency> = libs.findLibrary(alias).get()

internal fun Project.sdk(name: String): Int = libs.findVersion(name).get().requiredVersion.toInt()

internal val JAVA_VERSION = JavaVersion.VERSION_17

/** Module ohne eigene Tests lassen `test` nicht scheitern (Gradle 9 meldet das sonst als Fehler). */
internal fun Project.allowModulesWithoutTests() {
    tasks.withType<Test>().configureEach { failOnNoDiscoveredTests.set(false) }
}

/** Die Compose-Vorschau der IDE braucht Unit-Tests in jeder Variante (auch release). */
internal fun Project.enableHostTestsForAllVariants() {
    extensions.findByType(LibraryAndroidComponentsExtension::class.java)?.beforeVariants { builder ->
        builder.hostTests[HostTestBuilder.UNIT_TEST_TYPE]?.enable = true
    }
}
