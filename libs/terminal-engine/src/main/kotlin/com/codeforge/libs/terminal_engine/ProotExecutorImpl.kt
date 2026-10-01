// Modul: :libs:terminal-engine
package com.codeforge.libs.terminal_engine

import android.content.Context
import com.codeforge.core.domain.repository.SystemPathsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProotExecutorImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val systemPaths: SystemPathsRepository
) : ProotExecutor {

    override suspend fun startProcess(command: List<String>, workingDirectory: String): ProotProcessHandle =
        withContext(Dispatchers.IO) {
            val rootfsDir = File(systemPaths.getDistroDir("ubuntu"))
            val prootBinaryPath = "${context.applicationInfo.nativeLibraryDir}/libproot.so"
            val prootCmd = ProotCommandBuilder.build(
                prootBinaryPath = prootBinaryPath,
                rootfsDir = rootfsDir,
                workingDirectory = workingDirectory,
                command = command
            )

            val pb = ProcessBuilder(prootCmd)
            pb.environment()["LD_LIBRARY_PATH"] = context.applicationInfo.nativeLibraryDir
            pb.environment()["TMPDIR"] = systemPaths.getLocalTmpDir()
            pb.environment()["PROOT_TMP_DIR"] = systemPaths.getLocalTmpDir()

            val process = pb.start()

            object : ProotProcessHandle {
                override val stdIn: OutputStream = process.outputStream
                override val stdOut: InputStream = process.inputStream
                override fun destroy() {
                    process.destroy()
                }
            }
        }
}
