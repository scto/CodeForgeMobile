// Modul: :feature:agent
package com.codeforge.feature.agent

import android.content.Context
import android.content.res.AssetManager
import com.codeforge.libs.agent.AgentSkill
import com.codeforge.libs.agent.ProjectContext
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mit der App gelieferte Skills für den KI-Agenten (Assets unter `agent-skills/`, z. B. Googles `agp-9-upgrade`).
 * Sie werden beim ersten Gebrauch nach `filesDir/agent-skills` entpackt (nach einem App-Update erneut), damit
 * `use_skill` auch ihre Referenzdateien lesen kann. Sie brauchen keine Freigabe, weil sie nicht aus dem Projekt stammen.
 */
@Singleton
class BuiltInAgentSkills @Inject constructor(@ApplicationContext private val context: Context) {

    @Volatile private var cached: List<AgentSkill>? = null

    /** Mitgelieferte Skills (blockierend beim ersten Aufruf, daher auf einem IO-Thread aufrufen). */
    fun skills(): List<AgentSkill> = cached ?: synchronized(this) {
        cached ?: runCatching { load() }.getOrDefault(emptyList()).also { cached = it }
    }

    private fun load(): List<AgentSkill> {
        val target = File(context.filesDir, ASSET_DIR)
        val stamp = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime.toString()
        }.getOrDefault("0")
        val marker = File(target, ".stamp")
        if (marker.takeIf { it.isFile }?.readText() != stamp) {
            target.deleteRecursively()
            copyAssets(context.assets, ASSET_DIR, target)
            marker.writeText(stamp)
        }
        return ProjectContext.loadSkills(target)
    }

    private companion object {
        const val ASSET_DIR = "agent-skills"

        // Kopiert einen Asset-Ordner rekursiv (list() liefert für Dateien ein leeres Array).
        fun copyAssets(assets: AssetManager, path: String, dest: File) {
            val children = assets.list(path).orEmpty()
            if (children.isEmpty()) {
                dest.parentFile?.mkdirs()
                assets.open(path).use { input -> dest.outputStream().use { input.copyTo(it) } }
                return
            }
            dest.mkdirs()
            children.forEach { copyAssets(assets, "$path/$it", File(dest, it)) }
        }
    }
}
