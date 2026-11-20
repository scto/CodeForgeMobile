package com.codeforge.libs.plugin_sandbox

import android.app.Application
import android.content.ComponentName
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Messenger-Weg App ↔ [PluginSandboxService] (in Robolectric im selben Prozess; isolatedProcess gilt erst auf dem
 * Gerät). Prüft Verbindungsaufbau, Zuordnung der Antworten und Fehlerantworten.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ServiceTransportTest {

    @Test
    fun `Anfragen gehen über den Dienst und kommen zugeordnet zurück`() {
        val app = RuntimeEnvironment.getApplication() as Application
        val controller = Robolectric.buildService(PluginSandboxService::class.java).create()
        shadowOf(app).setComponentNameAndServiceForBindService(
            ComponentName(app, PluginSandboxService::class.java), controller.get().onBind(null)
        )
        val transport = ServiceSandboxTransport(app, timeoutMs = 10_000)
        // Der Verbindungs-Callback kommt über den Haupt-Looper: Aufrufe im Hintergrund, Haupt-Looper hier abarbeiten.
        var failure: Throwable? = null
        val worker = Thread { runCatching { runBlocking { scenario(transport) } }.onFailure { failure = it } }
        worker.start()
        val deadline = System.currentTimeMillis() + 20_000
        while (worker.isAlive && System.currentTimeMillis() < deadline) {
            shadowOf(android.os.Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        worker.join(1_000)
        failure?.let { throw it }
        assertTrue("Zeitüberschreitung", !worker.isAlive)
        controller.destroy()
    }

    private suspend fun scenario(transport: ServiceSandboxTransport) {
        run {
            val ping = transport.send(buildJsonObject { put("op", "ping") })
            assertEquals(JsonPrimitive(true), ping["ok"])
            assertEquals(JsonPrimitive(0), ping["result"])
            val bad = transport.send(buildJsonObject { put("op", "gibtsnicht") })
            assertEquals(JsonPrimitive(false), bad["ok"])
            val call = transport.send(buildJsonObject { put("op", "call"); put("pluginId", "fehlt") })
            assertTrue((call["error"] as JsonPrimitive).content.contains("nicht geladen"))
        }
    }
}
