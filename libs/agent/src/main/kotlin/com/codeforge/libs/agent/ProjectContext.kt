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

    /**
     * Ergänzt mitgelieferte Skills der App (ohne Freigabe, da nicht aus dem Projekt); ein Projekt-Skill gleichen
     * Namens hat Vorrang.
     */
    fun withBuiltInSkills(builtIn: List<AgentSkill>): ProjectContext {
        val names = skills.mapTo(HashSet()) { it.name }
        val extra = builtIn.filter { it.name !in names }
        val merged = (skills + extra).take(MAX_SKILLS)
        return if (extra.isEmpty()) this else ProjectContext(instructions, merged)
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

        /** Maximale Länge einer Referenzdatei eines Skills. */
        const val MAX_SKILL_FILE = 60_000

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
            val skills = SKILL_ROOTS.flatMap { rel -> loadSkills(File(root, rel)) }
                .filter { seen.add(it.name) }
                .take(MAX_SKILLS)
            return ProjectContext(instructions, skills)
        }

        /** Skills in den Unterordnern von [dir] (je Ordner eine `SKILL.md`); fehlerhafte werden übersprungen. */
        fun loadSkills(dir: File): List<AgentSkill> =
            dir.listFiles { f -> f.isDirectory }?.sortedBy { it.name }.orEmpty().mapNotNull { sub ->
                runCatching { parseSkill(sub) }.getOrNull()
            }

        internal fun parseSkill(dir: File): AgentSkill? {
            val file = File(dir, "SKILL.md").takeIf { it.isFile } ?: return null
            val text = file.readText()
            val meta = LinkedHashMap<String, String>()
            var body = text
            if (text.startsWith("---")) {
                val end = text.indexOf("\n---", 3)
                if (end > 0) {
                    parseFrontmatter(text.substring(3, end), meta)
                    body = text.substring(end + 4).trimStart('\n', '\r')
                }
            }
            val name = (meta["name"] ?: dir.name).lowercase().replace(Regex("[^a-z0-9_-]"), "-").take(64)
            val description = meta["description"].orEmpty().ifBlank { body.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().trimStart('#', ' ') }
            if (name.isBlank() || description.isBlank()) return null
            return AgentSkill(name, description.take(300), dir, body.take(MAX_SKILL_BODY))
        }

        // Einfaches YAML: `key: wert` auf oberster Ebene; eingerückte Folgezeilen gehören zum vorigen Wert
        // (mehrzeilige Beschreibung, verschachtelte Metadaten werden angehängt und nicht ausgewertet).
        private fun parseFrontmatter(yaml: String, meta: MutableMap<String, String>) {
            var last: String? = null
            for (line in yaml.lineSequence().filter { it.isNotBlank() }) {
                val i = line.indexOf(':')
                val indented = line.first().isWhitespace()
                val key = last
                if (!indented && i > 0) {
                    last = line.substring(0, i).trim()
                    meta[line.substring(0, i).trim()] = line.substring(i + 1).trim().trim('"', '\'')
                } else if (indented && key != null) {
                    meta[key] = (meta[key].orEmpty() + " " + line.trim()).trim()
                }
            }
        }
    }
}

/** Lädt den Text eines Skills (lesend, ohne Freigabe). */
class UseSkillTool(private val skills: List<AgentSkill>) : AgentTool {
    override val name = "use_skill"
    override val description = "Load the full instructions of a skill listed in <available-skills>, " +
        "or one of its reference files (pass file with the relative path the skill lists)."
    override val kind = ToolKind.READ
    override val parameters: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("name") { put("type", "string"); put(DESCRIPTION, "Skill name") }
            putJsonObject("file") { put("type", "string"); put(DESCRIPTION, "Optional: relative path of a file inside the skill folder") }
        }
        putJsonArray("required") { add(JsonPrimitive("name")) }
    }

    override fun describeCall(arguments: JsonObject): String = "Skill ${(arguments["name"] as? JsonPrimitive)?.content.orEmpty()}"

    override suspend fun execute(arguments: JsonObject): ToolResult {
        val wanted = arguments.requireString("name")
        val skill = skills.firstOrNull { it.name == wanted }
            ?: return ToolResult.error("Unbekannter Skill '$wanted'. Verfügbar: ${skills.joinToString { it.name }}")
        val file = (arguments["file"] as? JsonPrimitive)?.content?.trim().orEmpty()
        return if (file.isNotEmpty()) readFile(skill, file) else overview(skill)
    }

    private fun overview(skill: AgentSkill): ToolResult {
        val extra = files(skill)
        val tail = if (extra.isEmpty()) "" else "\n\n(Weitere Dateien im Skill-Ordner ${skill.dir.name}, mit file laden: ${extra.joinToString()})"
        return ToolResult(skill.body + tail)
    }

    // Dateien des Skill-Ordners (relativ, auch in Unterordnern) außer SKILL.md.
    private fun files(skill: AgentSkill): List<String> = skill.dir.walkTopDown().maxDepth(MAX_DEPTH)
        .filter { it.isFile && it.name != "SKILL.md" }
        .map { it.relativeTo(skill.dir).invariantSeparatorsPath }
        .sorted()
        .take(MAX_LISTED)
        .toList()

    private fun readFile(skill: AgentSkill, path: String): ToolResult {
        val base = skill.dir.canonicalFile
        val target = File(base, path).canonicalFile
        if (!target.path.startsWith(base.path + File.separator) || !target.isFile) {
            return ToolResult.error("Datei '$path' gibt es im Skill ${skill.name} nicht. Vorhanden: ${files(skill).joinToString()}")
        }
        val text = target.readText()
        return ToolResult(if (text.length > ProjectContext.MAX_SKILL_FILE) text.take(ProjectContext.MAX_SKILL_FILE) + "\n… (gekürzt)" else text)
    }

    private companion object {
        const val DESCRIPTION = "description"
        const val MAX_DEPTH = 6
        const val MAX_LISTED = 40
    }
}
