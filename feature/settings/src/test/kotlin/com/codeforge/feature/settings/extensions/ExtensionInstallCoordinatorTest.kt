// Modul: :feature:settings
package com.codeforge.feature.settings.extensions

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ExtensionInstallCoordinatorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @After
    fun tearDown() = scope.cancel()

    private fun zip(): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            z.putNextEntry(ZipEntry("bin/test-server"))
            z.write("#!/bin/sh".toByteArray())
            z.closeEntry()
        }
        return out.toByteArray()
    }

    private fun pkg(bytes: ByteArray) = LspExtensionPackage(
        id = "coord-test",
        nameRes = 0,
        descriptionRes = 0,
        version = "1",
        language = "Test",
        downloadUrl = "https://example.com/x.zip",
        expectedSha256 = Sha256Utils.calculateSha256(bytes),
        archiveType = ExtensionArchiveType.ZIP,
        executableRelativePath = "bin/test-server"
    )

    private fun coordinator(
        downloader: FileDownloader,
        starts: AtomicInteger
    ): ExtensionInstallCoordinator {
        val manager = ExtensionsManager(baseDir = tempFolder.newFolder(), downloader = downloader)
        return ExtensionInstallCoordinator(manager, scope) { starts.incrementAndGet() }
    }

    @Test
    fun `install runs independently of the caller, reports the outcome and starts the service once`() = runBlocking {
        val bytes = zip()
        val gate = CompletableDeferred<Unit>()
        val starts = AtomicInteger()
        val c = coordinator({ _, dest, onProgress ->
            gate.await()
            dest.parentFile?.mkdirs()
            dest.writeBytes(bytes)
            onProgress(bytes.size.toLong(), bytes.size.toLong())
            Result.success(dest)
        }, starts)
        val p = pkg(bytes)

        val outcome = async(start = CoroutineStart.UNDISPATCHED) { c.outcomes.first() }
        c.install(p)
        c.install(p) // zweiter Klick während des Laufs: ignoriert

        assertTrue(c.active.value.containsKey(p.id))
        assertEquals(1, starts.get())

        gate.complete(Unit)
        val result = withTimeout(5_000) { outcome.await() }

        assertTrue(result.result.isSuccess)
        assertFalse(c.active.value.containsKey(p.id))
    }

    @Test
    fun `cancelAll stops running installs without an outcome`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val c = coordinator({ _, _, _ ->
            started.complete(Unit)
            CompletableDeferred<Unit>().await() // hängt, bis abgebrochen
            Result.failure(IllegalStateException("unreachable"))
        }, AtomicInteger())
        val p = pkg(zip())

        c.install(p)
        withTimeout(5_000) { started.await() }
        assertTrue(c.active.value.containsKey(p.id))

        c.cancelAll()
        assertTrue(c.active.value.isEmpty())

        // Nach dem Abbruch ist eine neue Installation wieder möglich
        c.install(p)
        assertTrue(c.active.value.containsKey(p.id))
        c.cancelAll()
    }
}
