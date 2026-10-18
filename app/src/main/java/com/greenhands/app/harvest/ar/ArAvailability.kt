package com.greenhands.app.harvest.ar

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import com.greenhands.app.ar.ArCoreAvailabilityProbe
import com.greenhands.app.ar.domain.ArCoreAvailabilityKind
import com.greenhands.app.ar.domain.ArInstallOutcome

/**
 * ARCore availability for optional Harvest visualization.
 * Scanning and analysis must work even when this returns unsupported.
 * Reuses the destination ARCore probe — does not open a second session.
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
            when (ArCoreAvailabilityProbe.kind(context)) {
                ArCoreAvailabilityKind.SUPPORTED_INSTALLED -> State.SUPPORTED
                ArCoreAvailabilityKind.SUPPORTED_NOT_INSTALLED,
                ArCoreAvailabilityKind.SUPPORTED_APK_TOO_OLD -> State.UNKNOWN
                ArCoreAvailabilityKind.UNKNOWN_CHECKING,
                ArCoreAvailabilityKind.UNKNOWN_TIMED_OUT,
                ArCoreAvailabilityKind.UNKNOWN_ERROR -> State.UNKNOWN
                ArCoreAvailabilityKind.UNSUPPORTED_DEVICE_NOT_CAPABLE -> State.UNSUPPORTED
            }
        } catch (_: Throwable) {
            State.UNSUPPORTED
        }
    }

    fun requestInstallIfNeeded(activity: Activity): State {
        return try {
            when (ArCoreAvailabilityProbe.requestInstall(activity, alreadyRequested = false)) {
                ArInstallOutcome.INSTALLED -> State.SUPPORTED
                ArInstallOutcome.REQUESTED -> State.INSTALL_REQUESTED
                ArInstallOutcome.UNSUPPORTED,
                ArInstallOutcome.DECLINED -> State.UNSUPPORTED
                ArInstallOutcome.FAILED -> State.UNKNOWN
            }
        } catch (_: Throwable) {
            State.UNSUPPORTED
        }
    }
}
