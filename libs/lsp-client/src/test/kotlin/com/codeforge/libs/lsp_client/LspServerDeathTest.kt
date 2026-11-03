package com.codeforge.libs.lsp_client

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.OutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Test

/** Regression: Ein beendeter Serverprozess ("Stream closed") darf per Eingabe im Editor keinen Absturz auslösen. */
class LspServerDeathTest {

    private class ClosedOutput : OutputStream() {
        override fun write(b: Int) = throw IOException("Stream closed")
        override fun write(b: ByteArray, off: Int, len: Int) = throw IOException("Stream closed")
    }

    @Test
    fun `didOpen didChange didClose werfen nicht bei geschlossenem Server-Eingabestrom`() = runBlocking {
        val repository = LspClientRepositoryImpl()
        repository.attachConnection(LspRpcConnection(output = ClosedOutput(), input = ByteArrayInputStream(ByteArray(0))))

        repository.didOpen("/p/Main.kt", "kotlin", "fun main() {}")
        repository.didChange("/p/Main.kt", "fun main() { }", 2)
        repository.didChange("/p/Main.kt", "fun main() { x }", 3)
        repository.didClose("/p/Main.kt")
    }
}
