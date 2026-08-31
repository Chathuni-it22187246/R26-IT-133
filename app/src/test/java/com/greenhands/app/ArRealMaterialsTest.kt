package com.greenhands.app

import com.greenhands.app.sensor.ar.ArRealMaterials
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM-safe checks for Real AR material policy (no Filament/SceneView on classpath).
 */
class ArRealMaterialsTest {

    @Test
    fun matteDielectricConstantsAreStable() {
        assertEquals(0f, ArRealMaterials.METALLIC, 0f)
        assertEquals(1f, ArRealMaterials.ROUGHNESS, 0f)
        assertEquals(0.35f, ArRealMaterials.REFLECTANCE, 0f)
        assertTrue(ArRealMaterials.OVERLAY_ALPHA < 1f)
    }

    @Test
    fun coverageUsesTranslucentPerCellBodiesNotFullFootprint() {
        val coverage = java.io.File(
            "src/main/java/com/greenhands/app/sensor/ar/ArCoverageNodes.kt"
        ).readText()
        assertTrue(coverage.contains("CELL_BODY_HEIGHT"))
        assertTrue(coverage.contains("_body"))
        assertTrue(coverage.contains("cellGeometry"))
        assertFalse(coverage.contains("PlaneNode("))
        assertFalse(coverage.contains("structurePanels("))
        assertFalse(coverage.contains("QUAD_THICKNESS"))
    }

    @Test
    fun rendererUsesCentralMaterialFactory() {
        val text = java.io.File(
            "src/main/java/com/greenhands/app/sensor/ui/RealGreenhouseArScreen.kt"
        ).readText()
        assertTrue(text.contains("ArRealMaterials.opaqueColor"))
        assertTrue(text.contains("ArRealMaterials.transparentOverlay") || text.contains("arOverlay"))
        assertTrue(text.contains("CELL_BODY_ALPHA"))
        assertTrue(text.contains("rememberEnvironment(environmentLoader"))
        assertTrue(text.contains("ENVIRONMENTAL_HDR"))
        assertTrue(text.contains("showPlaneRenderer"))
        assertFalse(text.contains("0.30f / max"))
        assertTrue(text.contains("ArRealScale") || text.contains("TARGET_LONGEST") || text.contains("rootScale"))
    }

    @Test
    fun cellBodyAlphaIsTranslucent() {
        assertTrue(ArRealMaterials.CELL_BODY_ALPHA < 1f)
        assertTrue(ArRealMaterials.CELL_EDGE_ALPHA < 1f)
        assertTrue(ArRealMaterials.CELL_EDGE_ALPHA > ArRealMaterials.CELL_BODY_ALPHA)
    }
}
