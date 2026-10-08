package com.codeforge.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectInputValidatorTest {

    private val empty = ProjectTemplateDescriptor("empty_activity", ProjectTemplateKind.EMPTY_ACTIVITY)
    private val compose = ProjectTemplateDescriptor("compose_activity", ProjectTemplateKind.COMPOSE_ACTIVITY, supportsJava = false)

    private fun req(
        t: ProjectTemplateDescriptor = empty,
        name: String = "MyApp",
        pkg: String = "com.example.myapp",
        dir: String = "/tmp/p",
        lang: ProjectLanguage = ProjectLanguage.KOTLIN,
    ) = ProjectRequest(t, name, pkg, dir, language = lang)

    @Test fun validRequestHasNoErrors() {
        assertTrue(ProjectInputValidator.validate(req()) { false }.isEmpty())
    }

    @Test fun invalidNames() {
        assertFalse(ProjectInputValidator.isValidProjectName("1abc"))
        assertFalse(ProjectInputValidator.isValidProjectName("a"))
        assertTrue(ProjectInputValidator.isValidProjectName("My-App_1"))
    }

    @Test fun packageRules() {
        assertFalse(ProjectInputValidator.isValidPackageName("single"))
        assertTrue(ProjectInputValidator.isValidPackageName("com.example.app"))
        assertTrue(ProjectInputValidator.hasReservedSegment("com.example.class"))
        assertEquals(
            ProjectInputValidator.Error.RESERVED_PACKAGE_SEGMENT,
            ProjectInputValidator.validate(req(pkg = "com.example.new")) { false }[ProjectInputValidator.Field.PACKAGE_NAME]
        )
    }

    @Test fun existingDirectoryAndBlankTarget() {
        assertEquals(
            ProjectInputValidator.Error.DIRECTORY_EXISTS,
            ProjectInputValidator.validate(req()) { true }[ProjectInputValidator.Field.TARGET_DIR]
        )
        assertEquals(
            ProjectInputValidator.Error.TARGET_BLANK,
            ProjectInputValidator.validate(req(dir = " ")) { false }[ProjectInputValidator.Field.TARGET_DIR]
        )
    }

    @Test fun composeRequiresKotlin() {
        assertEquals(
            ProjectInputValidator.Error.COMPOSE_REQUIRES_KOTLIN,
            ProjectInputValidator.validate(req(t = compose, lang = ProjectLanguage.JAVA)) { false }[ProjectInputValidator.Field.LANGUAGE]
        )
    }

    @Test fun suggestPackage() {
        assertEquals("com.empty.myapp", ProjectInputValidator.suggestPackageName("Empty Activity", "MyApp"))
        assertEquals("com.example", ProjectInputValidator.suggestPackageName("", "123"))
    }
}
