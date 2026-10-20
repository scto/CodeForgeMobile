// Modul: :core:domain
package com.codeforge.core.domain.parser

import com.codeforge.core.domain.analyzer.AndroidDangerousPermissions
import com.codeforge.core.domain.model.ApkComponentCounts
import com.codeforge.core.domain.model.ApkDetailedMetadata
import com.codeforge.core.domain.model.ApkPermission

/**
 * Parser für die Ausgabe von `aapt dump badging <apk>` (35.2).
 */
object AaptBadgingParser {

    private val PACKAGE_NAME_REGEX = Regex("""package:.*?\bname='([^']+)'""")
    private val VERSION_CODE_REGEX = Regex("""\bversionCode='([^']+)'""")
    private val VERSION_NAME_REGEX = Regex("""\bversionName='([^']+)'""")
    private val COMPILE_SDK_REGEX = Regex("""\bcompileSdkVersion='([^']+)'""")
    private val MIN_SDK_REGEX = Regex("""sdkVersion:'([^']+)'""")
    private val TARGET_SDK_REGEX = Regex("""targetSdkVersion:'([^']+)'""")
    private val USES_PERMISSION_REGEX = Regex("""uses-permission:\s*(?:name=)?'([^']+)'(?:\s+maxSdkVersion='(\d+)')?""")
    private val APPLICATION_REGEX = Regex("""^application:.*""")
    private val ACTIVITY_REGEX = Regex("""^activity:\s*name='([^']+)'""")
    private val LAUNCHABLE_ACTIVITY_REGEX = Regex("""^launchable-activity:\s*name='([^']+)'""")
    private val SERVICE_REGEX = Regex("""^service:\s*name='([^']+)'""")
    private val RECEIVER_REGEX = Regex("""^receiver:\s*name='([^']+)'""")
    private val PROVIDER_REGEX = Regex("""^provider:\s*name='([^']+)'""")

    fun parse(output: String): ApkDetailedMetadata {
        var packageName: String? = null
        var versionCode: Long? = null
        var versionName: String? = null
        var compileSdk: Int? = null
        var minSdk: Int? = null
        var targetSdk: Int? = null
        var isDebuggable: Boolean? = null
        var allowBackup: Boolean? = null

        val permissions = mutableListOf<ApkPermission>()
        val seenPerms = mutableSetOf<String>()

        var activitiesCount = 0
        var exportedActivitiesCount = 0
        var servicesCount = 0
        var exportedServicesCount = 0
        var receiversCount = 0
        var exportedReceiversCount = 0
        var providersCount = 0
        var exportedProvidersCount = 0

        val warnings = mutableListOf<String>()

        for (line in output.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            if (trimmed.startsWith("package:")) {
                PACKAGE_NAME_REGEX.find(trimmed)?.let { packageName = it.groupValues[1] }
                VERSION_CODE_REGEX.find(trimmed)?.let { versionCode = it.groupValues[1].toLongOrNull() }
                VERSION_NAME_REGEX.find(trimmed)?.let { versionName = it.groupValues[1] }
                COMPILE_SDK_REGEX.find(trimmed)?.let { compileSdk = it.groupValues[1].toIntOrNull() }
            } else if (trimmed.startsWith("sdkVersion:")) {
                MIN_SDK_REGEX.find(trimmed)?.let { minSdk = it.groupValues[1].toIntOrNull() }
            } else if (trimmed.startsWith("targetSdkVersion:")) {
                TARGET_SDK_REGEX.find(trimmed)?.let { targetSdk = it.groupValues[1].toIntOrNull() }
            } else if (trimmed.startsWith("uses-permission:")) {
                USES_PERMISSION_REGEX.find(trimmed)?.let { match ->
                    val name = match.groupValues[1]
                    val maxSdk = match.groupValues.getOrNull(2)?.toIntOrNull()
                    if (seenPerms.add(name)) {
                        permissions.add(
                            ApkPermission(
                                name = name,
                                isDangerous = AndroidDangerousPermissions.isDangerous(name),
                                maxSdkVersion = maxSdk
                            )
                        )
                    }
                }
            } else if (trimmed.startsWith("application:")) {
                if (trimmed.contains("allowBackup='true'")) {
                    allowBackup = true
                } else if (trimmed.contains("allowBackup='false'")) {
                    allowBackup = false
                }
                if (trimmed.contains("debuggable='true'")) {
                    isDebuggable = true
                }
            } else if (trimmed == "application-debuggable") {
                isDebuggable = true
            } else if (trimmed.startsWith("launchable-activity:")) {
                activitiesCount++
                exportedActivitiesCount++
            } else if (trimmed.startsWith("activity:")) {
                activitiesCount++
                if (trimmed.contains("exported='true'")) {
                    exportedActivitiesCount++
                }
            } else if (trimmed.startsWith("service:")) {
                servicesCount++
                if (trimmed.contains("exported='true'")) {
                    exportedServicesCount++
                }
            } else if (trimmed.startsWith("receiver:")) {
                receiversCount++
                if (trimmed.contains("exported='true'")) {
                    exportedReceiversCount++
                }
            } else if (trimmed.startsWith("provider:")) {
                providersCount++
                if (trimmed.contains("exported='true'")) {
                    exportedProvidersCount++
                }
            }
        }

        if (isDebuggable == true) {
            warnings.add("Warnung: APK ist im Debug-Modus kompiliert (FLAG_DEBUGGABLE).")
        }
        if (allowBackup == true) {
            warnings.add("Warnung: Anwendungs-Backup ist erlaubt (android:allowBackup=\"true\").")
        }

        return ApkDetailedMetadata(
            packageName = packageName,
            versionName = versionName,
            versionCode = versionCode,
            minSdk = minSdk,
            targetSdk = targetSdk,
            compileSdk = compileSdk,
            permissions = permissions,
            components = ApkComponentCounts(
                activitiesCount = activitiesCount,
                exportedActivitiesCount = exportedActivitiesCount,
                servicesCount = servicesCount,
                exportedServicesCount = exportedServicesCount,
                receiversCount = receiversCount,
                exportedReceiversCount = exportedReceiversCount,
                providersCount = providersCount,
                exportedProvidersCount = exportedProvidersCount
            ),
            isDebuggable = isDebuggable,
            allowBackup = allowBackup,
            signatureResult = null,
            hasApksigner = false,
            warnings = warnings
        )
    }
}
