package com.codeforge.libs.dependency_updater_impl

import com.codeforge.libs.dependency_updater_api.DependencyLocation
import com.codeforge.libs.dependency_updater_api.DependencyUpdate
import com.codeforge.libs.dependency_updater_api.LibraryCoordinate
import com.codeforge.libs.dependency_updater_api.ProjectUpdateState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UpdateFlowTest {

    private val toml = """
        [versions]
        compose = "1.7.0"
        kotlin = "2.0.20"
        [libraries]
        ui = { module = "androidx.compose.ui:ui", version.ref = "compose" }
        graphics = { module = "androidx.compose.ui:ui-graphics", version.ref = "compose" }
        stdlib = { module = "org.jetbrains.kotlin:kotlin-stdlib", version.ref = "kotlin" }
        okhttp = "com.squareup.okhttp3:okhttp:4.12.0"
    """.trimIndent()

    private fun candidates() = DependencyScanner.group(TomlCatalogParser.parse("/p/libs.versions.toml", toml))

    private val server = mapOf(
        "androidx.compose.ui:ui" to listOf("1.7.0", "1.7.3", "1.8.0-alpha01"),
        "androidx.compose.ui:ui-graphics" to listOf("1.7.0", "1.7.2"),
        "org.jetbrains.kotlin:kotlin-stdlib" to listOf("2.0.20"),
        "com.squareup.okhttp3:okhttp" to listOf("4.12.0", "5.0.0")
    )

    @Test fun calculatorUsesMinimumForSharedVersionAndSkipsCurrent() {
        val updates = UpdateCalculator.compute(candidates()) { c, _ -> server[c.key] }
        val byVersion = updates.associateBy { it.currentVersion }
        assertEquals("1.7.2", byVersion["1.7.0"]!!.newVersion)   // min(1.7.3, 1.7.2)
        assertEquals("5.0.0", byVersion["4.12.0"]!!.newVersion)
        assertTrue("2.0.20" !in byVersion)                          // kein Update
        assertEquals(2, byVersion["1.7.0"]!!.coordinates.size)
    }

    @Test fun annotateCatalogFollowsLiveText() {
        val updates = UpdateCalculator.compute(candidates()) { c, _ -> server[c.key] }
        val live = "# neue Zeile\n# noch eine\n$toml"
        val ann = FileAnnotator.annotate("/p/libs.versions.toml", live, updates)
        val labels = ann.map { it.line to it.update.label }
        assertTrue(labels.contains(3 to "1.7.0 -> 1.7.2"))
        assertTrue(labels.any { it.second == "4.12.0 -> 5.0.0" })
    }

    @Test fun applierReplacesAndValidates() {
        val usage = TomlCatalogParser.parse("/p/x", toml).first { it.literal.value == "1.7.0" }
        val edit = TextEdit(usage.literal.start, usage.literal.end, "1.7.0", "1.7.2")
        val out = VersionApplier.apply(toml, listOf(edit)).getOrThrow()
        assertTrue(out.contains("compose = \"1.7.2\""))
        assertTrue(VersionApplier.apply(out, listOf(edit)).isFailure)
    }

    @Test fun dismissalStoreRoundTrip() {
        val f = File.createTempFile("dismiss", ".tsv")
        try {
            val s = FileDismissalStore(f)
            s.add("/proj", "a:b->2.0")
            s.add("/proj", "a:b->2.0")
            s.add("/other", "c:d->1.1")
            assertEquals(setOf("a:b->2.0"), FileDismissalStore(f).dismissed("/proj"))
        } finally { f.delete() }
    }

    @Test fun metadataParsing() {
        val xml = "<metadata><versioning><latest>2</latest><versions><version>1.0</version><version> 2.0 </version></versions></versioning></metadata>"
        assertEquals(listOf("1.0", "2.0"), MavenMetadata.parseVersions(xml))
    }

    @Test fun stateViews() {
        val u = DependencyUpdate(listOf(LibraryCoordinate("a", "b")), "1", "2", listOf(DependencyLocation("f", 0, 0, 1)), false)
        val s = ProjectUpdateState("/p", updates = listOf(u), dismissedKeys = setOf(u.key))
        assertTrue(s.pending.isEmpty())
        assertEquals(1, s.copy(dismissedKeys = emptySet()).promptable.size)
        assertTrue(s.copy(dismissedKeys = emptySet(), snoozedKeys = setOf(u.key)).promptable.isEmpty())
    }
}
