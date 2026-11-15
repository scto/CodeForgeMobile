package {{packageName}}.feature.profile

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/** Ziel des Profils (typsichere Navigation). */
@Serializable
data object ProfileDestination

fun NavGraphBuilder.profileScreen() {
    composable<ProfileDestination> { ProfileRoute() }
}
