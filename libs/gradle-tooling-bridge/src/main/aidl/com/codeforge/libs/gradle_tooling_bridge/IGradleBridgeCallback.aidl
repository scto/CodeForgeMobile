// Modul: :libs:gradle-tooling-bridge
package com.codeforge.libs.gradle_tooling_bridge;

/**
 * Wird vom GradleBridgeService (läuft im :gradletooling-Prozess) aufgerufen,
 * um Build-Fortschritt zurück in den App-Prozess zu streamen. Läuft in
 * separatem Prozess, damit die Gradle-Tooling-API-Klassen (und deren
 * Classloader) nicht mit denen der Android-App/ART-Runtime kollidieren.
 */
interface IGradleBridgeCallback {
    void onOutput(String line);
    void onProgress(String message, int percent);
    void onTaskStarted(String taskName);
    void onTaskFinished(String taskName, boolean success);
    void onBuildFinished(boolean success);
    void onBuildFailed(String message);
}
