/**
 * Modul: :libs:plugin-sandbox
 * @author Thomas Schmid
 *
 * [SandboxTransport] über den gebundenen [PluginSandboxService]. Verbindet bei Bedarf, ordnet Antworten über eine
 * laufende Nummer zu und bricht nach [timeoutMs] ab. Stirbt der Sandbox-Prozess, schlagen offene Aufrufe fehl und
 * [onReset] wird gerufen (die Laufzeit lädt die Plugins dann neu).
 */
package com.codeforge.libs.plugin_sandbox

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

class ServiceSandboxTransport(
    context: Context,
    private val timeoutMs: Long = 30_000,
) : SandboxTransport {
    private val app = context.applicationContext
    private val ids = AtomicInteger()
    private val pending = ConcurrentHashMap<Int, CompletableDeferred<JsonObject>>()
    private val connectLock = Mutex()
    private val replyThread = HandlerThread("plugin-sandbox-replies").apply { start() }
    private val replies = Messenger(Handler(replyThread.looper) { msg ->
        val json = msg.data?.getString(PluginSandboxService.KEY_JSON)
        pending.remove(msg.arg1)?.complete(
            runCatching { Json.parseToJsonElement(json.orEmpty()).jsonObject }.getOrElse { JsonObject(emptyMap()) }
        )
        true
    })

    @Volatile private var service: Messenger? = null
    @Volatile private var connection: ServiceConnection? = null

    /** Wird gerufen, wenn der Sandbox-Prozess beendet wurde (Plugins müssen neu geladen werden). */
    @Volatile var onReset: (() -> Unit)? = null

    override suspend fun send(request: JsonObject, code: ByteArray?): JsonObject {
        val target = connect()
        val id = ids.incrementAndGet()
        val deferred = CompletableDeferred<JsonObject>()
        pending[id] = deferred
        try {
            target.send(
                Message.obtain(null, PluginSandboxService.MSG_REQUEST, id, 0).apply {
                    replyTo = replies
                    data = Bundle().apply {
                        putString(PluginSandboxService.KEY_JSON, request.toString())
                        code?.let { putByteArray(PluginSandboxService.KEY_CODE, it) }
                    }
                }
            )
            return withTimeout(timeoutMs) { deferred.await() }
        } finally {
            pending.remove(id)
        }
    }

    private suspend fun connect(): Messenger = connectLock.withLock {
        service?.let { return it }
        val ready = CompletableDeferred<Messenger>()
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                val m = Messenger(binder)
                service = m
                ready.complete(m)
            }

            override fun onServiceDisconnected(name: ComponentName?) = reset("Sandbox-Prozess beendet")
            override fun onBindingDied(name: ComponentName?) = reset("Sandbox-Bindung verloren")
        }
        check(app.bindService(Intent(app, PluginSandboxService::class.java), conn, Context.BIND_AUTO_CREATE)) {
            "Sandbox-Dienst lässt sich nicht starten"
        }
        connection = conn
        withTimeout(CONNECT_TIMEOUT_MS) { ready.await() }
    }

    private fun reset(reason: String) {
        val had = service != null
        service = null
        connection?.let { runCatching { app.unbindService(it) } }
        connection = null
        pending.values.forEach { it.completeExceptionally(SandboxException(reason)) }
        pending.clear()
        if (had) onReset?.invoke()
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000L
    }
}
