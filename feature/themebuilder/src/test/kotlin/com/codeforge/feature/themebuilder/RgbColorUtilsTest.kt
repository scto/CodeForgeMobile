package com.codeforge.feature.themebuilder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RgbColorUtilsTest {

    @Test
    fun testHexToRgbValid() {
        val rgb1 = RgbColorUtils.hexToRgb("#FF0000")
        assertNotNull(rgb1)
        assertEquals(Triple(255, 0, 0), rgb1)

        val rgb2 = RgbColorUtils.hexToRgb("00FF00")
        assertNotNull(rgb2)
        assertEquals(Triple(0, 255, 0), rgb2)

        val rgb3 = RgbColorUtils.hexToRgb("#2196f3")
        assertNotNull(rgb3)
        assertEquals(Triple(33, 150, 243), rgb3)
    }

    @Test
    fun testHexToRgbInvalid() {
        assertNull(RgbColorUtils.hexToRgb(""))
        assertNull(RgbColorUtils.hexToRgb("#FFF"))
        assertNull(RgbColorUtils.hexToRgb("#GG0000"))
        assertNull(RgbColorUtils.hexToRgb("12345"))
        assertNull(RgbColorUtils.hexToRgb("1234567"))
    }

    @Test
    fun testRgbToHex() {
        assertEquals("#FF0000", RgbColorUtils.rgbToHex(255, 0, 0))
        assertEquals("#00FF00", RgbColorUtils.rgbToHex(0, 255, 0))
        assertEquals("#0000FF", RgbColorUtils.rgbToHex(0, 0, 255))
        assertEquals("#2196F3", RgbColorUtils.rgbToHex(33, 150, 243))

        // Clamping bounds
        assertEquals("#000000", RgbColorUtils.rgbToHex(-10, -5, 0))
        assertEquals("#FFFFFF", RgbColorUtils.rgbToHex(300, 500, 255))
    }

    @Test
    fun testIsValidHex() {
        assertTrue(RgbColorUtils.isValidHex("#AABBCC"))
        assertTrue(RgbColorUtils.isValidHex("112233"))
        assertFalse(RgbColorUtils.isValidHex("#ZZZZZZ"))
        assertFalse(RgbColorUtils.isValidHex("#12"))
    }

    @Test
    fun testQuickPresetsCountAndValidity() {
        assertEquals(12, RgbColorUtils.QUICK_PRESETS.size)
        for (hex in RgbColorUtils.QUICK_PRESETS) {
            assertTrue("Expected valid hex: $hex", RgbColorUtils.isValidHex(hex))
        }
    }
}
