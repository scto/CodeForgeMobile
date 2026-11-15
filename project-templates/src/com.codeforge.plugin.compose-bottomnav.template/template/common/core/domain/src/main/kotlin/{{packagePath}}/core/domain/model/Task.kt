package {{packageName}}.core.domain.model

/** Eine Aufgabe. */
data class Task(val id: Long, val title: String, val done: Boolean)

/** Fortschritt über alle Aufgaben. */
data class TaskStats(val total: Int, val done: Int) {
    val open: Int get() = total - done

    /** Anteil erledigter Aufgaben zwischen 0 und 1. */
    val progress: Float get() = if (total == 0) 0f else done.toFloat() / total
}
