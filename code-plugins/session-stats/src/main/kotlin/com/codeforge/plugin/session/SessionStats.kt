/**
 * Plugin: Sitzungsstatistik – reine Zähllogik (ohne Plugin-API, testbar).
 */
package com.codeforge.plugin.session

/** Zähler einer Sitzung; Ereignisse kommen als Thema + Nutzdaten wie auf dem IDE-Ereignisbus. */
class SessionStats {
    var saves = 0
        private set
    var opens = 0
        private set
    var builds = 0
        private set
    var failedBuilds = 0
        private set
    var commits = 0
        private set
    private val savesPerFile = mutableMapOf<String, Int>()

    /** Verarbeitet ein Ereignis; `true`, wenn sich etwas geändert hat. */
    fun onEvent(topic: String, payload: Map<String, String>): Boolean {
        when (topic) {
            "file.saved" -> {
                saves++
                payload["path"]?.let { savesPerFile[it] = (savesPerFile[it] ?: 0) + 1 }
            }
            "file.opened" -> opens++
            "build.finished" -> {
                builds++
                if (payload["success"] == "false") failedBuilds++
            }
            "git.committed" -> commits++
            else -> return false
        }
        return true
    }

    /** Wie oft [path] in dieser Sitzung gespeichert wurde. */
    fun savesOf(path: String): Int = savesPerFile[path] ?: 0

    /** Häufigst gespeicherte Datei (Name) oder `null`. */
    fun topFile(): String? = savesPerFile.maxByOrNull { it.value }?.key?.substringAfterLast('/')

    fun reset() {
        saves = 0; opens = 0; builds = 0; failedBuilds = 0; commits = 0
        savesPerFile.clear()
    }

    /** Text für die Statusleiste. */
    fun statusText(long: Boolean): String = if (long) {
        "$saves× gespeichert · $opens× geöffnet · Builds $builds (${failedBuilds} fehlgeschlagen) · $commits Commits"
    } else {
        "💾 $saves  📂 $opens  🔨 ${builds - failedBuilds}/$builds  ✔ $commits"
    }

    /** Zusammenfassung für Befehl und Editor-Aktion. */
    fun summary(): String = buildString {
        append("Diese Sitzung: ").append(statusText(long = true))
        topFile()?.let { append(" · am häufigsten gespeichert: ").append(it) }
    }
}
