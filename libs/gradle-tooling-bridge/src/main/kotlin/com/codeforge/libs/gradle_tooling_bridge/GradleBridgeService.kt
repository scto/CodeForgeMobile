// Modul: :libs:gradle-tooling-bridge
package com.codeforge.libs.gradle_tooling_bridge

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProgressEvent
import org.gradle.tooling.ProgressListener
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.ResultHandler
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream

private const val TAG = "GradleBridgeService"

/**
 * Läuft isoliert im :gradletooling-Prozess (siehe AndroidManifest.xml). Jeder Aufruf
 * aus dem App-Prozess kommt über den Binder-IPC-Mechanismus hier an — es gibt keine
 * gemeinsam genutzten Klassenobjekte mit der Activity/dem ViewModel-Layer, wodurch
 * Konflikte zwischen der Gradle-Tooling-API-Classloader-Hierarchie und ART vermieden
 * werden (siehe Design-Entscheidung im Projekt-README).
 *
 * TODO: GradleConnector.useInstallation(...) bzw. useGradleUserHomeDir(...) auf die
 * JDK/Gradle-Distribution innerhalb der PRoot-Rootfs zeigen lassen (JAVA_HOME aus
 * :libs:terminal-engine beziehen), sobald der Distro-Bootstrap produktiv ist.
 */
class GradleBridgeService : Service() {

    private var connection: ProjectConnection? = null
    private var currentCancellationHandle: org.gradle.tooling.CancellationTokenSource? = null

    private val binder = object : IGradleBridgeService.Stub() {

        override fun connect(projectRootPath: String, gradleUserHome: String) {
            try {
                connection?.close()
                val connector = GradleConnector.newConnector()
                    .forProjectDirectory(File(projectRootPath))

                if (gradleUserHome.isNotBlank()) {
                    connector.useGradleUserHomeDir(File(gradleUserHome))
                }

                connection = connector.connect()
                Log.i(TAG, "Verbunden mit Projekt: $projectRootPath")
            } catch (t: Throwable) {
                Log.e(TAG, "Verbindung fehlgeschlagen: ${t.message}", t)
            }
        }

        override fun runTask(taskName: String, callback: IGradleBridgeCallback) {
            val activeConnection = connection
            if (activeConnection == null) {
                callback.onBuildFailed("Keine aktive Verbindung. connect() zuerst aufrufen.")
                return
            }

            val cancellationSource = GradleConnector.newCancellationTokenSource()
            currentCancellationHandle = cancellationSource

            val outputStream = CallbackOutputStream(callback)

            callback.onTaskStarted(taskName)

            activeConnection.newBuild()
                .forTasks(taskName)
                .withCancellationToken(cancellationSource.token())
                .setStandardOutput(outputStream)
                .setStandardError(outputStream)
                .addProgressListener(ProgressListener { event: ProgressEvent ->
                    callback.onProgress(event.description, -1)
                })
                .run(object : ResultHandler<Void> {
                    override fun onComplete(result: Void?) {
                        callback.onTaskFinished(taskName, true)
                        callback.onBuildFinished(true)
                    }

                    override fun onFailure(failure: org.gradle.tooling.GradleConnectionException) {
                        callback.onTaskFinished(taskName, false)
                        callback.onBuildFailed(failure.message ?: "Build fehlgeschlagen: $taskName")
                    }
                })
        }

        override fun cancelBuild() {
            currentCancellationHandle?.cancel()
        }

        override fun disconnect() {
            connection?.close()
            connection = null
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        connection?.close()
        connection = null
        super.onDestroy()
    }
}

/**
 * Leitet Gradle-Standard-Output/-Error zeilenweise über den AIDL-Callback weiter,
 * damit :feature:terminal den Build-Log live darstellen kann.
 */
private class CallbackOutputStream(private val callback: IGradleBridgeCallback) : OutputStream() {
    private val buffer = ByteArrayOutputStream()

    override fun write(b: Int) {
        if (b == '\n'.code) {
            flushLine()
        } else {
            buffer.write(b)
        }
    }

    private fun flushLine() {
        val line = buffer.toString(Charsets.UTF_8.name())
        buffer.reset()
        if (line.isNotEmpty()) {
            callback.onOutput(line)
        }
    }

    override fun flush() {
        if (buffer.size() > 0) flushLine()
    }
}
