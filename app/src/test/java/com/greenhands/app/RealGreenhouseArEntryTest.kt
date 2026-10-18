package com.greenhands.app

import com.greenhands.app.sensor.ar.RealArAvailabilityUi
import com.greenhands.app.sensor.ar.RealArCameraPermissionUi
import com.greenhands.app.sensor.ar.RealArGateMapper
import com.greenhands.app.sensor.ar.RealArNavigation
import com.greenhands.app.ui.navigation.Routes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealGreenhouseArEntryTest {

    @Test
    fun realArRouteExistsAndIsSensorContent() {
        assertEquals("sensor_real_ar", Routes.SENSOR_REAL_AR)
        assertEquals(RealArNavigation.ROUTE, Routes.SENSOR_REAL_AR)
        assertTrue(Routes.isSensorContentRoute(Routes.SENSOR_REAL_AR))
        assertEquals(Routes.DASHBOARD, Routes.topLevelFor(Routes.SENSOR_REAL_AR))
    }

    @Test
    fun virtualPreviewRemainsAvailableAlongsideRealAr() {
        assertTrue(Routes.isSensorContentRoute(Routes.SENSOR_VIRTUAL_PREVIEW))
        assertTrue(Routes.SENSOR_VIRTUAL_PREVIEW in Routes.sensorContentRoutes)
        assertTrue(Routes.SENSOR_REAL_AR in Routes.sensorContentRoutes)
        assertFalse(Routes.SENSOR_REAL_AR == Routes.SENSOR_VIRTUAL_PREVIEW)
        assertTrue(RealArNavigation.isSensorSideRoute(Routes.SENSOR_VIRTUAL_PREVIEW))
        assertTrue(RealArNavigation.isSensorSideRoute(Routes.SENSOR_REAL_AR))
    }

    @Test
    fun arAvailabilityStateMapping() {
        assertEquals(
            RealArAvailabilityUi.SUPPORTED,
            RealArGateMapper.mapAvailability("SUPPORTED_INSTALLED")
        )
        assertEquals(
            RealArAvailabilityUi.NEEDS_INSTALL_OR_UPDATE,
            RealArGateMapper.mapAvailability("SUPPORTED_APK_TOO_OLD")
        )
        assertEquals(
            RealArAvailabilityUi.NEEDS_INSTALL_OR_UPDATE,
            RealArGateMapper.mapAvailability("SUPPORTED_NOT_INSTALLED")
        )
        assertEquals(
            RealArAvailabilityUi.CHECKING,
            RealArGateMapper.mapAvailability("UNKNOWN_CHECKING")
        )
        assertEquals(
            RealArAvailabilityUi.UNAVAILABLE,
            RealArGateMapper.mapAvailability("UNKNOWN_ERROR")
        )
        assertEquals(
            RealArAvailabilityUi.UNSUPPORTED_DEVICE,
            RealArGateMapper.mapAvailability("UNSUPPORTED_DEVICE_NOT_CAPABLE")
        )
    }

    @Test
    fun permissionStateMapping() {
        assertEquals(
            RealArCameraPermissionUi.GRANTED,
            RealArGateMapper.mapCameraPermission(true, shouldShowRationale = false, hasRequestedOnce = false)
        )
        assertEquals(
            RealArCameraPermissionUi.NOT_REQUESTED,
            RealArGateMapper.mapCameraPermission(false, shouldShowRationale = false, hasRequestedOnce = false)
        )
        assertEquals(
            RealArCameraPermissionUi.DENIED_CAN_RETRY,
            RealArGateMapper.mapCameraPermission(false, shouldShowRationale = true, hasRequestedOnce = true)
        )
        assertEquals(
            RealArCameraPermissionUi.DENIED_PERMANENT,
            RealArGateMapper.mapCameraPermission(false, shouldShowRationale = false, hasRequestedOnce = true)
        )
    }

    @Test
    fun unsupportedDeviceOffersVirtualFallbackAndCannotStartSession() {
        val avail = RealArAvailabilityUi.UNSUPPORTED_DEVICE
        assertTrue(RealArGateMapper.shouldOfferVirtualFallback(avail))
        assertFalse(
            RealArGateMapper.canStartArSession(avail, RealArCameraPermissionUi.GRANTED)
        )
    }

    @Test
    fun deniedCameraOffersVirtualFallbackAndCannotStartSession() {
        assertTrue(
            RealArGateMapper.shouldOfferVirtualFallback(RealArCameraPermissionUi.DENIED_CAN_RETRY)
        )
        assertTrue(
            RealArGateMapper.shouldOfferVirtualFallback(RealArCameraPermissionUi.DENIED_PERMANENT)
        )
        assertFalse(
            RealArGateMapper.canStartArSession(
                RealArAvailabilityUi.SUPPORTED,
                RealArCameraPermissionUi.DENIED_CAN_RETRY
            )
        )
    }

    @Test
    fun backNavigationUsesPopSemantics() {
        assertTrue(RealArNavigation.backReturnsToPreviousScreen())
        // Side route — not a workflow step replacement.
        assertFalse(Routes.SENSOR_REAL_AR == Routes.SENSOR_COVERAGE)
        assertFalse(Routes.SENSOR_REAL_AR == Routes.SENSOR_OPTIMIZE)
    }

    @Test
    fun virtualFallbackMapsToVirtualPreviewRoute() {
        assertEquals(Routes.SENSOR_VIRTUAL_PREVIEW, RealArNavigation.virtualFallbackRoute())
        assertEquals(Routes.SENSOR_VIRTUAL_PREVIEW, RealArNavigation.VIRTUAL_FALLBACK_ROUTE)
    }

    @Test
    fun sessionStartsOnlyWhenSupportedAndCameraGranted() {
        assertTrue(
            RealArGateMapper.canStartArSession(
                RealArAvailabilityUi.SUPPORTED,
                RealArCameraPermissionUi.GRANTED
            )
        )
        assertFalse(
            RealArGateMapper.canStartArSession(
                RealArAvailabilityUi.CHECKING,
                RealArCameraPermissionUi.GRANTED
            )
        )
    }
}
