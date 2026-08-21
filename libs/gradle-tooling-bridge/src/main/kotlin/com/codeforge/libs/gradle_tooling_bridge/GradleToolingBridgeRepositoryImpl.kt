// Modul: :libs:gradle-tooling-bridge
package com.codeforge.libs.gradle_tooling_bridge

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.codeforge.core.domain.repository.BuildEvent
import com.codeforge.core.domain.repository.GradleBuildRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class GradleToolingBridgeRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : GradleBuildRepository {

    private var service: IGradleBridgeService? = null
    private var projectRootPath: String? = null

    override suspend fun connect(projectRootPath: String): Result<Unit> = runCatching {
        this.projectRootPath = projectRootPath
        bindServiceIfNeeded()
        val gradleUserHome = context.getExternalFilesDir("gradle_home")?.path.orEmpty()
        requireService().connect(projectRootPath, gradleUserHome)
    }

    override fun runTask(taskName: String): Flow<BuildEvent> = callbackFlow {
        val activeService = service
        if (activeService == null) {
            trySend(BuildEvent.BuildFailed("Bridge-Service nicht gebunden. connect() zuerst aufrufen."))
            close()
            return@callbackFlow
        }

        val callback = object : IGradleBridgeCallback.Stub() {
            override fun onOutput(line: String) {
                trySend(BuildEvent.Output(line))
            }

            override fun onProgress(message: String, percent: Int) {
                trySend(BuildEvent.Progress(message, percent))
            }

            override fun onTaskStarted(taskName: String) {
                trySend(BuildEvent.TaskStarted(taskName))
            }

            override fun onTaskFinished(taskName: String, success: Boolean) {
                trySend(BuildEvent.TaskFinished(taskName, success))
            }

            override fun onBuildFinished(success: Boolean) {
                trySend(BuildEvent.BuildFinished(success))
                close()
            }

            override fun onBuildFailed(message: String) {
                trySend(BuildEvent.BuildFailed(message))
                close()
            }
        }

        activeService.runTask(taskName, callback)

        awaitClose {
            runCatching { service?.cancelBuild() }
        }
    }

    override suspend fun cancelBuild() {
        service?.cancelBuild()
    }

    override suspend fun disconnect() {
        service?.disconnect()
        boundConnection?.let { runCatching { context.unbindService(it) } }
        boundConnection = null
        service = null
    }

    private var boundConnection: ServiceConnection? = null

    /**
     * Bindet den Service (aus Sicht des Aufrufers synchron) via suspendCancellableCoroutine,
     * da bindService() selbst asynchron ist und der Bridge-Service erst nach onServiceConnected
     * nutzbar ist.
     */
    private suspend fun bindServiceIfNeeded() {
        if (service != null) return

        suspendCancellableCoroutine { continuation ->
            val intent = Intent(context, GradleBridgeService::class.java)
            val connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                    service = IGradleBridgeService.Stub.asInterface(binder)
                    if (continuation.isActive) continuation.resume(Unit)
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    service = null
                }
            }
            boundConnection = connection

            val bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            if (!bound && continuation.isActive) {
                continuation.resumeWithException(IllegalStateException("GradleBridgeService konnte nicht gebunden werden."))
            }
        }
    }

    private fun requireService(): IGradleBridgeService =
        service ?: error("GradleBridgeService ist nicht verbunden.")
}
