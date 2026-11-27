package com.codeforge.feature.build

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BuildVariantsTest {
    @Test
    fun variantsFromTasks() {
        val tasks = listOf(
            "assemble", "assembleDebug", "assembleRelease", "assembleFreeDebug", "assembleDebugAndroidTest",
            "assembleDebugUnitTest", ":app:assembleStaging", "clean"
        )
        assertEquals(listOf("debug", "release", "freeDebug"), BuildVariants.fromTasks(tasks))
    }

    @Test
    fun defaultsWithoutTasks() {
        assertEquals(listOf("debug", "release"), BuildVariants.fromTasks(listOf("clean", "help")))
    }

    @Test
    fun taskAndVariantRoundTrip() {
        assertEquals("assembleFreeDebug", BuildVariants.assembleTask("freeDebug"))
        assertEquals("freeDebug", BuildVariants.variantOf("clean assembleFreeDebug"))
        assertNull(BuildVariants.variantOf("lint"))
    }
}
