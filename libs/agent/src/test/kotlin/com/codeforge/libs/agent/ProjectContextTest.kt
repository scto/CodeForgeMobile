package com.codeforge.libs.agent

import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectContextTest {
    private val root = kotlin.io.path.createTempDirectory("ctx").toFile()

    @After
    fun tearDown() { root.deleteRecursively() }

    private fun skill(dir: String, content: String) {
        File(root, "$dir/SKILL.md").apply { parentFile.mkdirs() }.writeText(content)
    }

    @Test
    fun `Regeln kommen aus AGENTS_md vor CLAUDE_md und werden gekuerzt`() {
        File(root, "CLAUDE.md").writeText("claude")
        assertEquals("claude", ProjectContext.load(root).instructions)
        File(root, "AGENTS.md").writeText("x".repeat(ProjectContext.MAX_INSTRUCTIONS + 10))
        assertTrue(ProjectContext.load(root).instructions.endsWith("(gekürzt)"))
    }

    @Test
    fun `Skills mit Frontmatter, Fallback und Duplikaten`() {
        skill(".codeforge/skills/release", "---\nname: Release\ndescription: \"Release bauen\"\n---\n# Schritte\n1. tag")
        skill(".claude/skills/release", "---\nname: release\ndescription: doppelt\n---\nx")
        skill(".claude/skills/notes", "# Notizen\ntext")
        File(root, ".claude/skills/leer").mkdirs()
        val ctx = ProjectContext.load(root)
        assertEquals(listOf("release", "notes"), ctx.skills.map { it.name }.sortedByDescending { it == "release" })
        assertEquals("Release bauen", ctx.skills.first { it.name == "release" }.description)
        assertEquals("Notizen", ctx.skills.first { it.name == "notes" }.description)
        assertTrue(ctx.promptSection().contains("- release: Release bauen"))
    }

    @Test
    fun `use_skill liefert den Text oder listet die Namen`() = runBlocking {
        skill(".codeforge/skills/a", "---\nname: a\ndescription: d\n---\nBODY")
        File(root, ".codeforge/skills/a/helper.sh").writeText("echo")
        val tool = ProjectContext.load(root).tools().single()
        assertEquals(ToolKind.READ, tool.kind)
        val ok = tool.execute(buildJsonObject { put("name", "a") })
        assertTrue(ok.content.startsWith("BODY") && ok.content.contains("helper.sh"))
        val bad = tool.execute(buildJsonObject { put("name", "zzz") })
        assertTrue(bad.isError && bad.content.contains("a"))
    }

    @Test
    fun `leerer Kontext ergibt nichts`() {
        val ctx = ProjectContext.load(root)
        assertEquals("", ctx.promptSection())
        assertTrue(ctx.tools().isEmpty())
    }
}
