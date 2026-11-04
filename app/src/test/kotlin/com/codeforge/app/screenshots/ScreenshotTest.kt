/**
 * Modul: :app
 * @author Thomas Schmid
 */
package com.codeforge.app.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.codeforge.core.datastore.proto.ThemeConfig
import com.codeforge.core.datastore.proto.ThemeMode
import com.codeforge.core.designsystem.CodeForgeTheme
import com.codeforge.core.domain.model.RecentProject
import com.codeforge.feature.settings.AboutInfo
import com.codeforge.feature.settings.AboutSettingsScreen
import com.codeforge.feature.welcome.WelcomeContent
import com.codeforge.feature.welcome.WelcomeUiState
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import androidx.test.core.app.ApplicationProvider
import com.codeforge.core.resources.Res
import org.robolectric.annotation.GraphicsMode

/**
 * Erzeugt die Screenshots für die README (hell/dunkel) nach `docs/screenshots/`.
 *
 * Aufruf: `./gradlew :app:recordRoborazziDebug`. Die Bilder entstehen mit Beispieldaten und Robolectric
 * (keine Geräte-Darstellung: Schriften und Systemleisten weichen vom echten Gerät ab).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class ScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val now = System.currentTimeMillis()
    private val projects = listOf(
        RecentProject("1", "CodeForgeMobile", "/storage/emulated/0/CodeForgeMobileProjects/CodeForgeMobile", now - 3_600_000, 5),
        RecentProject("2", "KtorBackendService", "/storage/emulated/0/CodeForgeMobileProjects/KtorBackendService", now - 86_400_000, 2),
        RecentProject("3", "ComposeSamples", "/storage/emulated/0/CodeForgeMobileProjects/ComposeSamples", now - 172_800_000, 9),
    )

    private fun theme(dark: Boolean) = ThemeConfig.newBuilder()
        .setMode(if (dark) ThemeMode.DARK else ThemeMode.LIGHT)
        .setUseDynamicColor(false)
        .build()

    private fun shot(name: String, dark: Boolean, content: @Composable () -> Unit) {
        Res.init(ApplicationProvider.getApplicationContext())
        composeRule.setContent { CodeForgeTheme(themeState = theme(dark)) { content() } }
        composeRule.onRoot().captureRoboImage("../docs/screenshots/$name-${if (dark) "dark" else "light"}.png")
    }

    @Test fun welcomeLight() = shot("welcome", false) { welcome(withProjects = true) }

    @Test fun welcomeDark() = shot("welcome", true) { welcome(withProjects = true) }

    @Test fun welcomeEmptyLight() = shot("welcome-empty", false) { welcome(withProjects = false) }

    @Test fun welcomeEmptyDark() = shot("welcome-empty", true) { welcome(withProjects = false) }

    @Test fun aboutLight() = shot("about", false) { about() }

    @Test fun aboutDark() = shot("about", true) { about() }

    @Composable
    private fun welcome(withProjects: Boolean) = WelcomeContent(
        uiState = WelcomeUiState(recentProjects = if (withProjects) projects else emptyList(), isLoading = false),
    )

    @Composable
    private fun about() = AboutSettingsScreen(info = AboutInfo(versionName = "3.0.0", versionCode = 30000L))
}
