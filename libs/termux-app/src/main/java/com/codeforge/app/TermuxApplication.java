package com.codeforge.app;

import android.app.Application;
import android.content.Context;
import com.codeforge.shared.errors.Error;
import com.codeforge.shared.logger.Logger;
import com.codeforge.shared.termux.TermuxConstants;
import com.codeforge.shared.termux.file.TermuxFileUtils;
import com.codeforge.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.codeforge.shared.termux.settings.properties.TermuxAppSharedProperties;
import com.codeforge.shared.termux.shell.TermuxShellManager;
import com.codeforge.shared.termux.shell.am.TermuxAmSocketServer;
import com.codeforge.shared.termux.shell.command.environment.TermuxShellEnvironment;
import com.codeforge.shared.termux.theme.TermuxThemeUtils;

/**
 * TODO(Thomas)-ANGEPASST: urspruenglich dev.mutwakil.androidide.app.BaseApplication
 * (existiert in CodeForgeMobile nicht) — jetzt android.app.Application direkt.
 * super.onCreate() war der einzige genutzte Member, daher risikolos ersetzbar.
 * Diese Klasse wird NICHT automatisch instanziiert (kein android:name in diesem
 * Modul) — ihre onCreate()-Logik wird aus CodeForgeApplication.onCreate()
 * (:app) heraus aufgerufen. Siehe docs/sub/TERMUX-PORTING.md.
 */
public class TermuxApplication extends Application {

    private static final String LOG_TAG = "TermuxApplication";

    public void onCreate() {
        super.onCreate();

        Context context = getApplicationContext();

        // Set log config for the app
        setLogConfig(context);

        Logger.logDebug("Starting Application");

        // Init app wide SharedProperties loaded from termux.properties
        TermuxAppSharedProperties properties = TermuxAppSharedProperties.init(context);

        // Init app wide shell manager
        TermuxShellManager shellManager = TermuxShellManager.init(context);

        // Set NightMode.APP_NIGHT_MODE
        TermuxThemeUtils.setAppNightMode(properties.getNightMode());

        // Check and create termux files directory. If failed to access it like in case of secondary
        // user or external sd card installation, then don't run files directory related code
        Error error = TermuxFileUtils.isTermuxFilesDirectoryAccessible(this, true, true);
        boolean isTermuxFilesDirectoryAccessible = error == null;
        if (isTermuxFilesDirectoryAccessible) {
            Logger.logInfo(LOG_TAG, "Termux files directory is accessible");

            error = TermuxFileUtils.isAppsTermuxAppDirectoryAccessible(true, true);
            if (error != null) {
                Logger.logErrorExtended(LOG_TAG, "Create apps/termux-app directory failed\n" + error);
                return;
            }

            // Setup termux-am-socket server
            TermuxAmSocketServer.setupTermuxAmSocketServer(context);
        } else {
            Logger.logErrorExtended(LOG_TAG, "Termux files directory is not accessible\n" + error);
        }

        // Init TermuxShellEnvironment constants and caches after everything has been setup including termux-am-socket server
        TermuxShellEnvironment.init(this);

        if (isTermuxFilesDirectoryAccessible) {
            TermuxShellEnvironment.writeEnvironmentToFile(this);
        }
    }

    public static void setLogConfig(Context context) {
        Logger.setDefaultLogTag(TermuxConstants.TERMUX_APP_NAME);

        // Load the log level from shared preferences and set it to the {@link Logger.CURRENT_LOG_LEVEL}
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(context);
        if (preferences == null) return;
        preferences.setLogLevel(null, preferences.getLogLevel());
    }

}
