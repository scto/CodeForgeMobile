package com.codeforge.core.domain.model

/** Einzeln abschaltbare Fähigkeiten eines Language-Servers. */
enum class LspFeature { COMPLETION, DIAGNOSTICS, HOVER, SIGNATURE_HELP, CODE_ACTIONS, FORMATTING, INLAY_HINTS }

/**
 * JVM-Optionen eines Java-basierten Servers oder Dienstes. Leere Felder bedeuten „JVM-Standard“.
 * Größen wie `512m`, `2g`; [extraFlags] durch Leerraum getrennt.
 */
data class JvmOptions(
    val xms: String = "",
    val xmx: String = "",
    val maxMetaspace: String = "",
    val extraFlags: String = "",
) {
    /** Gültige Optionen als Argumentliste; ungültige Einträge werden ausgelassen. */
    fun toArgs(): List<String> = buildList {
        xms.trim().takeIf(::isValidSize)?.let { add("-Xms$it") }
        xmx.trim().takeIf(::isValidSize)?.let { add("-Xmx$it") }
        maxMetaspace.trim().takeIf(::isValidSize)?.let { add("-XX:MaxMetaspaceSize=$it") }
        addAll(extraFlags.split(WHITESPACE).filter(::isValidFlag))
    }

    /** Erste ungültige Angabe (zur Anzeige) oder `null`, wenn alles gültig ist. */
    fun firstInvalid(): String? =
        listOf(xms, xmx, maxMetaspace).map { it.trim() }.firstOrNull { it.isNotEmpty() && !isValidSize(it) }
            ?: extraFlags.split(WHITESPACE).firstOrNull { it.isNotEmpty() && !isValidFlag(it) }

    companion object {
        private val WHITESPACE = Regex("\\s+")
        private val SIZE = Regex("^[1-9][0-9]{0,6}[kKmMgG]$")
        // Bewusst eng: Die Flags landen in Umgebungsvariablen, die Startskripte per Shell auswerten.
        private val FLAG = Regex("^-[A-Za-z0-9:+\\-=._/,@%]{1,200}$")

        /** Prüft eine Größenangabe wie `512m`. */
        fun isValidSize(value: String): Boolean = SIZE.matches(value)

        /** Prüft ein einzelnes JVM-Flag (beginnt mit `-`, keine Shell-Sonderzeichen). */
        fun isValidFlag(value: String): Boolean = FLAG.matches(value)
    }
}

/** Einstellungen eines Language-Servers. */
data class LspServerSettings(
    val enabled: Boolean = true,
    val disabledFeatures: Set<LspFeature> = emptySet(),
    /** `null` = Standard des Servers ([LspServerDefaults.jvmFor]). */
    val jvm: JvmOptions? = null,
) {
    /** Ob [feature] genutzt werden darf. */
    fun allows(feature: LspFeature): Boolean = enabled && feature !in disabledFeatures
}

/** Vorgaben und Schlüssel der Server-Einstellungen. */
object LspServerDefaults {
    /** Server, die auf der JVM laufen und JVM-Optionen annehmen. */
    val JVM_SERVERS: Set<String> = setOf("kotlin-language-server", "jdtls", "lemminx")

    /** Einstellungs-Schlüssel zu einer Server-ID (die drei Web-Server teilen sich ein Paket). */
    fun keyFor(serverId: String): String =
        if (serverId.startsWith("vscode-") && serverId.endsWith("-language-server")) "vscode-langservers-extracted" else serverId

    /** Standard-JVM-Optionen eines Servers (klein gehalten fürs Telefon) oder `null`, wenn kein JVM-Server. */
    fun jvmFor(serverId: String): JvmOptions? = when (serverId) {
        // SerialGC: keine GC-Threads; kein Perf-Data-Mapping – spart RAM und Startzeit.
        "kotlin-language-server" -> JvmOptions(extraFlags = "-XX:+UseSerialGC -XX:-UsePerfData")
        "jdtls" -> JvmOptions(extraFlags = "-XX:+UseSerialGC")
        "lemminx" -> JvmOptions(xmx = "256m", extraFlags = "-XX:+UseSerialGC")
        else -> null
    }
}
