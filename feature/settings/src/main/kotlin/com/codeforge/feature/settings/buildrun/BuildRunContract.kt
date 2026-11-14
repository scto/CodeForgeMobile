/**
 * Modul: :feature:settings
 * @author Thomas Schmid
 */
package com.codeforge.feature.settings.buildrun

import androidx.compose.runtime.Immutable
import com.codeforge.core.domain.model.GradleLogLevel
import com.codeforge.core.domain.model.GradleRunSettings
import com.codeforge.core.domain.model.GradleStacktrace
import com.codeforge.core.domain.model.JvmOptions

/** Aufräum- und Wartungsaktionen. */
enum class BuildRunAction { CLEAR_BUILD_CACHE, DELETE_PROJECT_GRADLE, DELETE_USER_CACHES, UPDATE_WRAPPER, RELEASE_MEMORY }

/** Zustand von „Build & Run“. */
@Immutable
data class BuildRunState(
    val settings: GradleRunSettings = GradleRunSettings(),
    /** Zuletzt geöffnetes Projekt (Ziel für Projekt-`.gradle` und Wrapper); leer = keines. */
    val projectPath: String = "",
    val projectName: String = "",
    val gradleUserHome: String = "",
    /** Eingabe für die eigene Gradle-Installation (noch nicht gespeichert). */
    val gradleHomeInput: String = "",
    /** Eingabe der Wrapper-Version. */
    val wrapperVersion: String = "latest",
    /** Laufende Aktion. */
    val running: BuildRunAction? = null,
    /** Letzte Ausgabezeile der laufenden Wrapper-Aktualisierung. */
    val runningOutput: String = "",
    /** Aktion, die bestätigt werden soll. */
    val pendingConfirm: BuildRunAction? = null,
) {
    /** Ob die Wrapper-Version gültig ist. */
    val wrapperVersionValid: Boolean get() = WRAPPER_VERSION.matches(wrapperVersion.trim())

    companion object {
        private val WRAPPER_VERSION = Regex("^(latest|release-candidate|nightly|[0-9][0-9A-Za-z.+\\-]{0,30})$")
    }
}

/** Aktionen des Nutzers. */
sealed interface BuildRunEvent {
    data class SetLogLevel(val level: GradleLogLevel) : BuildRunEvent
    data class SetStacktrace(val mode: GradleStacktrace) : BuildRunEvent
    data class SetOffline(val enabled: Boolean) : BuildRunEvent
    data class SetNoBuildCache(val enabled: Boolean) : BuildRunEvent
    data class SetNoDaemon(val enabled: Boolean) : BuildRunEvent
    data class SetContinue(val enabled: Boolean) : BuildRunEvent
    data class SetExtraArgs(val value: String) : BuildRunEvent
    data class GradleHomeInputChanged(val value: String) : BuildRunEvent
    /** Eigene Gradle-Installation übernehmen (leer = Wrapper des Projekts). */
    data object SaveGradleHome : BuildRunEvent
    /** JVM-Optionen des Gradle-Daemons speichern; `null` = Werte des Projekts. */
    data class SaveDaemonJvm(val options: JvmOptions?) : BuildRunEvent
    /** Leerlaufzeit des Gradle-Daemons in Minuten (0 = Gradle-Standard). */
    data class SetDaemonIdleMinutes(val minutes: Int) : BuildRunEvent
    data class WrapperVersionChanged(val value: String) : BuildRunEvent
    /** Aktion anfordern (Löschaktionen fragen erst nach). */
    data class Request(val action: BuildRunAction) : BuildRunEvent
    data object Confirm : BuildRunEvent
    data object DismissConfirm : BuildRunEvent
}

/** Einmalige Effekte. */
sealed interface BuildRunEffect {
    data class ShowSnackbar(val message: String) : BuildRunEffect
}
