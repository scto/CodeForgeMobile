package {{packageName}}.core.domain.repository

import {{packageName}}.core.domain.model.Task
import kotlinx.coroutines.flow.Flow

/** Quelle der Aufgaben, in Anlegereihenfolge. */
interface TaskRepository {
    val tasks: Flow<List<Task>>

    suspend fun add(title: String): Task

    suspend fun setDone(id: Long, done: Boolean)

    suspend fun delete(id: Long)
}
