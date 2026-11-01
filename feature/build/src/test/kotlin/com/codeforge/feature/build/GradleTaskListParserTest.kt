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
}
