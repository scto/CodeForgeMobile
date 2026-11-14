package {{packageName}}.core.data

import {{packageName}}.core.domain.model.Greeting
import {{packageName}}.core.domain.repository.GreetingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/** Hält die Begrüßung im Speicher; durch Room oder DataStore ersetzbar, ohne die Domain zu ändern. */
@Singleton
class InMemoryGreetingRepository @Inject constructor() : GreetingRepository {
    private val state = MutableStateFlow(Greeting(text = "Hallo {{projectName}}", count = 0))
    override val greeting: StateFlow<Greeting> = state.asStateFlow()

    override suspend fun increment() = state.update { it.copy(count = it.count + 1) }
}
