/**
 * Modul: :core:domain
 * @author Thomas Schmid
 */
package com.codeforge.core.domain.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ApkMetadataParserTest {

    private val parser = ApkMetadataParser()
    private val dummyDir = File("/test/workspace/app/build/outputs/apk/debug")

    private fun readFixture(fileName: String): String {
        val stream = javaClass.classLoader?.getResourceAsStream("fixtures/$fileName")
            ?: error("Fixture fixtures/$fileName not found")
        return stream.bufferedReader().use { it.readText() }
    }

    @Test
    fun parseSingleApkMetadata() {
        val json = readFixture("apk_metadata_single.json")
        val result = parser.parse(json, dummyDir)

        assertTrue(result.isSuccess)
        val metadata = result.getOrThrow()
        assertEquals("com.codeforge.app", metadata.applicationId)
        assertEquals("debug", metadata.variantName)
        assertEquals(30000L, metadata.versionCode)
        assertEquals("3.0.0", metadata.versionName)
        assertEquals("app-debug.apk", metadata.file.name)
        assertTrue(metadata.isSigned)
    }

    @Test
    fun parseAbiSplitSelectsMatchingDeviceAbi() {
        val json = readFixture("apk_metadata_splits.json")
        // Gerät unterstützt arm64-v8a bevorzugt
        val result = parser.parse(json, dummyDir, supportedAbis = listOf("arm64-v8a", "armeabi-v7a"))

        assertTrue(result.isSuccess)
        val metadata = result.getOrThrow()
        assertEquals("com.example.splitapp", metadata.applicationId)
        assertEquals("app-arm64-v8a-release.apk", metadata.file.name)
        assertEquals("arm64-v8a", metadata.abiFilter)
        assertEquals(1002L, metadata.versionCode)
    }

    @Test
    fun parseUnsignedApkIdentifiesUnsignedState() {
        val json = readFixture("apk_metadata_unsigned.json")
        val result = parser.parse(json, dummyDir)

        assertTrue(result.isSuccess)
        val metadata = result.getOrThrow()
        assertEquals("app-release-unsigned.apk", metadata.file.name)
        assertFalse(metadata.isSigned)
    }
}
