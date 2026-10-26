package com.codeforge.feature.x2c.run

import android.content.Context
import com.codeforge.feature.x2c.X2cPhase
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidX2cRunNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) : X2cRunNotifier {

    override fun started(onCancelRequested: () -> Unit) {
        X2cForegroundService.onCancelRequested = onCancelRequested
        // Der Start kann scheitern (App im Hintergrund, Systembeschränkung). Die Arbeit läuft dann ohne Dienst weiter.
        runCatching { X2cForegroundService.start(context) }
    }

    override fun update(phase: X2cPhase?, percent: Int?) {
        runCatching { X2cForegroundService.update(context, phase, percent) }
    }

    override fun finished() {
        X2cForegroundService.onCancelRequested = null
        runCatching { X2cForegroundService.stop(context) }
    }
}
