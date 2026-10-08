// Modul: :libs:template-engine
package com.codeforge.libs.template_engine

import com.codeforge.core.domain.model.ProjectHandle
import com.codeforge.core.domain.model.ProjectLanguage
import com.codeforge.core.domain.model.ProjectRequest
import com.codeforge.core.domain.model.ProjectTemplateKind
import com.codeforge.libs.template_engine.templates.AppManifest
import com.codeforge.libs.template_engine.templates.BaseLayoutContentMain
import com.codeforge.libs.template_engine.templates.DefaultGitIgnore
import com.codeforge.libs.template_engine.templates.DefaultProguardRules
import com.codeforge.libs.template_engine.templates.SimpleMaterial3Theme
import com.codeforge.libs.template_engine.templates.basicactivity.BasicActivityJava
import com.codeforge.libs.template_engine.templates.basicactivity.BasicActivityKt
import com.codeforge.libs.template_engine.templates.basicactivity.xml.BasicActivityLayout
import com.codeforge.libs.template_engine.templates.bottomnav.BotNavActivityJava
import com.codeforge.libs.template_engine.templates.bottomnav.BotNavActivityKt
import com.codeforge.libs.template_engine.templates.bottomnav.BotNavDashboardFragmentJava
import com.codeforge.libs.template_engine.templates.bottomnav.BotNavDashboardFragmentKt
import com.codeforge.libs.template_engine.templates.bottomnav.BotNavDashboardViewModelJava
import com.codeforge.libs.template_engine.templates.bottomnav.BotNavDashboardViewModelKt
import com.codeforge.libs.template_engine.templates.bottomnav.BotNavHomeFragmentJava
import com.codeforge.libs.template_engine.templates.bottomnav.BotNavHomeFragmentKt
import com.codeforge.libs.template_engine.templates.bottomnav.BotNavHomeViewModelJava
import com.codeforge.libs.template_engine.templates.bottomnav.BotNavHomeViewModelKt
import com.codeforge.libs.template_engine.templates.bottomnav.BotNavNotifFragmentJava
import com.codeforge.libs.template_engine.templates.bottomnav.BotNavNotifFragmentKt
import com.codeforge.libs.template_engine.templates.bottomnav.BotNavNotifViewModelJava
import com.codeforge.libs.template_engine.templates.bottomnav.BotNavNotifViewModelKt
import com.codeforge.libs.template_engine.templates.bottomnav.xml.BotNavNavigationXml
import com.codeforge.libs.template_engine.templates.bottomnav.xml.BotNavStringsXml
import com.codeforge.libs.template_engine.templates.compose.ComposeActivityKt
import com.codeforge.libs.template_engine.templates.compose.ComposeColorKt
import com.codeforge.libs.template_engine.templates.compose.ComposeThemeKt
import com.codeforge.libs.template_engine.templates.compose.ComposeThemesXml
import com.codeforge.libs.template_engine.templates.compose.ComposeTypeKt
import com.codeforge.libs.template_engine.templates.cppactivity.CppActivityJava
import com.codeforge.libs.template_engine.templates.cppactivity.CppActivityKt
import com.codeforge.libs.template_engine.templates.cppactivity.jni.AndroidMkFile
import com.codeforge.libs.template_engine.templates.cppactivity.jni.ApplicationMkFile
import com.codeforge.libs.template_engine.templates.cppactivity.jni.BasicJniCppSource
import com.codeforge.libs.template_engine.templates.drawernav.DashboardFragmentJava
import com.codeforge.libs.template_engine.templates.drawernav.DashboardFragmentKt
import com.codeforge.libs.template_engine.templates.drawernav.DashboardViewModelJava
import com.codeforge.libs.template_engine.templates.drawernav.DashboardViewModelKt
import com.codeforge.libs.template_engine.templates.drawernav.HomeFragmentJava
import com.codeforge.libs.template_engine.templates.drawernav.HomeFragmentKt
import com.codeforge.libs.template_engine.templates.drawernav.HomeViewModelJava
import com.codeforge.libs.template_engine.templates.drawernav.HomeViewModelKt
import com.codeforge.libs.template_engine.templates.drawernav.NavDrawerActivityJava
import com.codeforge.libs.template_engine.templates.drawernav.NavDrawerActivityKt
import com.codeforge.libs.template_engine.templates.drawernav.xml.NavDrawerNavigationXml
import com.codeforge.libs.template_engine.templates.drawernav.xml.NavDrawerStringsXml
import com.codeforge.libs.template_engine.templates.emptyactivity.EmptyActivityJava
import com.codeforge.libs.template_engine.templates.emptyactivity.EmptyActivityKt
import com.codeforge.libs.template_engine.templates.gradle.AppBuildGradle
import com.codeforge.libs.template_engine.templates.gradle.GradleProperties
import com.codeforge.libs.template_engine.templates.gradle.RootBuildGradle
import com.codeforge.libs.template_engine.templates.gradle.SettingsGradle
import com.codeforge.libs.template_engine.templates.noandroidx.NoAndroidXActivityJava
import com.codeforge.libs.template_engine.templates.noandroidx.NoAndroidXActivityKt
import com.codeforge.libs.template_engine.templates.noandroidx.xml.NoAndroidXActivityLayout
import com.codeforge.libs.template_engine.templates.tabactivity.TabActivityJava
import com.codeforge.libs.template_engine.templates.tabactivity.TabActivityKt
import com.codeforge.libs.template_engine.templates.tabactivity.TabPageViewModelJava
import com.codeforge.libs.template_engine.templates.tabactivity.TabPageViewModelKt
import com.codeforge.libs.template_engine.templates.tabactivity.TabPagerAdapterJava
import com.codeforge.libs.template_engine.templates.tabactivity.TabPagerAdapterKt
import com.codeforge.libs.template_engine.templates.tabactivity.TabPlaceholderFragmentJava
import com.codeforge.libs.template_engine.templates.tabactivity.TabPlaceholderFragmentKt
import com.codeforge.libs.template_engine.templates.tabactivity.xml.TabStringsXml
import java.io.File

