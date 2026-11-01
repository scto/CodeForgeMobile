package com.codeforge.libs.terminal_engine

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MotdInstallTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun writesMotdAndReplacesExistingTermuxMotdScript() {
        val prefix = tmp.newFolder("usr")
        File(prefix, "etc/termux").mkdirs()
        val termuxScript = File(prefix, "etc/termux/motd.sh").apply { writeText("echo Welcome to Termux!") }

        ScriptInstaller.writeMotd(prefix, "CodeForgeMobile\n")

        assertEquals("CodeForgeMobile\n", File(prefix, "etc/motd").readText())
        assertTrue(termuxScript.readText().contains("etc/motd"))
        assertFalse(termuxScript.readText().contains("Termux"))
        // zweiter Aufruf ändert nichts
        ScriptInstaller.writeMotd(prefix, "CodeForgeMobile\n")
        assertFalse(File(prefix, "etc/motd.sh").exists())
    }

    @Test
    fun motdAssetNamesOnlyExistingCodeforgeEnvCommands() {
        val motd = File("src/main/assets/codeforge-motd").readText()
        val script = File("src/main/assets/codeforge-env").readText()
        val commands = Regex("codeforge-env ([a-z]+)").findAll(motd).map { it.groupValues[1] }.toSet()
        assertTrue(commands.isNotEmpty())
        for (c in commands) assertTrue("Befehl '$c' fehlt in codeforge-env", script.contains("\n  $c)") || script.contains("  $c|") || script.contains("|$c)") || script.contains("|$c|"))
        assertFalse(motd.contains("pkg update") || motd.contains("pkg upgrade"))
    }
}
