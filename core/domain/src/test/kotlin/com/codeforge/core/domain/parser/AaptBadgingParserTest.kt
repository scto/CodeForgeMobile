// Modul: :core:domain
package com.codeforge.core.domain.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AaptBadgingParserTest {

    @Test
    fun parseBadgingFixture() {
        val fixtureContent = javaClass.classLoader
            ?.getResourceAsStream("fixtures/aapt_dump_badging.txt")
            ?.bufferedReader()
            ?.readText()
            ?: error("Fixture nicht gefunden")

        val result = AaptBadgingParser.parse(fixtureContent)

        assertEquals("com.example.codeforge.testapp", result.packageName)
        assertEquals(10203L, result.versionCode)
        assertEquals("1.2.3", result.versionName)
        assertEquals(34, result.compileSdk)
        assertEquals(24, result.minSdk)
        assertEquals(34, result.targetSdk)

        assertEquals(true, result.isDebuggable)
        assertEquals(true, result.allowBackup)

        assertEquals(3, result.permissions.size)
        assertTrue(result.permissions.any { it.name == "android.permission.INTERNET" && !it.isDangerous })
        assertTrue(result.permissions.any { it.name == "android.permission.CAMERA" && it.isDangerous })
        assertTrue(result.permissions.any { it.name == "android.permission.ACCESS_FINE_LOCATION" && it.isDangerous })

        // Komponenten
        assertEquals(3, result.components.activitiesCount)
        assertEquals(2, result.components.exportedActivitiesCount)
        assertEquals(2, result.components.servicesCount)
        assertEquals(1, result.components.exportedServicesCount)
        assertEquals(1, result.components.receiversCount)
        assertEquals(1, result.components.exportedReceiversCount)
        assertEquals(1, result.components.providersCount)
        assertEquals(0, result.components.exportedProvidersCount)

        // Warnungen
        assertTrue(result.warnings.any { it.contains("FLAG_DEBUGGABLE") })
        assertTrue(result.warnings.any { it.contains("allowBackup") })
    }
}
