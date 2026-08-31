package com.greenhands.app.harvest.ar

import android.app.Activity
import android.content.Context
import com.google.ar.core.ArCoreApk
import com.google.ar.core.exceptions.UnavailableException

/**
 * ARCore availability for optional visualization.
 * Scanning and analysis must work even when this returns unsupported.
 */
object ArAvailability {
    enum class State {
        SUPPORTED,
        UNSUPPORTED,
        INSTALL_REQUESTED,
        UNKNOWN
    }

    fun check(context: Context): State {
        return try {
            when (ArCoreApk.getInstance().checkAvailability(context)) {
                ArCoreApk.Availability.SUPPORTED_INSTALLED -> State.SUPPORTED
                ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD,
                ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED -> State.UNKNOWN
                ArCoreApk.Availability.UNKNOWN_CHECKING,
                ArCoreApk.Availability.UNKNOWN_ERROR,
                ArCoreApk.Availability.UNKNOWN_TIMED_OUT -> State.UNKNOWN
                ArCoreApk.Availability.UNSUPPORTED_DEVICE_NOT_CAPABLE -> State.UNSUPPORTED
                else -> State.UNSUPPORTED
            }
        } catch (_: Throwable) {
            State.UNSUPPORTED
        }
    }

    fun requestInstallIfNeeded(activity: Activity): State {
        return try {
            when (ArCoreApk.getInstance().requestInstall(activity, true)) {
                ArCoreApk.InstallStatus.INSTALLED -> State.SUPPORTED
                ArCoreApk.InstallStatus.INSTALL_REQUESTED -> State.INSTALL_REQUESTED
                else -> State.UNKNOWN
            }
        } catch (_: UnavailableException) {
            State.UNSUPPORTED
        } catch (_: Throwable) {
            State.UNSUPPORTED
        }
    }
}
