/**
 * Modul: :libs:agent
 * @author Thomas Schmid
 *
 * Projektregeln (`AGENTS.md`/`CLAUDE.md`) und Skills (`SKILL.md`) für den Agenten.
 */
package com.codeforge.libs.agent

import java.io.File
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Eine Fähigkeit aus `<dir>/SKILL.md`: [description] steht im Prompt, [body] wird erst bei Bedarf geladen. */
data class AgentSkill(val name: String, val description: String, val dir: File, val body: String)

/** Geladener Projektkontext: Regeln und Skills. */
class ProjectContext(val instructions: String, val skills: List<AgentSkill>) {
    /** Anhang für den System-Prompt (leer, wenn es nichts gibt). */
    fun promptSection(): String = buildString {
        if (instructions.isNotBlank()) {
            append("\n\n<project-instructions>\n").append(instructions.trim()).append("\n</project-instructions>")
        }
        if (skills.isNotEmpty()) {
            append("\n\n<available-skills>\nCall use_skill with the name when a skill fits the task.\n")
            skills.forEach { append("- ").append(it.name).append(": ").append(it.description).append('\n') }
            append("</available-skills>")
        }
    }

    /** `true`, wenn weder Regeln noch Skills vorhanden sind. */
    val isEmpty: Boolean get() = instructions.isBlank() && skills.isEmpty()

    /** Kurzbeschreibung für die Freigabeabfrage, z. B. „Regeln, 2 Skills“. */
    fun summary(): String = listOfNotNull(
        "Regeln".takeIf { instructions.isNotBlank() },
        "${skills.size} Skills".takeIf { skills.isNotEmpty() },
    ).joinToString(", ")

    /** SHA-256 über Regeln und Skills; ändert sich der Inhalt, ist neu freizugeben. */
    fun fingerprint(): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        md.update(instructions.toByteArray())
        skills.forEach { md.update("\u0000${it.name}\u0000${it.description}\u0000${it.body}".toByteArray()) }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /** Werkzeuge, die der Kontext beisteuert (derzeit `use_skill`, nur wenn Skills existieren). */
    fun tools(): List<AgentTool> = if (skills.isEmpty()) emptyList() else listOf(UseSkillTool(skills))

    companion object {
        /** Kein Kontext. */
        val EMPTY = ProjectContext("", emptyList())

        /** Maximale Länge der Projektregeln im Prompt. */
        const val MAX_INSTRUCTIONS = 8_000

        /** Maximale Länge eines Skill-Textes. */
        const val MAX_SKILL_BODY = 24_000

        private const val MAX_SKILLS = 50
        private val INSTRUCTION_FILES = listOf("AGENTS.md", "CLAUDE.md", ".codeforge/AGENT.md")
        private val SKILL_ROOTS = listOf(".codeforge/skills", ".claude/skills")

        /** Liest Regeln und Skills unterhalb von [root]; fehlerhafte Dateien werden übersprungen. */
        fun load(root: File): ProjectContext {
            val instructions = INSTRUCTION_FILES.asSequence()
                .map { File(root, it) }
                .firstNotNullOfOrNull { f -> runCatching { f.takeIf { it.isFile }?.readText() }.getOrNull()?.takeIf { it.isNotBlank() } }
                ?.let { if (it.length > MAX_INSTRUCTIONS) it.take(MAX_INSTRUCTIONS) + "\n… (gekürzt)" else it }
                .orEmpty()
            val seen = HashSet<String>()
            val skills = SKILL_ROOTS.flatMap { rel ->
                File(root, rel).listFiles { f -> f.isDirectory }?.sortedBy { it.name }.orEmpty().mapNotNull { dir ->
                    runCatching { parseSkill(dir) }.getOrNull()
                }
            }.filter { seen.add(it.name) }.take(MAX_SKILLS)
            return ProjectContext(instructions, skills)
        }

        internal fun parseSkill(dir: File): AgentSkill? {
            val file = File(dir, "SKILL.md").takeIf { it.isFile } ?: return null
            val text = file.readText()
            val meta = LinkedHashMap<String, String>()
            var body = text
            if (text.startsWith("---")) {
                val end = text.indexOf("\n---", 3)
                if (end > 0) {
                    text.substring(3, end).lineSequence().forEach { line ->
                        val i = line.indexOf(':')
                        if (i > 0) meta[line.substring(0, i).trim()] = line.substring(i + 1).trim().trim('"', '\'')
                    }
                    body = text.substring(end + 4).trimStart('\n', '\r')
                }
            }
            val name = (meta["name"] ?: dir.name).lowercase().replace(Regex("[^a-z0-9_-]"), "-").take(64)
            val description = meta["description"].orEmpty().ifBlank { body.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().trimStart('#', ' ') }
            if (name.isBlank() || description.isBlank()) return null
            return AgentSkill(name, description.take(300), dir, body.take(MAX_SKILL_BODY))
        }
    }
}

/** Lädt den Text eines Skills (lesend, ohne Freigabe). */
class UseSkillTool(private val skills: List<AgentSkill>) : AgentTool {
    override val name = "use_skill"
    override val description = "Load the full instructions of a project skill listed in <available-skills>."
    override val kind = ToolKind.READ
    override val parameters: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") { putJsonObject("name") { put("type", "string"); put("description", "Skill name") } }
        putJsonArray("required") { add(JsonPrimitive("name")) }
    }

    override fun describeCall(arguments: JsonObject): String = "Skill ${(arguments["name"] as? JsonPrimitive)?.content.orEmpty()}"

    override suspend fun execute(arguments: JsonObject): ToolResult {
        val wanted = arguments.requireString("name")
        val skill = skills.firstOrNull { it.name == wanted }
            ?: return ToolResult.error("Unbekannter Skill '$wanted'. Verfügbar: ${skills.joinToString { it.name }}")
        val extra = skill.dir.listFiles { f -> f.isFile && f.name != "SKILL.md" }?.map { it.name }?.sorted().orEmpty()
        val tail = if (extra.isEmpty()) "" else "\n\n(Weitere Dateien im Skill-Ordner ${skill.dir.name}: ${extra.joinToString()})"
        return ToolResult(skill.body + tail)
    }
}
