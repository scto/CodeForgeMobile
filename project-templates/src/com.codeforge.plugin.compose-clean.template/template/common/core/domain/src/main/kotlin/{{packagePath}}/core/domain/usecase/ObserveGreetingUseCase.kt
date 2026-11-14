package {{packageName}}.core.domain.usecase

import {{packageName}}.core.domain.model.Greeting
import {{packageName}}.core.domain.repository.GreetingRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Liefert die Begrüßung. */
class ObserveGreetingUseCase @Inject constructor(private val repository: GreetingRepository) {
    operator fun invoke(): Flow<Greeting> = repository.greeting
}
