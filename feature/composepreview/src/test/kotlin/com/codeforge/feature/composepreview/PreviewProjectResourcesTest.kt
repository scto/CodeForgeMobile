package com.codeforge.feature.composepreview

import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Lädt die gelinkten Ressourcen eines echten Gradle-Builds. Läuft nur mit
 * `CF_RES_APK=<…ap_> CF_RES_ID=<id> CF_RES_TEXT=<erwarteter Text>` (sonst übersprungen).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PreviewProjectResourcesTest {

    @Test
    fun `Text aus den Projekt-Ressourcen, sonst aus der IDE`() {
        val apk = System.getProperty("cf.resApk")?.let(::File)
        assumeTrue(apk?.isFile == true)
        val host = ApplicationProvider.getApplicationContext<android.content.Context>()
        val ctx = PreviewProjectResources.contextFor(host, apk!!, Configuration(host.resources.configuration))
            ?: error("ap_ nicht ladbar")
        assertEquals(System.getProperty("cf.resText"), ctx.resources.getString(System.getProperty("cf.resId")!!.toInt()))
        // Framework-Ressourcen bleiben erreichbar.
        assertEquals(host.getString(android.R.string.ok), ctx.resources.getString(android.R.string.ok))
    }
}
