// Modul: :libs:theme-kit
package com.codeforge.libs.theme_kit

import com.codeforge.libs.color_utilities.hct.Hct
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeEngineTest {

    @Test
    fun allRolesForBothModes() {
        val light = ThemeEngine.roles(ThemeSpec.CODEFORGE, isDark = false)
        val dark = ThemeEngine.roles(ThemeSpec.CODEFORGE, isDark = true)
        assertEquals(36, light.values.size)
        assertNotEquals(light[ColorRole.PRIMARY], dark[ColorRole.PRIMARY])
        assertTrue(Hct.fromInt(light[ColorRole.SURFACE]).tone > Hct.fromInt(dark[ColorRole.SURFACE]).tone)
    }

    @Test
    fun primaryKeepsSeedHue() {
        val seedHue = Hct.fromInt(ColorHex.parse(ThemeSpec.CODEFORGE_SEED)!!).hue
        val primaryHue = Hct.fromInt(ThemeEngine.roles(ThemeSpec.CODEFORGE, false)[ColorRole.PRIMARY]).hue
        assertEquals(seedHue, primaryHue, 15.0)
    }

    @Test
    fun tertiaryOverrideUsesAccentHue() {
        val accentHue = Hct.fromInt(ColorHex.parse(ThemeSpec.CODEFORGE_ACCENT)!!).hue
        val tertiaryHue = Hct.fromInt(ThemeEngine.roles(ThemeSpec.CODEFORGE, false)[ColorRole.TERTIARY]).hue
        assertEquals(accentHue, tertiaryHue, 15.0)
    }

    @Test
    fun textPairsMeetAaContrast() {
        for (dark in listOf(false, true)) {
            val r = ThemeEngine.roles(ThemeSpec(seed = "#B33B15"), dark)
            for ((fg, bg) in ColorRole.CONTRAST_PAIRS) {
                assertTrue("$fg auf $bg (dunkel=$dark)", ThemeEngine.contrastRatio(r[fg], r[bg]) >= 4.4)
            }
        }
    }

    @Test
    fun highContrastIsStronger() {
        val std = ThemeEngine.roles(ThemeSpec(seed = "#6366F1"), false)
        val high = ThemeEngine.roles(ThemeSpec(seed = "#6366F1", contrast = ThemeContrast.HIGH), false)
        val c = { r: ColorRoles -> ThemeEngine.contrastRatio(r[ColorRole.ON_PRIMARY_CONTAINER], r[ColorRole.PRIMARY_CONTAINER]) }
        assertTrue(c(high) > c(std))
    }

    @Test
    fun everyStyleProducesAScheme() {
        for (style in ThemeStyle.entries) {
            assertEquals(36, ThemeEngine.roles(ThemeSpec(style = style), true).values.size)
        }
    }

    @Test
    fun hexRoundTripAndInvalid() {
        assertEquals("#6366F1", ColorHex.format(ColorHex.parse("6366f1")!!))
        assertNull(ColorHex.parse("#12345"))
        assertNull(ColorHex.parse("zzzzzz"))
        assertEquals("md_theme_on_primary_container", ColorRole.ON_PRIMARY_CONTAINER.resourceName)
    }

    @Test
    fun seedExtractorFindsDominant() {
        val red = 0xFFD32F2F.toInt()
        val pixels = IntArray(4096) { if (it % 10 == 0) 0xFF202020.toInt() else red }
        val seeds = SeedExtractor.extract(pixels)
        val hue = Hct.fromInt(seeds.first()).hue
        assertEquals(Hct.fromInt(red).hue, hue, 20.0)
        assertEquals(listOf(0xFF4285F4.toInt()), SeedExtractor.extract(IntArray(10)))
    }
}
