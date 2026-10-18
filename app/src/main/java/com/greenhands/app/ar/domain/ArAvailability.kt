package com.greenhands.app.ar.domain

/**
 * Device/ARCore availability without Android or ARCore types so unit tests stay pure Kotlin.
 * Names match [com.google.ar.core.ArCoreApk.Availability].
 */
enum class ArCoreAvailabilityKind {
    SUPPORTED_INSTALLED,
    SUPPORTED_NOT_INSTALLED,
    SUPPORTED_APK_TOO_OLD,
    UNSUPPORTED_DEVICE_NOT_CAPABLE,
    UNKNOWN_CHECKING,
    UNKNOWN_ERROR,
    UNKNOWN_TIMED_OUT
}

enum class CameraPermissionState {
    UNKNOWN,
    GRANTED,
    DENIED
}

enum class ArInstallOutcome {
    INSTALLED,
    REQUESTED,
    DECLINED,
    UNSUPPORTED,
    FAILED
}

enum class ArSessionFailure {
    PERMISSION_DENIED,
    UNSUPPORTED,
    NEEDS_INSTALL,
    NEEDS_UPDATE,
    SESSION_FAILED
}

enum class ArGateStatus {
    CHECKING,
    NEEDS_CAMERA_PERMISSION,
    PERMISSION_DENIED,
    UNSUPPORTED,
    NEEDS_INSTALL,
    NEEDS_UPDATE,
    INSTALL_REQUESTED,
    INSTALL_DECLINED,
    TRANSIENT_ERROR,
    READY,
    SESSION_FAILED
}

enum class ArTabletopPhase {
    GATE,
    DETECTING,
    PLACED
}

object ArAvailabilityGate {
    fun isTransient(kind: ArCoreAvailabilityKind): Boolean = when (kind) {
        ArCoreAvailabilityKind.UNKNOWN_CHECKING,
        ArCoreAvailabilityKind.UNKNOWN_TIMED_OUT -> true
        else -> false
    }

    fun fromAvailability(kind: ArCoreAvailabilityKind): ArGateStatus = when (kind) {
        ArCoreAvailabilityKind.SUPPORTED_INSTALLED -> ArGateStatus.READY
        ArCoreAvailabilityKind.SUPPORTED_NOT_INSTALLED -> ArGateStatus.NEEDS_INSTALL
        ArCoreAvailabilityKind.SUPPORTED_APK_TOO_OLD -> ArGateStatus.NEEDS_UPDATE
        ArCoreAvailabilityKind.UNSUPPORTED_DEVICE_NOT_CAPABLE -> ArGateStatus.UNSUPPORTED
        ArCoreAvailabilityKind.UNKNOWN_CHECKING -> ArGateStatus.CHECKING
        ArCoreAvailabilityKind.UNKNOWN_ERROR,
        ArCoreAvailabilityKind.UNKNOWN_TIMED_OUT -> ArGateStatus.TRANSIENT_ERROR
    }

    fun resolve(
        permission: CameraPermissionState,
        availability: ArCoreAvailabilityKind?,
        install: ArInstallOutcome? = null,
        sessionFailure: ArSessionFailure? = null
    ): ArGateStatus {
        if (sessionFailure != null) {
            return when (sessionFailure) {
                ArSessionFailure.PERMISSION_DENIED -> ArGateStatus.PERMISSION_DENIED
                ArSessionFailure.UNSUPPORTED -> ArGateStatus.UNSUPPORTED
                ArSessionFailure.NEEDS_INSTALL -> ArGateStatus.NEEDS_INSTALL
                ArSessionFailure.NEEDS_UPDATE -> ArGateStatus.NEEDS_UPDATE
                ArSessionFailure.SESSION_FAILED -> ArGateStatus.SESSION_FAILED
            }
        }
        when (permission) {
            CameraPermissionState.UNKNOWN -> return ArGateStatus.CHECKING
            CameraPermissionState.DENIED -> return ArGateStatus.PERMISSION_DENIED
            CameraPermissionState.GRANTED -> Unit
        }
        if (install == ArInstallOutcome.DECLINED) return ArGateStatus.INSTALL_DECLINED
        if (install == ArInstallOutcome.UNSUPPORTED) return ArGateStatus.UNSUPPORTED
        if (install == ArInstallOutcome.FAILED) return ArGateStatus.TRANSIENT_ERROR
        if (install == ArInstallOutcome.REQUESTED) return ArGateStatus.INSTALL_REQUESTED
        if (install == ArInstallOutcome.INSTALLED) return ArGateStatus.READY
        if (availability == null) return ArGateStatus.CHECKING
        return fromAvailability(availability)
    }

    fun canOpenLiveSession(status: ArGateStatus): Boolean = status == ArGateStatus.READY

    fun offersContinueIn3d(status: ArGateStatus): Boolean = when (status) {
        ArGateStatus.PERMISSION_DENIED,
        ArGateStatus.UNSUPPORTED,
        ArGateStatus.NEEDS_INSTALL,
        ArGateStatus.NEEDS_UPDATE,
        ArGateStatus.INSTALL_DECLINED,
        ArGateStatus.TRANSIENT_ERROR,
        ArGateStatus.SESSION_FAILED -> true
        else -> false
    }

    fun isUnsupportedFallback(status: ArGateStatus): Boolean =
        status == ArGateStatus.UNSUPPORTED
}
