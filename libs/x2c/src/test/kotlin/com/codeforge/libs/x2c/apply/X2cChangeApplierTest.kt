package com.codeforge.libs.x2c.apply

import com.codeforge.core.domain.model.JournalAction
import com.codeforge.core.domain.model.JournalStatus
import com.codeforge.core.domain.model.PlannedChange
import com.codeforge.core.domain.model.UndoConflictPolicy
import com.codeforge.libs.x2c.backup.X2cBackupRepositoryImpl
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class X2cChangeApplierTest {

    @get:Rule val tmp = TemporaryFolder()
    private lateinit var project: File
    private lateinit var zip: String
    private val applier = X2cChangeApplierImpl()
    private var counter = 0

    private fun write(rel: String, text: String) = File(project, rel).apply { parentFile.mkdirs(); writeText(text) }
    private fun read(rel: String) = File(project, rel).readText()

    @Before fun setUp() {
        project = tmp.newFolder("proj${counter++}")
        write("app/src/main/res/layout/activity_main.xml", "<LinearLayout/>")
        write("app/src/main/java/com/x/MainActivity.kt", "class MainActivity")
        write("app/build.gradle.kts", "plugins {}")
        zip = runBlocking { X2cBackupRepositoryImpl().createBackup(project.path, includeGit = false) }
    }

    private fun apply(
        changes: List<PlannedChange>,
        cancelled: () -> Boolean = { false },
        hook: (suspend (com.codeforge.core.domain.repository.ChangeRecorder) -> Unit)? = null
    ) = runBlocking { applier.apply(project.path, zip, changes, cancelled, hook) }

    private val create = PlannedChange.Create("app/src/main/kotlin/com/x/ui/MainScreen.kt", "fun MainScreen() {}")
    private val replace = PlannedChange.Replace("app/src/main/java/com/x/MainActivity.kt", "class MainActivity2")
    private val delete = PlannedChange.Delete("app/src/main/res/layout/activity_main.xml")

    @Test fun `neu ersetzen und loeschen mit Journal`() {
        val r = apply(listOf(create, replace, delete)).getOrThrow()
        assertEquals("fun MainScreen() {}", read(create.relativePath))
        assertEquals("class MainActivity2", read(replace.relativePath))
        assertFalse(File(project, delete.relativePath).exists())
        val j = r.journal
        assertEquals(JournalStatus.COMPLETE, j.status)
        assertEquals(listOf(JournalAction.CREATED, JournalAction.MODIFIED, JournalAction.DELETED), j.entries.map { it.action })
        val c = j.entries[0]; val m = j.entries[1]; val d = j.entries[2]
        assertTrue(c.hashBefore == null && c.hashAfter!!.length == 64)
        assertTrue(m.hashBefore != m.hashAfter && m.hashBefore!!.length == 64)
        assertTrue(d.hashAfter == null && d.hashBefore != null)
        assertTrue(File(r.journalPath).name.startsWith("x2c-journal-") && r.journalPath.contains("/Backup/"))
        assertTrue(File(r.journalPath).readText().contains("\"status\": \"COMPLETE\""))
        assertEquals(listOf("app/src/main/kotlin", "app/src/main/kotlin/com", "app/src/main/kotlin/com/x", "app/src/main/kotlin/com/x/ui").first(), r.journal.createdDirs.first())
    }

    @Test fun `vorhandene neue Datei wird uebersprungen und mit overwrite ersetzt`() {
        write(create.relativePath, "alt"); zip = runBlocking { X2cBackupRepositoryImpl().createBackup(project.path, false) }
        val r = apply(listOf(create)).getOrThrow()
        assertEquals(listOf(create.relativePath), r.skippedExisting)
        assertEquals("alt", read(create.relativePath))
        val r2 = apply(listOf(create.copy(overwrite = true))).getOrThrow()
        assertEquals("fun MainScreen() {}", read(create.relativePath))
        assertEquals(JournalAction.MODIFIED, r2.journal.entries.single().action)
    }

    @Test fun `seit Backup geaenderte Datei stoppt vor jeder Aenderung`() {
        write(replace.relativePath, "vom Benutzer geaendert")
        val r = apply(listOf(create, replace))
        assertTrue(r.isFailure && "geändert" in r.exceptionOrNull()!!.message!!)
        assertFalse(File(project, create.relativePath).exists())
        assertEquals("vom Benutzer geaendert", read(replace.relativePath))
        assertTrue(File(project, "Backup").listFiles { f -> f.name.startsWith("x2c-journal") }!!.isEmpty())
    }

    @Test fun `Datei ohne Backup-Eintrag wird abgelehnt`() {
        write("app/src/main/java/com/x/Neu.kt", "x")
        assertTrue(apply(listOf(PlannedChange.Replace("app/src/main/java/com/x/Neu.kt", "y"))).isFailure)
        assertEquals("x", read("app/src/main/java/com/x/Neu.kt"))
    }

    @Test fun `Pfade ausserhalb und geschuetzte Ordner werden abgelehnt`() {
        for (p in listOf("../evil.kt", "Backup/x.kt", ".git/config", "/abs.kt", "a/../../b.kt", "a//b.kt")) {
            assertTrue(p, apply(listOf(PlannedChange.Create(p, "x"))).isFailure)
        }
        assertFalse(File(tmp.root, "evil.kt").exists())
    }

    @Test fun `fehlendes ZIP aendert nichts`() {
        zip = File(project, "Backup/gibtsnicht.zip").path
        assertTrue(apply(listOf(create)).isFailure)
        assertFalse(File(project, create.relativePath).exists())
    }

    @Test fun `Fehler im Folgeschritt nimmt alles zurueck`() {
        val r = apply(listOf(create, replace, delete)) { rec ->
            rec.touch("app/build.gradle.kts")
            write("app/build.gradle.kts", "plugins { compose }")
            error("kaputt")
        }
        assertTrue(r.isFailure && "zurückgenommen" in r.exceptionOrNull()!!.message!!)
        assertFalse(File(project, create.relativePath).exists())
        assertFalse(File(project, "app/src/main/kotlin").exists())
        assertEquals("class MainActivity", read(replace.relativePath))
        assertEquals("<LinearLayout/>", read(delete.relativePath))
        assertEquals("plugins {}", read("app/build.gradle.kts"))
        val jf = File(project, "Backup").listFiles { f -> f.name.startsWith("x2c-journal") }!!.single()
        assertTrue("ROLLED_BACK" in jf.readText())
    }

    @Test fun `Abbruch nimmt alles zurueck`() {
        var calls = 0
        val r = apply(listOf(create, replace, delete), cancelled = { ++calls > 3 })
        assertTrue(r.isFailure && "Abgebrochen" in r.exceptionOrNull()!!.message!!)
        assertFalse(File(project, create.relativePath).exists())
        assertEquals("class MainActivity", read(replace.relativePath))
        assertEquals("<LinearLayout/>", read(delete.relativePath))
    }

    @Test fun `Folgeschritt wird ins Journal aufgenommen`() {
        val r = apply(listOf(create)) { rec ->
            write("app/build.gradle.kts", "plugins { compose }"); rec.touch("app/build.gradle.kts", alreadyChanged = true)
            write("gradle/libs.versions.toml", "[versions]"); rec.touch("gradle/libs.versions.toml", alreadyChanged = true)
            File(project, delete.relativePath).delete(); rec.touch(delete.relativePath, alreadyChanged = true)
        }.getOrThrow()
        val byPath = r.journal.entries.associateBy { it.relativePath }
        assertEquals(JournalAction.MODIFIED, byPath.getValue("app/build.gradle.kts").action)
        assertEquals(JournalAction.CREATED, byPath.getValue("gradle/libs.versions.toml").action)
        assertEquals(JournalAction.DELETED, byPath.getValue(delete.relativePath).action)
    }

    @Test fun `Folgeschritt darf keine ungesicherte Datei anfassen`() {
        write("app/src/main/java/com/x/Neu.kt", "x")
        val r = apply(listOf(create)) { rec -> rec.touch("app/src/main/java/com/x/Neu.kt") }
        assertTrue(r.isFailure)
        assertFalse(File(project, create.relativePath).exists())
    }

    @Test fun `undo stellt alles wieder her und fasst fremde Dateien nicht an`() {
        val j = apply(listOf(create, replace, delete)).getOrThrow().journalPath
        write("app/src/main/java/com/x/Fremd.kt", "mein Code")
        val u = runBlocking { applier.undo(project.path, j) }.getOrThrow()
        assertEquals(3, u.restored.size + u.deleted.size)
        assertFalse(File(project, create.relativePath).exists())
        assertFalse(File(project, "app/src/main/kotlin").exists())
        assertEquals("class MainActivity", read(replace.relativePath))
        assertEquals("<LinearLayout/>", read(delete.relativePath))
        assertEquals("mein Code", read("app/src/main/java/com/x/Fremd.kt"))
        assertTrue(runBlocking { applier.undo(project.path, j) }.isFailure)
        assertTrue("UNDONE" in File(j).readText())
    }

    @Test fun `undo mit Konflikt KEEP OVERWRITE und SAVE_AS_ORIG`() {
        for ((policy, check) in listOf(
            UndoConflictPolicy.KEEP to { assertEquals("Benutzer", read(replace.relativePath)) },
            UndoConflictPolicy.OVERWRITE to { assertEquals("class MainActivity", read(replace.relativePath)) },
            UndoConflictPolicy.SAVE_AS_ORIG to {
                assertEquals("class MainActivity", read(replace.relativePath))
                assertEquals("Benutzer", read(replace.relativePath + ".orig"))
            }
        )) {
            setUp()
            val j = apply(listOf(replace)).getOrThrow().journalPath
            write(replace.relativePath, "Benutzer")
            var asked = ""
            val u = runBlocking { applier.undo(project.path, j) { asked = it; policy } }.getOrThrow()
            assertEquals(replace.relativePath, asked)
            check()
            if (policy == UndoConflictPolicy.KEEP) {
                assertEquals(listOf(replace.relativePath), u.keptByUser)
                assertFalse("UNDONE" in File(j).readText())
            }
        }
    }

    @Test fun `undo erkennt schon Originalzustand`() {
        val j = apply(listOf(replace)).getOrThrow().journalPath
        write(replace.relativePath, "class MainActivity")
        val u = runBlocking { applier.undo(project.path, j) { error("keine Frage erwartet") } }.getOrThrow()
        assertEquals(listOf(replace.relativePath), u.alreadyOriginal)
    }

    @Test fun `Journal Roundtrip mit Sonderzeichen im Pfad`() {
        val odd = PlannedChange.Create("app/src/main/kotlin/a \"b\"/Ü.kt", "x")
        val r = apply(listOf(odd)).getOrThrow()
        val back = applier.readJournal(File(r.journalPath))
        assertEquals(r.journal, back)
    }

    @Test fun `doppelt geplanter Pfad wird abgelehnt`() {
        assertTrue(apply(listOf(create, create)).isFailure)
    }

    @Test fun `zweites Anwenden erzeugt eigenes Journal ohne Ueberschreiben`() {
        val a = apply(listOf(create)).getOrThrow().journalPath
        zip = runBlocking { X2cBackupRepositoryImpl().createBackup(project.path, false) }
        val b = apply(listOf(PlannedChange.Create("x/Y.kt", "y"))).getOrThrow().journalPath
        assertTrue(a != b && File(a).exists() && File(b).exists())
    }

    @Test fun `MiniJson parst und schreibt streng`() {
        val v = MiniJson.parse("""{"a":[1,2.5,"x\nä",null,true],"b":{}}""")
        assertEquals(v, MiniJson.parse(MiniJson.write(v)))
        for (bad in listOf("{", "[1,]", "{\"a\" 1}", "tru", "\"x", "{} x")) assertTrue(bad, runCatching { MiniJson.parse(bad) }.isFailure)
    }
}
