// Modul: :libs:terminal-engine
package com.codeforge.libs.terminal_engine

import java.io.InputStream
import java.io.OutputStream

interface ProotProcessHandle {
    val stdIn: OutputStream
    val stdOut: InputStream
    fun destroy()
}

interface ProotExecutor {
    suspend fun startProcess(command: List<String>, workingDirectory: String): ProotProcessHandle
}
