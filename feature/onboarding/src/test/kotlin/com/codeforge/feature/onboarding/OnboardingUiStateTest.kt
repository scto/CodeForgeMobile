// Modul: :feature:onboarding
package com.codeforge.feature.onboarding

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingUiStateTest {
    @Test
    fun `Loslegen braucht Speicher und App-Installation, Benachrichtigungen sind optional`() {
        assertFalse(OnboardingUiState().requiredPermissionsGranted)
        assertFalse(OnboardingUiState(storagePermissionGranted = true).requiredPermissionsGranted)
        assertFalse(OnboardingUiState(installPackagesGranted = true).requiredPermissionsGranted)
        assertTrue(OnboardingUiState(storagePermissionGranted = true, installPackagesGranted = true).requiredPermissionsGranted)
    }
}
