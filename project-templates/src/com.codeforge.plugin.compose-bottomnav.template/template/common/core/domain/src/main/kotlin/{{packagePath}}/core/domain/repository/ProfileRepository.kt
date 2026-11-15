package {{packageName}}.core.domain.repository

import {{packageName}}.core.domain.model.Profile
import kotlinx.coroutines.flow.Flow

/** Gespeichertes Profil. */
interface ProfileRepository {
    val profile: Flow<Profile>

    suspend fun update(profile: Profile)
}
