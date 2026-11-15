package {{packageName}}.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import {{packageName}}.MainViewModel
import {{packageName}}.feature.profile.profileScreen
import {{packageName}}.feature.tasks.tasksScreen
import {{packageName}}.feature.today.TodayDestination
import {{packageName}}.feature.today.todayScreen

/** Gerüst der App: obere Leiste, untere Navigationsleiste (NavigationBar) und die drei Bildschirme. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppShell(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    viewModel: MainViewModel = hiltViewModel()
) {
    val openTasks by viewModel.openTasks.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val current = TopLevelDestination.entries.firstOrNull { backStackEntry?.destination.isOn(it) }
        ?: TopLevelDestination.TODAY

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(current.label)) }) },
        bottomBar = {
            NavigationBar {
                TopLevelDestination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = destination == current,
                        onClick = { navController.navigateToTopLevel(destination) },
                        label = { Text(stringResource(destination.label)) },
                        icon = {
                            // Abzeichen mit der Zahl offener Aufgaben.
                            if (destination == TopLevelDestination.TASKS && openTasks > 0) {
                                BadgedBox(badge = { Badge { Text(openTasks.toString()) } }) {
                                    Icon(destination.icon, contentDescription = null)
                                }
                            } else {
                                Icon(destination.icon, contentDescription = null)
                            }
                        }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = TodayDestination,
            modifier = Modifier.padding(padding)
        ) {
            todayScreen(onOpenTasks = { navController.navigateToTopLevel(TopLevelDestination.TASKS) })
            tasksScreen()
            profileScreen()
        }
    }
}

private fun NavDestination?.isOn(destination: TopLevelDestination): Boolean =
    this?.hierarchy?.any { it.hasRoute(destination.route::class) } == true

/** Wechselt das Hauptziel: kein doppelter Eintrag im Rückstapel, Zustand der Ziele bleibt erhalten. */
private fun NavHostController.navigateToTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
