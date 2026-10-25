package com.codeforge.libs.j2k

import com.codeforge.libs.j2k.converter.VersionCatalogMigratorImpl
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class VersionCatalogMigratorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val migrator = VersionCatalogMigratorImpl()

    @Test
    fun testMigrateDependenciesToCatalog() = runBlocking {
        val proj = tempFolder.newFolder("myproj")
        val buildKts = File(proj, "app/build.gradle.kts").apply {
            parentFile.mkdirs()
            writeText("""
                dependencies {
                    implementation("androidx.core:core-ktx:1.12.0")
                    testImplementation("junit:junit:4.13.2")
                }
            """.trimIndent())
        }

        val result = migrator.migrateToCatalog(proj.absolutePath, listOf("app/build.gradle.kts"))
        val toml = result.catalogContent
        assertTrue(toml.contains("[versions]"))
        assertTrue(toml.contains("[libraries]"))
        assertTrue(toml.contains("core-ktx = \"1.12.0\"") || toml.contains("1.12.0"))

        val updated = result.updatedGradleFiles["app/build.gradle.kts"]!!
        assertTrue(updated.contains("implementation(libs."))
    }
}
