/**
 * Modul: :core:data
 * @author Thomas Schmid
 */
package com.codeforge.core.data.repository

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.codeforge.core.domain.model.ApkInstallStatus
import com.codeforge.core.domain.model.ApkMetadata
import com.codeforge.core.domain.model.ApkPackageInfo
import com.codeforge.core.domain.parser.ApkInstallStatusMapper
import com.codeforge.core.domain.parser.ApkMetadataParser
import com.codeforge.core.domain.repository.ApkInstallerRepository
import com.codeforge.core.resources.R
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileNotFoundException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

@Singleton
class ApkInstallerRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apkMetadataParser: ApkMetadataParser,
    private val apkInstallStatusMapper: ApkInstallStatusMapper,
) : ApkInstallerRepository {

    override fun findApk(
        projectRoot: File,
        preferredVariant: String?,
        supportedAbis: List<String>
    ): Result<ApkMetadata> = runCatching {
        val effectiveAbis = if (supportedAbis.isNotEmpty()) {
            supportedAbis
        } else {
            Build.SUPPORTED_ABIS.toList()
        }

        // 1. Suche nach output-metadata.json Dateien unter build/outputs/apk
        val metadataFiles = findOutputMetadataFiles(projectRoot)

        if (metadataFiles.isNotEmpty()) {
            // Sortiere nach bevorzugter Variante (z. B. preferredVariant oder "debug")
            val targetVariant = preferredVariant?.lowercase() ?: "debug"
            val sortedMetadata = metadataFiles.sortedByDescending { file ->
                val path = file.absolutePath.lowercase()
                when {
                    path.contains("/$targetVariant/") || path.contains("\\$targetVariant\\") -> 2
                    path.contains("/debug/") || path.contains("\\debug\\") -> 1
                    else -> 0
                }
            }

            for (metaFile in sortedMetadata) {
                val parentDir = metaFile.parentFile ?: projectRoot
                val parsed = apkMetadataParser.parse(
                    jsonContent = metaFile.readText(),
                    metadataDirectory = parentDir,
                    supportedAbis = effectiveAbis
                ).getOrNull()

                if (parsed != null && parsed.file.exists()) {
                    return@runCatching parsed
                }
            }
        }

        // 2. Fallback: Suche nach neuester *.apk unter build/outputs/apk
        val fallbackApk = findFallbackApk(projectRoot, preferredVariant)
            ?: throw FileNotFoundException(context.getString(R.string.install_keine_apk_gefunden))

        val isSigned = !fallbackApk.name.contains("-unsigned", ignoreCase = true)
        val pkgInfo = getApkPackageInfo(fallbackApk).getOrNull()

        ApkMetadata(
            file = fallbackApk,
            applicationId = pkgInfo?.packageName ?: "unknown",
            variantName = preferredVariant ?: "debug",
            versionCode = pkgInfo?.versionCode,
            versionName = pkgInfo?.versionName,
            abiFilter = null,
            isSigned = isSigned
        )
    }

    override fun getApkPackageInfo(apkFile: File): Result<ApkPackageInfo> = runCatching {
        if (!apkFile.exists()) {
            throw FileNotFoundException("APK file does not exist: ${apkFile.absolutePath}")
        }

        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.PackageInfoFlags.of(PackageManager.GET_ACTIVITIES.toLong()))
        } else {
            pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.GET_ACTIVITIES)
        } ?: error("Failed to parse package archive info from ${apkFile.name}")

        pkgInfo.applicationInfo?.sourceDir = apkFile.absolutePath
        pkgInfo.applicationInfo?.publicSourceDir = apkFile.absolutePath

        val appLabel = pkgInfo.applicationInfo?.loadLabel(pm)?.toString() ?: pkgInfo.packageName
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pkgInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            pkgInfo.versionCode.toLong()
        }
        val isSigned = !apkFile.name.contains("-unsigned", ignoreCase = true)

        ApkPackageInfo(
            file = apkFile,
            packageName = pkgInfo.packageName,
            versionName = pkgInfo.versionName,
            versionCode = versionCode,
            appLabel = appLabel,
            isSigned = isSigned
        )
    }

    override fun canRequestPackageInstalls(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    override fun installApk(apkFile: File): Flow<ApkInstallStatus> = callbackFlow {
        // Nicht signierte Release-APKs ablehnen
        if (apkFile.name.contains("-unsigned", ignoreCase = true)) {
            trySend(
                ApkInstallStatus.Failed(
                    reason = context.getString(R.string.install_nicht_signiert_fehler),
                    isUnsigned = true
                )
            )
            close()
            return@callbackFlow
        }

        // Berechtigung für Unbekannte Quellen prüfen
        if (!canRequestPackageInstalls()) {
            val settingsIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            } else {
                Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            trySend(ApkInstallStatus.NeedsPermission(settingsIntent))
            close()
            return@callbackFlow
        }

        val pkgInfo = getApkPackageInfo(apkFile).getOrNull()
        val packageName = pkgInfo?.packageName.orEmpty()
        val appLabel = pkgInfo?.appLabel

        trySend(ApkInstallStatus.Installing(packageName))

        val packageInstaller = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val sessionId = packageInstaller.createSession(params)
        val session = packageInstaller.openSession(sessionId)

        try {
            session.openWrite("package", 0, apkFile.length()).use { outStream ->
                apkFile.inputStream().use { inStream ->
                    inStream.copyTo(outStream)
                }
                session.fsync(outStream)
            }
        } catch (e: Exception) {
            runCatching { session.abandon() }
            runCatching { session.close() }
            trySend(
                ApkInstallStatus.Failed(
                    reason = e.localizedMessage ?: "Failed to write APK to installer session",
                    packageName = packageName
                )
            )
            close()
            return@callbackFlow
        }

        val action = "${context.packageName}.ACTION_INSTALL_STATUS_${sessionId}_${System.currentTimeMillis()}"

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(recvContext: Context?, intent: Intent?) {
                if (intent == null || intent.action != action) return
                val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)

                if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                    @Suppress("DEPRECATION")
                    val confirmationIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                    } else {
                        intent.getParcelableExtra(Intent.EXTRA_INTENT)
                    }
                    if (confirmationIntent != null) {
                        confirmationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        trySend(ApkInstallStatus.NeedsUserAction(confirmationIntent))
                    }
                } else {
                    val resultStatus = apkInstallStatusMapper.mapStatusCode(
                        statusCode = status,
                        rawMessage = message,
                        packageName = packageName,
                        appLabel = appLabel,
                        stringResolver = { resId, args ->
                            if (args.isEmpty()) context.getString(resId) else context.getString(resId, *args)
                        }
                    )
                    trySend(resultStatus)
                    close()
                }
            }
        }

        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(action),
            ContextCompat.RECEIVER_EXPORTED
        )

        val statusIntent = Intent(action).setPackage(context.packageName)
        val pendingIntent = android.app.PendingIntent.getBroadcast(
            context,
            sessionId,
            statusIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                android.app.PendingIntent.FLAG_MUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                android.app.PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        session.commit(pendingIntent.intentSender)

        awaitClose {
            runCatching { context.unregisterReceiver(receiver) }
            runCatching { session.close() }
        }
    }

    override fun launchApp(packageName: String): Result<Unit> = runCatching {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: error("No launch intent found for package: $packageName")
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
    }

    override fun uninstallApp(packageName: String): Result<Unit> = runCatching {
        val uri = Uri.parse("package:$packageName")
        val intent = Intent(Intent.ACTION_DELETE, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun findOutputMetadataFiles(dir: File): List<File> {
        val results = mutableListOf<File>()
        dir.walkTopDown()
            .maxDepth(8)
            .filter { file ->
                file.isFile &&
                    file.name == "output-metadata.json" &&
                    file.parentFile?.parentFile?.name == "outputs" ||
                    file.absolutePath.contains("build/outputs/apk") ||
                    file.absolutePath.contains("build\\outputs\\apk")
            }
            .forEach { results.add(it) }
        return results
    }

    private fun findFallbackApk(projectRoot: File, preferredVariant: String?): File? {
        val targetVariant = preferredVariant?.lowercase() ?: "debug"
        val apkCandidates = mutableListOf<File>()

        projectRoot.walkTopDown()
            .maxDepth(8)
            .filter { file ->
                file.isFile &&
                    file.extension.equals("apk", ignoreCase = true) &&
                    !file.name.contains("-unaligned", ignoreCase = true) &&
                    (file.absolutePath.contains("build/outputs/apk") ||
                        file.absolutePath.contains("build\\outputs\\apk"))
            }
            .forEach { apkCandidates.add(it) }

        if (apkCandidates.isEmpty()) return null

        // Sortiere: Bevorzuge Treffer mit Ziel-Variante im Pfad/Namen, danach neueste Änderungszeit
        return apkCandidates.sortedWith(
            compareByDescending<File> { file ->
                val p = file.absolutePath.lowercase()
                when {
                    p.contains("/$targetVariant/") || p.contains("-$targetVariant") -> 2
                    p.contains("/debug/") || p.contains("-debug") -> 1
                    else -> 0
                }
            }.thenByDescending { it.lastModified() }
        ).firstOrNull()
    }
}
