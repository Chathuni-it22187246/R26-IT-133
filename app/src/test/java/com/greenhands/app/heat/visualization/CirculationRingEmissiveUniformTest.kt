package com.greenhands.app.heat.visualization

import com.greenhands.app.ar.render.ArCirculationColorMaterial
import com.greenhands.app.equipment.data.InMemoryEquipmentLayoutRepository
import com.greenhands.app.equipment.model.EquipmentType
import com.greenhands.app.equipment.ui.EquipmentPlacementViewModel
import com.greenhands.app.equipment.ui.OverlayClimate
import com.greenhands.app.heat.domain.HeatFormulas
import com.greenhands.app.preview3d.model.DemoGreenhouseProfile
import com.greenhands.app.preview3d.model.PreviewMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Regression for Oppo 01:46:05 Filament panic:
 * `uniform named "emissiveFactor" not found` then SIGABRT in libfilament-jni.so.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CirculationRingEmissiveUniformTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val circ = HeatFormulas.circulation(25.0)
    private val climate = OverlayClimate(
        crop = null,
        stage = null,
        temperatureLabel = "25.0°C",
        humidityLabel = "70.0 % RH",
        vpdLabel = "Not published",
        circulation = circ,
        exhaust = HeatFormulas.exhaustFromCirculation(circ),
        fogger = HeatFormulas.fogger(70.0),
        optimalTemperatureC = 25.0,
        optimalHumidityPercent = 70.0
    )

    @Before
    fun setMain() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun reset() {
        Dispatchers.resetMain()
    }

    @Test
    fun sceneViewColorInstanceForbidsEmissiveFactor() {
        assertEquals("MaterialLoader.createColorInstance", ArCirculationColorMaterial.HOST_MATERIAL)
        assertFalse(CirculationRingAppearance.WRITES_EMISSIVE_FACTOR)
        assertFalse(ArCirculationColorMaterial.writesEmissiveFactor())
        assertFalse(ArCirculationColorMaterial.maySetParameter("emissiveFactor"))
        assertTrue("emissiveFactor" in ArCirculationColorMaterial.forbiddenUniforms)
        assertTrue("color" in ArCirculationColorMaterial.allowedUniforms)
        assertFalse("emissiveFactor" in ArCirculationColorMaterial.allowedUniforms)
    }

    @Test
    fun startVisualizationColorWritesNeverRequestEmissiveFactor() = runTest {
        val vm = demoVm("cfg-emissive-start")
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.CIRCULATION_FAN)
        vm.setSimulatedTemperature(30.0)
        vm.setSimulatedHumidity(60.0)
        vm.startBehavior()
        val group = vm.circulationEffectFrame().groups.single()
        val color = CirculationRingAnimator.colorAtDistance(group, 1.0, vm.state.value.displayedFieldSnapshot)
        val write = ArCirculationColorMaterial.frameColorWrite(color, fade = 1f)
        assertEquals(setOf("color"), write.uniformNames)
        assertFalse(write.requestsEmissiveFactor)
        assertTrue(write.alpha > 0f)
        assertFalse(ArCirculationColorMaterial.NATIVE_FILAMENT_ABORT_IS_CATCHABLE)
    }

    @Test
    fun zeroOneAndTwoFanFramesDoNotWriteEmissiveUniforms() = runTest {
        val vm = demoVm("cfg-emissive-counts")
        vm.enterPlacementMode()
        vm.setSimulatedTemperature(30.0)
        vm.setSimulatedHumidity(60.0)
        vm.startBehavior()
        assertNoEmissiveWrites(vm)

        vm.tapPalette(EquipmentType.CIRCULATION_FAN)
        vm.startBehavior()
        assertNoEmissiveWrites(vm)
        assertEquals(1, vm.circulationEffectFrame().groups.size)

        vm.duplicateSelected()
        vm.startBehavior()
        assertNoEmissiveWrites(vm)
        assertEquals(2, vm.circulationEffectFrame().groups.size)
    }

    @Test
    fun scientificBandColorsStillReachTheColorUniform() {
        val classified = TemperatureLegendClassifier.blendedColor(
            30.0, 25.0, circ.cdp, circ.con, HeatFormulas.exhaustFromCirculation(circ).eon
        )
        val write = ArCirculationColorMaterial.frameColorWrite(classified, 0.8f)
        assertEquals(classified.red, write.red, 0f)
        assertEquals(classified.green, write.green, 0f)
        assertEquals(classified.blue, write.blue, 0f)
        assertEquals(classified.alpha * 0.8f, write.alpha, 1e-6f)
        assertFalse(write.requestsEmissiveFactor)
        assertEquals("color", ArCirculationColorMaterial.COLOR_UNIFORM)
        assertEquals("emissiveFactor", ArCirculationColorMaterial.EMISSIVE_FACTOR_UNIFORM)
    }

    private fun assertNoEmissiveWrites(vm: EquipmentPlacementViewModel) {
        val frame = vm.circulationEffectFrame()
        if (frame.isEmpty) {
            val write = ArCirculationColorMaterial.frameColorWrite(VisualizationRgb.UNAVAILABLE, 1f)
            assertFalse(write.requestsEmissiveFactor)
            assertEquals(setOf("color"), write.uniformNames)
            return
        }
        frame.groups.forEach { group ->
            val color = CirculationRingAnimator.colorAtDistance(
                group, group.minDistanceM, vm.state.value.displayedFieldSnapshot
            )
            val write = ArCirculationColorMaterial.frameColorWrite(color, 1f)
            assertFalse(write.requestsEmissiveFactor)
            assertEquals(setOf("color"), write.uniformNames)
        }
    }

    private fun demoVm(configId: String): EquipmentPlacementViewModel {
        val vm = EquipmentPlacementViewModel(InMemoryEquipmentLayoutRepository())
        vm.attach(
            "grower@greenhands.app",
            configId,
            DemoGreenhouseProfile.LENGTH_M,
            DemoGreenhouseProfile.WIDTH_M,
            DemoGreenhouseProfile.HEIGHT_M,
            DemoGreenhouseProfile.CROP_ROW_COUNT,
            climate,
            PreviewMode.DEMO
        )
        return vm
    }
}
