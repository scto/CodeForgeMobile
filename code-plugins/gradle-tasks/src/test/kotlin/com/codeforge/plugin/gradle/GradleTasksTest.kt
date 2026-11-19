package com.codeforge.plugin.gradle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GradleTasksTest {
    @Test
    fun `Aufgaben je Modul`() {
        assertEquals(":app:assembleDebug", GradleTasks.forModule(":app", android = true).first())
        assertEquals(":build", GradleTasks.forModule(":", android = false).first())
        assertTrue(GradleTasks.forModule(":core:domain", android = false).contains(":core:domain:test"))
    }

    @Test
    fun `Freie Eingabe und Android-Erkennung`() {
        assertEquals(listOf(":app:clean", "assembleRelease"), GradleTasks.parse("  :app:clean   assembleRelease ; --init-script -I /"))
        assertTrue(GradleTasks.isAndroid("plugins { alias(libs.plugins.android.application) }"))
        assertTrue(GradleTasks.isAndroid("plugins { id(\"com.android.library\") }"))
        assertFalse(GradleTasks.isAndroid("plugins { kotlin(\"jvm\") }"))
    }
}
