package com.codeforge.libs.template_engine

import android.content.Context
import com.codeforge.core.common.logging.AppLogger
import com.codeforge.libs.template_engine.template.*
import com.codeforge.libs.template_engine.template.bottomnav.*
import com.codeforge.libs.template_engine.template.compose.*
import com.codeforge.libs.template_engine.template.drawernav.*
import com.codeforge.libs.template_engine.template.drawernav.xml.*
import com.codeforge.libs.template_engine.template.emptyactivity.*
import com.codeforge.libs.template_engine.template.gradle.*
import com.codeforge.libs.template_engine.template.noandroidx.*
import com.codeforge.libs.template_engine.template.noandroidx.xml.*
import com.codeforge.libs.template_engine.template.tabactivity.*
import java.io.File

fun CreateTemplate(
    context: Context,
    template: ProjectTemplate,
    projectDir: File,
    appId: String,
    minSdk: Int,
    language: String,
    useKts: Boolean = false
) {
    AppLogger.step("TemplateEngine", "CreateTemplate started: ${template.name} (${template.kind}) at ${projectDir.absolutePath}")
    AppLogger.d("TemplateEngine", "Params: appId=$appId, minSdk=$minSdk, language=$language, useKts=$useKts")

    try {
        val useKotlin = language.equals("Kotlin", ignoreCase = true)
        val name = projectDir.name
        val ext = if (useKts) ".kts" else ""
        val srcType = if (useKotlin) "kotlin" else "java"

        val appDir = File(projectDir, "app")
        val srcMain = File(appDir, "src/main")
        val resDir = File(srcMain, "res")
        val codeDir = File(srcMain, srcType)
        val valuesDir = File(resDir, "values")
        val valuesNightDir = File(resDir, "values-night")

        AppLogger.d("TemplateEngine", "Creating directory structure for ${projectDir.name}")
        listOf(codeDir, valuesDir, valuesNightDir).forEach { 
            if (!it.exists()) {
                val created = it.mkdirs()
                AppLogger.d("TemplateEngine", "Directory created: ${it.absolutePath} (success=$created)")
            }
        }

        AppLogger.d("TemplateEngine", "Copying gradle wrapper and build scripts")
        try {
            copyAssetsDir(context, "gradle", File(projectDir, "gradle"))
            copyAsset(context, "gradlew/gradlew", File(projectDir, "gradlew"))
            copyAsset(context, "gradlew/gradlew.bat", File(projectDir, "gradlew.bat"))
            File(projectDir, "gradlew").setExecutable(true)
        } catch (e: Exception) {
            AppLogger.w("TemplateEngine", "Warning copying gradlew assets: ${e.message}", e)
        }

        writeText(File(projectDir, "settings.gradle$ext"), SettingsGradle(name))
        writeText(File(projectDir, "build.gradle$ext"), RootBuildGradle(useKts))
        writeText(
            File(projectDir, "gradle.properties"),
            GradleProperties(
                addAndroidX = template.kind != ProjectTemplate.Kind.NO_ANDROIDX_ACTIVITY
            )
        )
        writeText(File(projectDir, "proguard-rules.pro"), DefaultProguardRules())
        writeText(File(projectDir, ".gitignore"), DefaultGitIgnore())

        try {
            copyAssetsDir(context, "res/resources", resDir)
        } catch (e: Exception) {
            AppLogger.w("TemplateEngine", "Warning copying res/resources assets: ${e.message}", e)
        }

        writeText(File(valuesDir, "strings.xml"), buildStringsXml(name))
        writeText(File(valuesDir, "themes.xml"), SimpleMaterial3Theme("AppTheme", false))
        writeText(File(valuesNightDir, "themes.xml"), SimpleMaterial3Theme("AppTheme", false))
        writeText(
            File(appDir, "build.gradle$ext"),
            AppBuildGradle(
                appId = appId,
                minSdk = minSdk,
                useKotlin = useKotlin,
                useKts = useKts,
                templateKind = template.kind
            )
        )
        writeText(File(srcMain, "AndroidManifest.xml"), AppManifest(appId))

        val pkgDir = File(codeDir, appId.replace('.', '/')).apply { mkdirs() }
        AppLogger.d("TemplateEngine", "Generating package files in ${pkgDir.absolutePath}")

        when (template.kind) {
            ProjectTemplate.Kind.NO_ACTIVITY -> {
                AppLogger.d("TemplateEngine", "Generated No Activity template")
            }

            ProjectTemplate.Kind.EMPTY_ACTIVITY -> {
                writeText(File(resDir, "layout/activity_main.xml"), BaseLayoutContentMain())
                if (useKotlin) {
                    writeText(File(pkgDir, "MainActivity.kt"), EmptyActivityKt(appId))
                } else {
                    writeText(File(pkgDir, "MainActivity.java"), EmptyActivityJava(appId))
                }
            }
            ProjectTemplate.Kind.CPP_ACTIVITY -> {
                writeText(File(resDir, "layout/activity_main.xml"), BaseLayoutContentMain())

                val jniDir = File(appDir, "src/main/jni").apply { mkdirs() }
                writeText(File(jniDir, "tomaslib.cpp"), BasicJniCppSource(appId))
                writeText(File(jniDir, "Android.mk"), AndroidMkFile())
                writeText(File(jniDir, "Application.mk"), ApplicationMkFile())

                if (useKotlin) {
                    writeText(File(pkgDir, "MainActivity.kt"), CppActivityKt(appId))
                } else {
                    writeText(File(pkgDir, "MainActivity.java"), CppActivityJava(appId))
                }
            }
            ProjectTemplate.Kind.BASIC_ACTIVITY -> {
                writeText(File(resDir, "layout/activity_main.xml"), BasicActivityLayout())
                writeText(File(resDir, "layout/content_main.xml"), BaseLayoutContentMain())

                if (useKotlin) {
                    writeText(File(pkgDir, "MainActivity.kt"), BasicActivityKt(appId))
                } else {
                    writeText(File(pkgDir, "MainActivity.java"), BasicActivityJava(appId))
                }
            }
            ProjectTemplate.Kind.NAV_DRAWER_ACTIVITY,
            ProjectTemplate.Kind.NAVIGATION_DRAWER_ACTIVITY -> {
                try {
                    copyAssetsDir(context, "templates/navDrawer/res", resDir)
                } catch (e: Exception) {
                    AppLogger.w("TemplateEngine", "Warning copying navDrawer assets: ${e.message}", e)
                }
                writeText(
                    File(resDir, "navigation/mobile_navigation.xml"),
                    NavDrawerNavigationXml(appId)
                )
                mergeStringsXml(valuesDir, NavDrawerStringsXml())

                val homeDir = File(pkgDir, "ui/home").apply { mkdirs() }
                val gallDir = File(pkgDir, "ui/gallery").apply { mkdirs() }
                val slideDir = File(pkgDir, "ui/slideshow").apply { mkdirs() }

                fun String.toGalleryViewModel() = replace("ui.dashboard", "ui.gallery")
                    .replace("DashboardViewModel", "GalleryViewModel")
                    .replace("dashboard", "gallery")

                fun String.toGalleryFragment() = replace("ui.dashboard", "ui.gallery")
                    .replace("DashboardFragment", "GalleryFragment")
                    .replace("FragmentDashboardBinding", "FragmentGalleryBinding")
                    .replace("textDashboard", "textGallery")
                    .replace("DashboardViewModel", "GalleryViewModel")

                fun String.toSlideshowViewModel() = replace("ui.dashboard", "ui.slideshow")
                    .replace("DashboardViewModel", "SlideshowViewModel")
                    .replace("dashboard", "slideshow")

                fun String.toSlideshowFragment() = replace("ui.dashboard", "ui.slideshow")
                    .replace("DashboardFragment", "SlideshowFragment")
                    .replace("FragmentDashboardBinding", "FragmentSlideshowBinding")
                    .replace("textDashboard", "textSlideshow")
                    .replace("DashboardViewModel", "SlideshowViewModel")

                if (useKotlin) {
                    writeText(File(pkgDir, "MainActivity.kt"), NavDrawerActivityKt(appId))
                    writeText(File(homeDir, "HomeViewModel.kt"), HomeViewModelKt(appId))
                    writeText(File(homeDir, "HomeFragment.kt"), HomeFragmentKt(appId))
                    writeText(
                        File(gallDir, "GalleryViewModel.kt"),
                        DashboardViewModelKt(appId).toGalleryViewModel()
                    )
                    writeText(
                        File(gallDir, "GalleryFragment.kt"),
                        DashboardFragmentKt(appId).toGalleryFragment()
                    )
                    writeText(
                        File(slideDir, "SlideshowViewModel.kt"),
                        DashboardViewModelKt(appId).toSlideshowViewModel()
                    )
                    writeText(
                        File(slideDir, "SlideshowFragment.kt"),
                        DashboardFragmentKt(appId).toSlideshowFragment()
                    )
                } else {
                    writeText(File(pkgDir, "MainActivity.java"), NavDrawerActivityJava(appId))
                    writeText(File(homeDir, "HomeViewModel.java"), HomeViewModelJava(appId))
                    writeText(File(homeDir, "HomeFragment.java"), HomeFragmentJava(appId))
                    writeText(
                        File(gallDir, "GalleryViewModel.java"),
                        DashboardViewModelJava(appId).toGalleryViewModel()
                    )
                    writeText(
                        File(gallDir, "GalleryFragment.java"),
                        DashboardFragmentJava(appId).toGalleryFragment()
                    )
                    writeText(
                        File(slideDir, "SlideshowViewModel.java"),
                        DashboardViewModelJava(appId).toSlideshowViewModel()
                    )
                    writeText(
                        File(slideDir, "SlideshowFragment.java"),
                        DashboardFragmentJava(appId).toSlideshowFragment()
                    )
                }
            }
            ProjectTemplate.Kind.BOTTOM_NAV_ACTIVITY,
            ProjectTemplate.Kind.BOTTOM_NAVIGATION_ACTIVITY -> {
                try {
                    copyAssetsDir(context, "templates/bottomNav/res", resDir)
                } catch (e: Exception) {
                    AppLogger.w("TemplateEngine", "Warning copying bottomNav assets: ${e.message}", e)
                }
                writeText(File(resDir, "navigation/mobile_navigation.xml"), NavigationXml(appId))
                mergeStringsXml(valuesDir, TabStringsXml())

                val homeDir = File(pkgDir, "ui/home").apply { mkdirs() }
                val dashDir = File(pkgDir, "ui/dashboard").apply { mkdirs() }
                val notiDir = File(pkgDir, "ui/notifications").apply { mkdirs() }

                if (useKotlin) {
                    writeText(File(pkgDir, "MainActivity.kt"), TabActivityKt(appId))
                    writeText(File(homeDir, "HomeViewModel.kt"), HomeViewModelKt(appId))
                    writeText(File(homeDir, "HomeFragment.kt"), HomeFragmentKt(appId))
                    writeText(File(dashDir, "DashboardViewModel.kt"), DashboardViewModelKt(appId))
                    writeText(File(dashDir, "DashboardFragment.kt"), DashboardFragmentKt(appId))
                    writeText(File(notiDir, "NotificationsViewModel.kt"), NotifViewModelKt(appId))
                    writeText(File(notiDir, "NotificationsFragment.kt"), NotifFragmentKt(appId))
                } else {
                    writeText(File(pkgDir, "MainActivity.java"), TabActivityJava(appId))
                    writeText(File(homeDir, "HomeViewModel.java"), HomeViewModelJava(appId))
                    writeText(File(homeDir, "HomeFragment.java"), HomeFragmentJava(appId))
                    writeText(File(dashDir, "DashboardViewModel.java"), DashboardViewModelJava(appId))
                    writeText(File(dashDir, "DashboardFragment.java"), DashboardFragmentJava(appId))
                    writeText(File(notiDir, "NotificationsViewModel.java"), NotifViewModelJava(appId))
                    writeText(File(notiDir, "NotificationsFragment.java"), NotifFragmentJava(appId))
                }
            }
            ProjectTemplate.Kind.TABBED_ACTIVITY -> {
                try {
                    copyAssetsDir(context, "templates/tabbed/res", resDir)
                } catch (e: Exception) {
                    AppLogger.w("TemplateEngine", "Warning copying tabbed assets: ${e.message}", e)
                }
                mergeStringsXml(valuesDir, BotNavStringsXml())

                val uiDir = File(pkgDir, "ui/main").apply { mkdirs() }

                if (useKotlin) {
                    writeText(File(pkgDir, "MainActivity.kt"), TabActivityKt(appId))
                    writeText(File(uiDir, "SectionsPagerAdapter.kt"), PagerAdapterKt(appId))
                    writeText(File(uiDir, "PageViewModel.kt"), PageViewModelKt(appId))
                    writeText(File(uiDir, "PlaceholderFragment.kt"), PlaceholderFragmentKt(appId))
                } else {
                    writeText(File(pkgDir, "MainActivity.java"), TabActivityJava(appId))
                    writeText(File(uiDir, "SectionsPagerAdapter.java"), PagerAdapterJava(appId))
                    writeText(File(uiDir, "PageViewModel.java"), PageViewModelJava(appId))
                    writeText(File(uiDir, "PlaceholderFragment.java"), PlaceholderFragmentJava(appId))
                }
            }
            ProjectTemplate.Kind.NO_ANDROIDX_ACTIVITY -> {
                writeText(File(resDir, "layout/activity_main.xml"), NoAndroidXActivityLayout())
                if (useKotlin) {
                    writeText(File(pkgDir, "MainActivity.kt"), NoAndroidXActivityKt(appId))
                } else {
                    writeText(File(pkgDir, "MainActivity.java"), NoAndroidXActivityJava(appId))
                }
            }
            ProjectTemplate.Kind.COMPOSE_ACTIVITY -> {
                val uiDir = File(pkgDir, "ui/theme").apply { mkdirs() }

                writeText(File(valuesDir, "themes.xml"), ComposeThemesXml())
                writeText(File(uiDir, "Color.kt"), ComposeColorKt(appId))
                writeText(File(uiDir, "Theme.kt"), ComposeThemeKt(appId))
                writeText(File(uiDir, "Type.kt"), ComposeTypeKt(appId))
                writeText(File(pkgDir, "MainActivity.kt"), ComposeActivityKt(appId))
            }
        }
        AppLogger.step("TemplateEngine", "CreateTemplate finished successfully for ${projectDir.name}")
    } catch (e: Exception) {
        AppLogger.e("TemplateEngine", "FAILED to create template for ${projectDir.name}: ${e.message}", e)
        throw e
    }
}

