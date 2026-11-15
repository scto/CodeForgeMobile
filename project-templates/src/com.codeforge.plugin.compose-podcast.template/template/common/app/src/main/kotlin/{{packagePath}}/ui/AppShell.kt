package {{packageName}}.ui

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import {{packageName}}.core.designsystem.icon.AppIcons
import {{packageName}}.core.resources.R
import {{packageName}}.feature.discover.DiscoverDestination
import {{packageName}}.feature.discover.discoverScreen
import {{packageName}}.feature.library.LibraryDestination
import {{packageName}}.feature.library.PodcastDestination
import {{packageName}}.feature.library.libraryScreen
import {{packageName}}.feature.library.podcastScreen
import {{packageName}}.feature.player.MiniPlayerRoute
import {{packageName}}.feature.player.PlayerDestination
import {{packageName}}.feature.player.playerScreen
import {{packageName}}.feature.settings.SettingsDestination
import {{packageName}}.feature.settings.settingsScreen

/** Die drei Ziele der unteren Leiste. */
enum class TopLevelDestination(val route: Any, val icon: ImageVector, @param:StringRes val label: Int) {
    DISCOVER(DiscoverDestination, Icons.Filled.Home, R.string.nav_discover),
    LIBRARY(LibraryDestination, AppIcons.Headphones, R.string.nav_library),
    SETTINGS(SettingsDestination, Icons.Filled.Settings, R.string.nav_settings)
}

/** Gerüst: Inhalt, Mini-Player und untere Leiste; der Vollbild-Player blendet beides aus. */
@Composable
fun AppShell(modifier: Modifier = Modifier, navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val showBars = destination?.hasRoute(PlayerDestination::class) != true
    Scaffold(
        modifier = modifier,
        bottomBar = {
            AnimatedVisibility(visible = showBars, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
                Column {
                    MiniPlayerRoute(onOpen = { navController.navigate(PlayerDestination) })
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                        TopLevelDestination.entries.forEach { item ->
                            NavigationBarItem(
                                selected = destination.isOn(item),
                                onClick = { navController.navigateToTopLevel(item) },
                                icon = { Icon(item.icon, contentDescription = null) },
                                label = { Text(stringResource(item.label)) },
                                colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer)
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = DiscoverDestination,
            modifier = Modifier.padding(bottom = if (showBars) padding.calculateBottomPadding() else 0.dp)
        ) {
            discoverScreen(
                onOpenPodcast = { navController.navigate(PodcastDestination(it)) },
                onOpenPlayer = { navController.navigate(PlayerDestination) }
            )
            libraryScreen(onOpenPodcast = { navController.navigate(PodcastDestination(it)) })
            podcastScreen(onBack = navController::popBackStack)
            playerScreen(onClose = navController::popBackStack)
            settingsScreen()
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
