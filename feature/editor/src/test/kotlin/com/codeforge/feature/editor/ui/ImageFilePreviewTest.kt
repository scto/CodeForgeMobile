package com.codeforge.feature.editor.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ImageFilePreviewTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun standardImageExtensionsRecognized() {
        assertTrue(isImageFilePath("icon.png"))
        assertTrue(isImageFilePath("photo.jpg"))
        assertTrue(isImageFilePath("photo.jpeg"))
        assertTrue(isImageFilePath("image.webp"))
        assertTrue(isImageFilePath("anim.gif"))
        assertTrue(isImageFilePath("favicon.ico"))
        assertTrue(isImageFilePath("logo.svg"))
        assertTrue(isImageFilePath("graphic.bmp"))
        assertTrue(isImageFilePath("file:///storage/emulated/0/icon.PNG"))
    }

    @Test
    fun nonImageFilesReturnFalse() {
        assertFalse(isImageFilePath("MainActivity.kt"))
        assertFalse(isImageFilePath("Service.java"))
        assertFalse(isImageFilePath("build.gradle.kts"))
        assertFalse(isImageFilePath("libs.versions.toml"))
        assertFalse(isImageFilePath("strings.xml"))
        assertFalse(isImageFilePath("AndroidManifest.xml"))
    }

    @Test
    fun vectorDrawableXmlRecognizedWhenInDrawableDirectory() {
        val drawableDir = tempFolder.newFolder("app", "src", "main", "res", "drawable")
        val vectorFile = File(drawableDir, "ic_launcher_background.xml").apply {
            writeText("""
                <vector xmlns:android="http://schemas.android.com/apk/res/android"
                    android:width="108dp"
                    android:height="108dp"
                    android:viewportWidth="108"
                    android:viewportHeight="108">
                    <path android:fillColor="#3DDC84" android:pathData="M0,0h108v108h-108z"/>
                </vector>
            """.trimIndent())
        }

        assertTrue(isImageFilePath(vectorFile.absolutePath))
    }

    @Test
    fun adaptiveIconXmlRecognizedWhenInMipmapDirectory() {
        val mipmapDir = tempFolder.newFolder("app", "src", "main", "res", "mipmap-anydpi-v26")
        val adaptiveFile = File(mipmapDir, "ic_launcher.xml").apply {
            writeText("""
                <?xml version="1.0" encoding="utf-8"?>
                <adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
                    <background android:drawable="@drawable/ic_launcher_background" />
                    <foreground android:drawable="@drawable/ic_launcher_foreground" />
                </adaptive-icon>
            """.trimIndent())
        }

        assertTrue(isImageFilePath(adaptiveFile.absolutePath))
    }
}
