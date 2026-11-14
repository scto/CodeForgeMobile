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
    val profile: Boolean = false,
    val rerunTasks: Boolean = false,
    val parallel: Boolean = false,
    /** Weitere Argumente, durch Leerraum getrennt (nur Optionen, die mit `-` beginnen). */
    val extraArgs: String = "",
    /** Pfad einer lokalen Gradle-Installation (enthält `bin/gradle`); leer = Wrapper des Projekts. */
    val customGradleHome: String = "",
    /** JVM-Optionen des Gradle-Daemons; `null` = Werte aus `gradle.properties` des Projekts. */
    val daemonJvm: JvmOptions? = null,
    /** Leerlaufzeit in Minuten, nach der sich der Gradle-Daemon beendet; 0 = Gradle-Standard (3 Stunden). */
    val daemonIdleMinutes: Int = 0,
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
        if (profile) add("--profile")
        if (rerunTasks) add("--rerun-tasks")
        if (parallel) add("--parallel")
        addAll(extraArgs.split(WHITESPACE).filter(JvmOptions::isValidFlag))
        if (daemonIdleMinutes > 0) add("-Dorg.gradle.daemon.idletimeout=" + daemonIdleMinutes.coerceAtMost(MAX_IDLE_MINUTES) * 60_000L)
        daemonJvm?.toArgs()?.takeIf { it.isNotEmpty() }?.let { add("-Dorg.gradle.jvmargs=" + it.joinToString(" ")) }
    }.filterNot { it in present }.distinct()

    /** Eigene Flags (aus [extraArgs]) in Eingabereihenfolge, ohne Doppelte. */
    val customFlags: List<String> get() = extraArgs.split(WHITESPACE).filter { it.isNotEmpty() }.distinct()

    /** Alle aktiven Flags zur Anzeige: häufige in Katalog-Reihenfolge, danach eigene. */
    fun activeFlags(): List<String> = COMMON_FLAGS.filter(::isActive) + customFlags

    /** Ob das häufige Flag [flag] gesetzt ist. */
    fun isActive(flag: String): Boolean = when (flag) {
        "--quiet" -> logLevel == GradleLogLevel.QUIET
        "--warn" -> logLevel == GradleLogLevel.WARN
        "--info" -> logLevel == GradleLogLevel.INFO
        "--debug" -> logLevel == GradleLogLevel.DEBUG
        "--stacktrace" -> stacktrace == GradleStacktrace.SHORT
        "--full-stacktrace" -> stacktrace == GradleStacktrace.FULL
        "--offline" -> offline
        "--no-build-cache" -> noBuildCache
        "--no-daemon" -> noDaemon
        "--continue" -> continueOnFailure
        "--profile" -> profile
        "--rerun-tasks" -> rerunTasks
        "--parallel" -> parallel
        else -> flag in customFlags
    }

    /**
     * Schaltet ein Flag um. Log-Stufen (`--quiet/--warn/--info/--debug`) und Stacktrace-Arten schließen sich jeweils
     * gegenseitig aus: Wer eins wählt, wählt das andere ab. Unbekannte Flags werden als eigenes Flag geschaltet.
     */
    fun toggle(flag: String): GradleRunSettings {
        val on = !isActive(flag)
        fun level(l: GradleLogLevel) = copy(logLevel = if (on) l else GradleLogLevel.DEFAULT)
        fun stack(m: GradleStacktrace) = copy(stacktrace = if (on) m else GradleStacktrace.NONE)
        return when (flag) {
            "--quiet" -> level(GradleLogLevel.QUIET)
            "--warn" -> level(GradleLogLevel.WARN)
            "--info" -> level(GradleLogLevel.INFO)
            "--debug" -> level(GradleLogLevel.DEBUG)
            "--stacktrace" -> stack(GradleStacktrace.SHORT)
            "--full-stacktrace" -> stack(GradleStacktrace.FULL)
            "--offline" -> copy(offline = on)
            "--no-build-cache" -> copy(noBuildCache = on)
            "--no-daemon" -> copy(noDaemon = on)
            "--continue" -> copy(continueOnFailure = on)
            "--profile" -> copy(profile = on)
            "--rerun-tasks" -> copy(rerunTasks = on)
            "--parallel" -> copy(parallel = on)
            else -> if (on) addCustomFlag(flag) else removeCustomFlag(flag)
        }
    }

    /**
     * Fügt ein eigenes Flag hinzu. Ein häufiges Flag wird stattdessen eingeschaltet; ungültige Angaben ändern nichts
     * (Prüfung mit [JvmOptions.isValidFlag], keine Shell-Sonderzeichen).
     */
    fun addCustomFlag(raw: String): GradleRunSettings {
        val flag = raw.trim()
        if (!JvmOptions.isValidFlag(flag)) return this
        if (flag in COMMON_FLAGS) return if (isActive(flag)) this else toggle(flag)
        if (flag in customFlags) return this
        return copy(extraArgs = (customFlags + flag).joinToString(" "))
    }

    /** Entfernt ein eigenes Flag; ein häufiges wird ausgeschaltet. */
    fun removeCustomFlag(flag: String): GradleRunSettings =
        if (flag in COMMON_FLAGS) (if (isActive(flag)) toggle(flag) else this)
        else copy(extraArgs = customFlags.filterNot { it == flag }.joinToString(" "))

    /** Erstes ungültiges Zusatzargument (zur Anzeige) oder `null`. */
    fun firstInvalidExtraArg(): String? =
        extraArgs.split(WHITESPACE).firstOrNull { it.isNotEmpty() && !JvmOptions.isValidFlag(it) }

    companion object {
        private val WHITESPACE = Regex("\\s+")
        private val LOG_FLAGS = setOf("--quiet", "-q", "--warn", "-w", "--info", "-i", "--debug", "-d")
        private val STACK_FLAGS = setOf("--stacktrace", "-s", "--full-stacktrace", "-S")

        /** Häufige Flags in Anzeige-Reihenfolge. */
        val COMMON_FLAGS = listOf(
            "--info", "--debug", "--quiet", "--warn", "--stacktrace", "--full-stacktrace", "--profile", "--rerun-tasks",
            "--no-build-cache", "--no-daemon", "--parallel", "--offline", "--continue",
        )

        /** Obergrenze der einstellbaren Leerlaufzeit (ein Tag). */
        const val MAX_IDLE_MINUTES = 24 * 60

        /** Auswahl der Leerlaufzeiten im Dialog (0 = Gradle-Standard). */
        val IDLE_CHOICES = listOf(0, 5, 15, 60)

        /** Vorgabe der Daemon-JVM-Optionen fürs Telefon (Ausgangswert im Dialog). */
        val DEFAULT_DAEMON_JVM = JvmOptions(xms = "256m", xmx = "1536m", maxMetaspace = "384m", extraFlags = "-XX:+UseSerialGC")
    }
}
