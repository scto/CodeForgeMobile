/**
 * Modul: :libs:dependency-updater-impl
 * @author Thomas Schmid
 */
package com.codeforge.libs.dependency_updater_impl

/** Reifegrad einer Version; höher = stabiler. */
enum class Stability { UNSTABLE, ALPHA, BETA, RC, STABLE }

/**
 * Gradle-ähnlicher Versionsvergleich: Tokens an `. - _ +` sowie an Ziffer/Buchstabe-Grenzen
 * (`1.0.0-rc01` → 1,0,0,rc,01), Zahlen numerisch, Qualifier nach Rang
 * (dev < alpha < beta < milestone < rc < snapshot < final/ga/release < sp), Zahl > Qualifier.
 */
object VersionComparator : Comparator<String> {

    private data class Part(val number: Long?, val text: String)

    private fun tokenize(version: String): List<Part> {
        val parts = ArrayList<Part>()
        val sb = StringBuilder()
        var digits: Boolean? = null
        fun flush() {
            if (sb.isNotEmpty()) {
                val s = sb.toString()
                parts += if (digits == true) Part(s.toLongOrNull() ?: Long.MAX_VALUE, s) else Part(null, s.lowercase())
                sb.setLength(0)
            }
            digits = null
        }
        for (c in version.trim()) {
            when {
                c == '.' || c == '-' || c == '_' || c == '+' -> flush()
                c.isDigit() -> { if (digits == false) flush(); digits = true; sb.append(c) }
                else -> { if (digits == true) flush(); digits = false; sb.append(c) }
            }
        }
        flush()
        return parts
    }

    private fun rank(q: String): Int = when (q) {
        "dev" -> 0
        "alpha", "a" -> 1
        "beta", "b" -> 2
        "milestone", "m", "eap", "preview", "pre" -> 3
        "rc", "cr" -> 4
        "snapshot" -> 5
        "final", "ga", "release" -> 6
        "sp" -> 7
        else -> 1
    }

    override fun compare(a: String, b: String): Int {
        val x = tokenize(a)
        val y = tokenize(b)
        val n = maxOf(x.size, y.size)
        for (i in 0 until n) {
            val p = x.getOrNull(i)
            val q = y.getOrNull(i)
            val c = when {
                p == null -> -compareMissing(q!!)
                q == null -> compareMissing(p)
                else -> comparePart(p, q)
            }
            if (c != 0) return c
        }
        return 0
    }

    /** Vergleich eines vorhandenen Teils gegen „nichts“ (kürzere Version). */
    private fun compareMissing(p: Part): Int = when {
        p.number != null -> if (p.number == 0L) 0 else 1
        else -> rank(p.text).compareTo(rank("final"))
    }

    private fun comparePart(p: Part, q: Part): Int = when {
        p.number != null && q.number != null -> p.number.compareTo(q.number)
        p.number != null -> 1
        q.number != null -> -1
        else -> {
            val r = rank(p.text).compareTo(rank(q.text))
            if (r != 0) r else p.text.compareTo(q.text)
        }
    }

    fun stabilityOf(version: String): Stability {
        var result = Stability.STABLE
        for (part in tokenize(version)) {
            if (part.number != null) continue
            val s = when (part.text) {
                "dev", "snapshot" -> Stability.UNSTABLE
                "alpha", "a" -> Stability.ALPHA
                "beta", "b", "milestone", "m", "eap", "preview", "pre" -> Stability.BETA
                "rc", "cr" -> Stability.RC
                else -> Stability.STABLE // z. B. "jre", "android", "final"
            }
            if (s < result) result = s
        }
        return result
    }
}

object UpdatePolicy {

    /** Dynamische/nicht auflösbare Versionen (`+`, Ranges, `latest.*`, Variablen) werden nie aktualisiert. */
    fun isUpgradable(version: String): Boolean {
        val v = version.trim()
        if (v.isEmpty()) return false
        if (v.any { it == '+' || it == '$' || it == '[' || it == '(' || it == ',' || it == ' ' }) return false
        return !v.startsWith("latest", ignoreCase = true)
    }

    /**
     * Neueste für [current] zulässige Version aus [available], oder `null`. Von stabilen Versionen
     * aus nur stabile; von Vorabversionen aus Versionen mit mindestens gleichem Reifegrad
     * (alpha → alpha/beta/rc/stable). `-SNAPSHOT`/dev nur, wenn [current] selbst so ist.
     */
    fun latest(current: String, available: Collection<String>): String? {
        val currentStability = VersionComparator.stabilityOf(current)
        return available
            .asSequence()
            .filter { VersionComparator.compare(it, current) > 0 }
            .filter {
                val s = VersionComparator.stabilityOf(it)
                s == Stability.STABLE || (currentStability != Stability.STABLE && s >= currentStability)
            }
            .maxWithOrNull(VersionComparator)
    }
}
