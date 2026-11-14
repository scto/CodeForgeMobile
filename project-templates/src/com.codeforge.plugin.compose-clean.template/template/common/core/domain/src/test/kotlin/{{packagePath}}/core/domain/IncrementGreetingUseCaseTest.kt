package {{packageName}}.core.domain

import {{packageName}}.core.domain.model.Greeting
import {{packageName}}.core.domain.repository.GreetingRepository
import {{packageName}}.core.domain.usecase.IncrementGreetingUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class IncrementGreetingUseCaseTest {
    private class FakeRepository : GreetingRepository {
        override val greeting = MutableStateFlow(Greeting("Hallo", 0))
        override suspend fun increment() = greeting.update { it.copy(count = it.count + 1) }
    }

    @Test
    fun incrementsCount() = runTest {
        val repo = FakeRepository()
        IncrementGreetingUseCase(repo)()
        assertEquals(1, repo.greeting.value.count)
    }
}
