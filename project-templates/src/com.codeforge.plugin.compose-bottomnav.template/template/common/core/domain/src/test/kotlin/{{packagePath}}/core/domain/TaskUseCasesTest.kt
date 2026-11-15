package {{packageName}}.core.domain

import {{packageName}}.core.domain.model.Task
import {{packageName}}.core.domain.model.TaskStats
import {{packageName}}.core.domain.repository.TaskRepository
import {{packageName}}.core.domain.usecase.AddTaskUseCase
import {{packageName}}.core.domain.usecase.ObserveTaskStatsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskUseCasesTest {
    private class FakeRepository(initial: List<Task> = emptyList()) : TaskRepository {
        override val tasks = MutableStateFlow(initial)
        override suspend fun add(title: String): Task =
            Task(tasks.value.size + 1L, title, false).also { task -> tasks.update { it + task } }
        override suspend fun setDone(id: Long, done: Boolean) =
            tasks.update { list -> list.map { if (it.id == id) it.copy(done = done) else it } }
        override suspend fun delete(id: Long) = tasks.update { list -> list.filterNot { it.id == id } }
    }

    @Test
    fun blankTitleIsRejected() = runTest {
        val repository = FakeRepository()
        assertTrue(AddTaskUseCase(repository)("   ").isFailure)
        assertTrue(repository.tasks.value.isEmpty())
    }

    @Test
    fun statsCountDoneTasks() = runTest {
        val repository = FakeRepository(listOf(Task(1, "A", true), Task(2, "B", false), Task(3, "C", false)))
        val stats = ObserveTaskStatsUseCase(repository)().first()
        assertEquals(TaskStats(total = 3, done = 1), stats)
        assertEquals(2, stats.open)
        assertEquals(1f / 3, stats.progress, 0.001f)
    }
}
