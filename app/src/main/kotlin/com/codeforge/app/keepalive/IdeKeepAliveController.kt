// Modul: :app
package com.codeforge.app.keepalive

import android.content.Context
import android.content.Intent
import com.codeforge.core.domain.runtime.IdeActivityRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Startet den [IdeKeepAliveService], sobald die Registry Arbeit meldet; er beendet sich selbst wieder. */
object IdeKeepAliveController {
    fun start(context: Context) {
        val app = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
            IdeActivityRegistry.state.map { it.needsForeground }.distinctUntilChanged().collect { needed ->
                if (needed) {
                    // Aus dem Hintergrund kann Android den Start verweigern; dann bleibt es bei der Registry ohne Benachrichtigung.
                    runCatching {
                        app.startForegroundService(
                            Intent(app, IdeKeepAliveService::class.java).setAction(IdeKeepAliveService.ACTION_START)
                        )
                    }
                }
            }
        }
    }
}
