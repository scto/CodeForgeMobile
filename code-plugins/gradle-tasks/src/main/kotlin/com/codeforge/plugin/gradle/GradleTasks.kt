/**
 * Plugin: Gradle-Aufgaben
 */
package com.codeforge.plugin.gradle

/** Übliche Aufgaben je Modul; [android] = Modul mit Android-Plugin (hat Varianten). */
object GradleTasks {
    fun forModule(gradlePath: String, android: Boolean): List<String> {
        val p = if (gradlePath == ":") "" else gradlePath
        val common = listOf("$p:clean", "$p:dependencies")
        return if (android) listOf("$p:assembleDebug", "$p:testDebugUnitTest", "$p:lintDebug") + common
        else listOf("$p:build", "$p:test") + common
    }

    /** Freie Eingabe → Aufgabenliste (Leerzeichen getrennt, nur Aufgabennamen – keine Optionen wie `--init-script`). */
    fun parse(input: String): List<String> =
        input.trim().split(Regex("\\s+")).filter { Regex("[A-Za-z0-9:_][A-Za-z0-9:._-]*").matches(it) }

    /** Ob ein Modul ein Android-Modul ist (Build-Skript wendet ein Android-Plugin an). */
    fun isAndroid(buildScript: String): Boolean =
        Regex("com\\.android\\.(application|library)|android\\.(application|library)|codeforge\\.android").containsMatchIn(buildScript)
}
