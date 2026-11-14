package com.codeforge.core.domain.model

/** Ausführlichkeit der Gradle-Ausgabe. */
enum class GradleLogLevel(val flag: String?) { DEFAULT(null), QUIET("--quiet"), WARN("--warn"), INFO("--info"), DEBUG("--debug") }

/** Stacktrace-Ausgabe bei Fehlern. */
enum class GradleStacktrace(val flag: String?) { NONE(null), SHORT("--stacktrace"), FULL("--full-stacktrace") }

/**
 * Einstellungen, die für jeden Gradle-Lauf der IDE gelten (Build, Clean, Tests, Wrapper …).
 */
data class GradleRunSettings(
    val logLevel: GradleLogLevel = GradleLogLevel.DEFAULT,
    val stacktrace: GradleStacktrace = GradleStacktrace.NONE,
    val offline: Boolean = false,
    val noBuildCache: Boolean = false,
    val noDaemon: Boolean = false,
    val continueOnFailure: Boolean = false,
    /** Weitere Argumente, durch Leerraum getrennt (nur Optionen, die mit `-` beginnen). */
    val extraArgs: String = "",
    /** Pfad einer lokalen Gradle-Installation (enthält `bin/gradle`); leer = Wrapper des Projekts. */
    val customGradleHome: String = "",
    /** JVM-Optionen des Gradle-Daemons; `null` = Werte aus `gradle.properties` des Projekts. */
    val daemonJvm: JvmOptions? = null,
) {
    /**
     * Zusätzliche Kommandozeilen-Argumente. Was der Aufruf in [present] schon selbst enthält, wird nicht doppelt gesetzt;
     * ein dort gesetztes Log-Level bzw. Stacktrace-Flag hat Vorrang.
     */
    fun toArgs(present: List<String> = emptyList()): List<String> = buildList {
        if (present.none { it in LOG_FLAGS }) logLevel.flag?.let(::add)
        if (present.none { it in STACK_FLAGS }) stacktrace.flag?.let(::add)
        if (offline) add("--offline")
        if (noBuildCache) add("--no-build-cache")
        if (noDaemon) add("--no-daemon")
        if (continueOnFailure) add("--continue")
        addAll(extraArgs.split(WHITESPACE).filter(JvmOptions::isValidFlag))
        daemonJvm?.toArgs()?.takeIf { it.isNotEmpty() }?.let { add("-Dorg.gradle.jvmargs=" + it.joinToString(" ")) }
    }.filterNot { it in present }.distinct()

    /** Erstes ungültiges Zusatzargument (zur Anzeige) oder `null`. */
    fun firstInvalidExtraArg(): String? =
        extraArgs.split(WHITESPACE).firstOrNull { it.isNotEmpty() && !JvmOptions.isValidFlag(it) }

    companion object {
        private val WHITESPACE = Regex("\\s+")
        private val LOG_FLAGS = setOf("--quiet", "-q", "--warn", "-w", "--info", "-i", "--debug", "-d")
        private val STACK_FLAGS = setOf("--stacktrace", "-s", "--full-stacktrace", "-S")

        /** Vorgabe der Daemon-JVM-Optionen fürs Telefon (Ausgangswert im Dialog). */
        val DEFAULT_DAEMON_JVM = JvmOptions(xms = "256m", xmx = "1536m", maxMetaspace = "384m", extraFlags = "-XX:+UseSerialGC")
    }
}
