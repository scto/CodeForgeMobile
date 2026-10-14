// Modul: :core:designsystem
package com.codeforge.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FluentTokensTest {

    @Test
    fun `brand tokens have valid distinct shades`() {
        val primary = FluentTokens.Brand.Primary
        assertEquals(Color(0xFF0078D4), primary)

        val shades = listOf(
            FluentTokens.Brand.Shade30,
            FluentTokens.Brand.Shade20,
            FluentTokens.Brand.Shade10,
            FluentTokens.Brand.Primary,
            FluentTokens.Brand.Tint10,
            FluentTokens.Brand.Tint20,
            FluentTokens.Brand.Tint30,
            FluentTokens.Brand.Tint40
        )
        assertEquals("All brand shades must be distinct", shades.size, shades.distinct().size)
    }

    @Test
    fun `corner radii conform to Fluent 2 scale`() {
        assertEquals(0.dp, FluentTokens.CornerRadius.None)
        assertEquals(2.dp, FluentTokens.CornerRadius.Small)
        assertEquals(4.dp, FluentTokens.CornerRadius.Medium)
        assertEquals(8.dp, FluentTokens.CornerRadius.Large)
        assertEquals(12.dp, FluentTokens.CornerRadius.XLarge)
        assertTrue(FluentTokens.CornerRadius.Circle.value > 1000f)
    }

    @Test
    fun `elevations are monotonically increasing`() {
        val levels = listOf(
            FluentTokens.Elevation.Level0,
            FluentTokens.Elevation.Level1,
            FluentTokens.Elevation.Level2,
            FluentTokens.Elevation.Level3,
            FluentTokens.Elevation.Level4
        )
        for (i in 0 until levels.size - 1) {
            assertTrue("Level $i must be smaller than level ${i + 1}", levels[i] < levels[i + 1])
        }
    }

    @Test
    fun `fluent color scheme light and dark differentiate correctly`() {
        val light = FluentColorScheme.Light
        val dark = FluentColorScheme.Dark

        assertFalse(light.isDark)
        assertTrue(dark.isDark)

        assertNotEquals(light.background, dark.background)
        assertNotEquals(light.surface, dark.surface)
        assertNotEquals(light.textPrimary, dark.textPrimary)
        assertNotEquals(light.stroke, dark.stroke)
    }
}
