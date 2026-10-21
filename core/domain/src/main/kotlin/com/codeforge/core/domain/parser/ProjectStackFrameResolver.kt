// Modul: :core:domain
package com.codeforge.core.domain.parser

import com.codeforge.core.domain.model.StackFrame
import java.io.File

object ProjectStackFrameResolver {

    /**
     * Löst einen [StackFrame] zu einer existierenden Projektdatei auf.
     * Prüft zielgerichtet Test- und Quellordner basierend auf dem Paketpfad,
     * gefolgt von einer Top-Down-Suche im Projektordner.
     *
     * @return Gefundene [File] bei Treffer, oder `null` bei Nicht-Treffer (z. B. JDK/Android-Framework-Zeilen).
     */
    fun resolve(projectRoot: File, frame: StackFrame): File? {
        if (!projectRoot.exists() || !projectRoot.isDirectory) return null
        if (frame.fileName.isBlank() || frame.fileName == "Native Method" || frame.fileName == "Unknown Source") {
            return null
        }

        val baseClass = frame.className.substringBefore('$')
        val pkgPath = if (baseClass.contains('.')) {
            baseClass.substringBeforeLast('.').replace('.', '/')
        } else {
            ""
        }

        // 1. Gezielte Suche nach Modulen mit passendem Paketpfad
        if (pkgPath.isNotEmpty()) {
            val sourceRoots = listOf(
                "src/test/kotlin",
                "src/test/java",
                "src/main/kotlin",
                "src/main/java",
                "app/src/test/kotlin",
                "app/src/test/java",
                "app/src/main/kotlin",
                "app/src/main/java"
            )

            for (sub in sourceRoots) {
                val candidate = File(projectRoot, "$sub/$pkgPath/${frame.fileName}")
                if (candidate.isFile) return candidate
            }

            // Suche in Submodulen (z. B. core/domain/src/test/kotlin/$pkgPath/...)
            val subModuleDirs = projectRoot.listFiles { f -> f.isDirectory && !f.name.startsWith(".") && f.name != "build" }
            if (subModuleDirs != null) {
                for (moduleDir in subModuleDirs) {
                    for (sub in listOf("src/test/kotlin", "src/test/java", "src/main/kotlin", "src/main/java")) {
                        val candidate = File(moduleDir, "$sub/$pkgPath/${frame.fileName}")
                        if (candidate.isFile) return candidate
                    }
                    // Falls zwei Ebenen (z. B. core/domain)
                    val nestedDirs = moduleDir.listFiles { f -> f.isDirectory && !f.name.startsWith(".") && f.name != "build" }
                    if (nestedDirs != null) {
                        for (nested in nestedDirs) {
                            for (sub in listOf("src/test/kotlin", "src/test/java", "src/main/kotlin", "src/main/java")) {
                                val candidate = File(nested, "$sub/$pkgPath/${frame.fileName}")
                                if (candidate.isFile) return candidate
                            }
                        }
                    }
                }
            }
        }

        // 2. Fallback: Datei-TopDown-Suche, ignoriert build- und .git-Verzeichnisse
        return projectRoot.walkTopDown()
            .onEnter { dir ->
                val name = dir.name
                name != "build" && name != ".git" && name != ".gradle" && name != ".idea"
            }
            .filter { it.isFile && it.name == frame.fileName }
            .firstOrNull()
    }
}
