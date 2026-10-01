// Modul: :libs:gradle-tooling-bridge
package com.codeforge.libs.gradle_tooling_bridge;

import com.codeforge.libs.gradle_tooling_bridge.IGradleBridgeCallback;
import java.util.List;

/**
 * Läuft im separaten :gradletooling-Prozess (siehe AndroidManifest.xml,
 * android:process=":gradletooling"). Kapselt die Gradle Tooling API
 * (org.gradle.tooling.GradleConnector), damit deren Klassen nicht im
 * Haupt-App-Prozess/-Classloader landen.
 */
interface IGradleBridgeService {
    void connect(String projectRootPath, String gradleUserHome);
    
    // Führt Builds mit Tasks und Argumenten (z.B. --no-daemon) aus
    void runBuild(in List<String> tasks, in List<String> arguments, IGradleBridgeCallback callback);
    
    // Liefert alle verfügbaren Tasks des Projekts zurück
    List<String> getAvailableTasks();
    
    // NEU: Liefert ein JSON-String mit Gradle-Version und Java-Home
    String getBuildEnvironment();
    
    // NEU: Liefert ein JSON-String mit der IntelliJ/IDE-Projektstruktur (Module, Source-Roots)
    String getIdeaProjectModel();
    
    void cancelBuild();
    void disconnect();
}
