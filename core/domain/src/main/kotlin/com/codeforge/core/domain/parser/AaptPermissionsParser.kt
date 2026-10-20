// Modul: :core:domain
package com.codeforge.core.domain.parser

import com.codeforge.core.domain.analyzer.AndroidDangerousPermissions
import com.codeforge.core.domain.model.ApkPermission

/**
 * Parser für die Ausgabe von `aapt dump permissions <apk>` (35.2 & 35.5).
 */
object AaptPermissionsParser {

    private val PACKAGE_REGEX = Regex("""^package:\s*(\S+)""")
    private val USES_PERMISSION_REGEX = Regex("""uses-permission:\s*(?:name=)?['"]?([^'"\s]+)['"]?(?:\s+maxSdkVersion=['"]?(\d+)['"]?)?""")
    private val DECLARED_PERMISSION_REGEX = Regex("""permission:\s*(?:name=)?['"]?([^'"\s]+)['"]?(?:\s+protectionLevel=['"]?([^'"\s]+)['"]?)?""")

    data class ParseResult(
        val packageName: String?,
        val permissions: List<ApkPermission>
    )

    fun parse(output: String): ParseResult {
        var packageName: String? = null
        val permissions = mutableListOf<ApkPermission>()
        val seenNames = mutableSetOf<String>()

        for (line in output.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            PACKAGE_REGEX.find(trimmed)?.let { match ->
                packageName = match.groupValues[1]
                return@let
            }

            USES_PERMISSION_REGEX.find(trimmed)?.let { match ->
                val name = match.groupValues[1]
                val maxSdk = match.groupValues.getOrNull(2)?.toIntOrNull()
                if (name.isNotEmpty() && seenNames.add(name)) {
                    permissions.add(
                        ApkPermission(
                            name = name,
                            isDangerous = AndroidDangerousPermissions.isDangerous(name),
                            maxSdkVersion = maxSdk,
                            protectionLevel = null
                        )
                    )
                }
                return@let
            }

            DECLARED_PERMISSION_REGEX.find(trimmed)?.let { match ->
                val name = match.groupValues[1]
                val protectionLevel = match.groupValues.getOrNull(2)?.ifBlank { null }
                val isDangerous = protectionLevel?.contains("dangerous", ignoreCase = true) == true ||
                    AndroidDangerousPermissions.isDangerous(name)
                if (name.isNotEmpty() && seenNames.add(name)) {
                    permissions.add(
                        ApkPermission(
                            name = name,
                            isDangerous = isDangerous,
                            maxSdkVersion = null,
                            protectionLevel = protectionLevel
                        )
                    )
                }
                return@let
            }
        }

        return ParseResult(packageName = packageName, permissions = permissions)
    }
}
