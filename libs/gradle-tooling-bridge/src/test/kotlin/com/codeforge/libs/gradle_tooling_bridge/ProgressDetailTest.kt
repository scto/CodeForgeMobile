// Modul: :libs:gradle-tooling-bridge
package com.codeforge.libs.gradle_tooling_bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgressDetailTest {

    @Test
    fun showsTaskConfigureAndPlain() {
        assertEquals(":app:compileDebugKotlin", progressDetail("> Task :app:compileDebugKotlin UP-TO-DATE"))
        assertEquals("Configure project :app", progressDetail("> Configure project :app"))
        assertEquals("Downloading https://x/y.jar", progressDetail("  Downloading https://x/y.jar "))
    }

    @Test
    fun skipsBlankAndTaskListLines() {
        assertNull(progressDetail("   "))
        assertNull(progressDetail("-----------"))
        assertNull(progressDetail("assembleDebug - Assembles the debug build."))
    }
}
