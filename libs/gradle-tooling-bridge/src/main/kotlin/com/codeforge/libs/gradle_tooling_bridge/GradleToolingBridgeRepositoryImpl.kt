// Modul: :libs:gradle-tooling-bridge
package com.codeforge.libs.gradle_tooling_bridge

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.codeforge.core.domain.repository.BuildStatus
import com.codeforge.core.domain.repository.GradleBuildRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
    
    private val mutex = Mutex()
    private var boundConnection: ServiceConnection? = null

    override fun executeTask(projectPath: String, taskName: String): Flow<BuildStatus> = callbackFlow {
        trySend(BuildStatus.Building("Verbinde mit Gradle Bridge..."))
        val activeService = service
        if (activeService == null) {
            runCatching {
                connect(projectPath)
            }
        }

        val currentService = service
        if (currentService == null) {
            trySend(BuildStatus.Failed("Bridge-Service nicht gebunden."))
            close()
            return@callbackFlow
        }

        val callback = object : IGradleBridgeCallback.Stub() {
            override fun onOutput(line: String) {
                trySend(BuildStatus.Building(line))
            }

            override fun onProgress(message: String, percent: Int) {
                trySend(BuildStatus.Building("$message ($percent%)"))
            }

            override fun onTaskStarted(name: String) {
                trySend(BuildStatus.Building("Task gestartet: $name"))
            }

            override fun onTaskFinished(name: String, success: Boolean) {}

            override fun onBuildFinished(success: Boolean) {
                if (success) {
                    trySend(BuildStatus.Success)
                } else {
                    trySend(BuildStatus.Failed("Build fehlgeschlagen"))
                }
                close()
            }

            override fun onBuildFailed(message: String) {
                trySend(BuildStatus.Failed(message))
                close()
            }
        }

        currentService.runBuild(listOf(taskName), emptyList(), callback)

        awaitClose {
            runCatching { service?.cancelBuild() }
        }
    }

    suspend fun connect(projectRootPath: String): Result<Unit> = runCatching {
        this.projectRootPath = projectRootPath
        bindServiceIfNeeded()
        val gradleUserHome = context.getExternalFilesDir("gradle_home")?.path.orEmpty()
        requireService().connect(projectRootPath, gradleUserHome)
    }

    suspend fun getAvailableTasks(): Result<List<String>> = runCatching {
        withContext(Dispatchers.IO) {
            requireService().availableTasks
        }
    }

    suspend fun getBuildEnvironment(): Result<String> = runCatching {
        withContext(Dispatchers.IO) {
            requireService().buildEnvironment
        }
    }

    suspend fun getProjectStructure(): Result<String> = runCatching {
        withContext(Dispatchers.IO) {
            requireService().ideaProjectModel
        }
    }

    suspend fun cancelBuild() {
        service?.cancelBuild()
    }

    suspend fun disconnect() {
        service?.disconnect()
        boundConnection?.let { runCatching { context.unbindService(it) } }
        boundConnection = null
        service = null
    }

    private suspend fun bindServiceIfNeeded() = mutex.withLock {
        if (service != null) return@withLock

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
