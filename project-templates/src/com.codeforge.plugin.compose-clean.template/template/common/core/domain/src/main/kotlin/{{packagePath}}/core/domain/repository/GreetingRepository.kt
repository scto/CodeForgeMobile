package {{packageName}}.core.domain.repository

import {{packageName}}.core.domain.model.Greeting
import kotlinx.coroutines.flow.Flow

/** Quelle der Begrüßung; Implementierung im Modul :core:data. */
interface GreetingRepository {
    /** Aktuelle Begrüßung als Stream. */
    val greeting: Flow<Greeting>

    /** Zählt die Begrüßungen hoch. */
    suspend fun increment()
}
