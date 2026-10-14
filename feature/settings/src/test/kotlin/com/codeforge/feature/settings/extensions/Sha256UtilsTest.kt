// Modul: :feature:settings
package com.codeforge.feature.settings.extensions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class Sha256UtilsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `calculateSha256 for empty bytes matches known digest`() {
        val expected = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        val actual = Sha256Utils.calculateSha256(ByteArray(0))
        assertEquals(expected, actual)
    }

    @Test
    fun `calculateSha256 for hello world matches standard SHA-256`() {
        val expected = "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9"
        val actual = Sha256Utils.calculateSha256("hello world".toByteArray(Charsets.UTF_8))
        assertEquals(expected, actual)
    }

    @Test
    fun `verifySha256 is case insensitive`() {
        val hashUpper = "B94D27B9934D3E08A52E52D7DA7DABFAC484EFE37A5380EE9088F7ACE2EFCDE9"
        assertTrue(Sha256Utils.verifySha256("hello world".toByteArray(Charsets.UTF_8), hashUpper))
    }

    @Test
    fun `verifySha256 returns false for wrong hash`() {
        assertFalse(Sha256Utils.verifySha256("hello world".toByteArray(Charsets.UTF_8), "0000000000000000000000000000000000000000000000000000000000000000"))
    }

    @Test
    fun `verifySha256 works on files`() {
        val file = tempFolder.newFile("test.txt")
        file.writeText("hello world", Charsets.UTF_8)
        val expected = "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9"
        assertTrue(Sha256Utils.verifySha256(file, expected))
        assertFalse(Sha256Utils.verifySha256(file, "incorrect_hash"))
    }

    @Test
    fun `verifySha256 returns false for non-existent file`() {
        val nonExistent = File(tempFolder.root, "does_not_exist.bin")
        assertFalse(Sha256Utils.verifySha256(nonExistent, "some_hash"))
    }
}