/**
 * Erzeugt ein Android-Gradle-Projekt aus den eingebauten Vorlagen: Gradle-/Manifest-Dateien und
 * Quelltexte kommen aus den Kotlin-Template-Funktionen (`templates/`), Wrapper, Basis-Ressourcen
 * (Launcher-Icons, Farben, Themes) und Layout-Ressourcen aus den Assets (siehe [AssetSource]).
 *
 * Blockierend (Dateisystem) — der Aufrufer läuft auf einem IO-Dispatcher.
 */
class ProjectGenerator(private val assets: AssetSource) {

    fun generate(request: ProjectRequest, projectDir: File): ProjectHandle {
        val kind = request.template.kind
        val useKotlin = request.language == ProjectLanguage.KOTLIN
        val useKts = request.useKotlinDsl
        val appId = request.packageName
        val name = request.projectName
        val ext = if (useKts) ".kts" else ""

        val appDir = File(projectDir, "app")
        val srcMain = File(appDir, "src/main")
        val resDir = File(srcMain, "res")
        val codeDir = File(srcMain, if (useKotlin) "kotlin" else "java")
        val valuesDir = File(resDir, "values")
        val valuesNightDir = File(resDir, "values-night")
        listOf(codeDir, valuesDir, valuesNightDir).forEach { it.mkdirs() }

        // --- Gradle-Grundgerüst ---
        copyAssetsDir(assets, "gradle", File(projectDir, "gradle"))
        copyAsset(assets, "gradlew/gradlew", File(projectDir, "gradlew"))
        copyAsset(assets, "gradlew/gradlew.bat", File(projectDir, "gradlew.bat"))
        File(projectDir, "gradlew").setExecutable(true)

        writeText(File(projectDir, "settings.gradle$ext"), SettingsGradle(name))
        writeText(File(projectDir, "build.gradle$ext"), RootBuildGradle(useKts))
        writeText(
            File(projectDir, "gradle.properties"),
            GradleProperties(addAndroidX = kind != ProjectTemplateKind.NO_ANDROIDX_ACTIVITY)
        )
        writeText(File(projectDir, "proguard-rules.pro"), DefaultProguardRules())
        writeText(File(projectDir, ".gitignore"), DefaultGitIgnore())

        // --- Basis-Ressourcen, danach projektspezifisch überschrieben ---
        copyAssetsDir(assets, "res/resources", resDir)
        writeText(File(valuesDir, "strings.xml"), buildStringsXml(name))
        writeText(File(valuesDir, "themes.xml"), SimpleMaterial3Theme("AppTheme", false))
        writeText(File(valuesNightDir, "themes.xml"), SimpleMaterial3Theme("AppTheme", false))
        writeText(
            File(appDir, "build.gradle$ext"),
            AppBuildGradle(
                appId = appId,
                minSdk = request.minSdk,
                useKotlin = useKotlin,
                useKts = useKts,
                templateKind = kind
            )
        )
        writeText(File(srcMain, "AndroidManifest.xml"), AppManifest(appId))

        val pkgDir = File(codeDir, appId.replace('.', '/')).apply { mkdirs() }
        val ctx = Ctx(useKotlin, appId, resDir, valuesDir, pkgDir, appDir)

        when (kind) {
            ProjectTemplateKind.NO_ACTIVITY -> Unit
            ProjectTemplateKind.EMPTY_ACTIVITY -> emptyActivity(ctx)
            ProjectTemplateKind.CPP_ACTIVITY -> cppActivity(ctx)
            ProjectTemplateKind.BASIC_ACTIVITY -> basicActivity(ctx)
            ProjectTemplateKind.NAV_DRAWER_ACTIVITY -> navDrawer(ctx)
            ProjectTemplateKind.BOTTOM_NAV_ACTIVITY -> bottomNav(ctx)
            ProjectTemplateKind.TABBED_ACTIVITY -> tabbed(ctx)
            ProjectTemplateKind.NO_ANDROIDX_ACTIVITY -> noAndroidX(ctx)
            ProjectTemplateKind.COMPOSE_ACTIVITY -> compose(ctx)
        }
        return ProjectHandle(rootPath = projectDir.path, projectName = name, moduleCount = 1)
    }

