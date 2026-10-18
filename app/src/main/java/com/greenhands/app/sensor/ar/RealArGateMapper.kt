package com.greenhands.app.sensor.ar

/**
 * Pure Phase 10E-B gate mapping — no AR session, no ViewModel simulation.
 * Unit-testable without camera / ARCore runtime.
 */
enum class RealArAvailabilityUi {
    CHECKING,
    SUPPORTED,
    NEEDS_INSTALL_OR_UPDATE,
    UNSUPPORTED_DEVICE,
    UNAVAILABLE
}

enum class RealArCameraPermissionUi {
    GRANTED,
    NOT_REQUESTED,
    DENIED_CAN_RETRY,
    DENIED_PERMANENT
}

enum class RealArSessionUi {
    IDLE,
    RUNNING,
    FAILED,
    TRACKING_LIMITED
}

enum class RealArFloorUi {
    SEARCHING,
    DETECTED
}

object RealArGateMapper {

    /**
     * Maps [com.google.ar.core.ArCoreApk.Availability] names without loading ARCore in unit tests.
     */
    fun mapAvailability(availabilityName: String): RealArAvailabilityUi = when (availabilityName) {
        "SUPPORTED_INSTALLED" -> RealArAvailabilityUi.SUPPORTED
        "SUPPORTED_APK_TOO_OLD",
        "SUPPORTED_NOT_INSTALLED" -> RealArAvailabilityUi.NEEDS_INSTALL_OR_UPDATE
        "UNSUPPORTED_DEVICE_NOT_CAPABLE" -> RealArAvailabilityUi.UNSUPPORTED_DEVICE
        "UNKNOWN_CHECKING",
        "UNKNOWN_TIMED_OUT" -> RealArAvailabilityUi.CHECKING
        "UNKNOWN_ERROR" -> RealArAvailabilityUi.UNAVAILABLE
        else -> RealArAvailabilityUi.UNAVAILABLE
    }

    /**
     * @param granted CAMERA permission currently granted
     * @param shouldShowRationale Activity.shouldShowRequestPermissionRationale(CAMERA)
     * @param hasRequestedOnce user has already been through at least one system permission dialog
     */
    fun mapCameraPermission(
        granted: Boolean,
        shouldShowRationale: Boolean,
        hasRequestedOnce: Boolean
    ): RealArCameraPermissionUi = when {
        granted -> RealArCameraPermissionUi.GRANTED
        !hasRequestedOnce -> RealArCameraPermissionUi.NOT_REQUESTED
        shouldShowRationale -> RealArCameraPermissionUi.DENIED_CAN_RETRY
        else -> RealArCameraPermissionUi.DENIED_PERMANENT
    }

    fun canStartArSession(
        availability: RealArAvailabilityUi,
        camera: RealArCameraPermissionUi
    ): Boolean =
        availability == RealArAvailabilityUi.SUPPORTED &&
            camera == RealArCameraPermissionUi.GRANTED

    fun shouldOfferVirtualFallback(availability: RealArAvailabilityUi): Boolean =
        availability == RealArAvailabilityUi.UNSUPPORTED_DEVICE ||
            availability == RealArAvailabilityUi.UNAVAILABLE ||
            availability == RealArAvailabilityUi.NEEDS_INSTALL_OR_UPDATE

    fun shouldOfferVirtualFallback(camera: RealArCameraPermissionUi): Boolean =
        camera == RealArCameraPermissionUi.DENIED_CAN_RETRY ||
            camera == RealArCameraPermissionUi.DENIED_PERMANENT
}

/** Navigation helpers for Real AR side-route (pure). Matches [com.greenhands.app.ui.navigation.Routes]. */
object RealArNavigation {
    const val ROUTE = "sensor_real_ar"
    const val VIRTUAL_FALLBACK_ROUTE = "sensor_virtual_preview"

    /** Back uses popBackStack — returns to Coverage or Optimize that opened Real AR. */
    fun backReturnsToPreviousScreen(): Boolean = true

    fun virtualFallbackRoute(): String = VIRTUAL_FALLBACK_ROUTE

    fun isSensorSideRoute(route: String): Boolean =
        route == ROUTE || route == VIRTUAL_FALLBACK_ROUTE
}
