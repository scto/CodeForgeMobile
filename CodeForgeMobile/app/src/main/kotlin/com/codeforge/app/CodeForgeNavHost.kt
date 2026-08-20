// Modul: :app
package com.codeforge.app

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.codeforge.feature.editor.EditorRoute

private object Routes {
    const val ONBOARDING = "onboarding"
    const val WELCOME = "welcome"
    const val EDITOR = "editor"
}

@Composable
fun CodeForgeNavHost(startOnboarding: Boolean) {
    val navController = rememberNavController()
    val startDestination = if (startOnboarding) Routes.ONBOARDING else Routes.WELCOME

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.ONBOARDING) {
            // :feature:onboarding – IntroPagerScreen / PermissionScreen / SetupScreen
            // OnboardingRoute(onFinished = { navController.navigate(Routes.WELCOME) { popUpTo(Routes.ONBOARDING) { inclusive = true } } })
        }
        composable(Routes.WELCOME) {
            // :feature:welcome – Icon-Button-Grid (Neu/Import/Öffnen/Clone/Settings)
            // WelcomeRoute(onOpenProject = { navController.navigate(Routes.EDITOR) })
        }
        composable(Routes.EDITOR) {
            EditorRoute(onNavigate = { route -> navController.navigate(route) })
        }
    }
}
