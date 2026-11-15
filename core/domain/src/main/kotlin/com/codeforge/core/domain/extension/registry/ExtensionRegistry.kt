/**
 * Modul: :core:domain
 * @author Thomas Schmid
 */
package com.codeforge.core.domain.extension.registry

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Etwas, das sich wieder abmelden lässt; mehrfaches [dispose] ist harmlos. */
fun interface Disposable {
    /** Meldet den Beitrag ab. */
    fun dispose()

    companion object {
        /** Ein [Disposable], das nichts tut. */
        val NONE: Disposable = Disposable {}
    }
}

/**
 * Typisierte Andockstelle für Beiträge (App selbst und Plugins).
 *
 * @param T Typ der Beiträge.
 * @param id eindeutige ID, z. B. `editor.action`.
 * @param permission Berechtigung, die ein Plugin für Beiträge braucht (`null` = keine).
 */
class ExtensionPoint<T : Any>(val id: String, val permission: String? = null) {
    override fun toString(): String = "ExtensionPoint($id)"
}

/** Ein Beitrag mit Herkunft. [pluginId] ist [ExtensionRegistry.HOST] für Beiträge der App. */
data class Contribution<T : Any>(val pluginId: String, val value: T)

/**
 * Register aller Beiträge. Jeder Beitrag gehört einem Plugin (oder der App), lässt sich einzeln über das
 * zurückgegebene [Disposable] oder gesammelt über [unregisterAll] entfernen. Reihenfolge = Registrierreihenfolge.
 */
interface ExtensionRegistry {
    /** Meldet [value] an [point] an. */
    fun <T : Any> register(point: ExtensionPoint<T>, value: T, pluginId: String = HOST): Disposable

    /** Aktuelle Beiträge an [point]. */
    fun <T : Any> extensions(point: ExtensionPoint<T>): List<T>

    /** Beiträge an [point] mit Herkunft. */
    fun <T : Any> contributions(point: ExtensionPoint<T>): List<Contribution<T>>

    /** Beobachtbare Beiträge an [point] (für UI und ViewModels). */
    fun <T : Any> observe(point: ExtensionPoint<T>): StateFlow<List<Contribution<T>>>

    /** Entfernt alles, was [pluginId] angemeldet hat. */
    fun unregisterAll(pluginId: String)

    /** Anzahl Beiträge je Andockstelle von [pluginId] (Anzeige im Plugins-Bildschirm). */
    fun summaryOf(pluginId: String): Map<String, Int>

    companion object {
        /** Herkunft der Beiträge der App selbst. */
        const val HOST = "codeforge"
    }
}

/** Thread-sichere Standard-Umsetzung von [ExtensionRegistry]. */
class ExtensionRegistryImpl : ExtensionRegistry {

    private class Entry(val pluginId: String, val value: Any)

    private val lock = Any()
    private val entries = LinkedHashMap<String, MutableList<Entry>>()
    private val flows = HashMap<String, MutableStateFlow<List<Contribution<Any>>>>()

    override fun <T : Any> register(point: ExtensionPoint<T>, value: T, pluginId: String): Disposable {
        val entry = Entry(pluginId, value)
        synchronized(lock) {
            entries.getOrPut(point.id) { mutableListOf() }.add(entry)
            publish(point.id)
        }
        return Disposable {
            synchronized(lock) {
                if (entries[point.id]?.remove(entry) == true) publish(point.id)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> extensions(point: ExtensionPoint<T>): List<T> =
        synchronized(lock) { entries[point.id].orEmpty().map { it.value as T } }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> contributions(point: ExtensionPoint<T>): List<Contribution<T>> =
        synchronized(lock) { entries[point.id].orEmpty().map { Contribution(it.pluginId, it.value as T) } }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> observe(point: ExtensionPoint<T>): StateFlow<List<Contribution<T>>> = synchronized(lock) {
        flows.getOrPut(point.id) { MutableStateFlow(snapshot(point.id)) }.asStateFlow() as StateFlow<List<Contribution<T>>>
    }

    override fun unregisterAll(pluginId: String) {
        synchronized(lock) {
            entries.forEach { (id, list) -> if (list.removeAll { it.pluginId == pluginId }) publish(id) }
        }
    }

    override fun summaryOf(pluginId: String): Map<String, Int> = synchronized(lock) {
        entries.mapValues { (_, list) -> list.count { it.pluginId == pluginId } }.filterValues { it > 0 }
    }

    private fun snapshot(id: String): List<Contribution<Any>> = entries[id].orEmpty().map { Contribution(it.pluginId, it.value) }

    private fun publish(id: String) {
        flows[id]?.value = snapshot(id)
    }
}
