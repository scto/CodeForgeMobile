package {{packageName}}.core.domain.usecase

import {{packageName}}.core.domain.repository.GreetingRepository
import javax.inject.Inject

/** Zählt die Begrüßungen hoch. */
class IncrementGreetingUseCase @Inject constructor(private val repository: GreetingRepository) {
    suspend operator fun invoke() = repository.increment()
}
