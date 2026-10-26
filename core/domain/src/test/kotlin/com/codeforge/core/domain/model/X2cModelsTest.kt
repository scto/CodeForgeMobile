package com.codeforge.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class X2cModelsTest {

    @Test
    fun `default X2cOptions has safe values`() {
        val options = X2cOptions()
        assertFalse(options.wireActivities)
        assertTrue(options.generateTheme)
        assertFalse(options.removeXmlAfterSuccess)
        assertFalse(options.useVersionCatalog)
        assertTrue(options.runBuildCheck)
        assertFalse(options.createGitBranch)
        assertTrue(options.includeGitInBackup)
        assertTrue(options.selectedLayouts.isEmpty())
        assertFalse(options.dontAskAgain)
    }

    @Test
    fun `X2cPlan correctly holds classifications and counts`() {
        val planEntry = LayoutPlanEntry(
            relativePath = "app/src/main/res/layout/activity_main.xml",
            layoutName = "activity_main",
            classification = LayoutClassification.AUTOMATIC,
            reason = "Standard-Tags (LinearLayout, TextView, Button)",
            rootTag = "LinearLayout",
            childCount = 2,
            touchedViewIds = listOf("btn_submit", "tv_title"),
            stage2Eligible = true
        )

        val plan = X2cPlan(
            projectPath = "/storage/emulated/0/CodeForgeMobileProjects/SampleApp",
            totalLayouts = 1,
            automaticCount = 1,
            withNotesCount = 0,
            notConvertibleCount = 0,
            layouts = listOf(planEntry),
            stage2CandidatesCount = 1,
            detectedTheme = "Theme.Material3.DayNight.NoActionBar",
            hasExistingCompose = false
        )

        assertEquals(1, plan.totalLayouts)
        assertEquals(LayoutClassification.AUTOMATIC, plan.layouts.first().classification)
        assertTrue(plan.layouts.first().stage2Eligible)
        assertEquals(2, plan.layouts.first().touchedViewIds.size)
    }

    @Test
    fun `X2cFileResult tracks status and todos`() {
        val result = X2cFileResult(
            sourceXmlPath = "res/layout/fragment_test.xml",
            outputComposePath = "app/src/main/kotlin/com/example/ui/compose/TestScreen.kt",
            status = X2cFileStatus.GENERATED_WITH_NOTES,
            todoCount = 2,
            notes = listOf("Custom View 'com.example.GraphView' als AndroidView-Rahmen generiert")
        )

        assertEquals(X2cFileStatus.GENERATED_WITH_NOTES, result.status)
        assertEquals(2, result.todoCount)
        assertEquals(1, result.notes.size)
    }

    @Test
    fun `X2cProgress sealed states represent migration lifecycle`() {
        val idle: X2cProgress = X2cProgress.Idle
        val analyzing: X2cProgress = X2cProgress.Analyzing("Layouts werden geprüft...")
        val backup: X2cProgress = X2cProgress.CreatingBackup("/project")
        val generating: X2cProgress = X2cProgress.GeneratingComposables(1, 3, "activity_main.xml")
        val failed: X2cProgress = X2cProgress.Failed("Speicherplatz reicht nicht")

        assertTrue(idle is X2cProgress.Idle)
        assertEquals("Layouts werden geprüft...", (analyzing as X2cProgress.Analyzing).message)
        assertEquals(1, (generating as X2cProgress.GeneratingComposables).current)
        assertEquals(3, generating.total)
        assertEquals("Speicherplatz reicht nicht", (failed as X2cProgress.Failed).error)
    }

    @Test
    fun `ComposeScanResult qualifies for auto prompt only if android and layouts exist and no compose`() {
        val scan = ComposeScanResult(
            projectPath = "/project",
            isAndroidProject = true,
            layoutCount = 5,
            moduleCount = 1,
            layoutFiles = listOf("app/src/main/res/layout/activity_main.xml"),
            hasExistingCompose = false,
            qualifiesForAutoPrompt = true
        )
        assertTrue(scan.qualifiesForAutoPrompt)
        assertEquals(5, scan.layoutCount)
        assertEquals(1, scan.moduleCount)
    }
}
