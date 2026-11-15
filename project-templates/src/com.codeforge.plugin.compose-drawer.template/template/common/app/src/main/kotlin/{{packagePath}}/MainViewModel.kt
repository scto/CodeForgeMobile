package {{packageName}}

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import {{packageName}}.core.domain.model.AppSettings
import {{packageName}}.core.domain.usecase.ObserveSettingsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** Liefert die Einstellungen für das Design der ganzen App. */
@HiltViewModel
class MainViewModel @Inject constructor(observeSettings: ObserveSettingsUseCase) : ViewModel() {
    val settings: StateFlow<AppSettings> = observeSettings()
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
}
