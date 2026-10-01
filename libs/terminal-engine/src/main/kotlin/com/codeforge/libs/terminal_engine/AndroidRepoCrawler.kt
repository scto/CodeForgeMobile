// Modul: :libs:terminal-engine
package com.codeforge.libs.terminal_engine

import com.codeforge.core.domain.model.ToolItem

object AndroidRepoCrawler {
    fun fetchPackages(platform: String = "linux"): List<ToolItem> {
        return listOf(
            ToolItem(
                id = "build-tools;36.0.0",
                version = "36.0.0",
                description = "Android SDK Build-Tools 36.0.0",
                isInstalled = false
            ),
            ToolItem(
                id = "build-tools;35.0.0",
                version = "35.0.0",
                description = "Android SDK Build-Tools 35.0.0",
                isInstalled = false
            ),
            ToolItem(
                id = "build-tools;34.0.0",
                version = "34.0.0",
                description = "Android SDK Build-Tools 34.0.0",
                isInstalled = false
            ),
            ToolItem(
                id = "platform-tools",
                version = "35.0.2",
                description = "Android SDK Platform-Tools 35.0.2",
                isInstalled = false
            ),
            ToolItem(
                id = "platforms;android-35",
                version = "35",
                description = "Android SDK Platform 35 (API 35)",
                isInstalled = false
            ),
            ToolItem(
                id = "platforms;android-34",
                version = "34",
                description = "Android SDK Platform 34 (API 34)",
                isInstalled = false
            ),
            ToolItem(
                id = "ndk;27.0.12077973",
                version = "27.0.12077973",
                description = "Android NDK 27.0.12077973",
                isInstalled = false
            ),
            ToolItem(
                id = "cmake;3.22.1",
                version = "3.22.1",
                description = "CMake 3.22.1",
                isInstalled = false
            )
        )
    }
}
