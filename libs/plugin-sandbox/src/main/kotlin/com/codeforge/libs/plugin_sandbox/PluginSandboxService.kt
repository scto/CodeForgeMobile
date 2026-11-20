/**
 * Modul: :libs:plugin-sandbox
 * @author Thomas Schmid
 *
 * Der Sandbox-Prozess (`android:isolatedProcess="true"`, eigener Prozess `:plugin_sandbox`): Er hat eine eigene
 * zufällige UID, keine Berechtigungen, keinen Zugriff auf Dateien der App und kein Netz. Plugin-Code kommt als Bytes
 * und wird mit [InMemoryDexClassLoader] geladen; die App spricht über einen [Messenger] mit ihm.
 */
package com.codeforge.libs.plugin_sandbox

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import com.codeforge.libs.plugin_api.CodeForgePlugin
import dalvik.system.InMemoryDexClassLoader
import java.nio.ByteBuffer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

class PluginSandboxService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val guest = SandboxGuest { code, entry ->
        val loader = InMemoryDexClassLoader(ByteBuffer.wrap(code), PluginSandboxService::class.java.classLoader)
        loader.loadClass(entry).getDeclaredConstructor().newInstance() as? CodeForgePlugin
            ?: error("$entry implementiert nicht CodeForgePlugin")
    }
    private lateinit var thread: HandlerThread
    private lateinit var messenger: Messenger

    override fun onCreate() {
        super.onCreate()
        thread = HandlerThread("plugin-sandbox").apply { start() }
        messenger = Messenger(Handler(thread.looper) { msg -> receive(msg); true })
    }

    private fun receive(msg: Message) {
        val replyTo = msg.replyTo ?: return
        val id = msg.arg1
        val data = Bundle(msg.data)
        scope.launch {
            val request = runCatching { Json.parseToJsonElement(data.getString(KEY_JSON).orEmpty()).jsonObject }
                .getOrElse { JsonObject(emptyMap()) }
            val response = guest.handle(request, data.getByteArray(KEY_CODE))
            val reply = Message.obtain(null, MSG_REPLY, id, 0).apply { this.data = Bundle().apply { putString(KEY_JSON, response.toString()) } }
            runCatching { replyTo.send(reply) }
        }
    }

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    override fun onDestroy() {
        scope.cancel()
        thread.quitSafely()
        super.onDestroy()
    }

    internal companion object {
        const val MSG_REQUEST = 1
        const val MSG_REPLY = 2
        const val KEY_JSON = "json"
        const val KEY_CODE = "code"
    }
}
