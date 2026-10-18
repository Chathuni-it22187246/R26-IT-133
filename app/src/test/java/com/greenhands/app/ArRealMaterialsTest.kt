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

    fun coverageUsesOutlineNodesNotFullFootprintSlabs() {

        val coverage = java.io.File(

            "src/main/java/com/greenhands/app/sensor/ar/ArCoverageNodes.kt"

        ).readText()

        assertTrue(coverage.contains("addOutlineBar"))

        assertFalse(coverage.contains("QUAD_THICKNESS"))

    }



    @Test

    fun rendererUsesCentralMaterialFactory() {

        val text = java.io.File(

            "src/main/java/com/greenhands/app/sensor/ui/RealGreenhouseArScreen.kt"

        ).readText()

        assertTrue(text.contains("ArRealMaterials.opaqueColor"))

        assertTrue(text.contains("ArSceneLighting") || text.contains("createFallbackEnvironment"))

        assertTrue(text.contains("AMBIENT_INTENSITY") || text.contains("ArSceneLighting"))

        assertTrue(text.contains("showPlaneRenderer"))

        assertFalse(text.contains("scaleFactor"))

        assertFalse(text.contains("ArTabletopScale"))

    }

}