    private class Ctx(
        val kotlin: Boolean,
        val appId: String,
        val resDir: File,
        val valuesDir: File,
        val pkgDir: File,
        val appDir: File
    ) {
        /** Schreibt `<dir>/<base>.kt|.java` je nach Sprache. */
        fun source(dir: File, base: String, kotlinSrc: () -> String, javaSrc: () -> String) {
            if (kotlin) writeText(File(dir, "$base.kt"), kotlinSrc()) else writeText(File(dir, "$base.java"), javaSrc())
        }
    }

    private fun emptyActivity(c: Ctx) {
        writeText(File(c.resDir, "layout/activity_main.xml"), BaseLayoutContentMain())
        c.source(c.pkgDir, "MainActivity", { EmptyActivityKt(c.appId) }, { EmptyActivityJava(c.appId) })
    }

    private fun cppActivity(c: Ctx) {
        writeText(File(c.resDir, "layout/activity_main.xml"), BaseLayoutContentMain())
        val jniDir = File(c.appDir, "src/main/jni").apply { mkdirs() }
        writeText(File(jniDir, "tomaslib.cpp"), BasicJniCppSource(c.appId))
        writeText(File(jniDir, "Android.mk"), AndroidMkFile())
        writeText(File(jniDir, "Application.mk"), ApplicationMkFile())
        c.source(c.pkgDir, "MainActivity", { CppActivityKt(c.appId) }, { CppActivityJava(c.appId) })
    }

    private fun basicActivity(c: Ctx) {
        writeText(File(c.resDir, "layout/activity_main.xml"), BasicActivityLayout())
        writeText(File(c.resDir, "layout/content_main.xml"), BaseLayoutContentMain())
        c.source(c.pkgDir, "MainActivity", { BasicActivityKt(c.appId) }, { BasicActivityJava(c.appId) })
    }

    private fun noAndroidX(c: Ctx) {
        writeText(File(c.resDir, "layout/activity_main.xml"), NoAndroidXActivityLayout())
        c.source(c.pkgDir, "MainActivity", { NoAndroidXActivityKt(c.appId) }, { NoAndroidXActivityJava(c.appId) })
    }

    private fun compose(c: Ctx) {
        val uiDir = File(c.pkgDir, "ui/theme").apply { mkdirs() }
        writeText(File(c.valuesDir, "themes.xml"), ComposeThemesXml())
        writeText(File(uiDir, "Color.kt"), ComposeColorKt(c.appId))
        writeText(File(uiDir, "Theme.kt"), ComposeThemeKt(c.appId))
        writeText(File(uiDir, "Type.kt"), ComposeTypeKt(c.appId))
        writeText(File(c.pkgDir, "MainActivity.kt"), ComposeActivityKt(c.appId))
    }

