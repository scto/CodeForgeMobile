// Modul: :libs:terminal-engine
package com.codeforge.libs.terminal_engine

data class JdkSource(val version: String, val url: String)

object JdkCatalog {
    val all = listOf(
        JdkSource("17", "https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.10%2B7/OpenJDK17U-jdk_aarch64_linux_hotspot_17.0.10_7.tar.gz"),
        JdkSource("21", "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.2%2B13/OpenJDK21U-jdk_aarch64_linux_hotspot_21.0.2_13.tar.gz")
    )
}
