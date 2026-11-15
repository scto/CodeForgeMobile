package {{packageName}}.core.domain.usecase

import {{packageName}}.core.domain.model.Profile
import {{packageName}}.core.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Liefert das Profil. */
class ObserveProfileUseCase @Inject constructor(private val repository: ProfileRepository) {
    operator fun invoke(): Flow<Profile> = repository.profile
}

/** Speichert das Profil; das Tagesziel wird auf 1 bis 20 begrenzt. */
class UpdateProfileUseCase @Inject constructor(private val repository: ProfileRepository) {
    suspend operator fun invoke(profile: Profile) =
        repository.update(profile.copy(name = profile.name.take(MAX_NAME), dailyGoal = profile.dailyGoal.coerceIn(1, MAX_GOAL)))

    private companion object {
        const val MAX_NAME = 40
        const val MAX_GOAL = 20
    }
}