    private fun navDrawer(c: Ctx) {
        copyAssetsDir(assets, "templates/navDrawer/res", c.resDir)
        writeText(File(c.resDir, "navigation/mobile_navigation.xml"), NavDrawerNavigationXml(c.appId))
        mergeStringsXml(c.valuesDir, NavDrawerStringsXml())

        val homeDir = File(c.pkgDir, "ui/home").apply { mkdirs() }
        val galleryDir = File(c.pkgDir, "ui/gallery").apply { mkdirs() }
        val slideshowDir = File(c.pkgDir, "ui/slideshow").apply { mkdirs() }

        // Gallery/Slideshow entstehen aus der Dashboard-Vorlage durch Umbenennen
        fun String.viewModelAs(screen: String, cls: String) = replace("ui.dashboard", "ui.$screen")
            .replace("DashboardViewModel", "${cls}ViewModel").replace("dashboard", screen)
        fun String.fragmentAs(screen: String, cls: String) = replace("ui.dashboard", "ui.$screen")
            .replace("DashboardFragment", "${cls}Fragment")
            .replace("FragmentDashboardBinding", "Fragment${cls}Binding")
            .replace("textDashboard", "text$cls").replace("DashboardViewModel", "${cls}ViewModel")

        c.source(c.pkgDir, "MainActivity", { NavDrawerActivityKt(c.appId) }, { NavDrawerActivityJava(c.appId) })
        c.source(homeDir, "HomeViewModel", { HomeViewModelKt(c.appId) }, { HomeViewModelJava(c.appId) })
        c.source(homeDir, "HomeFragment", { HomeFragmentKt(c.appId) }, { HomeFragmentJava(c.appId) })
        c.source(galleryDir, "GalleryViewModel",
            { DashboardViewModelKt(c.appId).viewModelAs("gallery", "Gallery") },
            { DashboardViewModelJava(c.appId).viewModelAs("gallery", "Gallery") })
        c.source(galleryDir, "GalleryFragment",
            { DashboardFragmentKt(c.appId).fragmentAs("gallery", "Gallery") },
            { DashboardFragmentJava(c.appId).fragmentAs("gallery", "Gallery") })
        c.source(slideshowDir, "SlideshowViewModel",
            { DashboardViewModelKt(c.appId).viewModelAs("slideshow", "Slideshow") },
            { DashboardViewModelJava(c.appId).viewModelAs("slideshow", "Slideshow") })
        c.source(slideshowDir, "SlideshowFragment",
            { DashboardFragmentKt(c.appId).fragmentAs("slideshow", "Slideshow") },
            { DashboardFragmentJava(c.appId).fragmentAs("slideshow", "Slideshow") })
    }

    private fun bottomNav(c: Ctx) {
        copyAssetsDir(assets, "templates/bottomNav/res", c.resDir)
        writeText(File(c.resDir, "navigation/mobile_navigation.xml"), BotNavNavigationXml(c.appId))
        mergeStringsXml(c.valuesDir, BotNavStringsXml())

        val homeDir = File(c.pkgDir, "ui/home").apply { mkdirs() }
        val dashboardDir = File(c.pkgDir, "ui/dashboard").apply { mkdirs() }
        val notificationsDir = File(c.pkgDir, "ui/notifications").apply { mkdirs() }

        c.source(c.pkgDir, "MainActivity", { BotNavActivityKt(c.appId) }, { BotNavActivityJava(c.appId) })
        c.source(homeDir, "HomeViewModel", { BotNavHomeViewModelKt(c.appId) }, { BotNavHomeViewModelJava(c.appId) })
        c.source(homeDir, "HomeFragment", { BotNavHomeFragmentKt(c.appId) }, { BotNavHomeFragmentJava(c.appId) })
        c.source(dashboardDir, "DashboardViewModel", { BotNavDashboardViewModelKt(c.appId) }, { BotNavDashboardViewModelJava(c.appId) })
        c.source(dashboardDir, "DashboardFragment", { BotNavDashboardFragmentKt(c.appId) }, { BotNavDashboardFragmentJava(c.appId) })
        c.source(notificationsDir, "NotificationsViewModel", { BotNavNotifViewModelKt(c.appId) }, { BotNavNotifViewModelJava(c.appId) })
        c.source(notificationsDir, "NotificationsFragment", { BotNavNotifFragmentKt(c.appId) }, { BotNavNotifFragmentJava(c.appId) })
    }

    private fun tabbed(c: Ctx) {
        copyAssetsDir(assets, "templates/tabbed/res", c.resDir)
        mergeStringsXml(c.valuesDir, TabStringsXml())

        val uiDir = File(c.pkgDir, "ui/main").apply { mkdirs() }
        c.source(c.pkgDir, "MainActivity", { TabActivityKt(c.appId) }, { TabActivityJava(c.appId) })
        c.source(uiDir, "SectionsPagerAdapter", { TabPagerAdapterKt(c.appId) }, { TabPagerAdapterJava(c.appId) })
        c.source(uiDir, "PageViewModel", { TabPageViewModelKt(c.appId) }, { TabPageViewModelJava(c.appId) })
        c.source(uiDir, "PlaceholderFragment", { TabPlaceholderFragmentKt(c.appId) }, { TabPlaceholderFragmentJava(c.appId) })
    }
}
