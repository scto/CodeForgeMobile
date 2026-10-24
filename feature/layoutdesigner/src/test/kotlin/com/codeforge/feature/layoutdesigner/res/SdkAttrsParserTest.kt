package com.codeforge.feature.layoutdesigner.res

import com.codeforge.feature.layoutdesigner.model.AttrKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class SdkAttrsParserTest {

    private val sampleAttrsXml = """
        <resources>
            <declare-styleable name="View">
                <attr name="background" format="reference|color" />
                <attr name="visibility">
                    <enum name="visible" value="0" />
                    <enum name="invisible" value="1" />
                    <enum name="gone" value="2" />
                </attr>
                <attr name="layout_gravity">
                    <flag name="top" value="0x30" />
                    <flag name="bottom" value="0x50" />
                    <flag name="start" value="0x00800003" />
                    <flag name="end" value="0x00800005" />
                </attr>
                <attr name="alpha" format="float" />
                <attr name="padding" format="dimension" />
                <attr name="id" format="reference" />
                <attr name="clickable" format="boolean" />
            </declare-styleable>
            <declare-styleable name="TextView">
                <attr name="text" format="string" />
                <attr name="textSize" format="dimension" />
                <attr name="lines" format="integer" />
            </declare-styleable>
        </resources>
    """.trimIndent()

    @Test
    fun parse_extractsStyleablesAndAttributesCorrectly() {
        val parser = SdkAttrsParser()
        val stream = ByteArrayInputStream(sampleAttrsXml.toByteArray(Charsets.UTF_8))
        val result = parser.parse(stream)

        assertEquals(2, result.size)
        assertTrue("View" in result)
        assertTrue("TextView" in result)

        val viewAttrs = result["View"]!!
        assertEquals(7, viewAttrs.size)

        val bg = viewAttrs.find { it.name == "android:background" }!!
        assertEquals(AttrKind.COLOR, bg.kind)

        val vis = viewAttrs.find { it.name == "android:visibility" }!!
        assertEquals(AttrKind.ENUM, vis.kind)
        assertEquals(listOf("visible", "invisible", "gone"), vis.options)

        val grav = viewAttrs.find { it.name == "android:layout_gravity" }!!
        assertEquals(AttrKind.FLAG, grav.kind)
        assertEquals(listOf("top", "bottom", "start", "end"), grav.options)

        val alpha = viewAttrs.find { it.name == "android:alpha" }!!
        assertEquals(AttrKind.FLOAT, alpha.kind)

        val padding = viewAttrs.find { it.name == "android:padding" }!!
        assertEquals(AttrKind.DIMENSION, padding.kind)

        val clickable = viewAttrs.find { it.name == "android:clickable" }!!
        assertEquals(AttrKind.BOOLEAN, clickable.kind)

        val tvAttrs = result["TextView"]!!
        val text = tvAttrs.find { it.name == "android:text" }!!
        assertEquals(AttrKind.TEXT, text.kind)

        val lines = tvAttrs.find { it.name == "android:lines" }!!
        assertEquals(AttrKind.INTEGER, lines.kind)
    }

    @Test
    fun getAttrsFor_returnsCachedResults() {
        val parser = SdkAttrsParser()
        parser.parse(ByteArrayInputStream(sampleAttrsXml.toByteArray(Charsets.UTF_8)))

        val cached = parser.getAttrsFor("View")
        assertEquals(7, cached.size)
        assertTrue(parser.getAttrsFor("Unknown").isEmpty())
    }
}