fun mergeStringsXml(valuesDir: File, additionalStringsXml: String) {
    val stringsFile = File(valuesDir, "strings.xml")
    if (!stringsFile.exists()) {
        writeText(
            stringsFile,
            """<?xml version="1.0" encoding="utf-8"?>
<resources>
</resources>
"""
        )
    }
    val base = stringsFile.readText()
    val baseNames = Regex("""<string\s+name="([^"]+)"""").findAll(base).map {
        it.groupValues[1]
    }.toSet()
    val additionalNodes = Regex(
        """<string\s+name="([^"]+)"[^>]*>.*?</string>""",
        RegexOption.DOT_MATCHES_ALL
    )
        .findAll(additionalStringsXml).map { it.value.trim() }
        .filter { node ->
            val n = Regex("""<string\s+name="([^"]+)"""").find(node)?.groupValues?.get(1)
            n != null && n !in baseNames
        }
        .toList()
    if (additionalNodes.isEmpty()) return
    val insertion = additionalNodes.joinToString("\n") { "    $it" } + "\n"
    val merged = if (base.contains("</resources>")) {
        base.replace(
            "</resources>",
            insertion + "</resources>"
        )
    } else {
        base + "\n" + insertion
    }
    stringsFile.writeText(merged)
}

fun buildStringsXml(name: String) = """
    <?xml version="1.0" encoding="utf-8"?>
    <resources>
        <string name="app_name">$name</string>
    </resources>
""".trimIndent()

fun writeText(file: File, content: String) {
    try {
        file.parentFile?.mkdirs()
        file.writeText(content)
        AppLogger.d("TemplateEngine", "Wrote file: ${file.absolutePath}")
    } catch (e: Exception) {
        AppLogger.e("TemplateEngine", "Error writing file ${file.absolutePath}: ${e.message}", e)
        throw e
    }
}

fun copyAsset(context: Context, assetPath: String, dst: File) {
    try {
        dst.parentFile?.mkdirs()
        context.assets.open(assetPath).use { it.copyTo(dst.outputStream()) }
        AppLogger.d("TemplateEngine", "Copied asset: $assetPath -> ${dst.absolutePath}")
    } catch (e: Exception) {
        AppLogger.w("TemplateEngine", "Could not copy asset $assetPath: ${e.message}")
    }
}

fun copyAssetsDir(context: Context, assetRoot: String, outRoot: File) {
    fun copyDir(path: String) {
        val entries = context.assets.list(path)?.toList() ?: return
        for (name in entries) {
            val child = if (path.isBlank()) name else "$path/$name"
            val children = context.assets.list(child)?.toList() ?: emptyList()
            if (children.isNotEmpty()) {
                copyDir(child)
            } else {
                val out = File(outRoot, child.removePrefix("$assetRoot/"))
                out.parentFile?.mkdirs()
                context.assets.open(child).use { it.copyTo(out.outputStream()) }
            }
        }
    }
    copyDir(assetRoot)
}
