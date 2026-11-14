package com.codeforge.core.domain.extension

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadRequirementTest {

    private val sha = "c".repeat(64)

    private fun manifest(requires: String, servers: String = "[]") = PluginManifestParser.parse(
        """{"id":"p.lsp","name":"L","version":"1","apiVersion":2,""" +
            """"contributes":{"lspServers":$servers,"requires":$requires}}"""
    )

    private fun download(url: String = "https://github.com/a/b/releases/download/1/server.zip", extra: String = "") =
        """[{"manager":"download","name":"kls","url":"$url","sha256":"$sha","archive":"zip",""" +
            """"check":"server/bin/kls"$extra}]"""

    @Test
    fun downloadIsParsed() {
        val r = manifest(download()).getOrThrow().contributes.requires.single()
        assertEquals(RequirementManager.DOWNLOAD, r.manager)
        assertEquals("server/bin/kls", r.check)
        assertEquals(DownloadArchive.ZIP, r.archive)
        assertEquals(sha, r.sha256)
    }

    @Test
    fun invalidDownloadIsRejected() {
        assertTrue(manifest(download(url = "http://github.com/x.zip")).isFailure)
        assertTrue(manifest(download().replace(sha, "abc")).isFailure)
        assertTrue(manifest(download().replace("server/bin/kls", "../kls")).isFailure)
        assertTrue(manifest(download().replace("\"zip\"", "\"rar\"")).isFailure)
    }

    @Test
    fun storeAllowsKnownHostsOnly() {
        val github = manifest(download()).getOrThrow().contributes.requires.single()
        val other = manifest(download(url = "https://example.com/x.zip")).getOrThrow().contributes.requires.single()
        assertTrue(RequirementPolicy.isStoreAllowed(github))
        assertFalse(RequirementPolicy.isStoreAllowed(other))
    }

    @Test
    fun placeholdersAndJvmArgs() {
        val server = manifest(
            "[]",
            """[{"id":"jdtls","languages":["java"],"command":["python3","${'$'}{deps}/jdtls/bin/jdtls","--jvm-arg=${'$'}{jvmArg}"],""" +
                """"env":{"OPTS":"${'$'}{jvmArgs}","HOME_DIR":"${'$'}{home}"}}]"""
        ).getOrThrow().contributes.lspServers.single()
        assertTrue(server.acceptsJvmOptions)
        val (cmd, env) = server.expand("/p", "/h", "/d", listOf("-Xmx1g", "-XX:+UseSerialGC"))
        assertEquals(listOf("python3", "/d/jdtls/bin/jdtls", "--jvm-arg=-Xmx1g", "--jvm-arg=-XX:+UseSerialGC"), cmd)
        assertEquals("-Xmx1g -XX:+UseSerialGC", env["OPTS"])
        assertEquals("/h", env["HOME_DIR"])
        val (bare, bareEnv) = server.expand("/p", "/h", "/d", emptyList())
        assertEquals(listOf("python3", "/d/jdtls/bin/jdtls"), bare)
        assertEquals("", bareEnv["OPTS"])
    }

    @Test
    fun noJvmPlaceholderNoJvm() {
        val server = manifest("[]", """[{"id":"clangd","languages":["c"],"command":["clangd"]}]""")
            .getOrThrow()
            .contributes
            .lspServers
            .single()
        assertFalse(server.acceptsJvmOptions)
        assertNull(server.env["X"])
    }
}
