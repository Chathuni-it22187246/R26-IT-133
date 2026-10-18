package com.greenhands.app.ar

import android.app.Activity
import android.content.Context
import com.google.ar.core.ArCoreApk
import com.google.ar.core.exceptions.UnavailableApkTooOldException
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException
import com.greenhands.app.ar.domain.ArCoreAvailabilityKind
import com.greenhands.app.ar.domain.ArInstallOutcome
import com.greenhands.app.ar.domain.ArSessionFailure

/**
 * Thin ARCore SDK adapter. Domain code never imports ARCore types.
 */
object ArCoreAvailabilityProbe {
    fun kind(context: Context): ArCoreAvailabilityKind {
        val availability = runCatching {
            ArCoreApk.getInstance().checkAvailability(context)
        }.getOrNull() ?: return ArCoreAvailabilityKind.UNKNOWN_ERROR
        return mapAvailability(availability)
    }

    fun mapAvailability(availability: ArCoreApk.Availability): ArCoreAvailabilityKind = when (availability) {
        ArCoreApk.Availability.SUPPORTED_INSTALLED -> ArCoreAvailabilityKind.SUPPORTED_INSTALLED
        ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED -> ArCoreAvailabilityKind.SUPPORTED_NOT_INSTALLED
        ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD -> ArCoreAvailabilityKind.SUPPORTED_APK_TOO_OLD
        ArCoreApk.Availability.UNSUPPORTED_DEVICE_NOT_CAPABLE ->
            ArCoreAvailabilityKind.UNSUPPORTED_DEVICE_NOT_CAPABLE
        ArCoreApk.Availability.UNKNOWN_CHECKING -> ArCoreAvailabilityKind.UNKNOWN_CHECKING
        ArCoreApk.Availability.UNKNOWN_TIMED_OUT -> ArCoreAvailabilityKind.UNKNOWN_TIMED_OUT
        ArCoreApk.Availability.UNKNOWN_ERROR -> ArCoreAvailabilityKind.UNKNOWN_ERROR
        else -> ArCoreAvailabilityKind.UNKNOWN_ERROR
    }

    fun requestInstall(activity: Activity, alreadyRequested: Boolean): ArInstallOutcome {
        return try {
            val status = ArCoreApk.getInstance().requestInstall(activity, !alreadyRequested)
            when (status) {
                ArCoreApk.InstallStatus.INSTALLED -> ArInstallOutcome.INSTALLED
                ArCoreApk.InstallStatus.INSTALL_REQUESTED -> ArInstallOutcome.REQUESTED
                else -> ArInstallOutcome.FAILED
            }
        } catch (_: UnavailableUserDeclinedInstallationException) {
            ArInstallOutcome.DECLINED
        } catch (_: UnavailableDeviceNotCompatibleException) {
            ArInstallOutcome.UNSUPPORTED
        } catch (_: Exception) {
            ArInstallOutcome.FAILED
        }
    }

    fun sessionFailureOf(error: Throwable): ArSessionFailure = when (error) {
        is SecurityException -> ArSessionFailure.PERMISSION_DENIED
        is UnavailableDeviceNotCompatibleException -> ArSessionFailure.UNSUPPORTED
        is UnavailableArcoreNotInstalledException -> ArSessionFailure.NEEDS_INSTALL
        is UnavailableApkTooOldException -> ArSessionFailure.NEEDS_UPDATE
        is UnavailableUserDeclinedInstallationException -> ArSessionFailure.NEEDS_INSTALL
        else -> ArSessionFailure.SESSION_FAILED
    }
}
