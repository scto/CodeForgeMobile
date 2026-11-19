/**
 * Modul: :libs:plugin-api
 * @author Thomas Schmid
 *
 * Seit API 4: Projekt- und Build-Dienst sowie Dienste zwischen Plugins.
 */
package com.codeforge.libs.plugin_api

import com.codeforge.core.domain.extension.registry.Disposable

/** Modul eines Projekts (Ordner mit `build.gradle(.kts)` und `src/`). */
data class PluginModule(
    /** Gradle-Pfad, z. B. `:feature:home`. */
    val gradlePath: String,
    /** Absoluter Ordner des Moduls. */
    val directory: String
)

/**
 * Ergebnisse des letzten Gradle-Builds eines Projekts (nur, wenn sie zum Stand der Quellen passen).
 * Pfade sind absolut.
 */
data class PluginModuleContext(
    /** Build-Variante, meist `debug`. */
    val variant: String,
    /** `.class`-Ordner aller Module. */
    val classDirectories: List<String>,
    /** `R.jar` des App-Moduls. */
    val rJars: List<String>,
    /** Gelinkte Ressourcen (`*.ap_`) des App-Moduls. */
    val resourceApk: String?,
    /** Gedexte externe Bibliotheken des App-Moduls. */
    val externalDex: List<String>
)

/** Projektmodell; Berechtigung `project`. */
interface ProjectService {
    /** Wurzel des geöffneten Projekts (`null`, wenn keins offen ist). */
    val rootPath: String?

    /** Module des geöffneten Projekts (leer ohne Projekt). */
    fun modules(): List<PluginModule>

    /** Build-Ergebnisse des geöffneten Projekts; `null`, wenn es keinen aktuellen Gradle-Build gibt. */
    fun buildOutputs(): PluginModuleContext?
}

/** Ergebnis eines Gradle-Laufs. */
data class PluginBuildResult(val success: Boolean, val message: String? = null)

/** Gradle-Aufgaben im geöffneten Projekt; Berechtigung `build`. */
interface BuildService {
    /**
     * Führt [tasks] nacheinander aus (z. B. `listOf(":app:assembleDebug")`) und meldet jede Ausgabezeile an
     * [onOutput]. Bricht beim ersten Fehler ab.
     */
    suspend fun runTasks(tasks: List<String>, onOutput: (String) -> Unit = {}): PluginBuildResult

    /** Bricht den laufenden Build ab. */
    suspend fun cancel()
}

/**
 * Schlüssel eines Dienstes, den Plugins einander anbieten. Der Typ [T] muss für beide Seiten derselbe sein: eine
 * Klasse aus dem SDK oder der Plattform (Kotlin-Funktionstypen, `java.util.function.*`, Datenklassen des SDK) –
 * jedes Plugin hat einen eigenen Klassenlader und sieht die Klassen anderer Plugins nicht.
 */
class ServiceKey<T : Any>(val id: String) {
    override fun equals(other: Any?): Boolean = other is ServiceKey<*> && other.id == id
    override fun hashCode(): Int = id.hashCode()
    override fun toString(): String = "ServiceKey($id)"
}

/** Dienste zwischen Plugins (keine Berechtigung nötig). */
interface PluginServices {
    /** Bietet [service] unter [key] an; wird beim Entladen des Plugins automatisch zurückgezogen. */
    fun <T : Any> provide(key: ServiceKey<T>, service: T): Disposable

    /** Erster Anbieter von [key] oder `null`. */
    fun <T : Any> get(key: ServiceKey<T>): T?

    /** Alle Anbieter von [key] (z. B. mehrere KI-Backends). */
    fun <T : Any> all(key: ServiceKey<T>): List<T>
}
