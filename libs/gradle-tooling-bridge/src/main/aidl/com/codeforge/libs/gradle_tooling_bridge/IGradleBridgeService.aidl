// Modul: :libs:gradle-tooling-bridge
package com.codeforge.libs.gradle_tooling_bridge;

import com.codeforge.libs.gradle_tooling_bridge.IGradleBridgeCallback;

/**
 * Läuft im separaten :gradletooling-Prozess (siehe AndroidManifest.xml,
 * android:process=":gradletooling"). Kapselt die Gradle Tooling API
 * (org.gradle.tooling.GradleConnector), damit deren Klassen nicht im
 * Haupt-App-Prozess/-Classloader landen.
 */
interface IGradleBridgeService {
    void connect(String projectRootPath, String gradleUserHome);
    void runTask(String taskName, IGradleBridgeCallback callback);
    void cancelBuild();
    void disconnect();
}
