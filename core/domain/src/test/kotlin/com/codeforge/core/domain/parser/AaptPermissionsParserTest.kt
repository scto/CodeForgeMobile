// Modul: :core:domain
package com.codeforge.core.domain.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AaptPermissionsParserTest {

    @Test
    fun parsePermissionsFixture() {
        val fixtureContent = javaClass.classLoader
            ?.getResourceAsStream("fixtures/aapt_dump_permissions.txt")
            ?.bufferedReader()
            ?.readText()
            ?: error("Fixture nicht gefunden")

        val result = AaptPermissionsParser.parse(fixtureContent)

        assertEquals("com.example.codeforge.testapp", result.packageName)
        assertEquals(8, result.permissions.size)

        // Internet: Standard
        val internet = result.permissions.find { it.name == "android.permission.INTERNET" }
        assertNotNull(internet)
        assertFalse(internet!!.isDangerous)

        // Location: Gefährlich
        val location = result.permissions.find { it.name == "android.permission.ACCESS_FINE_LOCATION" }
        assertNotNull(location)
        assertTrue(location!!.isDangerous)

        // Camera: Gefährlich mit maxSdk
        val camera = result.permissions.find { it.name == "android.permission.CAMERA" }
        assertNotNull(camera)
        assertTrue(camera!!.isDangerous)
        assertEquals(32, camera.maxSdkVersion)

        // Contacts & Audio: Gefährlich
        assertTrue(result.permissions.find { it.name == "android.permission.READ_CONTACTS" }!!.isDangerous)
        assertTrue(result.permissions.find { it.name == "android.permission.RECORD_AUDIO" }!!.isDangerous)

        // Custom permission mit protectionLevel='dangerous'
        val customDangerous = result.permissions.find { it.name == "com.example.codeforge.CUSTOM_DANGEROUS" }
        assertNotNull(customDangerous)
        assertTrue(customDangerous!!.isDangerous)
        assertEquals("dangerous", customDangerous.protectionLevel)

        // Custom permission mit protectionLevel='normal'
        val customNormal = result.permissions.find { it.name == "com.example.codeforge.CUSTOM_NORMAL" }
        assertNotNull(customNormal)
        assertFalse(customNormal!!.isDangerous)
    }

    @Test
    fun parseEmptyOutputReturnsNullPackageAndEmptyList() {
        val result = AaptPermissionsParser.parse("")
        assertEquals(null, result.packageName)
        assertTrue(result.permissions.isEmpty())
    }
}
