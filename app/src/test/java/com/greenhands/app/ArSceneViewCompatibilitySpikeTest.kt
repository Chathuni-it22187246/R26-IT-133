package com.greenhands.app

import com.greenhands.app.sensor.ar.spike.ArSceneViewCompatibilitySpike
import com.greenhands.app.ui.navigation.Routes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 10E-A: dependency/version documentation + virtual path unchanged.
 * Avoids loading Android View / native Filament classes on the JVM unit-test classpath.
 */
class ArSceneViewCompatibilitySpikeTest {

    @Test
    fun spikePinsCompatibleArsceneviewVersion() {
        assertEquals("2.2.1", ArSceneViewCompatibilitySpike.ARSCENEVIEW_VERSION)
        assertEquals("1.43.0", ArSceneViewCompatibilitySpike.EXPECTED_ARCORE_VERSION)
    }

    @Test
    fun virtualGreenhouseRouteStillExistsWithoutRealArRoute() {
        assertEquals("sensor_virtual_preview", Routes.SENSOR_VIRTUAL_PREVIEW)
        assertTrue(Routes.isSensorContentRoute(Routes.SENSOR_VIRTUAL_PREVIEW))
        // Real AR route is added in 10E-B; 10E-A only verified SceneView compiles.
        assertTrue(Routes.SENSOR_VIRTUAL_PREVIEW.isNotBlank())
    }
}
