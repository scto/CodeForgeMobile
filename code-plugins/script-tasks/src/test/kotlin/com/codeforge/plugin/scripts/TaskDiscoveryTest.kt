package com.codeforge.plugin.scripts

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskDiscoveryTest {
    private val root: File = Files.createTempDirectory("tasks").toFile()

    @Test
    fun `npm-Skripte mit pnpm`() {
        File(root, "package.json").writeText(
            """{"name":"x","scripts":{"dev":"vite","test":"vitest {run}","build:prod":"tsc && vite build"},"dependencies":{"a":"1"}}"""
        )
        File(root, "pnpm-lock.yaml").writeText("")
        val tasks = TaskDiscovery.npm(root)
        assertEquals(listOf("pnpm run dev", "pnpm run test", "pnpm run build:prod"), tasks.map { it.title })
        assertTrue(tasks[1].isTest)
    }

    @Test
    fun `Makefile-Ziele ohne Variablen und Muster`() {
        File(root, "Makefile").writeText("CC := gcc\nall: main\n.PHONY: test\ntest:\n\t./run\n%.o: %.c\nx ::= y\nclean :\n\trm -f *.o\n")
        assertEquals(listOf("all", "test", "clean"), TaskDiscovery.make(root).map { it.id.removePrefix("make:") })
    }

    @Test
    fun `Cargo, Go und just`() {
        File(root, "Cargo.toml").writeText("[package]")
        File(root, "go.mod").writeText("module x")
        File(root, "justfile").writeText("set shell := [\"bash\"]\n# Kommentar\nbuild:\n  cargo build\n@test arg='x':\n  cargo test\n")
        val all = TaskDiscovery.discover(root).map { it.id }
        assertTrue(all.containsAll(listOf("cargo:test", "go:test", "just:build", "just:test")))
    }

    @Test
    fun `Shell-Quoting`() {
        assertEquals("build:prod", TaskDiscovery.shellQuote("build:prod"))
        assertEquals("'a b'", TaskDiscovery.shellQuote("a b"))
        assertEquals("'it'\\''s'", TaskDiscovery.shellQuote("it's"))
    }
}
