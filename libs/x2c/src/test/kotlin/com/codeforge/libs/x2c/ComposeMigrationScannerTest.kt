package com.codeforge.libs.x2c

import com.codeforge.core.domain.model.LayoutClassification
import com.codeforge.libs.x2c.scanner.ComposeMigrationScannerImpl
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ComposeMigrationScannerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var scanner: ComposeMigrationScannerImpl
    private lateinit var projectDir: File

    @Before
    fun setUp() {
        scanner = ComposeMigrationScannerImpl()
        projectDir = tempFolder.newFolder("TestApp")
    }

    @Test
    fun `scanProject returns false for non-existent directory`() = runBlocking {
        val result = scanner.scanProject("/non/existent/path")
        assertFalse(result.isAndroidProject)
        assertEquals(0, result.layoutCount)
        assertFalse(result.qualifiesForAutoPrompt)
    }

    @Test
    fun `scanProject detects Android project with XML layouts qualifying for auto prompt`() = runBlocking {
        // App build file with AGP plugin
        val appDir = File(projectDir, "app")
        appDir.mkdirs()
        File(appDir, "build.gradle.kts").writeText(
            """
            plugins {
                id("com.android.application")
                kotlin("android")
            }
            android {
                compileSdk = 35
            }
            """.trimIndent()
        )

        // Res layout files
        val layoutDir = File(appDir, "src/main/res/layout")
        layoutDir.mkdirs()
        File(layoutDir, "activity_main.xml").writeText(
            """
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:orientation="vertical">
                <TextView
                    android:id="@+id/tv_title"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="Hello World" />
            </LinearLayout>
            """.trimIndent()
        )
        File(layoutDir, "item_user.xml").writeText(
            """
            <FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="wrap_content">
                <ImageView
                    android:id="@+id/iv_avatar"
                    android:layout_width="48dp"
                    android:layout_height="48dp" />
            </FrameLayout>
            """.trimIndent()
        )

        val scan = scanner.scanProject(projectDir.absolutePath)
        assertTrue("Should be recognized as Android project", scan.isAndroidProject)
        assertEquals(2, scan.layoutCount)
        assertEquals(1, scan.moduleCount)
        assertFalse("Compose should not be active", scan.hasExistingCompose)
        assertTrue("Should qualify for auto prompt", scan.qualifiesForAutoPrompt)
    }

    @Test
    fun `scanProject recognizes already active Compose and does not auto prompt`() = runBlocking {
        val appDir = File(projectDir, "app")
        appDir.mkdirs()
        File(appDir, "build.gradle.kts").writeText(
            """
            plugins {
                id("com.android.application")
                id("org.jetbrains.kotlin.plugin.compose")
            }
            android {
                buildFeatures {
                    compose = true
                }
            }
            dependencies {
                implementation(platform("androidx.compose:compose-bom:2024.10.01"))
            }
            """.trimIndent()
        )

        val layoutDir = File(appDir, "src/main/res/layout")
        layoutDir.mkdirs()
        File(layoutDir, "activity_main.xml").writeText("<LinearLayout/>")

        val scan = scanner.scanProject(projectDir.absolutePath)
        assertTrue(scan.isAndroidProject)
        assertEquals(1, scan.layoutCount)
        assertTrue("Should detect active Compose", scan.hasExistingCompose)
        assertFalse("Must not auto prompt when Compose is already active", scan.qualifiesForAutoPrompt)
    }

    @Test
    fun `scanProject excludes build and Backup and dot-gradle directories`() = runBlocking {
        File(projectDir, "build.gradle").writeText("apply plugin: 'com.android.application'")

        // Legitimate layout
        val validLayout = File(projectDir, "app/src/main/res/layout")
        validLayout.mkdirs()
        File(validLayout, "valid.xml").writeText("<LinearLayout/>")

        // Ignored directories
        val buildLayout = File(projectDir, "app/build/intermediates/res/layout")
        buildLayout.mkdirs()
        File(buildLayout, "ignored_build.xml").writeText("<LinearLayout/>")

        val backupLayout = File(projectDir, "Backup/my-backup/res/layout")
        backupLayout.mkdirs()
        File(backupLayout, "ignored_backup.xml").writeText("<LinearLayout/>")

        val scan = scanner.scanProject(projectDir.absolutePath)
        assertEquals(1, scan.layoutCount)
        assertTrue(scan.layoutFiles.first().endsWith("valid.xml"))
    }

    @Test
    fun `createPlan accurately classifies layouts and detects theme and catalog`() = runBlocking {
        val appDir = File(projectDir, "app")
        appDir.mkdirs()
        File(appDir, "build.gradle.kts").writeText("plugins { id(\"com.android.application\") }")

        // Version catalog
        val gradleDir = File(projectDir, "gradle")
        gradleDir.mkdirs()
        File(gradleDir, "libs.versions.toml").writeText("[versions]\n[libraries]")

        // Material 3 theme in values
        val valuesDir = File(appDir, "src/main/res/values")
        valuesDir.mkdirs()
        File(valuesDir, "themes.xml").writeText(
            """
            <resources>
                <style name="Theme.MyApp" parent="Theme.Material3.DayNight.NoActionBar">
                    <item name="colorPrimary">@color/purple_500</item>
                </style>
            </resources>
            """.trimIndent()
        )

        val layoutDir = File(appDir, "src/main/res/layout")
        layoutDir.mkdirs()

        // 1. Automatic layout (LinearLayout)
        File(layoutDir, "activity_main.xml").writeText(
            """
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent">
                <TextView
                    android:id="@+id/title"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content" />
                <Button
                    android:id="@+id/btn_submit"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content" />
            </LinearLayout>
            """.trimIndent()
        )

        // 2. DataBinding (NOT_CONVERTIBLE)
        File(layoutDir, "activity_databinding.xml").writeText(
            """
            <layout xmlns:android="http://schemas.android.com/apk/res/android">
                <data>
                    <variable name="user" type="com.example.User" />
                </data>
                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="match_parent">
                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="@{user.name}" />
                </LinearLayout>
            </layout>
            """.trimIndent()
        )

        // 3. MotionLayout (NOT_CONVERTIBLE)
        File(layoutDir, "motion_header.xml").writeText(
            """
            <androidx.constraintlayout.motion.widget.MotionLayout
                xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent">
            </androidx.constraintlayout.motion.widget.MotionLayout>
            """.trimIndent()
        )

        // 4. Custom View (WITH_NOTES)
        File(layoutDir, "custom_chart.xml").writeText(
            """
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent">
                <com.example.ui.CustomLineChart
                    android:id="@+id/chart"
                    android:layout_width="match_parent"
                    android:layout_height="200dp" />
            </LinearLayout>
            """.trimIndent()
        )

        // 5. Variant in layout-land (WITH_NOTES)
        val landDir = File(appDir, "src/main/res/layout-land")
        landDir.mkdirs()
        File(landDir, "activity_main.xml").writeText(
            """
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:orientation="horizontal">
            </LinearLayout>
            """.trimIndent()
        )

        val plan = scanner.createPlan(projectDir.absolutePath)

        assertEquals(5, plan.totalLayouts)
        assertEquals(1, plan.automaticCount)
        assertEquals(2, plan.withNotesCount)
        assertEquals(2, plan.notConvertibleCount)
        assertTrue("Version catalog should be detected", plan.hasVersionCatalog)
        assertEquals("Theme.Material3", plan.detectedTheme)
        assertEquals(1, plan.stage2CandidatesCount) // activity_main.xml is eligible

        val mainEntry = plan.layouts.first { it.layoutName == "activity_main" && !it.isVariant }
        assertEquals(LayoutClassification.AUTOMATIC, mainEntry.classification)
        assertTrue(mainEntry.stage2Eligible)
        assertEquals(listOf("title", "btn_submit"), mainEntry.touchedViewIds)

        val dbEntry = plan.layouts.first { it.layoutName == "activity_databinding" }
        assertEquals(LayoutClassification.NOT_CONVERTIBLE, dbEntry.classification)

        val motionEntry = plan.layouts.first { it.layoutName == "motion_header" }
        assertEquals(LayoutClassification.NOT_CONVERTIBLE, motionEntry.classification)

        val customEntry = plan.layouts.first { it.layoutName == "custom_chart" }
        assertEquals(LayoutClassification.WITH_NOTES, customEntry.classification)

        val landEntry = plan.layouts.first { it.layoutName == "activity_main" && it.isVariant }
        assertTrue(landEntry.isVariant)
        assertEquals("land", landEntry.variantQualifier)
    }

    @Test
    fun `analyzeLayoutFile handles XML with XXE attempt securely`() = runBlocking {
        val appDir = File(projectDir, "app")
        appDir.mkdirs()
        File(appDir, "build.gradle.kts").writeText("plugins { id(\"com.android.application\") }")

        val layoutDir = File(appDir, "src/main/res/layout")
        layoutDir.mkdirs()

        val xxePayload = """
            <?xml version="1.0" encoding="utf-8"?>
            <!DOCTYPE foo [
                <!ELEMENT foo ANY >
                <!ENTITY xxe SYSTEM "file:///etc/passwd" >]>
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent">
                <TextView
                    android:id="@+id/text"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="&xxe;" />
            </LinearLayout>
        """.trimIndent()

        File(layoutDir, "xxe_test.xml").writeText(xxePayload)

        // Must parse safely without throwing unhandled security exception or leaking
        val plan = scanner.createPlan(projectDir.absolutePath)
        assertNotNull(plan)
        assertEquals(1, plan.totalLayouts)
    }
}
