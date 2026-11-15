package {{packageName}}.core.data

import {{packageName}}.core.domain.model.Task
import {{packageName}}.core.domain.repository.TaskRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/** Hält die Aufgaben im Speicher; durch Room ersetzbar, ohne Domain und Oberfläche zu ändern. */
@Singleton
class InMemoryTaskRepository @Inject constructor() : TaskRepository {
    private val nextId = AtomicLong(SAMPLES.size.toLong())
    private val state = MutableStateFlow(SAMPLES)
    override val tasks: StateFlow<List<Task>> = state.asStateFlow()

    override suspend fun add(title: String): Task {
        val task = Task(nextId.incrementAndGet(), title, done = false)
        state.update { it + task }
        return task
    }

    override suspend fun setDone(id: Long, done: Boolean) =
        state.update { tasks -> tasks.map { if (it.id == id) it.copy(done = done) else it } }

    override suspend fun delete(id: Long) = state.update { tasks -> tasks.filterNot { it.id == id } }

    private companion object {
        val SAMPLES = listOf(
            Task(1, "App ausprobieren", done = true),
            Task(2, "Eigene Aufgabe anlegen", done = false),
            Task(3, "Namen im Profil eintragen", done = false)
        )
    }
}
