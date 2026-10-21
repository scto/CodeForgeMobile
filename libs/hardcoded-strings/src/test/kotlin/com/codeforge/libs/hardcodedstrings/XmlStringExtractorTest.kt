package com.codeforge.libs.hardcodedstrings

import com.codeforge.libs.hardcodedstrings.extractor.XmlStringExtractor
import com.codeforge.libs.hardcodedstrings.model.ReplacementContextType
import com.codeforge.libs.hardcodedstrings.model.StringConfidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class XmlStringExtractorTest {

    private val extractor = XmlStringExtractor()

    @Test
    fun `extracts android text and hint attributes`() {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent">
                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="Hardcoded Title" />
                <EditText
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:hint="Geben Sie Ihren Namen ein" />
            </LinearLayout>
        """.trimIndent()

        val findings = extractor.extract(File("/app/src/main/res/layout/activity_main.xml"), xml)
        assertEquals(2, findings.size)
        assertEquals("Hardcoded Title", findings[0].rawValue)
        assertEquals(ReplacementContextType.XML_ATTRIBUTE, findings[0].contextType)
        assertEquals(StringConfidence.HIGH, findings[0].confidence)
        assertEquals("Geben Sie Ihren Namen ein", findings[1].rawValue)
    }

    @Test
    fun `ignores string references and null`() {
        val xml = """
            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="@string/already_extracted" />
        """.trimIndent()

        val findings = extractor.extract(File("/app/src/main/res/layout/item.xml"), xml)
        assertTrue(findings.isEmpty())
    }

    @Test
    fun `ignores files under res values directory`() {
        val xml = """
            <resources>
                <string name="test">Some Text</string>
            </resources>
        """.trimIndent()

        val findings = extractor.extract(File("/app/src/main/res/values/strings.xml"), xml)
        assertTrue(findings.isEmpty())
    }
}
