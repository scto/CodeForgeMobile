package {{packageName}}.core.data

import {{packageName}}.core.domain.model.Profile
import {{packageName}}.core.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Profil im Speicher; für Dauerhaftigkeit durch DataStore ersetzen. */
@Singleton
class InMemoryProfileRepository @Inject constructor() : ProfileRepository {
    private val state = MutableStateFlow(Profile())
    override val profile: StateFlow<Profile> = state.asStateFlow()

    override suspend fun update(profile: Profile) {
        state.value = profile
    }
}
