package com.codeforge.feature.assetstudio

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.codeforge.feature.assetstudio.logic.AssetFilePlanner
import com.codeforge.feature.assetstudio.logic.ForegroundSource
import com.codeforge.feature.assetstudio.logic.IconNames
import com.codeforge.feature.assetstudio.logic.VectorXmlWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AssetStudioLogicTest {

    @Test
    fun `Anzeigenamen`() {
        assertEquals("10mp", IconNames.displayName("_10mp"))
        assertEquals("1k plus", IconNames.displayName("_1kPlus"))
        assertEquals("access alarm", IconNames.displayName("AccessAlarm"))
        assertEquals("ic_access_alarm", IconNames.suggestResourceName("AccessAlarm"))
    }

    @Test
    fun `Katalog sortiert und Suche`() {
        val cat = IconNames.parseCatalog(listOf("Zoom", "_10mp", "AccessAlarm", "AccessAlarms", "_10mp", ""))
        assertEquals(listOf("_10mp", "AccessAlarm", "AccessAlarms", "Zoom"), cat)
        assertEquals(listOf("AccessAlarm", "AccessAlarms"), IconNames.search(cat, "access ala"))
        assertEquals(cat, IconNames.search(cat, "  "))
    }

    @Test
    fun `Ressourcennamen`() {
        assertTrue(IconNames.isValidResourceName("ic_my_icon"))
        assertFalse(IconNames.isValidResourceName("Ic"))
        assertFalse(IconNames.isValidResourceName("1abc"))
        assertFalse(IconNames.isValidResourceName(""))
    }

    @Test
    fun `Farben parsen`() {
        assertEquals(0xFF6200EE.toInt(), AssetFilePlanner.parseColor("#6200EE"))
        assertEquals(0xFFAABBCC.toInt(), AssetFilePlanner.parseColor("abc"))
        assertEquals(0x80112233.toInt(), AssetFilePlanner.parseColor("#80112233"))
        assertNull(AssetFilePlanner.parseColor("#12"))
        assertNull(AssetFilePlanner.parseColor("zzzzzz"))
        assertEquals("#6200EE", AssetFilePlanner.colorHex(0xFF6200EE.toInt()))
    }

    @Test
    fun `Inset`() {
        assertEquals(20, AssetFilePlanner.insetPercent(0.6f))
        assertEquals(0, AssetFilePlanner.insetPercent(1f))
        assertEquals(35, AssetFilePlanner.insetPercent(0.1f))
    }

    @Test
    fun `Adaptive Icon Dateien`() {
        val files = AssetFilePlanner.planAdaptiveIcon("ic_x", ForegroundSource.VectorXml("<vector/>"), 0xFF112233.toInt(), 0.6f)
        val paths = files.map { it.relativePath }
        assertEquals(
            listOf(
                "drawable/ic_x_fg.xml", "drawable/ic_x_foreground.xml", "values/ic_x_background.xml",
                "mipmap-anydpi-v26/ic_x.xml", "mipmap-anydpi-v26/ic_x_round.xml"
            ),
            paths
        )
        assertTrue(String(files[1].bytes).contains("""android:insetLeft="20%""""))
        assertTrue(String(files[2].bytes).contains("#112233"))
        val png = AssetFilePlanner.planAdaptiveIcon("ic_x", ForegroundSource.Png(byteArrayOf(1)), 0, 1f)
        assertEquals("drawable-nodpi/ic_x_fg.png", png.first().relativePath)
    }

    @Test
    fun `Drawable Dateien`() {
        assertEquals("drawable/a.xml", AssetFilePlanner.planDrawable("a", ForegroundSource.VectorXml("<vector/>")).single().relativePath)
        assertEquals("drawable-nodpi/a.png", AssetFilePlanner.planDrawable("a", ForegroundSource.Png(byteArrayOf(1))).single().relativePath)
    }

    @Test
    fun `res-Ordner finden und schreiben`() {
        val root = kotlin.io.path.createTempDirectory("asset").toFile()
        try {
            assertNull(AssetFilePlanner.findResDir(root))
            File(root, "feature/x/src/main/res").mkdirs()
            assertEquals("res", AssetFilePlanner.findResDir(root)?.name)
            File(root, "app/src/main/res").mkdirs()
            assertTrue(AssetFilePlanner.findResDir(root)!!.path.endsWith("app/src/main/res"))
            val res = AssetFilePlanner.findResDir(root)!!
            AssetFilePlanner.write(res, AssetFilePlanner.planDrawable("d", ForegroundSource.VectorXml("<vector/>")))
            assertEquals("<vector/>", File(res, "drawable/d.xml").readText())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `ImageVector wird zu Vector-XML`() {
        val v = ImageVector.Builder("t", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 2f); lineTo(22f, 22f); horizontalLineTo(2f); close()
            }
        }.build()
        val xml = VectorXmlWriter.toXml(v)
        assertTrue(xml.contains("""android:viewportWidth="24""""))
        assertTrue(xml.contains("""android:fillColor="#000000""""))
        assertTrue(xml.contains("""android:pathData="M12,2L22,22H2Z""""))
        val tinted = VectorXmlWriter.toXml(v, Color(0xFFFF0000))
        assertTrue(tinted.contains("""android:fillColor="#FF0000""""))
    }
}
