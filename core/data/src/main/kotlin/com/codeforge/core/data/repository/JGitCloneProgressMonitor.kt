// Modul: :core:data
package com.codeforge.core.data.repository

import org.eclipse.jgit.lib.ProgressMonitor

/**
 * JGit meldet Fortschritt pro Task (z.B. "Receiving objects", "Resolving deltas") mit
 * jeweils eigenem Total. Wir bilden das auf ein einfaches (Titel, Prozent-im-aktuellen-Task)
 * Paar ab — für eine echte Gesamtfortschrittsanzeige über alle Tasks hinweg müsste man
 * Tasks gewichten, was JGit nicht vorab mitteilt.
 */
internal class JGitCloneProgressMonitor(
    private val onUpdate: (taskTitle: String, percent: Int) -> Unit
) : ProgressMonitor {

    private var currentTitle = ""
    private var currentTotal = 0
    private var currentCompleted = 0
    private var cancelled = false

    override fun start(totalTasks: Int) = Unit

    override fun beginTask(title: String, totalWork: Int) {
        currentTitle = title
        currentTotal = totalWork
        currentCompleted = 0
    }

    override fun update(completed: Int) {
        currentCompleted += completed
        val percent = if (currentTotal > 0) {
            ((currentCompleted * 100) / currentTotal).coerceIn(0, 100)
        } else {
            0
        }
        onUpdate(currentTitle, percent)
    }

    override fun endTask() = Unit

    override fun isCancelled(): Boolean = cancelled

    override fun showDuration(enabled: Boolean) = Unit

    fun cancel() {
        cancelled = true
    }
}
