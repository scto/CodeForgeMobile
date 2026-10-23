/**
 * Modul: :core:domain
 * @author Thomas Schmid
 */
package com.codeforge.core.domain.repository

import com.codeforge.core.domain.model.AndroidDebugTarget
import java.io.File

/**
 * Verwaltet den Debugging-Lebenszyklus für Android-Apps via ADB und JDWP:
 * Prüft Debug-Fähigkeit, startet Apps mit "Waiting for Debugger" (`am start -D`),
 * ermittelt Prozess-IDs (PID), konfiguriert TCP-JDWP-Portweiterleitungen und
 * stellt sicher, dass beim Beenden oder bei Fehlern sauber aufgeräumt wird.
 */
interface AndroidDebugManager {

    /**
     * Prüft, ob ein ausführbares `adb`-Binary im System vorhanden ist.
     */
    suspend fun isAdbAvailable(): Boolean

    /**
     * Gibt alle PIDs zurück, die aktuell auf dem Gerät einen JDWP-Server betreiben (`adb jdwp`).
     */
    suspend fun getDebuggablePids(): Result<List<Int>>

    /**
     * Prüft, ob die Ziel-App mit `debuggable = true` gebaut oder installiert ist.
     * Prüft zuerst die APK-Datei falls vorhanden, andernfalls Paketinformationen via ADB.
     */
    suspend fun isAppDebuggable(packageName: String, apkFile: File? = null): Result<Boolean>

    /**
     * Startet die App wartend auf den Debugger (`am start -D`) und ermittelt die neue Prozess-ID.
     */
    suspend fun startAppWaitingForDebugger(
        packageName: String,
        activityName: String? = null,
        timeoutMs: Long = 10_000L
    ): Result<Int>

    /**
     * Richtet eine Port-Weiterleitung von `tcp:<port>` auf `jdwp:<pid>` ein.
     */
    suspend fun forwardPort(port: Int, pid: Int): Result<Unit>

    /**
     * Entfernt die Port-Weiterleitung für den angegebenen TCP-Port (`adb forward --remove tcp:<port>`).
     */
    suspend fun removeForward(port: Int): Result<Unit>

    /**
     * Startet den gesamten Anhänge-Ablauf:
     * 1. ADB-Verfügbarkeitsprüfung
     * 2. Prüfung des `debuggable`-Flags
     * 3. Starten der App mit `am start -D` (falls `waitForDebugger` wahr) und PID-Ermittlung
     * 4. Portweiterleitung via ADB
     * 5. Verbindung des DAP-/JDI-Clients
     */
    suspend fun attach(target: AndroidDebugTarget, apkFile: File? = null): Result<Unit>

    /**
     * Beendet die Debug-Sitzung, entfernt die Port-Weiterleitung und stoppt optional die Ziel-App.
     */
    suspend fun detach(target: AndroidDebugTarget): Result<Unit>
}
