// Modul: :feature:build
package com.codeforge.feature.build.analyzer

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.codeforge.core.domain.analyzer.AndroidDangerousPermissions
import com.codeforge.core.domain.analyzer.ApkComparator
import com.codeforge.core.domain.analyzer.ApkZipAnalyzer
import com.codeforge.core.domain.model.ApkAnalysisResult
import com.codeforge.core.domain.model.ApkComparisonResult
import com.codeforge.core.domain.model.ApkComponentCounts
import com.codeforge.core.domain.model.ApkDetailedMetadata
import com.codeforge.core.domain.model.ApkPermission
import com.codeforge.core.domain.parser.AaptBadgingParser
import com.codeforge.core.domain.parser.AaptPermissionsParser
import com.codeforge.core.domain.parser.ApksignerOutputParser
import com.codeforge.core.domain.parser.AxmlManifestParser
import com.codeforge.core.domain.repository.ApkAnalyzerRepository
import com.codeforge.libs.terminal_engine.TermuxEnvironment
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApkAnalyzerRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : ApkAnalyzerRepository {

    // Testbarer Prozess-Starter
    internal var processLauncher: (command: List<String>, env: Map<String, String>, workingDir: File) -> Process = { cmd, env, dir ->
        ProcessBuilder(cmd).apply {
            directory(dir)
            environment().clear()
            environment().putAll(env)
            redirectErrorStream(true)
        }.start()
    }

    override suspend fun analyzeApk(apkFile: File): Result<ApkAnalysisResult> = withContext(Dispatchers.IO) {
        runCatching {
            val metadata = extractMetadata(apkFile)
            val zipResult = ApkZipAnalyzer.analyze(apkFile, metadata = metadata)

            // Falls apksigner Warnungen oder Multi-ABI Warnungen vorliegen, zusammenführen
            val combinedWarnings = mutableListOf<String>()
            combinedWarnings.addAll(zipResult.analysisWarnings)
            metadata?.warnings?.let { combinedWarnings.addAll(it) }

            zipResult.copy(
                metadata = metadata,
                analysisWarnings = combinedWarnings.distinct()
            )
        }
    }

    override suspend fun compareApks(baseFile: File, comparedFile: File): Result<ApkComparisonResult> = withContext(Dispatchers.IO) {
        runCatching {
            val baseResult = analyzeApk(baseFile).getOrThrow()
            val comparedResult = analyzeApk(comparedFile).getOrThrow()
            ApkComparator.compare(baseResult, comparedResult)
        }
    }

    private fun extractMetadata(apkFile: File): ApkDetailedMetadata? {
        // 1. Versuche aapt / aapt2 falls vorhanden
        val aaptBinary = resolveAaptBinary()
        var aaptMetadata: ApkDetailedMetadata? = null

        if (aaptBinary != null) {
            val badgingRun = runCommand(listOf(aaptBinary.absolutePath, "dump", "badging", apkFile.absolutePath), 10)
            if (badgingRun.exitCode == 0 && badgingRun.output.isNotBlank()) {
                val parsed = AaptBadgingParser.parse(badgingRun.output)

                // Optional Berechtigungen vertiefen per `aapt dump permissions`
                val permRun = runCommand(listOf(aaptBinary.absolutePath, "dump", "permissions", apkFile.absolutePath), 10)
                val perms = if (permRun.exitCode == 0 && permRun.output.isNotBlank()) {
                    val permParsed = AaptPermissionsParser.parse(permRun.output)
                    if (permParsed.permissions.isNotEmpty()) permParsed.permissions else parsed.permissions
                } else {
                    parsed.permissions
                }

                aaptMetadata = parsed.copy(permissions = perms)
            }
        }

        // 2. Falls aapt nicht verfügbar oder unvollständig: PackageManager
        val pmMetadata = if (aaptMetadata?.packageName == null) {
            extractFromPackageManager(apkFile)
        } else null

        // 3. Fallback: AXML Manifest direkt aus ZIP lesen
        val axmlMetadata = if (aaptMetadata?.packageName == null && pmMetadata?.packageName == null) {
            extractFromAxml(apkFile)
        } else null

        val baseMeta = aaptMetadata ?: pmMetadata ?: axmlMetadata ?: ApkDetailedMetadata()

        // 4. Signatur-Prüfung per apksigner (falls vorhanden) (35.2)
        val apksignerBinary = resolveApksignerBinary()
        return if (apksignerBinary != null) {
            val sigRun = runCommand(
                listOf(apksignerBinary.absolutePath, "verify", "--verbose", "--print-certs", apkFile.absolutePath),
                15
            )
            val verificationResult = ApksignerOutputParser.parse(sigRun.output)
            baseMeta.copy(
                hasApksigner = true,
                signatureResult = verificationResult
            )
        } else {
            baseMeta.copy(
                hasApksigner = false,
                signatureResult = null
            )
        }
    }

    private fun extractFromPackageManager(apkFile: File): ApkDetailedMetadata? {
        return try {
            val pm = context.packageManager ?: return null
            val flags = PackageManager.GET_PERMISSIONS or
                PackageManager.GET_ACTIVITIES or
                PackageManager.GET_SERVICES or
                PackageManager.GET_RECEIVERS or
                PackageManager.GET_PROVIDERS

            @Suppress("DEPRECATION")
            val pkgInfo: PackageInfo = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                pm.getPackageArchiveInfo(apkFile.absolutePath, flags)
            }) ?: return null

            val appInfo = pkgInfo.applicationInfo
            val isDebuggable = if (appInfo != null) (appInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0 else null
            val allowBackup = if (appInfo != null) (appInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP) != 0 else null

            val perms = pkgInfo.requestedPermissions?.map { perm ->
                ApkPermission(
                    name = perm,
                    isDangerous = AndroidDangerousPermissions.isDangerous(perm)
                )
            } ?: emptyList()

            val activities = pkgInfo.activities ?: emptyArray()
            val services = pkgInfo.services ?: emptyArray()
            val receivers = pkgInfo.receivers ?: emptyArray()
            val providers = pkgInfo.providers ?: emptyArray()

            val warnings = mutableListOf<String>()
            if (isDebuggable == true) {
                warnings.add("Warnung: APK ist im Debug-Modus kompiliert (FLAG_DEBUGGABLE).")
            }
            if (allowBackup == true) {
                warnings.add("Warnung: Anwendungs-Backup ist erlaubt (android:allowBackup=\"true\").")
            }

            @Suppress("DEPRECATION")
            val vCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pkgInfo.longVersionCode
            } else {
                pkgInfo.versionCode.toLong()
            }

            ApkDetailedMetadata(
                packageName = pkgInfo.packageName,
                versionName = pkgInfo.versionName,
                versionCode = vCode,
                minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) appInfo?.minSdkVersion else null,
                targetSdk = appInfo?.targetSdkVersion,
                compileSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) appInfo?.compileSdkVersion else null,
                permissions = perms,
                components = ApkComponentCounts(
                    activitiesCount = activities.size,
                    exportedActivitiesCount = activities.count { it.exported },
                    servicesCount = services.size,
                    exportedServicesCount = services.count { it.exported },
                    receiversCount = receivers.size,
                    exportedReceiversCount = receivers.count { it.exported },
                    providersCount = providers.size,
                    exportedProvidersCount = providers.count { it.exported }
                ),
                isDebuggable = isDebuggable,
                allowBackup = allowBackup,
                warnings = warnings
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun extractFromAxml(apkFile: File): ApkDetailedMetadata? {
        return try {
            ZipFile(apkFile).use { zip ->
                val manifestEntry = zip.getEntry("AndroidManifest.xml") ?: return null
                zip.getInputStream(manifestEntry).use { input ->
                    AxmlManifestParser.parse(input.readBytes())
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    internal fun resolveAaptBinary(): File? {
        val candidates = mutableListOf<File>()

        try {
            val sdkRoot = File(TermuxEnvironment.sdkRoot)
            val buildTools = File(sdkRoot, "build-tools")
            if (buildTools.isDirectory) {
                buildTools.listFiles()
                    ?.filter { it.isDirectory }
                    ?.sortedByDescending { it.name }
                    ?.forEach { dir ->
                        candidates.add(File(dir, "aapt"))
                        candidates.add(File(dir, "aapt2"))
                    }
            }

            candidates.add(File(TermuxEnvironment.prefix, "bin/aapt"))
            candidates.add(File(TermuxEnvironment.prefix, "bin/aapt2"))
        } catch (_: Exception) {}

        // Fallbacks
        candidates.add(File("/data/data/com.termux/files/usr/bin/aapt"))
        candidates.add(File("/data/data/com.termux/files/usr/bin/aapt2"))

        System.getenv("PATH")?.split(":")?.forEach { p ->
            if (p.isNotBlank()) {
                candidates.add(File(p, "aapt"))
                candidates.add(File(p, "aapt2"))
            }
        }

        return candidates.firstOrNull { it.isFile && it.canExecute() }
    }

    internal fun resolveApksignerBinary(): File? {
        val candidates = mutableListOf<File>()

        try {
            val sdkRoot = File(TermuxEnvironment.sdkRoot)
            val buildTools = File(sdkRoot, "build-tools")
            if (buildTools.isDirectory) {
                buildTools.listFiles()
                    ?.filter { it.isDirectory }
                    ?.sortedByDescending { it.name }
                    ?.forEach { dir ->
                        candidates.add(File(dir, "apksigner"))
                    }
            }

            candidates.add(File(TermuxEnvironment.prefix, "bin/apksigner"))
        } catch (_: Exception) {}

        // Fallbacks
        candidates.add(File("/data/data/com.termux/files/usr/bin/apksigner"))

        System.getenv("PATH")?.split(":")?.forEach { p ->
            if (p.isNotBlank()) {
                candidates.add(File(p, "apksigner"))
            }
        }

        return candidates.firstOrNull { it.isFile && it.canExecute() }
    }

    private data class ProcessRunResult(val exitCode: Int, val output: String)

    private fun runCommand(command: List<String>, timeoutSeconds: Long): ProcessRunResult {
        return try {
            val env = try { TermuxEnvironment.environment() } catch (_: Exception) { emptyMap() }
            val process = processLauncher(command, env, File("."))
            val reader = BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8))
            val output = StringBuilder()
            val completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (completed) {
                reader.forEachLine { line ->
                    output.appendLine(line)
                }
                ProcessRunResult(process.exitValue(), output.toString())
            } else {
                process.destroyForcibly()
                ProcessRunResult(-1, "Timeout nach $timeoutSeconds s")
            }
        } catch (e: Exception) {
            ProcessRunResult(-1, e.message ?: "Ausführungsfehler")
        }
    }
}
