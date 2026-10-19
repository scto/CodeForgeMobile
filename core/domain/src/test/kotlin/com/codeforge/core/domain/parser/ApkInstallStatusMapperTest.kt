/**
 * Modul: :core:domain
 * @author Thomas Schmid
 */
package com.codeforge.core.domain.parser

import com.codeforge.core.domain.model.ApkInstallStatus
import com.codeforge.core.resources.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkInstallStatusMapperTest {

    private val mapper = ApkInstallStatusMapper()
    private val fakeResolver = StringResolver { resId, args ->
        when (resId) {
            R.string.install_fehler_downgrade -> "Downgrade error"
            R.string.install_fehler_speicherplatz -> "Insufficient storage"
            R.string.install_inkompatible_signatur_nachricht -> "Incompatible signature"
            R.string.install_fehler_blockiert -> "Blocked by policy"
            R.string.install_fehler_abgebrochen -> "Aborted"
            R.string.install_fehler_beschaedigt -> "Corrupt package"
            R.string.install_fehler_konflikt -> "Package conflict"
            R.string.install_fehler_unbekannt -> "Status ${args.getOrNull(0)}: ${args.getOrNull(1)}"
            else -> "Resource $resId"
        }
    }

    @Test
    fun mapSuccessStatus() {
        val result = mapper.mapStatusCode(
            statusCode = ApkInstallStatusCodes.STATUS_SUCCESS,
            rawMessage = "Success",
            packageName = "com.example.app",
            appLabel = "Example App",
            stringResolver = fakeResolver
        )

        assertTrue(result is ApkInstallStatus.Installed)
        val installed = result as ApkInstallStatus.Installed
        assertEquals("com.example.app", installed.packageName)
        assertEquals("Example App", installed.appLabel)
    }

    @Test
    fun mapStorageFailure() {
        val result = mapper.mapStatusCode(
            statusCode = ApkInstallStatusCodes.STATUS_FAILURE_STORAGE,
            rawMessage = "Not enough space",
            packageName = "com.example.app",
            appLabel = null,
            stringResolver = fakeResolver
        )

        assertTrue(result is ApkInstallStatus.Failed)
        val failed = result as ApkInstallStatus.Failed
        assertTrue(failed.isInsufficientStorage)
        assertEquals("Insufficient storage", failed.reason)
        assertEquals("com.example.app", failed.packageName)
    }

    @Test
    fun mapIncompatibleSignatureFailure() {
        val result = mapper.mapStatusCode(
            statusCode = ApkInstallStatusCodes.STATUS_FAILURE_INCOMPATIBLE,
            rawMessage = "Signatures do not match",
            packageName = "com.example.app",
            appLabel = null,
            stringResolver = fakeResolver
        )

        assertTrue(result is ApkInstallStatus.Failed)
        val failed = result as ApkInstallStatus.Failed
        assertTrue(failed.isSignatureIncompatible)
        assertEquals("Incompatible signature", failed.reason)
    }

    @Test
    fun mapDowngradeFailureViaMessage() {
        val result = mapper.mapStatusCode(
            statusCode = ApkInstallStatusCodes.STATUS_FAILURE,
            rawMessage = "INSTALL_FAILED_VERSION_DOWNGRADE: Cannot downgrade",
            packageName = "com.example.app",
            appLabel = null,
            stringResolver = fakeResolver
        )

        assertTrue(result is ApkInstallStatus.Failed)
        val failed = result as ApkInstallStatus.Failed
        assertTrue(failed.isDowngrade)
        assertEquals("Downgrade error", failed.reason)
    }

    @Test
    fun mapBlockedFailure() {
        val result = mapper.mapStatusCode(
            statusCode = ApkInstallStatusCodes.STATUS_FAILURE_BLOCKED,
            rawMessage = "Blocked",
            packageName = "com.example.app",
            appLabel = null,
            stringResolver = fakeResolver
        )

        assertTrue(result is ApkInstallStatus.Failed)
        val failed = result as ApkInstallStatus.Failed
        assertEquals("Blocked by policy", failed.reason)
    }

    @Test
    fun mapAbortedFailure() {
        val result = mapper.mapStatusCode(
            statusCode = ApkInstallStatusCodes.STATUS_FAILURE_ABORTED,
            rawMessage = "Aborted by user",
            packageName = "com.example.app",
            appLabel = null,
            stringResolver = fakeResolver
        )

        assertTrue(result is ApkInstallStatus.Failed)
        val failed = result as ApkInstallStatus.Failed
        assertEquals("Aborted", failed.reason)
    }

    @Test
    fun mapInvalidPackageFailure() {
        val result = mapper.mapStatusCode(
            statusCode = ApkInstallStatusCodes.STATUS_FAILURE_INVALID,
            rawMessage = "Corrupt apk",
            packageName = "com.example.app",
            appLabel = null,
            stringResolver = fakeResolver
        )

        assertTrue(result is ApkInstallStatus.Failed)
        val failed = result as ApkInstallStatus.Failed
        assertEquals("Corrupt package", failed.reason)
    }

    @Test
    fun mapConflictFailure() {
        val result = mapper.mapStatusCode(
            statusCode = ApkInstallStatusCodes.STATUS_FAILURE_CONFLICT,
            rawMessage = "Conflicting provider",
            packageName = "com.example.app",
            appLabel = null,
            stringResolver = fakeResolver
        )

        assertTrue(result is ApkInstallStatus.Failed)
        val failed = result as ApkInstallStatus.Failed
        assertEquals("Package conflict", failed.reason)
    }

    @Test
    fun mapGenericFailure() {
        val result = mapper.mapStatusCode(
            statusCode = 999,
            rawMessage = "Internal failure",
            packageName = "com.example.app",
            appLabel = null,
            stringResolver = fakeResolver
        )

        assertTrue(result is ApkInstallStatus.Failed)
        val failed = result as ApkInstallStatus.Failed
        assertEquals("Status 999: Internal failure", failed.reason)
    }
}
