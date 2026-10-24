package com.codeforge.feature.layoutdesigner.model

import com.codeforge.feature.layoutdesigner.lint.IssueSeverity
import com.codeforge.feature.layoutdesigner.lint.LayoutLinter
import com.codeforge.feature.layoutdesigner.xml.LayoutXml
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class LayoutTemplatesTest {

    @Test
    fun testAllTemplates_validXmlAndLintClean() {
        for (template in LayoutTemplate.entries) {
            val xml = template.xml()
            val doc = LayoutXml.parse(xml)
            assertNotNull("Template ${template.name} should parse cleanly", doc)
            val issues = LayoutLinter.lint(doc)
            val errors = issues.filter { it.severity == IssueSeverity.ERROR }
            assertEquals("Template ${template.name} should have 0 errors, but had: $errors", 0, errors.size)
        }
    }
}
