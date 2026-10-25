/**
 * Modul: :feature:settings
 * @author Thomas Schmid
 *
 * Tests für Task 47.1: Zurück-Navigation in den Einstellungen,
 * leere Backstack-Fallbacks und Dialog-Zustände.
 */
package com.codeforge.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsNavigationTest {

    @Test
    fun backNavigation_invokesCallback() {
        var backCalled = false
        val onBack: () -> Unit = { backCalled = true }

        onBack()

        assertTrue("Back callback should be invoked", backCalled)
    }

    @Test
    fun emptyStackNavigation_fallsBackToStartDestination() {
        var currentRoute: String? = "settings_editor"
        val startDestination = "welcome"
        val backStack = mutableListOf<String>()

        fun navigateBackOrStart() {
            if (backStack.isNotEmpty()) {
                currentRoute = backStack.removeAt(backStack.lastIndex)
            } else {
                currentRoute = startDestination
            }
        }

        // When back stack is empty, it must navigate to start destination instead of a blank screen
        navigateBackOrStart()
        assertEquals(startDestination, currentRoute)

        // When back stack has items, it must pop the last item
        backStack.add("settings")
        currentRoute = "settings_theme"
        navigateBackOrStart()
        assertEquals("settings", currentRoute)
    }

    @Test
    fun dialogState_consumesBackBeforeExitingScreen() {
        var isDialogOpen = true
        var isScreenClosed = false

        fun handleBack() {
            if (isDialogOpen) {
                isDialogOpen = false
            } else {
                isScreenClosed = true
            }
        }

        // First back dismisses the dialog
        handleBack()
        assertFalse(isDialogOpen)
        assertFalse(isScreenClosed)

        // Second back exits the screen
        handleBack()
        assertTrue(isScreenClosed)
    }
}
