// Modul: :libs:gradle-tooling-bridge
package com.codeforge.libs.gradle_tooling_bridge

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.events.ProgressListener
import org.gradle.tooling.events.task.TaskFinishEvent
import org.gradle.tooling.events.task.TaskStartEvent
import org.gradle.tooling.events.task.TaskSuccessResult
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.build.BuildEnvironment
import org.gradle.tooling.model.idea.IdeaProject
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream

private const val TAG = "GradleBridgeService"

/**
 * Läuft isoliert im :gradletooling-Prozess (siehe AndroidManifest.xml). Jeder Aufruf
 * aus dem App-Prozess kommt über den Binder-IPC-Mechanismus hier an — es gibt keine
 * gemeinsam genutzten Klassenobjekte mit der Activity/dem ViewModel-Layer, wodurch
 * Konflikte zwischen der Gradle-Tooling-API-Classloader-Hierarchie und ART vermieden
 * werden (siehe Design-Entscheidung im Projekt-README von MobileIDE).
 *
 * TODO: GradleConnector.useInstallation(...) bzw. useGradleUserHomeDir(...) auf die
 * JDK/Gradle-Distribution innerhalb der PRoot-Rootfs zeigen lassen (JAVA_HOME aus
 * :libs:termix beziehen), sobald der Distro-Bootstrap produktiv ist.
 */
class GradleBridgeService : Service() {

    private var connection: ProjectConnection? = null
    private var currentCancellationHandle: org.gradle.tooling.CancellationTokenSource? = null
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

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

        override fun getAvailableTasks(): List<String> {
            return try {
                val model = connection?.getModel(GradleProject::class.java)
                model?.tasks?.map { it.name }?.distinct()?.sorted() ?: emptyList()
            } catch (t: Throwable) {
                Log.e(TAG, "Fehler beim Laden der Tasks", t)
                emptyList()
            }
        }

        override fun getBuildEnvironment(): String {
            return try {
                val env = connection?.getModel(BuildEnvironment::class.java)
                val json = JSONObject().apply {
                    put("gradleVersion", env?.gradle?.gradleVersion ?: "unknown")
                    put("javaHome", env?.java?.javaHome?.absolutePath ?: "unknown")
                    put("javaArguments", env?.java?.jvmArguments?.joinToString(" ") ?: "")
                }
                json.toString()
            } catch (t: Throwable) {
                Log.e(TAG, "Fehler beim Laden des BuildEnvironments", t)
                "{}"
            }
        }

        override fun getIdeaProjectModel(): String {
            return try {
                val ideaProject = connection?.getModel(IdeaProject::class.java)
                val projectJson = JSONObject().apply {
                    put("name", ideaProject?.name)
                    put("description", ideaProject?.description)
                    
                    val modulesArray = JSONArray()
                    ideaProject?.modules?.forEach { module ->
                        val moduleJson = JSONObject().apply {
                            put("name", module.name)
                            put("gradleProject", module.gradleProject?.path)
                            
                            val contentRootsArray = JSONArray()
                            module.contentRoots.forEach { root ->
                                contentRootsArray.put(root.rootDirectory.absolutePath)
                            }
                            put("contentRoots", contentRootsArray)
                        }
                        modulesArray.put(moduleJson)
                    }
                    put("modules", modulesArray)
                }
                projectJson.toString()
            } catch (t: Throwable) {
                Log.e(TAG, "Fehler beim Laden des IdeaProject-Modells", t)
                "{}"
            }
        }

        override fun runBuild(tasks: List<String>, arguments: List<String>, callback: IGradleBridgeCallback) {
            val activeConnection = connection ?: run {
                callback.onBuildFailed("Keine aktive Verbindung. connect() zuerst aufrufen.")
                return
            }

            serviceScope.launch {
                val cancellationSource = GradleConnector.newCancellationTokenSource()
                currentCancellationHandle = cancellationSource
                val outputStream = SafeCallbackOutputStream(callback)

                try {
                    activeConnection.newBuild()
                        .forTasks(*tasks.toTypedArray())
                        .withArguments(*arguments.toTypedArray())
                        .withCancellationToken(cancellationSource.token())
                        .setStandardOutput(outputStream)
                        .setStandardError(outputStream)
                        .addProgressListener(ProgressListener { event ->
                            when (event) {
                                is TaskStartEvent -> callback.onTaskStarted(event.descriptor.name)
                                is TaskFinishEvent -> callback.onTaskFinished(
                                    event.descriptor.name,
                                    event.result is TaskSuccessResult
                                )
                                else -> callback.onProgress(event.displayName, -1)
                            }
                        }, setOf(OperationType.TASK))
                        .run()

                    callback.onBuildFinished(true)
                } catch (t: Throwable) {
                    callback.onBuildFailed(t.message ?: "Build fehlgeschlagen: ${t.message}")
                }
            }
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
 * damit :feature:terminal den Build-Log live darstellen kann. Thread-Safe und reinigt Returns (\r).
 */
private class SafeCallbackOutputStream(private val callback: IGradleBridgeCallback) : OutputStream() {
    private val buffer = ByteArrayOutputStream()

    @Synchronized
    override fun write(b: Int) {
        buffer.write(b)
        if (b == '\n'.code) {
            flushLine()
        }
    }

    private fun flushLine() {
        val line = buffer.toString(Charsets.UTF_8.name()).trimEnd('\r', '\n')
        buffer.reset()
        if (line.isNotEmpty()) {
            callback.onOutput(line)
        }
    }

    override fun flush() {
        if (buffer.size() > 0) flushLine()
    }
}
