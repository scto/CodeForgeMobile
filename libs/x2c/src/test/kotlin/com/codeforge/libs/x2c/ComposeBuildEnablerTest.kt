package com.codeforge.libs.x2c

import com.codeforge.libs.x2c.build.ComposeBuildEnablerImpl
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ComposeBuildEnablerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val enabler = ComposeBuildEnablerImpl()

    @Test
    fun testPrerequisitesFailsWhenMinSdkBelow21() = runBlocking {
        val proj = tempFolder.newFolder("LowSdkApp")
        val appBuild = File(proj, "app/build.gradle.kts").apply {
            parentFile.mkdirs()
            writeText(
                """
                plugins {
                    id("com.android.application")
                    id("org.jetbrains.kotlin.android") version "2.0.21"
                }
                android {
                    namespace = "com.example.lowsdk"
                    compileSdk = 34
                    defaultConfig {
                        minSdk = 19
                        targetSdk = 34
                    }
                }
                dependencies {}
                """.trimIndent()
            )
        }

        val result = enabler.checkPrerequisites(proj.absolutePath)
        assertTrue("Prerequisites check executed", result.isSuccess)
        val prereq = result.getOrThrow()
        assertFalse("minSdk below 21 must not be satisfied", prereq.isMinSdkSatisfied)
        assertEquals(19, prereq.detectedMinSdk)
        assertTrue(
            "Error message must specify minSdk >= 21 and official doc reference",
            prereq.errors.any { it.contains("minSdk >= 21") && it.contains("developer.android.com") }
        )

        // enableComposeInProject must abort without partial changes
        val enableResult = enabler.enableComposeInProject(
            projectPath = proj.absolutePath,
            useVersionCatalog = false,
            wireActivities = false,
            hasConstraintLayout = false
        )
        assertTrue("enableCompose must fail when minSdk < 21", enableResult.isFailure)
        assertTrue(enableResult.exceptionOrNull()?.message?.contains("minSdk >= 21") == true)
        // File remains unchanged
        assertFalse("Build file must not contain compose", appBuild.readText().contains("compose = true"))
    }

    @Test
    fun testEnableComposeKotlinDslWithKotlin2() = runBlocking {
        val proj = tempFolder.newFolder("KotlinDslApp")
        File(proj, "build.gradle.kts").apply {
            writeText(
                """
                plugins {
                    id("com.android.application") version "8.7.0" apply false
                    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
                }
                """.trimIndent()
            )
        }
        val appBuild = File(proj, "app/build.gradle.kts").apply {
            parentFile.mkdirs()
            writeText(
                """
                plugins {
                    id("com.android.application")
                    id("org.jetbrains.kotlin.android")
                }
                android {
                    namespace = "com.example.kotlindsl"
                    compileSdk = 35
                    defaultConfig {
                        minSdk = 24
                        targetSdk = 35
                    }
                }
                dependencies {
                    implementation("androidx.core:core-ktx:1.15.0")
                }
                """.trimIndent()
            )
        }

        val result = enabler.enableComposeInProject(
            projectPath = proj.absolutePath,
            useVersionCatalog = false,
            wireActivities = true,
            hasConstraintLayout = true
        )
        assertTrue("enableCompose succeeds", result.isSuccess)
        val buildResult = result.getOrThrow()
        assertEquals("2.0.21", buildResult.kotlinVersionUsed)

        val updatedContent = appBuild.readText()
        // 1. Compose compiler plugin added
        assertTrue("Contains compose compiler plugin", updatedContent.contains("id(\"org.jetbrains.kotlin.plugin.compose\")"))
        // 2. buildFeatures { compose = true }
        assertTrue("Contains buildFeatures compose = true", updatedContent.contains("compose = true"))
        // 3. Compose dependencies
        assertTrue("Contains compose-bom", updatedContent.contains("androidx.compose:compose-bom:2024.10.01"))
        assertTrue("Contains compose ui", updatedContent.contains("androidx.compose.ui:ui"))
        assertTrue("Contains compose material3", updatedContent.contains("androidx.compose.material3:material3"))
        assertTrue("Contains debug ui-tooling", updatedContent.contains("debugImplementation(\"androidx.compose.ui:ui-tooling\")"))
        assertTrue("Contains activity-compose", updatedContent.contains("androidx.activity:activity-compose:1.9.3"))
        assertTrue("Contains constraintlayout-compose", updatedContent.contains("androidx.constraintlayout:constraintlayout-compose:1.1.0"))
        // Existing dependencies preserved
        assertTrue("Contains core-ktx", updatedContent.contains("androidx.core:core-ktx:1.15.0"))
    }

    @Test
    fun testEnableComposeGroovyDslWithKotlin19() = runBlocking {
        val proj = tempFolder.newFolder("GroovyApp")
        File(proj, "build.gradle").apply {
            writeText(
                """
                buildscript {
                    dependencies {
                        classpath 'com.android.tools.build:gradle:8.2.0'
                        classpath 'org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.24'
                    }
                }
                """.trimIndent()
            )
        }
        val appBuild = File(proj, "app/build.gradle").apply {
            parentFile.mkdirs()
            writeText(
                """
                apply plugin: 'com.android.application'
                apply plugin: 'org.jetbrains.kotlin.android'

                android {
                    compileSdkVersion 34
                    defaultConfig {
                        minSdkVersion 21
                        targetSdkVersion 34
                    }
                }

                dependencies {
                    implementation 'androidx.appcompat:appcompat:1.7.0'
                }
                """.trimIndent()
            )
        }

        val result = enabler.enableComposeInProject(
            projectPath = proj.absolutePath,
            useVersionCatalog = false,
            wireActivities = false,
            hasConstraintLayout = false
        )
        assertTrue("enableCompose succeeds for Groovy", result.isSuccess)
        val buildResult = result.getOrThrow()
        assertEquals("1.9.24", buildResult.kotlinVersionUsed)

        val updated = appBuild.readText()
        // Groovy style preserved
        assertTrue("Contains buildFeatures compose = true", updated.contains("compose = true"))
        // Kotlin 1.9 uses composeOptions instead of plugin
        assertTrue("Contains composeOptions", updated.contains("composeOptions"))
        assertTrue("Contains kotlinCompilerExtensionVersion 1.5.14", updated.contains("1.5.14"))
        assertFalse("Must NOT contain Kotlin 2 compose plugin", updated.contains("kotlin.plugin.compose"))
        // Dependencies
        assertTrue("Contains BOM", updated.contains("androidx.compose:compose-bom:2024.10.01"))
        assertTrue("Contains material3", updated.contains("androidx.compose.material3:material3"))
        assertTrue("Preserves appcompat", updated.contains("androidx.appcompat:appcompat:1.7.0"))
    }

    @Test
    fun testEnableComposeWithVersionCatalog() = runBlocking {
        val proj = tempFolder.newFolder("CatalogApp")
        val catalogFile = File(proj, "gradle/libs.versions.toml").apply {
            parentFile.mkdirs()
            writeText(
                """
                # Existing catalog comments
                [versions]
                agp = "8.6.0"
                kotlin = "2.0.21"

                [libraries]
                androidx-core = { group = "androidx.core", name = "core-ktx", version = "1.13.1" }

                [plugins]
                android-app = { id = "com.android.application", version.ref = "agp" }
                """.trimIndent()
            )
        }
        val appBuild = File(proj, "app/build.gradle.kts").apply {
            parentFile.mkdirs()
            writeText(
                """
                plugins {
                    alias(libs.plugins.android.app)
                    id("org.jetbrains.kotlin.android")
                }
                android {
                    namespace = "com.example.catalog"
                    compileSdk = 35
                    defaultConfig {
                        minSdk = 26
                    }
                }
                dependencies {
                    implementation(libs.androidx.core)
                }
                """.trimIndent()
            )
        }

        val result = enabler.enableComposeInProject(
            projectPath = proj.absolutePath,
            useVersionCatalog = true,
            wireActivities = true,
            hasConstraintLayout = false
        )
        assertTrue(result.isSuccess)

        val updatedCatalog = catalogFile.readText()
        // Comments preserved
        assertTrue("Comments preserved", updatedCatalog.contains("# Existing catalog comments"))
        // Versions added
        assertTrue("compose-bom version in catalog", updatedCatalog.contains("compose-bom = \"2024.10.01\""))
        assertTrue("activity-compose version in catalog", updatedCatalog.contains("activity-compose = \"1.9.3\""))
        // Libraries added
        assertTrue("androidx-compose-bom in libraries", updatedCatalog.contains("androidx-compose-bom"))
        assertTrue("androidx-compose-ui in libraries", updatedCatalog.contains("androidx-compose-ui"))
        assertTrue("androidx-compose-material3 in libraries", updatedCatalog.contains("androidx-compose-material3"))
        assertTrue("androidx-activity-compose in libraries", updatedCatalog.contains("androidx-activity-compose"))

        // Build file references catalog aliases
        val updatedBuild = appBuild.readText()
        assertTrue("build.gradle.kts references libs.androidx.compose.bom", updatedBuild.contains("libs.androidx.compose.bom"))
        assertTrue("build.gradle.kts references libs.androidx.compose.ui", updatedBuild.contains("libs.androidx.compose.ui"))
        assertTrue("build.gradle.kts references libs.androidx.compose.material3", updatedBuild.contains("libs.androidx.compose.material3"))
        assertTrue("build.gradle.kts references libs.androidx.activity.compose", updatedBuild.contains("libs.androidx.activity.compose"))
    }

    @Test
    fun testDoesNotDuplicateExistingComposeConfiguration() = runBlocking {
        val proj = tempFolder.newFolder("ExistingComposeApp")
        val appBuild = File(proj, "app/build.gradle.kts").apply {
            parentFile.mkdirs()
            writeText(
                """
                plugins {
                    id("com.android.application")
                    id("org.jetbrains.kotlin.android") version "2.0.21"
                    id("org.jetbrains.kotlin.plugin.compose")
                }
                android {
                    namespace = "com.example.existing"
                    compileSdk = 35
                    defaultConfig {
                        minSdk = 24
                    }
                    buildFeatures {
                        compose = true
                    }
                }
                dependencies {
                    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
                    implementation("androidx.compose.ui:ui")
                    implementation("androidx.compose.ui:ui-tooling-preview")
                    implementation("androidx.compose.material3:material3")
                    debugImplementation("androidx.compose.ui:ui-tooling")
                }
                """.trimIndent()
            )
        }

        val originalContent = appBuild.readText()
        val result = enabler.enableComposeInProject(
            projectPath = proj.absolutePath,
            useVersionCatalog = false,
            wireActivities = false,
            hasConstraintLayout = false
        )
        assertTrue(result.isSuccess)
        val buildResult = result.getOrThrow()
        // No new dependencies added
        assertTrue("No duplicate dependencies added", buildResult.addedDependencies.isEmpty())
        val afterContent = appBuild.readText()
        // Plugin count remains 1
        val pluginOccurrences = Regex("""org\.jetbrains\.kotlin\.plugin\.compose""").findAll(afterContent).count()
        assertEquals(1, pluginOccurrences)
        // compose = true count remains 1
        val composeFeatureCount = Regex("""compose\s*=\s*true""").findAll(afterContent).count()
        assertEquals(1, composeFeatureCount)
    }

    @Test
    fun testUnknownKotlinVersionFailsClearly() = runBlocking {
        val proj = tempFolder.newFolder("UnknownKotlinApp")
        File(proj, "app/build.gradle.kts").apply {
            parentFile.mkdirs()
            writeText(
                """
                plugins {
                    id("com.android.application")
                    id("org.jetbrains.kotlin.android")
                }
                android {
                    minSdk = 24
                }
                dependencies {}
                """.trimIndent()
            )
        }

        val result = enabler.enableComposeInProject(
            projectPath = proj.absolutePath,
            useVersionCatalog = false,
            wireActivities = false,
            hasConstraintLayout = false,
            kotlinVersionOverride = "1.6.10" // Not in compat matrix
        )
        assertTrue("Unknown version fails", result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue(
            "Error mentions unknown version 1.6.10",
            ex?.message?.contains("Unbekannte Kotlin-Version '1.6.10'") == true
        )
    }
}
