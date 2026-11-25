// Modul: :feature:settings
package com.codeforge.feature.settings.diagnostics

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Environment
import android.os.Process
import android.os.StatFs
import android.view.Display
import com.codeforge.core.resources.R
import com.codeforge.shared.termux.TermuxConstants
import java.io.File
import kotlin.math.roundToInt

/** Liest Geräte-, Anzeige-, Laufzeit-, Speicher- und App-Angaben; blockierend (auf einem IO-Dispatcher aufrufen). */
class SystemInfoCollector(private val context: Context) {

    /** Alle Abschnitte. Einzelne Fehler werden als „—“ gezeigt, nie geworfen. */
    fun collect(): List<InfoSection> = listOf(device(), display(), runtime(), storage(), app())

    private fun device() = InfoSection(
        InfoSectionKind.DEVICE,
        listOf(
            InfoItem(R.string.sysinfo_manufacturer, Build.MANUFACTURER.capitalized()),
            InfoItem(R.string.sysinfo_model, Build.MODEL),
            InfoItem(R.string.sysinfo_brand, Build.BRAND.capitalized()),
            InfoItem(R.string.sysinfo_device, Build.DEVICE),
            InfoItem(R.string.sysinfo_android_version, Build.VERSION.RELEASE),
            InfoItem(R.string.sysinfo_sdk_version, Build.VERSION.SDK_INT.toString()),
            InfoItem(R.string.sysinfo_hardware, Build.HARDWARE),
            InfoItem(R.string.sysinfo_architecture, System.getProperty("os.arch").orEmpty().ifBlank { DASH }),
            InfoItem(R.string.sysinfo_abis, Build.SUPPORTED_ABIS.joinToString(", ")),
            InfoItem(R.string.sysinfo_security_patch, Build.VERSION.SECURITY_PATCH.ifBlank { DASH }),
        ),
        tiles = true,
    )

    private fun display(): InfoSection {
        val display = context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)
        val metrics = context.resources.displayMetrics
        val refresh = display?.supportedModes?.maxOfOrNull { it.refreshRate } ?: display?.refreshRate
        val am = context.getSystemService(ActivityManager::class.java)
        val pm = context.packageManager
        return InfoSection(
            InfoSectionKind.DISPLAY,
            listOf(
                InfoItem(R.string.sysinfo_resolution, "${metrics.widthPixels} × ${metrics.heightPixels}"),
                InfoItem(R.string.sysinfo_density, "${metrics.densityDpi} dpi"),
                InfoItem(R.string.sysinfo_refresh_rate, refresh?.let { "${it.roundToInt()} Hz" } ?: DASH),
                InfoItem(R.string.sysinfo_opengl, am?.deviceConfigurationInfo?.reqGlEsVersion?.let(InfoFormat::glEs) ?: DASH),
                InfoItem(R.string.sysinfo_vulkan, yesNo(pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION))),
                InfoItem(R.string.sysinfo_hdr, yesNo(display?.isHdr == true)),
                InfoItem(R.string.sysinfo_wide_color, yesNo(context.resources.configuration.isScreenWideColorGamut)),
            ),
        )
    }

    private fun runtime(): InfoSection {
        val am = context.getSystemService(ActivityManager::class.java)
        val mem = ActivityManager.MemoryInfo().also { am?.getMemoryInfo(it) }
        val largeHeap = context.applicationInfo.flags and ApplicationInfo.FLAG_LARGE_HEAP != 0
        val bits = if (Process.is64Bit()) "64-bit" else "32-bit"
        return InfoSection(
            InfoSectionKind.RUNTIME,
            listOf(
                InfoItem(R.string.sysinfo_total_memory, InfoFormat.bytes(mem.totalMem)),
                InfoItem(R.string.sysinfo_available_memory, InfoFormat.bytes(mem.availMem)),
                InfoItem(R.string.sysinfo_low_ram, yesNo(am?.isLowRamDevice == true)),
                InfoItem(R.string.sysinfo_heap, "${am?.memoryClass ?: 0} MB / ${am?.largeMemoryClass ?: 0} MB"),
                InfoItem(R.string.sysinfo_large_heap, yesNo(largeHeap)),
                InfoItem(R.string.sysinfo_runtime, "${Build.SUPPORTED_ABIS.firstOrNull().orEmpty()} ($bits)"),
                InfoItem(R.string.sysinfo_cpu_cores, Runtime.getRuntime().availableProcessors().toString()),
                InfoItem(R.string.sysinfo_kernel, System.getProperty("os.version").orEmpty().ifBlank { DASH }),
            ),
        )
    }

    private fun storage(): InfoSection {
        val stat = runCatching { StatFs(context.filesDir.path) }.getOrNull()
        val prefix = File(TermuxConstants.TERMUX_PREFIX_DIR_PATH)
        return InfoSection(
            InfoSectionKind.STORAGE,
            listOf(
                InfoItem(R.string.sysinfo_storage_free, stat?.availableBytes?.let(InfoFormat::bytes) ?: DASH),
                InfoItem(R.string.sysinfo_storage_total, stat?.totalBytes?.let(InfoFormat::bytes) ?: DASH),
                InfoItem(R.string.sysinfo_external_storage, yesNo(Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED)),
                InfoItem(R.string.sysinfo_terminal_installed, yesNo(File(prefix, "bin/sh").exists())),
            ),
        )
    }

    private fun app(): InfoSection {
        val info = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
        val code = info?.let { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) it.longVersionCode else legacyCode(it) }
        return InfoSection(
            InfoSectionKind.APP,
            listOf(
                InfoItem(R.string.sysinfo_app_version, "${info?.versionName ?: DASH} ($code)"),
                InfoItem(R.string.sysinfo_sora_version, LibraryVersions.SORA_EDITOR),
                InfoItem(R.string.sysinfo_treesitter_version, LibraryVersions.TREE_SITTER),
                InfoItem(R.string.sysinfo_compose_version, LibraryVersions.COMPOSE_BOM),
                InfoItem(R.string.sysinfo_kotlin_version, KotlinVersion.CURRENT.toString()),
                InfoItem(R.string.sysinfo_target_sdk, context.applicationInfo.targetSdkVersion.toString()),
            ),
        )
    }

    private fun yesNo(value: Boolean) = context.getString(if (value) R.string.sysinfo_yes else R.string.sysinfo_no)

    @Suppress("DEPRECATION")
    private fun legacyCode(info: android.content.pm.PackageInfo): Long = info.versionCode.toLong()

    private fun String.capitalized() = replaceFirstChar { it.titlecase(java.util.Locale.ROOT) }

    private companion object {
        const val DASH = "—"
    }
}
