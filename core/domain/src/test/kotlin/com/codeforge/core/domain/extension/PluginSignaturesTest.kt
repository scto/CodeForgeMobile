package com.codeforge.core.domain.extension

import java.io.File
import java.nio.file.Files
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginSignaturesTest {

    private fun keys(): KeyPair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()

    private fun plugin(): File = Files.createTempDirectory("sig").toFile().apply {
        File(this, "plugin.json").writeText("""{"id":"a.b","name":"A","version":"1"}""")
        File(this, "lib/x.txt").apply { parentFile.mkdirs(); writeText("x") }
    }

    private fun sign(dir: File, kp: KeyPair) {
        val sig = Signature.getInstance("SHA256withECDSA").run {
            initSign(kp.private); update(PluginSignatures.manifestOf(dir).toByteArray()); sign()
        }
        val b64 = Base64.getEncoder()
        File(dir, PluginSignatures.FILE_NAME).writeText(
            """{"algorithm":"SHA256withECDSA","publicKey":"${b64.encodeToString(kp.public.encoded)}","signature":"${b64.encodeToString(sig)}"}"""
        )
    }

    @Test
    fun `unsigniert und gültig signiert`() {
        val dir = plugin()
        assertEquals(PluginSignatures.Result.Unsigned, PluginSignatures.verify(dir))
        sign(dir, keys())
        val r = PluginSignatures.verify(dir)
        assertTrue(r is PluginSignatures.Result.Signed)
        assertEquals(64, (r as PluginSignatures.Result.Signed).fingerprint.length)
    }

    @Test(expected = SecurityException::class)
    fun `veränderte Datei wird erkannt`() {
        val dir = plugin()
        sign(dir, keys())
        File(dir, "lib/x.txt").writeText("manipuliert")
        PluginSignatures.verify(dir)
    }

    @Test(expected = SecurityException::class)
    fun `zusätzliche Datei wird erkannt`() {
        val dir = plugin()
        sign(dir, keys())
        File(dir, "classes.dex").writeText("dex")
        PluginSignatures.verify(dir)
    }

    @Test
    fun `Update-Regel (Vertrauen beim ersten Mal)`() {
        val a = PluginSignatures.Result.Signed("aa")
        assertNull(PluginSignatures.updateViolation("", PluginSignatures.Result.Unsigned))
        assertNull(PluginSignatures.updateViolation("", a))
        assertNull(PluginSignatures.updateViolation("aa", a))
        assertNotNull(PluginSignatures.updateViolation("aa", PluginSignatures.Result.Unsigned))
        assertNotNull(PluginSignatures.updateViolation("aa", PluginSignatures.Result.Signed("bb")))
        assertEquals("AB:CD:EF:01:23:45:67:89", PluginSignatures.shortFingerprint("abcdef0123456789ffff"))
    }

    /** Gegenprobe mit dem Signier-Skript (python3 + openssl), falls vorhanden. */
    @Test
    fun `scripts plugin-sign py erzeugt gültige Signaturen`() {
        val script = generateSequence(File("").absoluteFile) { it.parentFile }.map { File(it, "scripts/plugin-sign.py") }.firstOrNull { it.isFile }
        fun ok(vararg cmd: String) = runCatching { ProcessBuilder(*cmd).redirectErrorStream(true).start().waitFor() == 0 }.getOrDefault(false)
        org.junit.Assume.assumeTrue(script != null && ok("openssl", "version") && ok("python3", "--version"))
        val key = File.createTempFile("cfkey", ".pem").apply { delete() }
        assertTrue(ok("python3", script!!.path, "--new-key", key.path))
        val dir = plugin()
        assertTrue(ok("python3", script.path, dir.path, key.path))
        assertTrue(PluginSignatures.verify(dir) is PluginSignatures.Result.Signed)
    }

    @Test
    fun `Herkunftsangabe`() {
        val f = File.createTempFile("buildinfo", ".json")
        f.writeText("""{"commit":"a1b2c3d","dirty":true,"builtAt":"2026-11-20T10:00:00Z","builder":"gradle","apiVersion":4}""")
        assertEquals("a1b2c3d+dirty · 2026-11-20 · gradle", PluginBuildInfo.summary(f))
        f.writeText("kein json")
        assertEquals("", PluginBuildInfo.summary(f))
    }
}
