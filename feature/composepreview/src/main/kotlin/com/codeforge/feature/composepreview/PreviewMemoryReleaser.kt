/**
 * Modul: :feature:composepreview
 * @author Thomas Schmid
 */
package com.codeforge.feature.composepreview

import com.codeforge.core.domain.runtime.MemoryPressure
import com.codeforge.core.domain.runtime.MemoryReleaser
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import javax.inject.Inject

/**
 * Gibt Vorschau-Caches frei; bei hoher Dringlichkeit auch den warmen Compiler-Daemon (nur im Leerlauf).
 * Die nächste Vorschau startet ihn wieder, der erste Lauf danach ist entsprechend langsamer.
 */
class PreviewMemoryReleaser @Inject internal constructor(
    private val renderer: ComposePreviewRendererImpl
) : MemoryReleaser {
    override val label: String get() = Res.string(R.string.memory_preview)

    override suspend fun release(pressure: MemoryPressure): Int {
        var released = renderer.releaseMemoryCaches()
        if (pressure != MemoryPressure.LOW && KotlincDaemon.shared.stopIfIdle()) released++
        return released
    }
}
