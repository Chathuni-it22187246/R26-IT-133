package com.greenhands.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.greenhands.app.ar.domain.ArTabletopPhase
import com.greenhands.app.ar.render.ArEnvironmentalFieldHost
import com.greenhands.app.ar.ui.ArEnvironmentalFieldOverlay
import com.greenhands.app.equipment.data.InMemoryEquipmentLayoutRepository
import com.greenhands.app.equipment.ui.EquipmentPlacementViewModel
import com.greenhands.app.equipment.ui.OverlayClimate
import com.greenhands.app.heat.domain.HeatFormulas
import com.greenhands.app.heat.visualization.EnvironmentalFieldFrame
import com.greenhands.app.heat.visualization.EnvironmentalFieldLayer
import com.greenhands.app.preview3d.model.DemoGreenhouseProfile
import com.greenhands.app.preview3d.model.PreviewMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class EnvironmentalFieldComposeRegressionTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun overlayHostRecomposesThroughPlacementLayerStartStopAndRepositionWithoutComposerCrash() {
        val vm = demoVm()
        val createCount = AtomicInteger(0)
        val hostRef = AtomicReference<ArEnvironmentalFieldHost?>(null)
        lateinit var setPlaced: (Boolean) -> Unit
        lateinit var setFrame: (EnvironmentalFieldFrame) -> Unit

        composeRule.setContent {
            val placedState = remember { mutableStateOf(false) }
            val frameState = remember { mutableStateOf(vm.environmentalFieldFrame()) }
            setPlaced = { placedState.value = it }
            setFrame = { frameState.value = it }
            MaterialTheme {
                FieldOverlayComposeHarness(
                    engineIdentity = 707,
                    placed = placedState.value,
                    frame = frameState.value,
                    createCount = createCount,
                    hostRef = hostRef,
                    onToggle = { vm.toggleFieldLegendExpanded() }
                )
            }
        }

        composeRule.onNodeWithTag("field_compose_harness").assertIsDisplayed()
        assertEquals(1, createCount.get())

        composeRule.runOnIdle {
            setPlaced(true)
            setFrame(vm.environmentalFieldFrame())
        }
        composeRule.waitForIdle()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("ar_env_legend_visible", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.runOnIdle {
            assertEquals(EnvironmentalFieldLayer.TEMPERATURE, vm.state.value.environmentalLayer)
            assertTrue(vm.environmentalFieldFrame().isEmpty)
        }

        composeRule.runOnIdle {
            vm.setSimulatedTemperature(28.0)
            vm.setSimulatedHumidity(55.0)
            vm.startBehavior()
            setFrame(vm.environmentalFieldFrame())
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertTrue(!vm.environmentalFieldFrame().isEmpty)
        }
        composeRule.runOnIdle {
            vm.toggleFieldLegendExpanded()
            setFrame(vm.environmentalFieldFrame())
        }
        composeRule.waitForIdle()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("ar_env_crop_stage", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.runOnIdle {
            vm.toggleFieldLegendExpanded()
            setFrame(vm.environmentalFieldFrame())
        }
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            vm.setEnvironmentalLayer(EnvironmentalFieldLayer.HUMIDITY)
            setFrame(vm.environmentalFieldFrame())
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(EnvironmentalFieldLayer.HUMIDITY, vm.environmentalFieldFrame().layer)
        }

        composeRule.runOnIdle {
            vm.setEnvironmentalLayer(EnvironmentalFieldLayer.OFF)
            setFrame(vm.environmentalFieldFrame())
        }
        composeRule.waitForIdle()
        assertTrue(
            composeRule.onAllNodesWithTag("ar_env_field_overlay", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        )

        composeRule.runOnIdle {
            vm.setEnvironmentalLayer(EnvironmentalFieldLayer.TEMPERATURE)
            setFrame(vm.environmentalFieldFrame())
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(EnvironmentalFieldLayer.TEMPERATURE, vm.environmentalFieldFrame().layer)
            assertTrue(!vm.environmentalFieldFrame().isEmpty)
        }

        composeRule.runOnIdle {
            vm.stopBehavior()
            setFrame(vm.environmentalFieldFrame())
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertTrue(vm.environmentalFieldFrame().isEmpty)
            assertNull(vm.state.value.physicsSnapshot)
        }

        composeRule.runOnIdle {
            setPlaced(false)
            vm.onArReposition()
            setFrame(vm.environmentalFieldFrame())
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertTrue(vm.environmentalFieldFrame().isEmpty)
        }

        composeRule.runOnIdle {
            setPlaced(true)
            setFrame(vm.environmentalFieldFrame())
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertTrue(vm.environmentalFieldFrame().isEmpty)
            assertEquals(1, createCount.get())
            assertSame(hostRef.get(), hostRef.get())
        }
        val host = hostRef.get()
        assertTrue(host != null)
        composeRule.runOnIdle {
            host!!.destroy()
            host.destroy()
            assertTrue(host.disposeCount >= 1)
        }
        assertEquals(1, createCount.get())
    }

    private fun demoVm(): EquipmentPlacementViewModel {
        val circ = HeatFormulas.circulation(25.0)
        val vm = EquipmentPlacementViewModel(InMemoryEquipmentLayoutRepository())
        vm.attach(
            "grower@greenhands.app",
            "cfg-field-compose-crash",
            DemoGreenhouseProfile.LENGTH_M,
            DemoGreenhouseProfile.WIDTH_M,
            DemoGreenhouseProfile.HEIGHT_M,
            DemoGreenhouseProfile.CROP_ROW_COUNT,
            OverlayClimate(
                crop = com.greenhands.app.heat.model.Crop.TOMATO,
                stage = com.greenhands.app.heat.profile.CropProfileRegistry.stagesFor(
                    com.greenhands.app.heat.model.Crop.TOMATO
                ).first { it.id == "vegetative" },
                temperatureLabel = "25.0°C",
                humidityLabel = "70.0 % RH",
                vpdLabel = "Not published",
                circulation = circ,
                exhaust = HeatFormulas.exhaustFromCirculation(circ),
                fogger = HeatFormulas.fogger(70.0),
                optimalTemperatureC = 25.0,
                optimalHumidityPercent = 70.0
            ),
            PreviewMode.DEMO
        )
        return vm
    }
}

@Composable
private fun FieldOverlayComposeHarness(
    engineIdentity: Int,
    placed: Boolean,
    frame: EnvironmentalFieldFrame,
    createCount: AtomicInteger,
    hostRef: AtomicReference<ArEnvironmentalFieldHost?>,
    onToggle: () -> Unit
) {
    val host = remember(engineIdentity) {
        createCount.incrementAndGet()
        ArEnvironmentalFieldHost(engineIdentity).also { created ->
            created.lifecycle.attach(engineIdentity)
        }
    }
    LaunchedEffect(placed, frame.signature, frame.layer, frame.height) {
        val phase = if (placed) ArTabletopPhase.PLACED else ArTabletopPhase.DETECTING
        host.bind(ArEnvironmentalFieldHost.request(frame, phase))
        if (!placed) host.clear()
    }
    DisposableEffect(host) {
        hostRef.set(host)
        onDispose { host.destroy() }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("field_compose_harness")
    ) {
        ArEnvironmentalFieldOverlay(
            placed = placed,
            model = frame.legend,
            onToggle = onToggle,
            modifier = Modifier.align(Alignment.TopStart)
        )
    }
}
