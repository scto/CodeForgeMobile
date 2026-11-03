package com.codeforge.feature.build

import org.junit.Test
import org.junit.Assert.assertEquals

class GradleTaskListParserTest {
    @Test
    fun parsesTaskNamesAndIgnoresHeadersAndNoise() {
        val out = listOf(
            "> Task :tasks",
            "",
            "------------------------------------------------------------",
            "Tasks runnable from root project 'demo'",
            "------------------------------------------------------------",
            "",
            "Build tasks",
            "-----------",
            "assemble - Assembles all variants of all applications.",
            ":app:assembleDebug - Assembles main outputs for debug.",
            "clean - Deletes the build directory.",
            "",
            "Other tasks",
            "-----------",
            "compileDebugKotlin",
            "assemble - Assembles all variants of all applications.",
            "BUILD SUCCESSFUL in 3s"
        )
        assertEquals(
            listOf(":app:assembleDebug", "assemble", "clean", "compileDebugKotlin"),
            GradleTaskListParser.parse(out)
        )
    }

    @Test
    fun parseDetailedKeepsGroupAndDescription() {
        val out = listOf(
            "Build tasks",
            "-----------",
            "assemble - Assembles all variants.",
            "",
            "Other tasks",
            "-----------",
            "compileDebugKotlin"
        )
        val infos = GradleTaskListParser.parseDetailed(out)
        assertEquals(GradleTaskInfo("assemble", "Build", "Assembles all variants."), infos[0])
        assertEquals(GradleTaskInfo("compileDebugKotlin", "Other", ""), infos[1])
    }

    @Test
    fun filterTasksMatchesNameAndGroup() {
        val tasks = listOf(
            GradleTaskInfo(":app:lintDebug", "Verification", "Runs lint"),
            GradleTaskInfo("clean", "Build", "Deletes build dir")
        )
        assertEquals(listOf(":app:lintDebug"), filterTasks(tasks, "LINT").map { it.name })
        assertEquals(listOf("clean"), filterTasks(tasks, "build").map { it.name })
    }
}
